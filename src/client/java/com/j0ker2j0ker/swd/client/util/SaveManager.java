package com.j0ker2j0ker.swd.client.util;

import com.j0ker2j0ker.swd.client.SwdClient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Locale;

public class SaveManager {

    private static final int dataVersion = 4774;
    private static final String versionName = "26.1 Snapshot 6";
    private static final byte isSnapshot = (byte)1;

    private static final Queue<ChunkSaveTask> saveQueue = new ConcurrentLinkedQueue<>();
    private static Thread saveThread = null;

    public static boolean isSaving = false;
    public static String name;
    public static String path;
    public static String regionPath;

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
                if(Files.exists(Paths.get("saves").resolve(name))) {
                    int i = 1;
                    while(Files.exists(Paths.get("saves").resolve(name + " " + i))) i++;
                    name += " " + i;
                }
            }else {
                name = SwdClient.CONFIG.saveWorldTo;
            }
            Path resolvedPath = mc.getLevelSource().getBaseDir().resolve(name);
            path = normalizePathForOs(resolvedPath);
            regionPath = normalizePathForOs(Paths.get(path, "dimensions", "minecraft", "overworld", "region"));

            try {
                if(!Files.exists(Path.of(path))) {
                    Files.createDirectories(Path.of(regionPath));
                    createLevelDat(Path.of(path), name, mc.player);
                    if(mc.getCurrentServer().getIconBytes() != null) {
                        byte[] icon = mc.getCurrentServer().getIconBytes();
                        FileOutputStream fos = new FileOutputStream(Paths.get(path).resolve("icon.png").toFile());
                        fos.write(icon);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(Minecraft.getInstance().getCurrentServer().getResourcePackStatus().name().equalsIgnoreCase("ENABLED")) {
                Path packTempPath = SwdClient.resourcepack_locations;
                if(!Files.exists(Path.of(path).resolve("resourcepacks"))) {
                    try {
                        Files.createDirectory(Path.of(path).resolve("resourcepacks"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                Path packTargetPath = Path.of(path).resolve("resourcepacks").resolve("resources.zip");
                try {
                    Files.copy(packTempPath, packTargetPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }


            printStatus("§a> Started saving chunks...");
            saveChunksAround(12);
        }
    }

    public static void stop() {
        if (!isSaving) return;
        isSaving = false;
        printStatus("§c> Stopped saving chunks.");
    }

    public static void printStatus(String msg) {
        if(mc != null && mc.gui != null) {
            mc.gui.setOverlayMessage(Component.nullToEmpty(msg), false);
        }
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
                    saveChunkToRegion(path, chunk, false);
                }
            }
        }
    }

    public static void saveChunkToRegion(String worldFolder, LevelChunk wc, boolean showMessage) {
        CompoundTag nbt = buildChunkNbt(wc);
        saveQueue.add(new ChunkSaveTask(wc.getPos(), nbt));

        if (saveThread == null || !saveThread.isAlive()) {
            Path regionDir = Paths.get(worldFolder, "dimensions", "minecraft", "overworld", "region");
            saveThread = new Thread(() -> processQueue(regionDir));
            saveThread.start();
        }

        if (showMessage) printStatus("§a> Saving chunk " + wc.getPos());
    }

    private static void processQueue(Path regionDir) {
        try (RegionStorage storage = new RegionStorage(regionDir)) {
            while (!saveQueue.isEmpty() && isSaving) {
                ChunkSaveTask task = saveQueue.poll();
                if (task != null) {
                    storage.write(task.pos, task.nbt);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void createLevelDat(Path worldFolder, String worldName, LocalPlayer p) throws IOException {
        Files.createDirectories(worldFolder);
        Files.createDirectories(worldFolder.resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region"));

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

        // this is a default overworld, however a void world is needed
        /*overworld = new CompoundTag();
        overworld.putString("type", "minecraft:overworld");
        CompoundTag generator  = new  CompoundTag();
        generator.putString("settings", "minecraft:overworld");
        generator.putString("type", "minecraft:noise");
        CompoundTag biome_source = new  CompoundTag();
        biome_source.putString("preset",  "minecraft:overworld");
        biome_source.putString("type", "minecraft:multi_noise");
        generator.put("biome_source", biome_source);
        overworld.put("generator", generator);
        dimensions.put("minecraft:overworld", overworld);*/
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
        generator.putString("settings", "minecraft:nether");
        generator.putString("type", "minecraft:noise");
        CompoundTag biome_source = new  CompoundTag();
        biome_source.putString("preset",  "minecraft:nether");
        biome_source.putString("type", "minecraft:multi_noise");
        generator.put("biome_source", biome_source);
        the_nether.put("generator", generator);
        dimensions.put("minecraft:the_nether", the_nether);

        the_end = new CompoundTag();
        the_end.putString("type", "minecraft:the_end");
        generator  = new  CompoundTag();
        generator.putString("settings", "minecraft:end");
        generator.putString("type", "minecraft:noise");
        biome_source = new  CompoundTag();
        biome_source.putString("type", "minecraft:the_end");
        generator.put("biome_source", biome_source);
        the_end.put("generator", generator);
        dimensions.put("minecraft:the_end", the_end);
        data.put("dimensions", dimensions);

        root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", dataVersion);

        Path world_gen_settingsDat = datFolder.resolve("world_gen_settings.dat");
        NbtIo.writeCompressed(root, world_gen_settingsDat);
    }


    public static CompoundTag buildChunkNbt(LevelChunk wc) {
        ChunkPos pos = wc.getPos();

        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion", dataVersion);
        chunk.putInt("xPos", pos.x());
        chunk.putInt("zPos", pos.z());
        chunk.putString("Status", "full");
        chunk.putLong("LastUpdate", 0L);
        chunk.putLong("InhabitedTime", 0L);

        ListTag sections = new ListTag();

        LevelChunkSection[] sectionArray = wc.getSections();
        for (int secIndex = 0; secIndex < sectionArray.length; secIndex++) {
            LevelChunkSection section = sectionArray[secIndex];
            if (section == null || section.hasOnlyAir()) continue;

            CompoundTag sec = new CompoundTag();
            sec.putInt("Y", secIndex + wc.getMinSectionY());

            ListTag paletteList = new ListTag();
            Map<BlockState, Integer> paletteIndex = new HashMap<>();
            int[] indices = new int[16 * 16 * 16];

            int i = 0;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Integer idx = paletteIndex.get(state);
                        if (idx == null) {
                            idx = paletteList.size();
                            paletteIndex.put(state, idx);

                            CompoundTag entry = new CompoundTag();
                            entry.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                            if (!state.getProperties().isEmpty()) {
                                CompoundTag props = new CompoundTag();
                                for (Property<?> prop : state.getProperties()) {
                                    props.putString(prop.getName(), String.valueOf(state.getValue(prop)));
                                }
                                entry.put("Properties", props);
                            }
                            paletteList.add(entry);
                        }
                        indices[i++] = idx;
                    }
                }
            }

            int paletteSize = paletteList.size();
            int bits = paletteSize <= 1 ? 4 : Math.max(4, ceilLog2(paletteSize));

            CompoundTag blockStates = new CompoundTag();
            blockStates.put("palette", paletteList);
            long[] data = packIndicesVanilla(indices, bits);
            blockStates.put("data", new LongArrayTag(data));
            sec.put("block_states", blockStates);

            CompoundTag biomes = new CompoundTag();
            ListTag biomePalette = new ListTag();
            biomePalette.add(StringTag.valueOf("minecraft:plains"));
            biomes.put("palette", biomePalette);
            biomes.put("data", new LongArrayTag(new long[]{0L}));
            sec.put("biomes", biomes);

            sections.add(sec);
        }

        chunk.put("sections", sections);

        ListTag blockEntities = new ListTag();
        wc.getBlockEntities().forEach((posE, be) -> {
            CompoundTag beNbt = be.saveWithFullMetadata(wc.getLevel().registryAccess());
            beNbt.putInt("x", posE.getX());
            beNbt.putInt("y", posE.getY());
            beNbt.putInt("z", posE.getZ());
            blockEntities.add(beNbt);
        });

        chunk.put("block_entities", blockEntities);

        chunk.put("entities", new ListTag());
        chunk.put("Heightmaps", new CompoundTag());
        chunk.putByte("isLightOn", (byte) 0);

        return chunk;
    }

    private static int ceilLog2(int n) {
        int v = n - 1;
        return 32 - Integer.numberOfLeadingZeros(v);
    }

    private static long[] packIndicesVanilla(int[] indices, int bits) {
        if (bits <= 0 || bits > 32) throw new IllegalArgumentException("bits must be 1..32");
        final int entriesPerLong = 64 / bits;

        final int total = indices.length;
        final int longs = (total + entriesPerLong - 1) / entriesPerLong;
        long[] data = new long[longs];

        long mask = (1L << bits) - 1;

        int entryInLong = 0;
        int longIndex = 0;

        for (int idx : indices) {
            int shift = entryInLong * bits;

            data[longIndex] |= ((long) idx & mask) << shift;

            entryInLong++;
            if (entryInLong == entriesPerLong) {
                entryInLong = 0;
                longIndex++;
            }
        }
        return data;
    }

    private static String normalizePathForOs(Path path) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String normalized = path.toAbsolutePath().normalize().toString();
        if (os.contains("win")) return normalized;
        if (os.contains("mac") || os.contains("darwin") || os.contains("nux") || os.contains("nix")) {
            return normalized.replace('\\', '/');
        }
        return normalized.replace('\\', '/');
    }

    private record ChunkSaveTask(ChunkPos pos, CompoundTag nbt) {
    }

}