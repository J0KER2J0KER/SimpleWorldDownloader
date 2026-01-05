package com.j0ker2j0ker.swd.client.util;

import com.j0ker2j0ker.swd.client.config.SwdConfig;
//import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
//TODO
public class SaveManager {

    private static boolean isSaving = false;
    public static String name;
    public static String path;
    public static String regionPath;

    public static boolean getIsSaving() {
        return isSaving;
    }

    public static void toggle() {
        if(isSaving) stop();
        else start();
    }

    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if(!isSaving && mc.getCurrentServer() != null) {
            isSaving = true;
            name = mc.getCurrentServer().ip;
            if(Files.exists(Paths.get("saves/"+name))) {
                int i = 1;
                while(Files.exists(Paths.get("saves/"+name + i))) i++;
                name += i;
            }
            path = mc.getLevelSource().getBaseDir().toString() + "\\" + name;
            regionPath = path + "\\region";

            try {
                if(!Files.exists(Path.of(path))) {
                    Files.createDirectories(Path.of(regionPath));
                    createLevelDat(Path.of(path), name, mc.player);
                    if(mc.getCurrentServer().getIconBytes() != null) {
                        byte[] icon = mc.getCurrentServer().getIconBytes();
                        FileOutputStream fos = new FileOutputStream(path + "\\icon.png");
                        fos.write(icon);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            printStatus("§a> Started saving chunks...");
            saveChunksAround(12);
        }
    }

    public static void stop() {
        isSaving = false;
        printStatus("§c> Stopped saving chunks.");
    }

    public static void printStatus(String msg) {
        Minecraft mc = Minecraft.getInstance();
        /*SwdConfig config = AutoConfig.getConfigHolder(SwdConfig.class).getConfig();
        if(!config.showMessages) return;*/
        if(mc != null && mc.gui != null) {
            mc.gui.setOverlayMessage(Component.nullToEmpty(msg), false);
        }
    }

    public static void saveChunksAround(int radius) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;

        if (world == null || client.player == null) return;

        int playerChunkX = client.player.chunkPosition().x;
        int playerChunkZ = client.player.chunkPosition().z;

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
        Path regionDir = Paths.get(worldFolder, "region");
        try (RegionStorage storage = new RegionStorage(regionDir)) {
            storage.write(wc.getPos(), nbt);
            if (showMessage) printStatus("§a> Saved chunk " + wc.getPos());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void createLevelDat(Path worldFolder, String worldName, LocalPlayer p) throws IOException {
        Files.createDirectories(worldFolder);
        Files.createDirectories(worldFolder.resolve("region"));

        CompoundTag data = new CompoundTag();

        data.putInt("DataVersion", 4764);
        data.putString("LevelName", worldName);
        data.putLong("LastPlayed", System.currentTimeMillis());
        data.putInt("version", 19133);
        data.putInt("GameType", 1);
        data.putInt("SpawnX", p.getBlockX());
        data.putInt("SpawnY", p.getBlockY());
        data.putInt("SpawnZ", p.getBlockZ());
        data.putInt("Difficulty", 1);
        data.putByte("initialized", (byte)1);
        data.putByte("hardcore", (byte)0);
        data.putByte("allowCommands", (byte)1);

        CompoundTag version = new CompoundTag();
        version.putString("Name", "26.1 Snapshot 1");
        version.putInt("Id", 4764);
        version.putString("Series", "main");
        version.putByte("Snapshot", (byte)0);
        data.put("Version", version);

        CompoundTag gameRules = new CompoundTag();
        gameRules.putString("doDaylightCycle", "true");
        gameRules.putString("doWeatherCycle", "true");
        data.put("GameRules", gameRules);

        CompoundTag dataPacks = new CompoundTag();
        ListTag enabled = new ListTag();
        enabled.add(StringTag.valueOf("vanilla"));
        dataPacks.put("Enabled", enabled);
        dataPacks.put("Disabled", new ListTag());
        data.put("DataPacks", dataPacks);

        CompoundTag player = new CompoundTag();
        player.putString("Dimension", "minecraft:overworld");
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(p.getBlockX()));
        pos.add(DoubleTag.valueOf(p.getBlockY()));
        pos.add(DoubleTag.valueOf(p.getBlockZ()));
        player.put("Pos", pos);
        data.put("Player", player);

        CompoundTag worldGenSettings = new CompoundTag();
        worldGenSettings.putLong("seed", 0L);
        worldGenSettings.putByte("generate_features", (byte)0);
        worldGenSettings.putByte("bonus_chest", (byte)0);

        CompoundTag dimensions = new CompoundTag();

        {
            CompoundTag overworld = new CompoundTag();
            overworld.putString("type", "minecraft:overworld");

            CompoundTag generator = new CompoundTag();
            generator.putString("type", "minecraft:flat");

            CompoundTag settings = new CompoundTag();
            settings.put("layers", new ListTag());
            settings.putString("biome", "minecraft:plains");
            settings.putByte("structure", (byte)0);

            generator.put("settings", settings);
            overworld.put("generator", generator);

            dimensions.put("minecraft:overworld", overworld);
        }

        {
            CompoundTag nether = new CompoundTag();
            nether.putString("type", "minecraft:the_nether");

            CompoundTag generator = new CompoundTag();
            generator.putString("type", "minecraft:noise");
            generator.putString("settings", "minecraft:nether");

            CompoundTag biomeSource = new CompoundTag();
            biomeSource.putString("type", "minecraft:multi_noise");
            biomeSource.putString("preset", "minecraft:nether");

            generator.put("biome_source", biomeSource);
            nether.put("generator", generator);

            dimensions.put("minecraft:the_nether", nether);
        }

        {
            CompoundTag end = new CompoundTag();
            end.putString("type", "minecraft:the_end");

            CompoundTag generator = new CompoundTag();
            generator.putString("type", "minecraft:noise");
            generator.putString("settings", "minecraft:end");

            CompoundTag biomeSource = new CompoundTag();
            biomeSource.putString("type", "minecraft:the_end");

            generator.put("biome_source", biomeSource);
            end.put("generator", generator);

            dimensions.put("minecraft:the_end", end);
        }

        worldGenSettings.put("dimensions", dimensions);
        data.put("WorldGenSettings", worldGenSettings);

        CompoundTag dragonFight = new CompoundTag();
        dragonFight.putByte("NeedsStateScanning", (byte)0);
        dragonFight.putByte("DragonKilled", (byte)0);
        dragonFight.putByte("PreviouslyKilled", (byte)0);
        dragonFight.putByte("IsRespawning", (byte)0);
        data.put("DragonFight", dragonFight);

        CompoundTag root = new CompoundTag();
        root.put("Data", data);

        Path levelDat = worldFolder.resolve("level.dat");
        NbtIo.writeCompressed(root, levelDat);

        long now = System.currentTimeMillis();
        ByteBuffer buf = ByteBuffer.allocate(8).putLong(now);
        Files.write(worldFolder.resolve("session.lock"), buf.array());
    }


    public static CompoundTag buildChunkNbt(LevelChunk wc) {
        ChunkPos pos = wc.getPos();

        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion", 4764); // 1.21.10
        chunk.putInt("xPos", pos.x);
        chunk.putInt("zPos", pos.z);
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
            int[] indices = new int[16 * 16 * 16]; // 4096 blocks

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
        chunk.put("block_entities", new ListTag());
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
        if (entriesPerLong <= 0) throw new IllegalArgumentException("bits too large for 64-bit packing");

        final int total = indices.length;
        final int longs = (total + entriesPerLong - 1) / entriesPerLong;
        long[] data = new long[longs];

        long mask = (bits == 64) ? -1L : ((1L << bits) - 1);

        int entryInLong = 0;
        int longIndex = 0;

        for (int i = 0; i < total; i++) {
            int idx = indices[i];
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

}