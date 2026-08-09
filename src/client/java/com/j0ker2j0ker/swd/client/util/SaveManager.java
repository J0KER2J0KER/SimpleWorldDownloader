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
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.TagValueOutput;
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

    private static final int DATA_VERSION = 4903;
    private static final String VERSION_NAME = "26.2";
    private static final byte IS_SNAPSHOT = (byte) 0;

    private static final int PLAYER_INVENTORY_SLOTS = 36;
    private static final int DOUBLE_CHEST_SLOTS = 54;
    private static final int SINGLE_CHEST_SLOTS = 27;
    private static final long META_FLUSH_INTERVAL_MS = 5000L;
    private static final DateTimeFormatter ADVANCEMENT_TIME_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Queue<ChunkSaveTask> saveQueue = new ConcurrentLinkedQueue<>();
    private static final java.util.Set<String> queuedChunks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> touchedChunks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final int MAX_QUEUE_SIZE = 4096;
    public static Thread saveThread = null;

    public static volatile boolean isSaving = false;
    private static volatile net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastSavedDimension = null;
    private static boolean isResumingExistingWorld = false;
    public static String name;
    public static Path path;

    private static HashMap<BlockPos, List<ItemStack>> cacheBlockInventories;
    private static HashMap<UUID, List<ItemStack>> cacheEntityInventories;
    private static HashMap<UUID, CompoundTag> cacheEntityOverrides;
    private static HashMap<BlockPos, CompoundTag> blockEntitySnapshots;
    private static java.util.Set<UUID> interactedEntities;
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
        if (isSaving) stop();
        else start();
    }

    public static void start() {
        if (isSaving || mc.player == null) return;

        ops = Objects.requireNonNull(mc.level).registryAccess().createSerializationContext(NbtOps.INSTANCE);
        isSaving = true;

        determineWorldName();
        path = mc.getLevelSource().getBaseDir().resolve(name);

        setupWorldFolder();
        if (SwdClient.CONFIG.includePlayerData) {
            createPlayerDataFile();
        }

        cacheBlockInventories = new HashMap<>();
        cacheEntityInventories = new HashMap<>();
        cacheEntityOverrides = new HashMap<>();
        blockEntitySnapshots = new HashMap<>();
        interactedEntities = new HashSet<>();
        cachePlayerUuid = mc.player.getUUID();
        cachedStatsByType = new JsonObject();
        cachedAdvancements = new JsonObject();
        removedAdvancements = new HashSet<>();
        advancementsResetThisSession = false;
        statsDirty = false;
        advancementsDirty = false;
        lastMetaFlushTimeMs = 0L;
        lastSavedDimension = mc.level != null ? mc.level.dimension() : null;

        bootstrapAdvancementsFromClientCache();

        if (isResumingExistingWorld) {
            printStatus(Component.translatable("swd.status.resume_saving").withStyle(ChatFormatting.GREEN));
            saveChunksAround(mc.options.renderDistance().get(), true);
        } else {
            printStatus(Component.translatable("swd.status.started_saving").withStyle(ChatFormatting.GREEN));
            saveChunksAround(mc.options.renderDistance().get());
        }
    }

    public static void stop() {
        if (!isSaving) return;
        flushPlayerMetaFiles(true);
        isSaving = false;

        if (SwdClient.CONFIG.includePlayerData) {
            createPlayerDataFile();
        }
        printStatus(Component.translatable("swd.status.stopped_saving").withStyle(ChatFormatting.RED));

        queuedChunks.clear();
        touchedChunks.clear();

        if (cacheBlockInventories != null) cacheBlockInventories.clear();
        if (cacheEntityInventories != null) cacheEntityInventories.clear();
        if (cacheEntityOverrides != null) cacheEntityOverrides.clear();
        if (blockEntitySnapshots != null) blockEntitySnapshots.clear();
        if (interactedEntities != null) interactedEntities.clear();
        cachePlayerUuid = null;
        cachedStatsByType = null;
        cachedAdvancements = null;
        removedAdvancements = null;
        advancementsResetThisSession = false;
        statsDirty = false;
        advancementsDirty = false;
        lastMetaFlushTimeMs = 0L;
        lastSavedDimension = null;
        isResumingExistingWorld = false;
    }

    public static void cacheAwardStatsPacket(ClientboundAwardStatsPacket packet) {
        if (!isSaving || path == null || mc.player == null || mc.isLocalServer() || mc.getCurrentServer() == null)
            return;
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
        if (!isSaving || path == null || mc.player == null || mc.isLocalServer() || mc.getCurrentServer() == null)
            return;
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
            if (!SwdClient.CONFIG.includeEntities) return;
            printStatus(Component.translatable("swd.status.villager_trade_saved").withStyle(ChatFormatting.GREEN));
            cacheVillagerMerchantData(villager, merchantScreen.getMenu());
            return;
        }

        String title = screen.getTitle().getString();

        if (screen instanceof AbstractContainerScreen<?> container && title.equals(Component.translatable("container.enderchest").getString())) {
            if (mc.player == null) return;

            int slotIndex = 0;
            for (ItemStack stack : container.getMenu().getItems()) {
                if (slotIndex >= 27) break;
                mc.player.getEnderChestInventory().setItem(slotIndex++, stack);
            }

            printStatus(Component.translatable("swd.status.enderchest_saved").withStyle(ChatFormatting.GREEN));
            return;
        }

        List<ItemStack> items = extractContainerItems(screen);
        if (items == null) return;

        trimPlayerInventory(items);

        if (lastClicked instanceof BlockPos blockPos) {
            handleBlockContainer(blockPos, items, screen);
        } else if (lastClicked instanceof net.minecraft.world.entity.Entity entity) {
            if (!SwdClient.CONFIG.includeEntities) return;
            handleEntityContainer(entity, items);
        }
    }

    private static List<ItemStack> extractContainerItems(Screen screen) {
        if (screen instanceof AbstractFurnaceScreen<?> fs) {
            printStatus(Component.translatable("swd.status.container_saved").withStyle(ChatFormatting.GREEN));
            return fs.getMenu().getItems();
        }
        if (screen instanceof AbstractContainerScreen<?> cs && isSupportedContainerScreen(screen)) {
            printStatus(Component.translatable("swd.status.container_saved").withStyle(ChatFormatting.GREEN));
            return cs.getMenu().getItems();
        }
        if (screen instanceof HorseInventoryScreen hs) {
            printStatus(Component.translatable("swd.status.container_saved").withStyle(ChatFormatting.GREEN));
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

    private static void handleBlockContainer(BlockPos pos, List<ItemStack> items, Screen screen) {
        if (cachePairedChestInventories(pos, items)) {
            return;
        }

        cacheBlockInventories.put(pos, new ArrayList<>(items)); // defensive copy

        // Snapshot the full BlockEntity NBT for Crafter/Dropper/Dispenser.
        // The server only sends inventory data during active GUI interaction;
        // non-inventory fields (Crafter disabled/triggered, etc.) must come from
        // the client-side BE at the moment the player closes the screen.
        if (mc.level != null && isPrecisionBlockEntityScreen(screen)) {
            var be = mc.level.getBlockEntity(pos);
            if (be != null) {
                CompoundTag snapshot = be.saveWithFullMetadata(mc.level.registryAccess());
                // Remove the Items key from the snapshot — we'll inject from cache later.
                snapshot.remove("Items");
                blockEntitySnapshots.put(pos, snapshot);
            }
        }

        saveChunkNow(pos);
    }

    /**
     * Returns true for containers whose BE carries critical non-inventory state.
     */
    private static boolean isPrecisionBlockEntityScreen(Screen screen) {
        if (screen == null) return false;
        return switch (screen.getClass().getSimpleName()) {
            case "CrafterScreen", "DispenserScreen", "DropperScreen" -> true;
            default -> false;
        };
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
        if (!SwdClient.CONFIG.includeEntities) return;
        UUID uuid = entity.getUUID();
        if (!interactedEntities.contains(uuid)) return;
        cacheEntityInventories.put(uuid, new ArrayList<>(items));
        interactedEntities.add(uuid);
        saveChunkNow(entity.blockPosition());
    }

    private static void cacheVillagerMerchantData(AbstractVillager merchant, MerchantMenu menu) {
        if (!interactedEntities.contains(merchant.getUUID())) return;

        if (!SwdClient.CONFIG.includeEntities) return;
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
        interactedEntities.add(merchant.getUUID());
        saveChunkNow(merchant.blockPosition());
    }

    /**
     * Called when the player interacts with any entity (right-click).
     * Caches entity-specific data (item frame contents, etc.) and
     * marks the entity for persistence so it is included in future chunk saves.
     */
    public static void onEntityInteract(net.minecraft.world.entity.Entity entity) {
        if (!isSaving || mc.level == null) return;

        UUID uuid = entity.getUUID();
        interactedEntities.add(uuid);

        // ItemFrame / GlowItemFrame: cache the displayed item
        if (entity instanceof net.minecraft.world.entity.decoration.ItemFrame frame) {
            ItemStack displayed = frame.getItem();
            if (!displayed.isEmpty()) {
                List<ItemStack> items = new ArrayList<>();
                items.add(displayed.copy());
                cacheEntityInventories.put(uuid, items);
                printStatus(Component.translatable("swd.status.itemframe_saved").withStyle(ChatFormatting.GREEN));
            }
        }

        saveChunkNow(entity.blockPosition());
    }


    private static void saveChunkNow(BlockPos pos) {
        if (mc.level == null) return;
        LevelChunk wc = mc.level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (wc != null) {
            touchChunk(wc.getPos(), mc.level.dimension());
            // Clear dedup so that subsequent saveChunkNow calls with fresh
            // cache data (from onScreenClosed → cacheVillagerMerchantData,
            // handleBlockContainer, etc.) are not silently dropped.
            queuedChunks.remove(packChunkDimKey(wc.getPos(), mc.level.dimension()));
            saveChunkToRegion(path, wc, false, mc.level.dimension());
        }
    }

    /**
     * Mark a chunk as "touched" so that even on resume it will be saved.
     */
    private static void touchChunk(ChunkPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        touchedChunks.add(packChunkDimKey(pos, dim));
    }

    public static CompoundTag buildEntityChunkNbt(LevelChunk wc) {
        CompoundTag chunk = new CompoundTag();

        chunk.putInt("DataVersion", DATA_VERSION);

        ChunkPos pos = wc.getPos();
        chunk.putIntArray("Position", new int[]{pos.x(), pos.z()});

        ListTag entityList = new ListTag();

        if (SwdClient.CONFIG.includeEntities) {
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    pos.getMinBlockX(), wc.getLevel().getMinY(), pos.getMinBlockZ(),
                    pos.getMaxBlockX(), wc.getLevel().getMaxY(), pos.getMaxBlockZ()
            );

            wc.getLevel().getEntities(null, box).forEach(entity -> {
                if (entity instanceof net.minecraft.world.entity.player.Player) return;

                CompoundTag entityNbt = saveEntityToNbt(entity);

                // Entity persistence: merge cached inventory/override data for interacted entities.
                // The server sends complete data for basic entities (cows, sheep, zombies, etc.)
                // but only sends inventory/trade data during active player interaction.
                // The cache preserves this interaction data across chunk re-saves.
                injectCachedEntityInventory(entity, entityNbt);

                entityList.add(entityNbt);
            });
        }

        chunk.put("Entities", entityList);

        return chunk;
    }

    public static CompoundTag buildChunkNbt(LevelChunk wc) {
        CompoundTag chunk = createBaseChunkNbt(wc);

        ListTag blockEntities = new ListTag();
        wc.getBlockEntities().forEach((bePos, be) -> {
            if (wc.getBlockState(bePos).isAir() || !wc.getBlockState(bePos).hasBlockEntity()) {
                return;
            }
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

    /**
     * Merge old (disk) and new (server + cache) entity chunk NBTs.
     * Matches entities by type + block position; old entities with cached
     * inventory/trade data are preserved unless the new data has fresh cache.
     */
    private static CompoundTag mergeEntityChunkNbt(CompoundTag oldChunk, CompoundTag newChunk) {
        ListTag oldList = oldChunk.getList("Entities").orElse(new ListTag());
        ListTag newList = newChunk.getList("Entities").orElse(new ListTag());

        java.util.Map<String, CompoundTag> oldByKey = new java.util.HashMap<>();
        for (int i = 0; i < oldList.size(); i++) {
            CompoundTag nbt = oldList.getCompound(i).orElseThrow();
            String key = entityMatchKey(nbt);
            if (!key.isEmpty()) oldByKey.put(key, nbt);
        }

        java.util.Set<String> matchedNewKeys = new java.util.HashSet<>();
        ListTag merged = new ListTag();

        // Process new entities
        for (int i = 0; i < newList.size(); i++) {
            CompoundTag newNbt = newList.getCompound(i).orElseThrow();
            String key = entityMatchKey(newNbt);
            CompoundTag oldNbt = key.isEmpty() ? null : oldByKey.get(key);

            if (oldNbt != null) {
                // Entity matched by stable key → keep the current server packet as the base,
                // and only restore interaction-only fields when the server packet is empty.
                CompoundTag mergedEntity = newNbt.copy();

                Tag oldItems = copyTag(oldNbt, "Items");
                if (!hasNonEmptyList(newNbt, "Items") && oldItems != null) {
                    mergedEntity.put("Items", oldItems);
                }

                Tag oldOffers = copyTag(oldNbt, "Offers");
                if (!newNbt.contains("Offers") && oldOffers != null) {
                    mergedEntity.put("Offers", oldOffers);
                }

                Tag oldVillagerData = copyTag(oldNbt, "VillagerData");
                if (!newNbt.contains("VillagerData") && oldVillagerData != null) {
                    mergedEntity.put("VillagerData", oldVillagerData);
                }

                merged.add(mergedEntity);
                matchedNewKeys.add(key);
            } else {
                // No old match → new entity, keep as-is
                merged.add(newNbt);
            }
        }

        // Add old entities that weren't matched by any new entity
        for (var entry : oldByKey.entrySet()) {
            if (!matchedNewKeys.contains(entry.getKey())) {
                merged.add(entry.getValue());
            }
        }

        CompoundTag result = oldChunk.copy();
        result.put("Entities", merged);
        return result;
    }

    /**
     * Build a stable match key from entity NBT: use UUID, fall back to type@blockPos.
     */
    private static String entityMatchKey(CompoundTag nbt) {
        try {
            if (nbt.contains("UUID")) {
                var uuidTag = nbt.get("UUID");
                if (uuidTag instanceof IntArrayTag arr && arr.size() >= 4) {
                    int[] uuid = arr.getAsIntArray();
                    return new UUID(
                            ((long) uuid[0] << 32) | (uuid[1] & 0xFFFFFFFFL),
                            ((long) uuid[2] << 32) | (uuid[3] & 0xFFFFFFFFL)
                    ).toString();
                }
            }
            // Fallback for entities without UUID
            String id = nbt.getString("id").orElse("");
            if (id.isEmpty()) return "";
            ListTag pos = nbt.getList("Pos").orElse(new ListTag());
            if (pos.isEmpty()) return "";
            int bx = (int) Math.floor(pos.getDouble(0).orElse(0.0));
            int by = (int) Math.floor(pos.getDouble(1).orElse(0.0));
            int bz = (int) Math.floor(pos.getDouble(2).orElse(0.0));
            return id + "@" + bx + "," + by + "," + bz;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Merge old (disk) and new (server + live BE) block chunk NBTs.
     * Matches block entities by id + x/y/z; old cached fields (Items,
     * Disabled, Triggered) are preserved unless the new data carries fresh values.
     */
    private static CompoundTag mergeBlockChunkNbt(CompoundTag oldChunk, CompoundTag newChunk) {
        ListTag oldList = oldChunk.getList("block_entities").orElse(new ListTag());
        ListTag newList = newChunk.getList("block_entities").orElse(new ListTag());

        java.util.Map<String, CompoundTag> oldByKey = new java.util.HashMap<>();
        for (int i = 0; i < oldList.size(); i++) {
            CompoundTag nbt = oldList.getCompound(i).orElseThrow();
            String key = beMatchKey(nbt);
            if (!key.isEmpty()) oldByKey.put(key, nbt);
        }

        java.util.Set<String> matchedNewKeys = new java.util.HashSet<>();
        ListTag merged = new ListTag();

        for (int i = 0; i < newList.size(); i++) {
            CompoundTag newNbt = newList.getCompound(i).orElseThrow();
            String key = beMatchKey(newNbt);
            CompoundTag oldNbt = key.isEmpty() ? null : oldByKey.get(key);

            if (oldNbt != null) {
                boolean newHasInv = newNbt.contains("Items") && !newNbt.getList("Items").orElse(new ListTag()).isEmpty();
                // Keep the current server packet as the base so empty packets do not
                // roll the block entity back to an older snapshot.
                CompoundTag mergedBe = newNbt.copy();

                Tag oldItems = copyTag(oldNbt, "Items");
                Tag oldDisabled = copyTag(oldNbt, "Disabled");
                Tag oldTriggered = copyTag(oldNbt, "Triggered");
                Tag oldCrafting = copyTag(oldNbt, "crafting_ticks_remaining");

                if (!newHasInv && oldItems != null) mergedBe.put("Items", oldItems);
                if (!newNbt.contains("Disabled") && oldDisabled != null) mergedBe.put("Disabled", oldDisabled);
                if (!newNbt.contains("Triggered") && oldTriggered != null) mergedBe.put("Triggered", oldTriggered);
                if (!newNbt.contains("crafting_ticks_remaining") && oldCrafting != null)
                    mergedBe.put("crafting_ticks_remaining", oldCrafting);

                merged.add(mergedBe);
                matchedNewKeys.add(key);
            } else {
                merged.add(newNbt);
            }
        }

        // Do not keep unmatched old block entities; they may point to air now.
        CompoundTag result = oldChunk.copy();
        result.put("block_entities", merged);
        return result;
    }

    /**
     * Match key for block entities: "id@x,y,z".
     */
    private static String beMatchKey(CompoundTag nbt) {
        try {
            String id = nbt.getString("id").orElse("");
            if (id.isEmpty()) return "";
            int bx = nbt.getInt("x").orElse(Integer.MIN_VALUE);
            int by = nbt.getInt("y").orElse(Integer.MIN_VALUE);
            int bz = nbt.getInt("z").orElse(Integer.MIN_VALUE);
            if (bx == Integer.MIN_VALUE) return "";
            return id + "@" + bx + "," + by + "," + bz;
        } catch (Exception e) {
            return "";
        }
    }


    private static void injectCachedBlockInventory(BlockPos pos, CompoundTag beTag) {
        if (cacheBlockInventories.containsKey(pos)) {
            beTag.put("Items", buildItemsListTag(cacheBlockInventories.get(pos)));
        }
    }

    private static void injectCachedEntityInventory(net.minecraft.world.entity.Entity entity, CompoundTag entityNbt) {
        if (!interactedEntities.contains(entity.getUUID())) return;

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

        if (SwdClient.CONFIG.includeResourcePacks
                && Minecraft.getInstance().getCurrentServer() != null
                && Minecraft.getInstance().getCurrentServer().getResourcePackStatus().name().equalsIgnoreCase("ENABLED")) {
            Path packTempPath = SwdClient.resourcepack_locations;
            Path pathResourcepacks = path.resolve("resourcepacks");
            if (!Files.exists(pathResourcepacks)) {
                try {
                    Files.createDirectory(pathResourcepacks);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            Path packTargetPath = pathResourcepacks.resolve("resources.zip");
            try {
                if (packTempPath != null) {
                    Files.copy(packTempPath, packTargetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void determineWorldName() {
        isResumingExistingWorld = false;
        boolean allowResume = SwdClient.CONFIG.resumeDownloads;
        if (SwdClient.CONFIG.saveWorldTo.isEmpty()) {
            if (mc.getCurrentServer() != null) name = mc.getCurrentServer().ip.replaceAll("[\\\\/:*?\"<>|]", "_");
            else {
                if (mc.getSingleplayerServer() == null) name = "Replay Mod";
                else if (mc.getSingleplayerServer().getWorldData().getLevelName().equalsIgnoreCase("Replay"))
                    name = "Flashback";
                else name = mc.getSingleplayerServer().getWorldData().getLevelName().replaceAll("[\\\\/:*?\"<>|]", "_");
            }
            Path saves = Paths.get("saves");
            // Reuse existing SWD world: if a directory with the base name was already
            // saved by this mod, continue writing into it instead of creating a new one.
            if (allowResume && SwdWorldMarker.isMarked(saves.resolve(name))) {
                isResumingExistingWorld = true;
                return;
            }
            if (Files.exists(saves.resolve(name))) {
                int i = 1;
                while (Files.exists(saves.resolve(name + " " + i))) i++;
                name += " " + i;
            }
        } else {
            Path saves = Paths.get("saves");
            if (allowResume && SwdWorldMarker.isMarked(saves.resolve(SwdClient.CONFIG.saveWorldTo))) {
                isResumingExistingWorld = true;
            }
            name = SwdClient.CONFIG.saveWorldTo;
        }
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
        if (!SwdClient.CONFIG.includePlayerData) return;
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
        if (!SwdClient.CONFIG.includePlayerData) return;
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

    private static Tag copyTag(CompoundTag object, String key) {
        if (object == null || !object.contains(key)) return null;
        Tag tag = object.get(key);
        return tag == null ? null : tag.copy();
    }

    private static boolean hasNonEmptyList(CompoundTag object, String key) {
        return object != null && object.getList(key).orElse(new ListTag()).size() > 0;
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
        saveChunksAround(radius, false);
    }

    private static void saveChunksAround(int radius, boolean touchOnResume) {
        ClientLevel world = mc.level;

        if (world == null || mc.player == null) return;

        int playerChunkX = mc.player.chunkPosition().x();
        int playerChunkZ = mc.player.chunkPosition().z();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                LevelChunk chunk = world.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk != null && !chunk.isEmpty()) {
                    if (!isResumingExistingWorld || touchOnResume) {
                        touchChunk(chunk.getPos(), world.dimension());
                    }
                    saveChunkToRegion(path, chunk, false, world.dimension());
                }
            }
        }
    }

    public static void saveChunkToRegion(Path worldFolder, LevelChunk wc, boolean showMessage, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        if (wc.isEmpty()) return;

        String dedupKey = packChunkDimKey(wc.getPos(), dimension);

        // Dedup: skip if this exact chunk+dimension is already queued
        if (!queuedChunks.add(dedupKey)) {
            return; // already queued, skip
        }
        if (saveQueue.size() >= MAX_QUEUE_SIZE) {
            queuedChunks.remove(dedupKey);
            if (showMessage)
                printStatus(Component.translatable("swd.status.queue_full", wc.getPos()).withStyle(ChatFormatting.RED));
            return;
        }

        CompoundTag blockNbt = buildChunkNbt(wc);
        CompoundTag entityNbt = buildEntityChunkNbt(wc);

        saveQueue.add(new ChunkSaveTask(wc.getPos(), blockNbt, entityNbt, dimension));

        // Detect dimension change: when player enters a new dimension, trigger a batch save.
        // Update lastSavedDimension BEFORE saveChunksAround to prevent re-entrant triggering.
        var previousDim = lastSavedDimension;
        lastSavedDimension = dimension;

        if (previousDim != null && dimension != null && previousDim != dimension) {
            saveChunksAround(6);
        }

        if (saveThread == null || !saveThread.isAlive()) {
            saveThread = new Thread(() -> processQueue(worldFolder));
            saveThread.start();
        }

        if (showMessage) {
            printStatus(Component.translatable("swd.status.saving_chunk", wc.getPos()).withStyle(ChatFormatting.GREEN));
        }
    }

    private static void processQueue(Path worldFolder) {
        java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, RegionStorage> blockStorages = new java.util.HashMap<>();
        java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, RegionStorage> entityStorages = new java.util.HashMap<>();
        try {

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

                var dimKey = task.dimension != null ? task.dimension : net.minecraft.world.level.Level.OVERWORLD;

                RegionStorage blockStorage = blockStorages.computeIfAbsent(dimKey, dk -> {
                    String ns = dk.identifier().getNamespace();
                    String p = dk.identifier().getPath();
                    Path dir = worldFolder.resolve("dimensions").resolve(ns).resolve(p).resolve("region");
                    checkPathExists(dir);
                    return new RegionStorage(dir);
                });
                RegionStorage entityStorage = entityStorages.computeIfAbsent(dimKey, dk -> {
                    String ns = dk.identifier().getNamespace();
                    String p = dk.identifier().getPath();
                    Path dir = worldFolder.resolve("dimensions").resolve(ns).resolve(p).resolve("entities");
                    checkPathExists(dir);
                    return new RegionStorage(dir);
                });

                String dedupKey = packChunkDimKey(task.pos, task.dimension);
                boolean touched = touchedChunks.contains(dedupKey);
                boolean skipBlockWrite = false;
                boolean skipEntityWrite = false;
                CompoundTag oldBlockNbt = null;

                if (isResumingExistingWorld && !touched) {
                    try {
                        oldBlockNbt = blockStorage.read(task.pos, task.dimension);
                        boolean hasRealChunk = oldBlockNbt != null && !isEmptyChunkNbt(oldBlockNbt);
                        skipBlockWrite = hasRealChunk;
                        skipEntityWrite = hasRealChunk;
                    } catch (IOException ignored) {
                    }
                }

                if (!skipBlockWrite) {
                    CompoundTag finalBlockNbt = task.blockNbt;
                    if (isResumingExistingWorld && touched && task.blockNbt != null) {
                        try {
                            CompoundTag mergeSource = oldBlockNbt != null ? oldBlockNbt : blockStorage.read(task.pos, task.dimension);
                            boolean mergeSourceEmpty = mergeSource != null && isEmptyChunkNbt(mergeSource);
                            if (mergeSource != null && !mergeSourceEmpty) {
                                finalBlockNbt = mergeBlockChunkNbt(mergeSource, task.blockNbt);
                            }
                        } catch (IOException ignored) {
                        }
                    }
                    blockStorage.write(task.pos, finalBlockNbt, task.dimension);
                }

                if (!skipEntityWrite) {
                    CompoundTag finalEntityNbt = task.entityNbt;
                    if (isResumingExistingWorld && touched && task.entityNbt != null) {
                        try {
                            CompoundTag oldEntityNbt = entityStorage.read(task.pos, task.dimension);
                            if (oldEntityNbt != null) {
                                finalEntityNbt = mergeEntityChunkNbt(oldEntityNbt, task.entityNbt);
                            }
                        } catch (IOException ignored) {
                        }
                    }
                    entityStorage.write(task.pos, finalEntityNbt, task.dimension);
                }

                // Remove from dedup set after successful write/skip
                queuedChunks.remove(packChunkDimKey(task.pos, task.dimension));
            }
        } catch (IOException e) {
            SwdClient.LOGGER.error("Failed to process chunk save queue!", e);
        } finally {
            for (RegionStorage rs : blockStorages.values()) {
                try {
                    rs.close();
                } catch (Exception ignored) {
                }
            }
            for (RegionStorage rs : entityStorages.values()) {
                try {
                    rs.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void createPlayerDataFile() {
        try (LevelStorageSource.LevelStorageAccess access =
                     LevelStorageSource.createDefault(mc.getLevelSource().getBaseDir()).createAccess(name)) {
            PlayerDataStorage playerStorage = access.createPlayerStorage();
            playerStorage.save(mc.player);
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
        data.putByte("initialized", (byte) 1);
        data.putByte("allowCommands", (byte) 1);

        CompoundTag difficulty_settings = new CompoundTag();
        difficulty_settings.putString("difficulty", "normal");
        difficulty_settings.putByte("hardcore", (byte) 0);
        difficulty_settings.putByte("locked", (byte) 0);
        data.put("difficulty_settings", difficulty_settings);

        data.putLong("Time", 0);

        CompoundTag spawn = new CompoundTag();
        spawn.putFloat("pitch", 0);
        spawn.putFloat("yaw", 0);
        spawn.putString("dimension", "minecraft:overworld");
        spawn.putIntArray("pos", new int[]{p.getBlockX(), p.getBlockY(), p.getBlockZ()});
        data.put("spawn", spawn);

        CompoundTag version = new CompoundTag();
        version.putString("Name", VERSION_NAME);
        version.putInt("Id", DATA_VERSION);
        version.putString("Series", "main");
        version.putByte("Snapshot", IS_SNAPSHOT);
        data.put("Version", version);

        CompoundTag gameRulesV2 = new CompoundTag();
        gameRulesV2.putByte("minecraft:do_daylight_cycle", (byte) 1);
        gameRulesV2.putByte("minecraft:do_weather_cycle", (byte) 1);
        gameRulesV2.putInt("minecraft:random_tick_speed", 0);
        data.put("game_rules", gameRulesV2);

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
        data.putByte("minecraft:spawn_wandering_traders", (byte) 1);
        data.putByte("minecraft:block_drops", (byte) 1);
        data.putByte("minecraft:reduced_debug_info", (byte) 0);
        data.putByte("minecraft:show_death_messages", (byte) 1);
        data.putByte("minecraft:spawn_monsters", (byte) 1);
        data.putByte("minecraft:spawner_blocks_work", (byte) 1);
        data.putByte("minecraft:tnt_explodes", (byte) 1);
        data.putByte("minecraft:immediate_respawn", (byte) 0);
        data.putByte("minecraft:player_movement_check", (byte) 1);
        data.putByte("minecraft:spread_vines", (byte) 1);
        data.putByte("minecraft:block_explosion_drop_decay", (byte) 1);
        data.putInt("minecraft:max_entity_cramming", 24);
        data.putByte("minecraft:forgive_dead_players", (byte) 1);
        data.putByte("minecraft:fall_damage", (byte) 1);
        data.putByte("minecraft:send_command_feedback", (byte) 1);
        data.putByte("minecraft:global_sound_events", (byte) 1);
        data.putByte("minecraft:elytra_movement_check", (byte) 1);
        data.putInt("minecraft:fire_spread_radius_around_player", 128);
        data.putByte("minecraft:freeze_damage", (byte) 1);
        data.putByte("minecraft:natural_health_regeneration", (byte) 1);
        data.putByte("minecraft:mob_explosion_drop_decay", (byte) 1);
        data.putInt("minecraft:players_nether_portal_default_delay", 80);
        data.putByte("minecraft:mob_drops", (byte) 1);
        data.putByte("minecraft:log_admin_commands", (byte) 1);
        data.putByte("minecraft:mob_griefing", (byte) 1);
        data.putByte("minecraft:spawn_mobs", (byte) 1);
        data.putByte("minecraft:pvp", (byte) 1);
        data.putByte("minecraft:spectators_generate_chunks", (byte) 1);
        data.putInt("minecraft:max_command_sequence_length", 65536);
        data.putByte("minecraft:players_nether_portal_creative_delay", (byte) 0);
        data.putInt("minecraft:players_sleeping_percentage", 100);
        data.putByte("minecraft:advance_weather", (byte) 1);
        data.putInt("minecraft:max_block_modifications", 32768);
        data.putInt("minecraft:max_command_forks", 65536);
        data.putByte("minecraft:drowning_damage", (byte) 1);
        data.putByte("minecraft:show_advancement_messages", (byte) 1);
        data.putByte("minecraft:command_block_output", (byte) 1);
        data.putByte("minecraft:locator_bar", (byte) 1);
        data.putInt("minecraft:respawn_radius", 10);
        data.putByte("minecraft:raids", (byte) 1);
        data.putByte("minecraft:spawn_phantoms", (byte) 1);
        data.putByte("minecraft:max_snow_accumulation_height", (byte) 1);
        data.putByte("minecraft:limited_crafting", (byte) 0);
        data.putByte("minecraft:allow_entering_nether_using_portals", (byte) 1);
        data.putByte("minecraft:lava_source_conversion", (byte) 0);
        data.putByte("minecraft:tnt_explosion_drop_decay", (byte) 0);
        data.putByte("minecraft:universal_anger", (byte) 0);
        data.putByte("minecraft:keep_inventory", (byte) 0);
        data.putByte("minecraft:spawn_patrols", (byte) 1);
        data.putInt("minecraft:random_tick_speed", 0);
        data.putByte("minecraft:fire_damage", (byte) 1);
        data.putByte("minecraft:entity_drops", (byte) 1);
        data.putByte("minecraft:advance_time", (byte) 1);
        data.putByte("minecraft:command_blocks_work", (byte) 1);
        data.putByte("minecraft:spawn_wardens", (byte) 1);
        data.putByte("minecraft:water_source_conversion", (byte) 1);
        data.putByte("minecraft:projectiles_can_break_blocks", (byte) 1);
        data.putByte("minecraft:ender_pearls_vanish_on_death", (byte) 1);

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", DATA_VERSION);

        Path game_rulesDat = datFolder.resolve("game_rules.dat");
        NbtIo.writeCompressed(root, game_rulesDat);

        // random_sequences.dat
        data = new CompoundTag();
        data.putByte("salt", (byte) 0);
        CompoundTag sequences = new CompoundTag();
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
        root.put("data", new ListTag());
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
        data.putByte("raining", (byte) 0);
        data.putByte("thundering", (byte) 0);
        data.putByte("clear_weather_time", (byte) 0);
        data.putInt("rain_time", 0);
        data.putInt("thundering_time", 0);

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", DATA_VERSION);

        Path weatherDat = datFolder.resolve("weather.dat");
        NbtIo.writeCompressed(root, weatherDat);

        // world_clocks.dat
        data = new CompoundTag();
        CompoundTag overworld = new CompoundTag();
        overworld.putLong("total_ticks", 30);
        data.put("minecraft:overworld", overworld);
        CompoundTag the_end = new CompoundTag();
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
        CompoundTag dimensions = new CompoundTag();

        overworld = new CompoundTag();
        overworld.putString("type", "minecraft:overworld");
        CompoundTag generator = new CompoundTag();
        generator.putString("type", "minecraft:flat");
        CompoundTag settings = new CompoundTag();
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
        generator = new CompoundTag();
        generator.putString("type", "minecraft:flat");
        settings = new CompoundTag();
        settings.putString("biome", "minecraft:the_nether");
        settings.put("layers", new ListTag());
        settings.putByte("features", (byte) 0);
        settings.putByte("lakes", (byte) 0);
        generator.put("settings", settings);
        the_nether.put("generator", generator);
        dimensions.put("minecraft:the_nether", the_nether);

        the_end = new CompoundTag();
        the_end.putString("type", "minecraft:the_end");
        generator = new CompoundTag();
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
        if (!Files.exists(endData)) Files.createDirectories(endData);

        data = new CompoundTag();
        data.putByte("dragon_killed", (byte) 1);
        data.putByte("needs_state_scanning", (byte) 0);
        data.putInt("respawn_time", 0);
        data.putByte("previously_killed", (byte) 1);
        data.put("gateways", new ListTag());

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", DATA_VERSION);

        Path dragonDat = endData.resolve("ender_dragon_fight.dat");
        NbtIo.writeCompressed(root, dragonDat);
    }

    public static void printStatus(Component msg) {
        mc.gui.hud.setOverlayMessage(msg, false);
    }

    private static void checkPathExists(Path path) {
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Pack a ChunkPos + dimension into a stable string key for dedup/lookups.
     * This avoids any cross-dimension collision when chunk coordinates match.
     */
    private static String packChunkDimKey(ChunkPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        String dimId = dim != null ? dim.identifier().toString() : "minecraft:overworld";
        return dimId + "|" + pos.x() + "," + pos.z();
    }

    /**
     * Convert a UUID into the 4-int NBT array format Minecraft expects.
     */
    private static int[] uuidToIntArray(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return new int[]{(int) (most >> 32), (int) most, (int) (least >> 32), (int) least};
    }

    private record ChunkSaveTask(ChunkPos pos, CompoundTag blockNbt, CompoundTag entityNbt,
                                 net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
    }

    private static boolean isEmptyChunkNbt(CompoundTag nbt) {
        if (nbt == null) return true;
        String status = nbt.getString("Status").orElse("");
        if ("empty".equals(status)) return true;
        ListTag sections = nbt.getList("sections").orElse(new ListTag());
        if (sections.isEmpty()) return true;
        return isAllAirSections(sections);
    }

    private static boolean isAllAirSections(ListTag sections) {
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag sec = sections.getCompound(i).orElseThrow();
            Tag blockStatesTag = sec.get("block_states");
            if (!(blockStatesTag instanceof CompoundTag blockStates)) {
                continue;
            }
            ListTag palette = blockStates.getList("palette").orElse(new ListTag());
            if (palette.isEmpty()) {
                continue;
            }
            if (!isSingleAirPalette(palette)) {
                return false;
            }
        }
        return true;
    }

    private static final java.util.Set<String> AIR_BLOCK_NAMES = java.util.Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private static boolean isSingleAirPalette(ListTag palette) {
        if (palette.isEmpty()) return false;
        for (int i = 0; i < palette.size(); i++) {
            if (!isAirBlockEntry(palette.get(i))) return false;
        }
        return true;
    }

    private static boolean isAirBlockEntry(Tag entry) {
        if (entry instanceof CompoundTag compound) {
            String name = compound.getString("Name").orElse("");
            return AIR_BLOCK_NAMES.contains(name);
        }
        if (entry instanceof StringTag str) {
            String value = str.toString();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return AIR_BLOCK_NAMES.contains(value);
        }
        return false;
    }

}