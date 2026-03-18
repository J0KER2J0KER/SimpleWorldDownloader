package com.j0ker2j0ker.swd.client.util;

import com.j0ker2j0ker.swd.client.SwdClient;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SaveManager {

    private static final int dataVersion = 4782;
    private static final String versionName = "26.1 Pre-Release 3";
    private static final byte isSnapshot = (byte)1;

    private static final Queue<ChunkSaveTask> saveQueue = new ConcurrentLinkedQueue<>();
    private static Thread saveThread = null;

    public static boolean isSaving = false;
    public static String name;
    public static Path path;

    private static final Minecraft mc = Minecraft.getInstance();

    public static void toggle() {
        if(isSaving) stop();
        else start();
    }

    public static void start() {
        if(!isSaving && mc.getCurrentServer() != null && mc.player != null) {
            isSaving = true;
            if(SwdClient.CONFIG.saveWorldTo.isEmpty()) {
                name = mc.getCurrentServer().ip.replaceAll("[\\\\/:*?\"<>|]", "_");
                Path saves = Paths.get("saves");
                if(Files.exists(saves.resolve(name))) {
                    int i = 1;
                    while(Files.exists(saves.resolve(name + " " + i))) i++;
                    name += " " + i;
                }
            }else {
                name = SwdClient.CONFIG.saveWorldTo;
            }
            path = mc.getLevelSource().getBaseDir().resolve(name);

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
                    Files.copy(packTempPath, packTargetPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            createPlayerDataFile(path);

            printStatus("§a> Started saving chunks...");
            saveChunksAround(12);
        }
    }

    private static void createPlayerDataFile(Path path) {
        Path playerdataPath;
        try {
            playerdataPath = Files.createDirectories(path.resolve("players").resolve("data"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(mc.level == null || mc.player == null) return;
        var ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);

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
        else root.putInt("playerGameType", mc.player.gameMode().getId());
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

        //TODO Containers
        //TODO ender chest
        ListTag enderItems = new ListTag();
        root.put("EnderItems", enderItems);

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

        root.putInt("DataVersion", dataVersion);
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

        Path playerDat = playerdataPath.resolve(mc.player.getStringUUID() + ".dat");
        try {
            NbtIo.writeCompressed(root, playerDat);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<CompoundTag> saveItem(ItemStack stack, com.mojang.serialization.DynamicOps<Tag> ops) {
        if (stack == null || stack.isEmpty()) return Optional.empty();

        return ItemStack.CODEC.encodeStart(ops, stack)
                .resultOrPartial(err -> System.err.println("Failed to encode item: " + err))
                .map(tag -> (CompoundTag) tag);
    }

    public static void stop() {
        if (!isSaving) return;
        isSaving = false;
        printStatus("§c> Stopped saving chunks.");
    }

    public static void printStatus(String msg) {
        mc.gui.setOverlayMessage(Component.nullToEmpty(msg), false);
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

    private static void checkPathExists(Path path) {
        if(!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void processQueue(Path regionDir, Path entityDir, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        try (RegionStorage blockStorage = new RegionStorage(regionDir);
             RegionStorage entityStorage = new RegionStorage(entityDir)) {

            while (!saveQueue.isEmpty() && isSaving) {
                ChunkSaveTask task = saveQueue.poll();
                if (task != null) {
                    blockStorage.write(task.pos, task.blockNbt, dimension);
                    entityStorage.write(task.pos, task.entityNbt, dimension);
                }
            }
        } catch (IOException e) {
            SwdClient.LOGGER.error("Failed to process chunk save queue!", e);
        }
    }


    public static void createLevelDat(Path worldFolder, String worldName, LocalPlayer p) throws IOException {
        Files.createDirectories(worldFolder);
        CompoundTag data = new CompoundTag();

        data.putInt("DataVersion", dataVersion);
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
        version.putString("Name", versionName);
        version.putInt("Id", dataVersion);
        version.putString("Series", "main");
        version.putByte("Snapshot", isSnapshot);
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
        root.putInt("DataVersion", dataVersion);

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
        root.putInt("DataVersion", dataVersion);

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
        root.putInt("DataVersion", dataVersion);

        Path random_sequencesDat = datFolder.resolve("random_sequences.dat");
        NbtIo.writeCompressed(root, random_sequencesDat);

        // scheduled_events.dat
        data = new CompoundTag();
        data.put("events", new ListTag());

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", dataVersion);

        Path scheduled_eventsDat = datFolder.resolve("scheduled_events.dat");
        NbtIo.writeCompressed(root, scheduled_eventsDat);

        // scoreboard.dat
        root = new CompoundTag();
        root.put("data", new  ListTag());
        root.putInt("DataVersion", dataVersion);

        Path scoreboardDat = datFolder.resolve("scoreboard.dat");
        NbtIo.writeCompressed(root, scoreboardDat);

        // stopwatches.dat
        data = new CompoundTag();
        data.put("stopwatches", new ListTag());

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", dataVersion);

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
        root.putInt("DataVersion", dataVersion);

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
        root.putInt("DataVersion", dataVersion);

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
        root.putInt("DataVersion", dataVersion);

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
        root.putInt("DataVersion", dataVersion);

        Path dragonDat = endData.resolve("ender_dragon_fight.dat");
        NbtIo.writeCompressed(root, dragonDat);
    }

    public static CompoundTag buildEntityChunkNbt(LevelChunk wc) {
        CompoundTag chunk = new CompoundTag();

        chunk.putInt("DataVersion", dataVersion);

        ChunkPos pos = wc.getPos();
        chunk.putIntArray("Position", new int[]{pos.x(), pos.z()});

        ListTag entityList = new ListTag();

        net.minecraft.world.phys.AABB chunkBox = new net.minecraft.world.phys.AABB(
                wc.getPos().getMinBlockX(), wc.getLevel().getMinY(), wc.getPos().getMinBlockZ(),
                wc.getPos().getMaxBlockX(), wc.getLevel().getMaxY(), wc.getPos().getMaxBlockZ()
        );
        wc.getLevel().getEntities(null, chunkBox).forEach(entity -> {
            if (!(entity instanceof net.minecraft.world.entity.player.Player)) {
                try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(
                        entity.problemPath(), com.mojang.logging.LogUtils.getLogger())) {

                    TagValueOutput output = TagValueOutput.createWithContext(
                            reporter,
                            wc.getLevel().registryAccess()
                    );
                    entity.save(output);

                    CompoundTag entityNbt = output.buildResult();
                    entityList.add(entityNbt);
                }
            }
        });

        chunk.put("Entities", entityList);

        return chunk;
    }

    public static CompoundTag buildChunkNbt(LevelChunk wc) {
        ChunkPos pos = wc.getPos();
        var registries = wc.getLevel().registryAccess();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);

        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion", dataVersion);
        chunk.putInt("xPos", pos.x());
        chunk.putInt("zPos", pos.z());
        chunk.putInt("yPos", wc.getMinSectionY());
        chunk.putString("Status", "full");

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

        ListTag blockEntities = new ListTag();
        wc.getBlockEntities().forEach((posE, be) -> blockEntities.add(be.saveWithFullMetadata(registries)));
        chunk.put("block_entities", blockEntities);

        return chunk;
    }

    private record ChunkSaveTask(ChunkPos pos, CompoundTag blockNbt, CompoundTag entityNbt) { }

}