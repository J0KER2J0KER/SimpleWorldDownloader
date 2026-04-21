package com.j0ker2j0ker.swd.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.j0ker2j0ker.swd.client.SwdClient;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SaveManager {

    private static final int DATA_VERSION = 4790;
    private static final String VERSION_NAME = "26.1.2";
    private static final byte IS_SNAPSHOT = (byte)0;

    private static final int PLAYER_INVENTORY_SLOTS = 36;
    private static final int DOUBLE_CHEST_SLOTS = 54;
    private static final int SINGLE_CHEST_SLOTS = 27;
    private static final long META_FLUSH_INTERVAL_MS = 5000L;
    private static final DateTimeFormatter ADVANCEMENT_TIME_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String OVERWORLD = "overworld";
    private static final String NETHER = "the_nether";
    private static final String END = "the_end";


    private static final Queue<ChunkSaveTask> saveQueue = new ConcurrentLinkedQueue<>();
    public static Thread saveThread = null;

    public static volatile boolean isSaving = false;
    public static String name;
    public static Path path;

    private static CompoundTag cacheRootTag;
    private static Path cachePlayerDatPath;

    private static HashMap<BlockPos, List<ItemStack>> cacheBlockInventories;
    private static HashMap<UUID, List<ItemStack>> cacheEntityInventories;
    private static HashMap<UUID, CompoundTag> cacheEntityOverrides;
    private static UUID cachePlayerUuid;
    private static JsonObject cachedStatsByType;
    private static JsonObject cachedAdvancements;
    private static Set<String> removedAdvancements;
    private static boolean advancementsResetThisSession;
    private static boolean statsDirty;
    private static boolean advancementsDirty;
    private static long lastMetaFlushTimeMs;

    public static Object lastClicked;

    private static final Minecraft mc = Minecraft.getInstance();
    private static DynamicOps<Tag> ops;

    public static void toggle() {
        if(isSaving) stop();
        else start();
    }

    public static void start() {
        if(isSaving || mc.player == null) return;

        ops = Objects.requireNonNull(mc.level).registryAccess().createSerializationContext(NbtOps.INSTANCE);
        isSaving = true;

        determineWorldName();
        path = mc.getLevelSource().getBaseDir().resolve(name);

        setupWorldFolder();
        createPlayerDataCache(path);

        cacheBlockInventories = new HashMap<>();
        cacheEntityInventories = new HashMap<>();
        cacheEntityOverrides = new HashMap<>();
        cachePlayerUuid = mc.player.getUUID();
        cachedStatsByType = new JsonObject();
        cachedAdvancements = new JsonObject();
        removedAdvancements = new HashSet<>();
        advancementsResetThisSession = false;
        statsDirty = false;
        advancementsDirty = false;
        lastMetaFlushTimeMs = 0L;

        bootstrapAdvancementsFromClientCache();

        printStatus("§a> Started saving chunks...");
        saveChunksAround(12);
    }

    public static void stop() {
        if (!isSaving) return;
        flushPlayerMetaFiles(true);
        isSaving = false;

        createPlayerDataFile();
        printStatus("§c> Stopped saving chunks.");

        if (cacheBlockInventories != null) cacheBlockInventories.clear();
        if (cacheEntityInventories != null) cacheEntityInventories.clear();
        if (cacheEntityOverrides != null) cacheEntityOverrides.clear();
        cachePlayerUuid = null;
        cachedStatsByType = null;
        cachedAdvancements = null;
        removedAdvancements = null;
        advancementsResetThisSession = false;
        statsDirty = false;
        advancementsDirty = false;
        lastMetaFlushTimeMs = 0L;
    }

    public static void cacheAwardStatsPacket(ClientboundAwardStatsPacket packet) {
        if (!isSaving || path == null || mc.player == null || mc.isLocalServer() || mc.getCurrentServer() == null) return;
        if (cachedStatsByType == null) cachedStatsByType = new JsonObject();
        if (cachePlayerUuid == null) cachePlayerUuid = mc.player.getUUID();

        boolean changed = false;
        for (Object2IntMap.Entry<Stat<?>> entry : packet.stats().object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            String typeId = getStatTypeId(stat);
            String valueId = getStatValueId(stat);
            if (typeId == null || valueId == null) continue;

            JsonObject typeObject = getOrCreateJsonObject(cachedStatsByType, typeId);
            int incomingValue = entry.getIntValue();
            int existingValue = getInt(typeObject, valueId, Integer.MIN_VALUE);
            if (incomingValue > existingValue) {
                typeObject.addProperty(valueId, incomingValue);
                changed = true;
            }
        }

        if (changed) {
            statsDirty = true;
            maybeFlushPlayerMetaFiles();
        }
    }

    public static void cacheAdvancementPacket(ClientboundUpdateAdvancementsPacket packet) {
        if (!isSaving || path == null || mc.player == null || mc.isLocalServer() || mc.getCurrentServer() == null) return;
        if (cachedAdvancements == null) cachedAdvancements = new JsonObject();
        if (removedAdvancements == null) removedAdvancements = new HashSet<>();
        if (cachePlayerUuid == null) cachePlayerUuid = mc.player.getUUID();

        boolean changed = false;
        boolean hadProgressUpdates = !packet.getProgress().isEmpty();
        boolean hadRemovals = !packet.getRemoved().isEmpty();

        if (packet.shouldReset()) {
            cachedAdvancements = new JsonObject();
            removedAdvancements.clear();
            advancementsResetThisSession = true;
            if (hadProgressUpdates || hadRemovals) {
                changed = true;
            }
        }

        for (Identifier removedId : packet.getRemoved()) {
            String key = removedId.toString();
            cachedAdvancements.remove(key);
            removedAdvancements.add(key);
            changed = true;
        }

        packet.getProgress().forEach((advancementId, progress) -> {
            String key = advancementId.toString();
            JsonObject incoming = buildAdvancementJson(progress);
            JsonObject existing = getObject(cachedAdvancements, key);
            JsonObject merged = mergeAdvancementObjects(existing, incoming);
            cachedAdvancements.add(key, merged);
            removedAdvancements.remove(key);
        });

        if (hadProgressUpdates) {
            changed = true;
        }

        if (changed) {
            advancementsDirty = true;
            maybeFlushPlayerMetaFiles();
        }
    }

    @SuppressWarnings("unchecked")
    private static void bootstrapAdvancementsFromClientCache() {
        if (!isSaving || mc.getConnection() == null || cachedAdvancements == null) return;

        try {
            ClientPacketListener connection = mc.getConnection();
            ClientAdvancements clientAdvancements = connection.getAdvancements();

            var progressField = ClientAdvancements.class.getDeclaredField("progress");
            progressField.setAccessible(true);

            Map<AdvancementHolder, AdvancementProgress> progressMap =
                    (Map<AdvancementHolder, AdvancementProgress>) progressField.get(clientAdvancements);

            if (progressMap == null || progressMap.isEmpty()) return;

            boolean seeded = false;
            for (Map.Entry<AdvancementHolder, AdvancementProgress> entry : progressMap.entrySet()) {
                AdvancementHolder holder = entry.getKey();
                AdvancementProgress progress = entry.getValue();
                if (holder == null || progress == null) continue;

                String key = holder.id().toString();
                JsonObject existing = getObject(cachedAdvancements, key);
                JsonObject merged = mergeAdvancementObjects(existing, buildAdvancementJson(progress));
                cachedAdvancements.add(key, merged);
                seeded = true;
            }

            if (seeded) {
                advancementsDirty = true;
            }
        } catch (ReflectiveOperationException e) {
            SwdClient.LOGGER.warn("Failed to bootstrap advancements from client cache", e);
        }
    }

    public static void onScreenClosed(Screen screen) {
        if (!SaveManager.isSaving) return;

        if (screen instanceof MerchantScreen merchantScreen && lastClicked instanceof AbstractVillager villager) {
            printStatus("§a> Villager trade data saved.");
            cacheVillagerMerchantData(villager, merchantScreen.getMenu());
            return;
        }

        String title = screen.getTitle().getString();

        if (screen instanceof AbstractContainerScreen<?> container && title.equals(Component.translatable("container.enderchest").getString())) {
            printStatus("§a> Enderchest content saved.");
            cacheEnderItems(container.getMenu().getItems());
            return;
        }

        List<ItemStack> items = extractContainerItems(screen);
        if (items == null) return;

        trimPlayerInventory(items);

        if (lastClicked instanceof BlockPos blockPos) {
            handleBlockContainer(blockPos, items);
        } else if (lastClicked instanceof net.minecraft.world.entity.Entity entity) {
            handleEntityContainer(entity, items);
        }
    }

    private static List<ItemStack> extractContainerItems(Screen screen) {
        if (screen instanceof AbstractFurnaceScreen<?> fs) {
            printStatus("§a> Container content saved.");
            return fs.getMenu().getItems();
        }
        if (screen instanceof AbstractContainerScreen<?> cs && isSupportedContainerScreen(screen)) {
            printStatus("§a> Container content saved.");
            return cs.getMenu().getItems();
        }
        if (screen instanceof HorseInventoryScreen hs) {
            printStatus("§a> Container content saved.");
            List<ItemStack> items = hs.getMenu().getItems();
            items.removeFirst();
            items.removeFirst();
            return items;
        }
        return null;
    }

    private static boolean isSupportedContainerScreen(Screen screen) {
        String simpleName = screen.getClass().getSimpleName();
        return switch (simpleName) {
            case "ContainerScreen",
                 "HopperScreen",
                 "ShulkerBoxScreen",
                 "DispenserScreen",
                 "DropperScreen",
                 "BrewingStandScreen",
                 "CrafterScreen" -> true;
            default -> false;
        };
    }

    private static void trimPlayerInventory(List<ItemStack> items) {
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS && !items.isEmpty(); i++) {
            items.removeLast();
        }
    }

    private static void handleBlockContainer(BlockPos pos, List<ItemStack> items) {
        if (cachePairedChestInventories(pos, items)) {
            return;
        }

        cacheBlockInventories.put(pos, new ArrayList<>(items)); // defensive copy
        saveChunkNow(pos);
    }

    private static boolean cachePairedChestInventories(BlockPos pos, List<ItemStack> items) {
        if (mc.level == null || items.size() < DOUBLE_CHEST_SLOTS) return false;

        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)
                || !state.hasProperty(ChestBlock.TYPE)
                || !state.hasProperty(ChestBlock.FACING)) {
            return false;
        }

        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) return false;

        Direction facing = state.getValue(ChestBlock.FACING);
        Direction offset = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
        BlockPos partnerPos = pos.relative(offset);

        BlockState partnerState = mc.level.getBlockState(partnerPos);
        if (!(partnerState.getBlock() instanceof ChestBlock)
                || !partnerState.hasProperty(ChestBlock.TYPE)
                || partnerState.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return false;
        }

        List<ItemStack> leftHalf = new ArrayList<>(items.subList(0, SINGLE_CHEST_SLOTS));
        List<ItemStack> rightHalf = new ArrayList<>(items.subList(SINGLE_CHEST_SLOTS, DOUBLE_CHEST_SLOTS));

        if (type == ChestType.LEFT) {
            cacheBlockInventories.put(pos, leftHalf);
            cacheBlockInventories.put(partnerPos, rightHalf);
        } else {
            cacheBlockInventories.put(pos, rightHalf);
            cacheBlockInventories.put(partnerPos, leftHalf);
        }

        saveChunkNow(pos);
        saveChunkNow(partnerPos);
        return true;
    }

    private static void handleEntityContainer(net.minecraft.world.entity.Entity entity, List<ItemStack> items) {
        cacheEntityInventories.put(entity.getUUID(), new ArrayList<>(items));
        saveChunkNow(entity.blockPosition());
    }

    private static void cacheVillagerMerchantData(AbstractVillager merchant, MerchantMenu menu) {
        CompoundTag overlay = new CompoundTag();

        MerchantOffers offers = menu.getOffers();
        saveMerchantOffers(offers).ifPresent(tag -> overlay.put("Offers", tag));

        overlay.putInt("Xp", menu.getTraderXp());
        overlay.putInt("RestocksToday", readIntField(merchant, "numberOfRestocksToday"));
        overlay.putLong("LastRestock", readLongField(merchant, "lastRestockGameTime"));
        overlay.putLong("LastGossipDecay", readLongField(merchant, "lastGossipDecayTime"));

        if (merchant instanceof Villager villager) {
            VillagerData data = villager.getVillagerData().withLevel(menu.getTraderLevel());
            saveVillagerData(data).ifPresent(tag -> overlay.put("VillagerData", tag));
            overlay.putBoolean("VillagerDataFinalized", true);
        }

        cacheEntityOverrides.put(merchant.getUUID(), overlay);
        saveChunkNow(merchant.blockPosition());
    }

    private static void saveChunkNow(BlockPos pos) {
        LevelChunk wc = mc.level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (wc != null) {
            saveChunkToRegion(path, wc, false, mc.level.dimension());
        }
    }

    public static CompoundTag buildEntityChunkNbt(LevelChunk wc) {
        CompoundTag chunk = new CompoundTag();

        chunk.putInt("DataVersion", DATA_VERSION);

        ChunkPos pos = wc.getPos();
        chunk.putIntArray("Position", new int[]{pos.x(), pos.z()});

        ListTag entityList = new ListTag();

        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                pos.getMinBlockX(), wc.getLevel().getMinY(), pos.getMinBlockZ(),
                pos.getMaxBlockX(), wc.getLevel().getMaxY(), pos.getMaxBlockZ()
        );

        wc.getLevel().getEntities(null, box).forEach(entity -> {
            if (entity instanceof net.minecraft.world.entity.player.Player) return;

            CompoundTag entityNbt = saveEntityToNbt(entity);
            injectCachedEntityInventory(entity, entityNbt);
            entityList.add(entityNbt);
        });

        chunk.put("Entities", entityList);

        return chunk;
    }

    public static CompoundTag buildChunkNbt(LevelChunk wc) {
        CompoundTag chunk = createBaseChunkNbt(wc);

        ListTag blockEntities = new ListTag();
        wc.getBlockEntities().forEach((bePos, be) -> {
            CompoundTag beTag = be.saveWithFullMetadata(wc.getLevel().registryAccess());
            injectCachedBlockInventory(bePos, beTag);
            blockEntities.add(beTag);
        });
        chunk.put("block_entities", blockEntities);
        return chunk;
    }

    private static CompoundTag createBaseChunkNbt(LevelChunk wc) {
        ChunkPos pos = wc.getPos();
        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion", DATA_VERSION);
        chunk.putInt("xPos", pos.x());
        chunk.putInt("zPos", pos.z());
        chunk.putInt("yPos", wc.getMinSectionY());
        chunk.putString("Status", "full");

        var registries = wc.getLevel().registryAccess();

        ListTag sections = new ListTag();
        LevelChunkSection[] sectionArray = wc.getSections();
        for (int secIndex = 0; secIndex < sectionArray.length; secIndex++) {
            LevelChunkSection section = sectionArray[secIndex];
            if (section == null) continue;

            CompoundTag sec = new CompoundTag();
            sec.putByte("Y", (byte) (secIndex + wc.getMinSectionY()));

            Strategy<BlockState> blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);

            var blockCodec = PalettedContainer.codecRW(
                    BlockState.CODEC,
                    blockStrategy,
                    Blocks.AIR.defaultBlockState()
            );

            blockCodec.encodeStart(ops, section.getStates())
                    .result().ifPresent(tag -> sec.put("block_states", tag));

            var biomeRegistry = registries.lookupOrThrow(Registries.BIOME);
            Strategy<Holder<Biome>> biomeStrategy = Strategy.createForBiomes(biomeRegistry.asHolderIdMap());

            var biomeCodec = PalettedContainer.codecRW(
                    Biome.CODEC,
                    biomeStrategy,
                    biomeRegistry.getOrThrow(Biomes.PLAINS)
            );

            biomeCodec.encodeStart(ops, (PalettedContainer<Holder<Biome>>) section.getBiomes())
                    .result().ifPresent(tag -> sec.put("biomes", tag));

            sections.add(sec);
        }
        chunk.put("sections", sections);

        return chunk;
    }

    private static CompoundTag saveEntityToNbt(net.minecraft.world.entity.Entity entity) {
        try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(
                entity.problemPath(),
                com.mojang.logging.LogUtils.getLogger())) {

            TagValueOutput output = TagValueOutput.createWithContext(
                    reporter,
                    entity.level().registryAccess()
            );

            entity.save(output);

            return output.buildResult();
        }
    }

    private static void injectCachedBlockInventory(BlockPos pos, CompoundTag beTag) {
        if (cacheBlockInventories.containsKey(pos)) {
            beTag.put("Items", buildItemsListTag(cacheBlockInventories.get(pos)));
        }
    }

    private static void injectCachedEntityInventory(net.minecraft.world.entity.Entity entity, CompoundTag entityNbt) {
        if (cacheEntityInventories.containsKey(entity.getUUID())) {
            entityNbt.put("Items", buildItemsListTag(cacheEntityInventories.get(entity.getUUID())));
        }
        CompoundTag override = cacheEntityOverrides.get(entity.getUUID());
        if (override != null) {
            entityNbt.merge(override.copy());
        }
    }

    private static Optional<CompoundTag> saveMerchantOffers(MerchantOffers offers) {
        if (offers.isEmpty()) return Optional.empty();
        return MerchantOffers.CODEC.encodeStart(ops, offers)
                .resultOrPartial(err -> System.err.println("Failed to encode merchant offers: " + err))
                .map(tag -> (CompoundTag) tag);
    }

    private static Optional<CompoundTag> saveVillagerData(VillagerData data) {
        return VillagerData.CODEC.encodeStart(ops, data)
                .resultOrPartial(err -> System.err.println("Failed to encode villager data: " + err))
                .map(tag -> (CompoundTag) tag);
    }

    private static long readLongField(Object target, String fieldName) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getLong(target);
        } catch (ReflectiveOperationException e) {
            return 0L;
        }
    }

    private static int readIntField(Object target, String fieldName) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    private static ListTag buildItemsListTag(List<ItemStack> items) {
        ListTag list = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                int finalI = i;
                saveItem(stack, ops).ifPresent(tag -> {
                    tag.putByte("Slot", (byte) finalI);
                    list.add(tag);
                });
            }
        }
        return list;
    }

    private static void setupWorldFolder() {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                createLevelDat(path, name, mc.player);

                if (mc.getCurrentServer() != null && mc.getCurrentServer().getIconBytes() != null) {
                    byte[] icon = mc.getCurrentServer().getIconBytes();
                    Path iconPath = path.resolve("icon.png");

                    try (FileOutputStream fos = new FileOutputStream(iconPath.toFile())) {
                        fos.write(icon);
                    }
                }
            }

            SwdWorldMarker.writeMarker(path);
        } catch (IOException e) {
            SwdClient.LOGGER.error("Can't create save directory or write icon!", e);
        }

        if(Minecraft.getInstance().getCurrentServer() != null && Minecraft.getInstance().getCurrentServer().getResourcePackStatus().name().equalsIgnoreCase("ENABLED")) {
            Path packTempPath = SwdClient.resourcepack_locations;
            Path pathResourcepacks = path.resolve("resourcepacks");
            if(!Files.exists(pathResourcepacks)) {
                try {
                    Files.createDirectory(pathResourcepacks);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            Path packTargetPath = pathResourcepacks.resolve("resources.zip");
            try {
                if(packTempPath != null) {
                    Files.copy(packTempPath, packTargetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void determineWorldName() {
        if(SwdClient.CONFIG.saveWorldTo.isEmpty()) {
            if(mc.getCurrentServer() != null) name = mc.getCurrentServer().ip.replaceAll("[\\\\/:*?\"<>|]", "_");
            else {
                if(mc.getSingleplayerServer() == null) name = "Replay Mod";
                else if(mc.getSingleplayerServer().getWorldData().getLevelName().equalsIgnoreCase("Replay")) name = "Flashback";
                else name = mc.getSingleplayerServer().getWorldData().getLevelName().replaceAll("[\\\\/:*?\"<>|]", "_");
            }
            Path saves = Paths.get("saves");
            if(Files.exists(saves.resolve(name))) {
                int i = 1;
                while(Files.exists(saves.resolve(name + " " + i))) i++;
                name += " " + i;
            }
        }else {
            name = SwdClient.CONFIG.saveWorldTo;
        }
    }

    private static void createPlayerDataCache(Path path) {
        Path playerdataPath;
        try {
            playerdataPath = Files.createDirectories(path.resolve("players").resolve("data"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(mc.level == null || mc.player == null) return;

        CompoundTag root = new CompoundTag();

        CompoundTag brain = new CompoundTag();
        brain.put("memories", new ListTag());
        root.put("Brain", brain);

        root.putInt("HurtByTimestamp", 0);
        root.putShort("SleepTimer", (short) mc.player.getSleepTimer());
        if(mc.player.isInvulnerable()) root.putByte("Invulnerable", (byte) 1);
        else root.putByte("Invulnerable", (byte) 0);
        if(mc.player.isFallFlying()) root.putByte("FallFlying", (byte) 1);
        else root.putByte("FallFlying", (byte) 0);
        root.putInt("PortalCooldown", mc.player.getPortalCooldown());
        root.putFloat("AbsorptionAmount", mc.player.getAbsorptionAmount());

        CompoundTag abilities = new CompoundTag();
        Abilities ab = mc.player.getAbilities();
        if(ab.invulnerable) abilities.putByte("invulnerable", (byte) 1);
        else  abilities.putByte("invulnerable", (byte) 0);
        if(ab.mayfly) abilities.putByte("mayfly", (byte) 1);
        else abilities.putByte("mayfly", (byte) 0);
        if(ab.instabuild) abilities.putByte("instabuild", (byte) 1);
        else abilities.putByte("instabuild", (byte) 0);
        abilities.putFloat("walkSpeed", ab.getWalkingSpeed());
        if (ab.mayBuild) abilities.putByte("mayBuild", (byte) 1);
        else abilities.putByte("mayBuild", (byte) 0);
        if(ab.flying) abilities.putByte("flying", (byte) 1);
        else abilities.putByte("flying", (byte) 0);
        abilities.putFloat("flySpeed", ab.getFlyingSpeed());
        root.put("abilities", abilities);

        CompoundTag recipeBook = new CompoundTag();
        recipeBook.put("recipes", new ListTag());
        recipeBook.put("toBeDisplayed", new ListTag());
        root.put("recipeBook", recipeBook);

        root.putShort("DeathTime", (short) mc.player.deathTime);
        root.putInt("XpSeed", 0);
        root.putInt("XpTotal", mc.player.totalExperience);
        root.putIntArray("UUID",  new int[]{0, 0, 0, 0});
        if(mc.player.gameMode() == null) root.putInt("playerGameType", 1);
        else root.putInt("playerGameType", Objects.requireNonNull(mc.player.gameMode()).getId());
        root.putByte("seenCredits", (byte) 0);

        ListTag motion = new ListTag();
        Vec3 currentMotion = mc.player.getDeltaMovement();
        motion.add(DoubleTag.valueOf(currentMotion.x));
        motion.add(DoubleTag.valueOf(currentMotion.y));
        motion.add(DoubleTag.valueOf(currentMotion.z));
        root.put("Motion", motion);

        root.putFloat("Health", mc.player.getHealth());
        root.putFloat("foodSaturationLevel", mc.player.getFoodData().getSaturationLevel());

        CompoundTag equipment = new CompoundTag();
        saveItem(mc.player.getInventory().getItem(39), ops).ifPresent(t -> equipment.put("head", t));
        saveItem(mc.player.getInventory().getItem(38), ops).ifPresent(t -> equipment.put("chest", t));
        saveItem(mc.player.getInventory().getItem(37), ops).ifPresent(t -> equipment.put("legs", t));
        saveItem(mc.player.getInventory().getItem(36), ops).ifPresent(t -> equipment.put("feet", t));
        saveItem(mc.player.getOffhandItem(), ops).ifPresent(t -> equipment.put("offhand", t));

        root.put("equipment", equipment);

        root.putDouble("fall_distance", mc.player.fallDistance);
        root.putShort("Air", (short) mc.player.getAirSupply());
        if(mc.player.onGround()) root.putByte("ground", (byte) 1);
        else root.putByte("ground", (byte) 0);
        root.putString("Dimension", mc.level.dimension().identifier().toString());

        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(mc.player.getYRot()));
        rotation.add(FloatTag.valueOf(mc.player.getXRot()));
        root.put("Rotation", rotation);

        root.putInt("XpLevel", mc.player.experienceLevel);
        root.putInt("current_impulse_context_reset_grace_time", 0);

        CompoundTag warden_spawn_tracker = new CompoundTag();
        warden_spawn_tracker.putInt("warning_level", 0);
        warden_spawn_tracker.putInt("ticks_since_last_warning", 380);
        warden_spawn_tracker.putInt("cooldown_ticks", 0);
        root.put("warden_spawn_tracker", warden_spawn_tracker);

        root.putInt("Score", mc.player.getScore());

        ListTag pos =  new ListTag();
        pos.add(DoubleTag.valueOf(mc.player.getX()));
        pos.add(DoubleTag.valueOf(mc.player.getY()));
        pos.add(DoubleTag.valueOf(mc.player.getZ()));
        root.put("Pos", pos);

        root.putShort("Fire", (short) mc.player.getRemainingFireTicks());
        root.putFloat("XpP", mc.player.experienceProgress);

        ListTag attributes = new ListTag();

        CompoundTag attributes0 = new CompoundTag();
        attributes0.putString("id", "minecraft:waypoint_transmit_range");
        attributes0.putDouble("base", 60000000);

        CompoundTag attributes1 = new CompoundTag();
        attributes1.putString("id", "minecraft:block_interaction_range");
        attributes1.putDouble("base", 4.5);

        CompoundTag attributes2 = new CompoundTag();
        attributes2.putString("id", "minecraft:entity_interaction_range");
        attributes2.putDouble("base", 3);

        CompoundTag attributes3 = new CompoundTag();
        attributes3.putString("id", "minecraft:movement_speed");
        attributes3.putDouble("base", 0.10000000149011612);

        attributes.add(attributes0);
        attributes.add(attributes1);
        attributes.add(attributes2);
        attributes.add(attributes3);
        root.put("attributes", attributes);

        root.putInt("DataVersion", DATA_VERSION);
        root.putInt("foodLevel", mc.player.getFoodData().getFoodLevel());
        root.putFloat("foodExhaustionLevel", 0f);
        root.putByte("spawn_extra_particles_on_fall", (byte) 0);
        root.putShort("HurtTime", (short) mc.player.hurtTime);
        root.putInt("SelectedItemSlot", mc.player.getInventory().getSelectedSlot());

        ListTag inventory = new ListTag();
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            int finalSlot = slot;
            saveItem(stack, ops).ifPresent(compound -> {
                compound.putByte("Slot", (byte) finalSlot);
                inventory.add(compound);
            });
        }
        root.put("Inventory", inventory);

        root.putInt("foodTickTimer", 0);

        cachePlayerDatPath  = playerdataPath.resolve(mc.player.getStringUUID() + ".dat");
        cacheRootTag = root;
    }

    public static void cacheEnderItems(List<ItemStack> items) {
        ListTag enderItems = new ListTag();
        int i = 0;
        for (ItemStack stack  : items) {
            if(i>26) break;
            int finalI = i;
            saveItem(stack, ops).ifPresent(compound -> {
                compound.putByte("Slot", (byte) finalI);
                enderItems.add(compound);
            });
            i++;
        }
        cacheRootTag.put("EnderItems", enderItems);
    }

    private static Optional<CompoundTag> saveItem(ItemStack stack, DynamicOps<Tag> ops) {
        if (stack == null || stack.isEmpty()) return Optional.empty();

        return ItemStack.CODEC.encodeStart(ops, stack)
                .resultOrPartial(err -> System.err.println("Failed to encode item: " + err))
                .map(tag -> (CompoundTag) tag);
    }

    private static void maybeFlushPlayerMetaFiles() {
        flushPlayerMetaFiles(false);
    }

    private static void flushPlayerMetaFiles(boolean force) {
        if ((!statsDirty && !advancementsDirty) && !force) return;
        if (path == null) return;

        long now = System.currentTimeMillis();
        if (!force && now - lastMetaFlushTimeMs < META_FLUSH_INTERVAL_MS) return;

        UUID targetPlayerUuid = cachePlayerUuid;
        if (targetPlayerUuid == null && mc.player != null) {
            targetPlayerUuid = mc.player.getUUID();
        }
        if (targetPlayerUuid == null) return;

        Path playersPath = path.resolve("players");
        try {
            if (statsDirty || force) {
                writeStatsFile(playersPath.resolve("stats").resolve(targetPlayerUuid + ".json"));
                statsDirty = false;
            }

            if (advancementsDirty || force) {
                writeAdvancementsFile(playersPath.resolve("advancements").resolve(targetPlayerUuid + ".json"));
                advancementsDirty = false;
            }

            lastMetaFlushTimeMs = now;
        } catch (IOException e) {
            SwdClient.LOGGER.error("Failed to write player advancement/stats files", e);
        }
    }

    private static void writeStatsFile(Path statsFile) throws IOException {
        JsonObject existingRoot = readJsonObject(statsFile);
        JsonObject mergedStats = new JsonObject();

        JsonObject existingStats = getObject(existingRoot, "stats");
        if (existingStats != null) {
            existingStats.entrySet().forEach(typeEntry -> {
                if (!(typeEntry.getValue() instanceof JsonObject typeObj)) return;
                mergedStats.add(typeEntry.getKey(), typeObj.deepCopy());
            });
        }

        if (cachedStatsByType != null) {
            cachedStatsByType.entrySet().forEach(typeEntry -> {
                if (!(typeEntry.getValue() instanceof JsonObject incomingTypeObj)) return;

                JsonObject mergedTypeObj = getOrCreateJsonObject(mergedStats, typeEntry.getKey());
                incomingTypeObj.entrySet().forEach(statEntry -> {
                    int incoming = statEntry.getValue().isJsonPrimitive() ? statEntry.getValue().getAsInt() : 0;
                    int existing = getInt(mergedTypeObj, statEntry.getKey(), Integer.MIN_VALUE);
                    if (incoming > existing) {
                        mergedTypeObj.addProperty(statEntry.getKey(), incoming);
                    }
                });
            });
        }

        JsonObject root = new JsonObject();
        root.add("stats", mergedStats);
        root.addProperty("DataVersion", DATA_VERSION);
        writeJsonObject(statsFile, root);
    }

    private static void writeAdvancementsFile(Path advancementsFile) throws IOException {
        JsonObject existingRoot = readJsonObject(advancementsFile);
        JsonObject mergedRoot = new JsonObject();

        if (!advancementsResetThisSession) {
            existingRoot.entrySet().forEach(entry -> {
                if ("DataVersion".equals(entry.getKey())) return;
                mergedRoot.add(entry.getKey(), entry.getValue().deepCopy());
            });
        }

        if (removedAdvancements != null) {
            removedAdvancements.forEach(mergedRoot::remove);
        }

        if (cachedAdvancements != null) {
            cachedAdvancements.entrySet().forEach(entry -> {
                if (!(entry.getValue() instanceof JsonObject incomingObj)) return;
                JsonObject existing = getObject(mergedRoot, entry.getKey());
                mergedRoot.add(entry.getKey(), mergeAdvancementObjects(existing, incomingObj));
            });
        }

        mergedRoot.addProperty("DataVersion", DATA_VERSION);
        writeJsonObject(advancementsFile, mergedRoot);
    }

    private static JsonObject buildAdvancementJson(AdvancementProgress progress) {
        JsonObject result = new JsonObject();
        JsonObject criteria = new JsonObject();

        for (String criterionName : progress.getCompletedCriteria()) {
            CriterionProgress criterionProgress = progress.getCriterion(criterionName);
            if (criterionProgress == null || !criterionProgress.isDone()) continue;
            Instant obtained = criterionProgress.getObtained();
            if (obtained != null) {
                criteria.addProperty(criterionName, ADVANCEMENT_TIME_FORMAT.format(obtained));
            }
        }

        result.add("criteria", criteria);
        result.addProperty("done", progress.isDone());
        return result;
    }

    private static JsonObject mergeAdvancementObjects(JsonObject base, JsonObject incoming) {
        JsonObject merged = new JsonObject();

        JsonObject baseCriteria = getObject(base, "criteria");
        JsonObject incomingCriteria = getObject(incoming, "criteria");
        JsonObject mergedCriteria = new JsonObject();

        if (baseCriteria != null) {
            baseCriteria.entrySet().forEach(entry -> mergedCriteria.add(entry.getKey(), entry.getValue().deepCopy()));
        }
        if (incomingCriteria != null) {
            incomingCriteria.entrySet().forEach(entry -> mergedCriteria.add(entry.getKey(), entry.getValue().deepCopy()));
        }

        merged.add("criteria", mergedCriteria);
        boolean done = getBoolean(base, "done") || getBoolean(incoming, "done");
        merged.addProperty("done", done);
        return merged;
    }

    private static String getStatTypeId(Stat<?> stat) {
        Identifier typeId = BuiltInRegistries.STAT_TYPE.getKey(stat.getType());
        return typeId == null ? null : typeId.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String getStatValueId(Stat<?> stat) {
        Identifier valueId = ((net.minecraft.core.Registry) stat.getType().getRegistry()).getKey(stat.getValue());
        if (valueId != null) return valueId.toString();
        Object rawValue = stat.getValue();
        return rawValue == null ? null : rawValue.toString();
    }

    private static JsonObject getOrCreateJsonObject(JsonObject parent, String key) {
        JsonObject existing = getObject(parent, key);
        if (existing != null) return existing;

        JsonObject created = new JsonObject();
        parent.add(key, created);
        return created;
    }

    private static JsonObject getObject(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement value = object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        if (object == null) return fallback;
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return fallback;
        try {
            return value.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject object, String key) {
        if (object == null) return false;
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private static JsonObject readJsonObject(Path file) throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        try {
            JsonElement element = JsonParser.parseString(Files.readString(file));
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            SwdClient.LOGGER.warn("Invalid JSON at {}, starting from empty object", file, e);
            return new JsonObject();
        }
    }

    private static void writeJsonObject(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(json));
    }

    public static void saveChunksAround(int radius) {
        ClientLevel world = mc.level;

        if (world == null || mc.player == null) return;

        int playerChunkX = mc.player.chunkPosition().x();
        int playerChunkZ = mc.player.chunkPosition().z();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                LevelChunk chunk = world.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk != null) {
                    saveChunkToRegion(path, chunk, false, world.dimension());
                }
            }
        }
    }

    public static void saveChunkToRegion(Path worldFolder, LevelChunk wc, boolean showMessage, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        CompoundTag blockNbt = buildChunkNbt(wc);
        CompoundTag entityNbt = buildEntityChunkNbt(wc);

        saveQueue.add(new ChunkSaveTask(wc.getPos(), blockNbt, entityNbt));

        if (saveThread == null || !saveThread.isAlive()) {
            String dim = "overworld";
            if(dimension != null && dimension == ClientLevel.NETHER) dim = "the_nether";
            if(dimension != null && dimension == ClientLevel.END) dim = "the_end";
            Path regionDir = worldFolder.resolve("dimensions").resolve("minecraft").resolve(dim).resolve("region");
            Path entityDir = worldFolder.resolve("dimensions").resolve("minecraft").resolve(dim).resolve("entities");
            checkPathExists(regionDir);
            checkPathExists(entityDir);
            saveThread = new Thread(() -> processQueue(regionDir, entityDir, dimension));
            saveThread.start();
        }

        if (showMessage) printStatus("§a> Saving chunk " + wc.getPos());
    }

    private static void processQueue(Path regionDir, Path entityDir, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        try (RegionStorage blockStorage = new RegionStorage(regionDir);
             RegionStorage entityStorage = new RegionStorage(entityDir)) {

            while (true) {
                ChunkSaveTask task = saveQueue.poll();
                if (task == null) {
                    if (!isSaving) {
                        break;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                blockStorage.write(task.pos, task.blockNbt, dimension);
                entityStorage.write(task.pos, task.entityNbt, dimension);
            }
        } catch (IOException e) {
            SwdClient.LOGGER.error("Failed to process chunk save queue!", e);
        }
    }

    private static void createPlayerDataFile() {
        try {
            NbtIo.writeCompressed(cacheRootTag, cachePlayerDatPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void createLevelDat(Path worldFolder, String worldName, LocalPlayer p) throws IOException {
        Files.createDirectories(worldFolder);
        CompoundTag data = new CompoundTag();

        data.putInt("DataVersion", DATA_VERSION);
        data.putString("LevelName", worldName);
        data.putLong("LastPlayed", System.currentTimeMillis());
        data.putInt("version", 19133);
        data.putInt("GameType", 1);
        data.putByte("initialized", (byte)1);
        data.putByte("allowCommands", (byte)1);

        CompoundTag difficulty_settings = new CompoundTag();
        difficulty_settings.putString("difficulty", "normal");
        difficulty_settings.putByte("hardcore", (byte)0);
        difficulty_settings.putByte("locked", (byte)0);
        data.put("difficulty_settings", difficulty_settings);

        data.putLong("Time", 0);

        CompoundTag spawn = new CompoundTag();
        spawn.putFloat("pitch", 0);
        spawn.putFloat("yaw", 0);
        spawn.putString("dimension", "minecraft:overworld");
        spawn.putIntArray("pos", new int[] {p.getBlockX(), p.getBlockY(), p.getBlockZ()});
        data.put("spawn", spawn);

        CompoundTag version = new CompoundTag();
        version.putString("Name", VERSION_NAME);
        version.putInt("Id", DATA_VERSION);
        version.putString("Series", "main");
        version.putByte("Snapshot", IS_SNAPSHOT);
        data.put("Version", version);

        CompoundTag dataPacks = new CompoundTag();
        ListTag enabled = new ListTag();
        enabled.add(StringTag.valueOf("vanilla"));
        enabled.add(StringTag.valueOf("fabric-convention-tags-v2"));
        enabled.add(StringTag.valueOf("fabric-gametest-api-v1"));
        dataPacks.put("Enabled", enabled);
        ListTag disabled = new ListTag();
        disabled.add(StringTag.valueOf("minecart_improvements"));
        disabled.add(StringTag.valueOf("redstone_experiments"));
        disabled.add(StringTag.valueOf("trade_rebalance"));
        dataPacks.put("Disabled", disabled);
        data.put("DataPacks", dataPacks);

        CompoundTag root = new CompoundTag();
        root.put("Data", data);

        Path levelDat = worldFolder.resolve("level.dat");
        NbtIo.writeCompressed(root, levelDat);

        long now = System.currentTimeMillis();
        ByteBuffer buf = ByteBuffer.allocate(8).putLong(now);
        Files.write(worldFolder.resolve("session.lock"), buf.array());

        createNewDatFiles(worldFolder);
    }

    private static void createNewDatFiles(Path worldFolder) throws IOException {
        Files.createDirectories(worldFolder.resolve("data").resolve("minecraft"));
        Path datFolder = worldFolder.resolve("data").resolve("minecraft");

        // custom_boss_events.dat
        CompoundTag root = new CompoundTag();
        root.put("data", new ListTag());
        root.putInt("DataVersion", DATA_VERSION);

        Path custom_boss_eventsDat = datFolder.resolve("custom_boss_events.dat");
        NbtIo.writeCompressed(root, custom_boss_eventsDat);

        // game_rules.dat
        CompoundTag data = new CompoundTag();
        data.putByte("minecraft:spawn_wandering_traders",  (byte) 1);
        data.putByte("minecraft:block_drops",  (byte) 1);
        data.putByte("minecraft:reduced_debug_info",  (byte) 0);
        data.putByte("minecraft:show_death_messages",  (byte) 1);
        data.putByte("minecraft:spawn_monsters",  (byte) 1);
        data.putByte("minecraft:spawner_blocks_work",  (byte) 1);
        data.putByte("minecraft:tnt_explodes",  (byte) 1);
        data.putByte("minecraft:immediate_respawn",  (byte) 0);
        data.putByte("minecraft:player_movement_check",  (byte) 1);
        data.putByte("minecraft:spread_vines",  (byte) 1);
        data.putByte("minecraft:block_explosion_drop_decay",  (byte) 1);
        data.putInt("minecraft:max_entity_cramming",  24);
        data.putByte("minecraft:forgive_dead_players",  (byte) 1);
        data.putByte("minecraft:fall_damage",  (byte) 1);
        data.putByte("minecraft:send_command_feedback",  (byte) 1);
        data.putByte("minecraft:global_sound_events",  (byte) 1);
        data.putByte("minecraft:elytra_movement_check",  (byte) 1);
        data.putInt("minecraft:fire_spread_radius_around_player",  128);
        data.putByte("minecraft:freeze_damage",  (byte) 1);
        data.putByte("minecraft:natural_health_regeneration",  (byte) 1);
        data.putByte("minecraft:mob_explosion_drop_decay",  (byte) 1);
        data.putInt("minecraft:players_nether_portal_default_delay",  80);
        data.putByte("minecraft:mob_drops",  (byte) 1);
        data.putByte("minecraft:log_admin_commands",  (byte) 1);
        data.putByte("minecraft:mob_griefing",  (byte) 1);
        data.putByte("minecraft:spawn_mobs",  (byte) 1);
        data.putByte("minecraft:pvp",  (byte) 1);
        data.putByte("minecraft:spectators_generate_chunks",  (byte) 1);
        data.putInt("minecraft:max_command_sequence_length",  65536);
        data.putByte("minecraft:players_nether_portal_creative_delay",  (byte) 0);
        data.putInt("minecraft:players_sleeping_percentage",  100);
        data.putByte("minecraft:advance_weather",  (byte) 1);
        data.putInt("minecraft:max_block_modifications",  32768);
        data.putInt("minecraft:max_command_forks",  65536);
        data.putByte("minecraft:drowning_damage",  (byte) 1);
        data.putByte("minecraft:show_advancement_messages",  (byte) 1);
        data.putByte("minecraft:command_block_output",  (byte) 1);
        data.putByte("minecraft:locator_bar",  (byte) 1);
        data.putInt("minecraft:respawn_radius",  10);
        data.putByte("minecraft:raids",  (byte) 1);
        data.putByte("minecraft:spawn_phantoms",  (byte) 1);
        data.putByte("minecraft:max_snow_accumulation_height",  (byte) 1);
        data.putByte("minecraft:limited_crafting",  (byte) 0);
        data.putByte("minecraft:allow_entering_nether_using_portals",  (byte) 1);
        data.putByte("minecraft:lava_source_conversion",  (byte) 0);
        data.putByte("minecraft:tnt_explosion_drop_decay",  (byte) 0);
        data.putByte("minecraft:universal_anger",  (byte) 0);
        data.putByte("minecraft:keep_inventory",  (byte) 0);
        data.putByte("minecraft:spawn_patrols",  (byte) 1);
        data.putInt("minecraft:random_tick_speed",  3);
        data.putByte("minecraft:fire_damage",  (byte) 1);
        data.putByte("minecraft:entity_drops",  (byte) 1);
        data.putByte("minecraft:advance_time",  (byte) 1);
        data.putByte("minecraft:command_blocks_work",  (byte) 1);
        data.putByte("minecraft:spawn_wardens",  (byte) 1);
        data.putByte("minecraft:water_source_conversion",  (byte) 1);
        data.putByte("minecraft:projectiles_can_break_blocks",  (byte) 1);
        data.putByte("minecraft:ender_pearls_vanish_on_death",  (byte) 1);

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", DATA_VERSION);

        Path game_rulesDat = datFolder.resolve("game_rules.dat");
        NbtIo.writeCompressed(root, game_rulesDat);

        // random_sequences.dat
        data = new CompoundTag();
        data.putByte("salt", (byte) 0);
        CompoundTag sequences = new  CompoundTag();
        CompoundTag snow = new CompoundTag();
        snow.putLongArray("source", new long[]{0, 0});
        sequences.put("Minecraft:blocks/snow", snow);
        data.put("sequences", sequences);

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", DATA_VERSION);

        Path random_sequencesDat = datFolder.resolve("random_sequences.dat");
        NbtIo.writeCompressed(root, random_sequencesDat);

        // scheduled_events.dat
        data = new CompoundTag();
        data.put("events", new ListTag());

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", DATA_VERSION);

        Path scheduled_eventsDat = datFolder.resolve("scheduled_events.dat");
        NbtIo.writeCompressed(root, scheduled_eventsDat);

        // scoreboard.dat
        root = new CompoundTag();
        root.put("data", new  ListTag());
        root.putInt("DataVersion", DATA_VERSION);

        Path scoreboardDat = datFolder.resolve("scoreboard.dat");
        NbtIo.writeCompressed(root, scoreboardDat);

        // stopwatches.dat
        data = new CompoundTag();
        data.put("stopwatches", new ListTag());

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", DATA_VERSION);

        Path stopwatchesDat = datFolder.resolve("stopwatches.dat");
        NbtIo.writeCompressed(root, stopwatchesDat);

        // weather.dat
        data = new CompoundTag();
        data.putByte("raining",  (byte) 0);
        data.putByte("thundering",  (byte) 0);
        data.putByte("clear_weather_time",  (byte) 0);
        data.putInt("rain_time",  0);
        data.putInt("thundering_time",  0);

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", DATA_VERSION);

        Path weatherDat = datFolder.resolve("weather.dat");
        NbtIo.writeCompressed(root, weatherDat);

        // world_clocks.dat
        data = new CompoundTag();
        CompoundTag overworld = new  CompoundTag();
        overworld.putLong("total_ticks", 30);
        data.put("minecraft:overworld", overworld);
        CompoundTag the_end = new  CompoundTag();
        the_end.putLong("total_ticks", 30);
        data.put("minecraft:the_end", the_end);

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", DATA_VERSION);

        Path world_clocksDat = datFolder.resolve("world_clocks.dat");
        NbtIo.writeCompressed(root, world_clocksDat);

        // world_gen_settings.dat
        data = new CompoundTag();
        data.putByte("bonus_chest", (byte) 0);
        data.putByte("generate_structures", (byte) 0);
        data.putLong("seed", 0);
        CompoundTag dimensions = new  CompoundTag();

        overworld = new CompoundTag();
        overworld.putString("type", "minecraft:overworld");
        CompoundTag generator  = new  CompoundTag();
        generator.putString("type", "minecraft:flat");
        CompoundTag settings  = new  CompoundTag();
        settings.putByte("features", (byte) 0);
        settings.putString("biome", "minecraft:plains");
        settings.put("layers", new ListTag());
        settings.putByte("lakes", (byte) 0);
        ListTag structure_overrides = new ListTag();
        structure_overrides.add(StringTag.valueOf("minecraft:strongholds"));
        structure_overrides.add(StringTag.valueOf("minecraft:villages"));
        settings.put("structure_overrides", structure_overrides);
        generator.put("settings", settings);
        overworld.put("generator", generator);
        dimensions.put("minecraft:overworld", overworld);

        CompoundTag the_nether = new CompoundTag();
        the_nether.putString("type", "minecraft:the_nether");
        generator  = new  CompoundTag();
        generator.putString("type", "minecraft:flat");
        settings = new  CompoundTag();
        settings.putString("biome", "minecraft:the_nether");
        settings.put("layers", new ListTag());
        settings.putByte("features", (byte) 0);
        settings.putByte("lakes", (byte) 0);
        generator.put("settings", settings);
        the_nether.put("generator", generator);
        dimensions.put("minecraft:the_nether", the_nether);

        the_end = new CompoundTag();
        the_end.putString("type", "minecraft:the_end");
        generator  = new  CompoundTag();
        generator.putString("type", "minecraft:flat");
        settings = new CompoundTag();
        settings.putString("biome", "minecraft:the_end");
        settings.put("layers", new ListTag());
        settings.putByte("features", (byte) 0);
        settings.putByte("lakes", (byte) 0);

        ListTag end_structure_overrides = new ListTag();
        settings.put("structure_overrides", end_structure_overrides);

        generator.put("settings", settings);
        the_end.put("generator", generator);
        dimensions.put("minecraft:the_end", the_end);

        data.put("dimensions", dimensions);

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", DATA_VERSION);

        Path world_gen_settingsDat = datFolder.resolve("world_gen_settings.dat");
        NbtIo.writeCompressed(root, world_gen_settingsDat);

        // ender_dragon_fight.dat
        Path endData = path.resolve("dimensions").resolve("minecraft").resolve("the_end").resolve("data").resolve("minecraft");
        if(!Files.exists(endData)) Files.createDirectories(endData);

        data = new CompoundTag();
        data.putByte("dragon_killed",  (byte) 1);
        data.putByte("needs_state_scanning",  (byte) 0);
        data.putInt("respawn_time",  0);
        data.putByte("previously_killed",  (byte) 1);
        data.put("gateways",  new  ListTag());

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", DATA_VERSION);

        Path dragonDat = endData.resolve("ender_dragon_fight.dat");
        NbtIo.writeCompressed(root, dragonDat);
    }

    public static void printStatus(String msg) {
        mc.gui.setOverlayMessage(Component.nullToEmpty(msg), false);
    }

    private static void checkPathExists(Path path) {
        if(!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private record ChunkSaveTask(ChunkPos pos, CompoundTag blockNbt, CompoundTag entityNbt) { }

}