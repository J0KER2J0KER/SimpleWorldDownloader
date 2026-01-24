package com.j0ker2j0ker.swd.client.util;

import com.j0ker2j0ker.swd.client.SwdClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SaveManager {

    private static final Queue<ChunkSaveTask> saveQueue = new ConcurrentLinkedQueue<>();
    private static Thread saveThread = null;

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
        MinecraftClient mc = MinecraftClient.getInstance();
        if(!isSaving && mc.getCurrentServerEntry() != null && mc.player != null) {
            isSaving = true;
            if(SwdClient.CONFIG.saveWorldTo.isEmpty()) {
                name = mc.getCurrentServerEntry().address.replaceAll("[\\\\/:*?\"<>|]", "_");
                if(Files.exists(Paths.get("saves").resolve(name))) {
                    int i = 1;
                    while(Files.exists(Paths.get("saves").resolve(name + " " + i))) i++;
                    name += " " + i;
                }
            }else {
                name = SwdClient.CONFIG.saveWorldTo;
            }
            path = mc.getLevelStorage().getSavesDirectory().resolve(name).toString();
            regionPath = Paths.get(path, "region").toString();

            try {
                if(!Files.exists(Path.of(path))) {
                    Files.createDirectories(Path.of(regionPath));
                    createLevelDat(Path.of(path), name, mc.player);
                    if(mc.getCurrentServerEntry().getFavicon() != null) {
                        byte[] icon = mc.getCurrentServerEntry().getFavicon();
                        FileOutputStream fos = new FileOutputStream(Paths.get(path).resolve("icon.png").toFile());
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if(mc != null && mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(Text.of(msg), false);
        }
    }

    public static void saveChunksAround(int radius) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;

        if (world == null || client.player == null) return;

        int playerChunkX = client.player.getChunkPos().x;
        int playerChunkZ = client.player.getChunkPos().z;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk != null) {
                    saveChunkToRegion(path, chunk, false);
                }
            }
        }
    }

    public static void saveChunkToRegion(String worldFolder, WorldChunk wc, boolean showMessage) {
        NbtCompound nbt = buildChunkNbt(wc);
        saveQueue.add(new ChunkSaveTask(wc.getPos(), nbt));

        if (saveThread == null || !saveThread.isAlive()) {
            Path regionDir = Paths.get(worldFolder, "region");
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


    public static void createLevelDat(Path worldFolder, String worldName, ClientPlayerEntity p) throws IOException {
        Files.createDirectories(worldFolder);
        Files.createDirectories(worldFolder.resolve("region"));

        NbtCompound data = new NbtCompound();

        data.putInt("DataVersion", 4671);
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

        NbtCompound version = new NbtCompound();
        version.putString("Name", "1.21.11");
        version.putInt("Id", 4671);
        version.putString("Series", "main");
        version.putByte("Snapshot", (byte)0);
        data.put("Version", version);

        NbtCompound gameRules = new NbtCompound();
        gameRules.putString("doDaylightCycle", "true");
        gameRules.putString("doWeatherCycle", "true");
        data.put("GameRules", gameRules);

        NbtCompound dataPacks = new NbtCompound();
        NbtList enabled = new NbtList();
        enabled.add(NbtString.of("vanilla"));
        dataPacks.put("Enabled", enabled);
        dataPacks.put("Disabled", new NbtList());
        data.put("DataPacks", dataPacks);

        NbtCompound player = new NbtCompound();
        player.putString("Dimension", "minecraft:overworld");
        NbtList pos = new NbtList();
        pos.add(NbtDouble.of(p.getBlockX()));
        pos.add(NbtDouble.of(p.getBlockY()));
        pos.add(NbtDouble.of(p.getBlockZ()));
        player.put("Pos", pos);
        data.put("Player", player);

        NbtCompound worldGenSettings = new NbtCompound();
        worldGenSettings.putLong("seed", 0L);
        worldGenSettings.putByte("generate_features", (byte)0);
        worldGenSettings.putByte("bonus_chest", (byte)0);

        NbtCompound dimensions = new NbtCompound();

        {
            NbtCompound overworld = new NbtCompound();
            overworld.putString("type", "minecraft:overworld");

            NbtCompound generator = new NbtCompound();
            generator.putString("type", "minecraft:flat");

            NbtCompound settings = new NbtCompound();
            settings.put("layers", new NbtList());
            settings.putString("biome", "minecraft:plains");
            settings.putByte("structure", (byte)0);

            generator.put("settings", settings);
            overworld.put("generator", generator);

            dimensions.put("minecraft:overworld", overworld);
        }

        {
            NbtCompound nether = new NbtCompound();
            nether.putString("type", "minecraft:the_nether");

            NbtCompound generator = new NbtCompound();
            generator.putString("type", "minecraft:noise");
            generator.putString("settings", "minecraft:nether");

            NbtCompound biomeSource = new NbtCompound();
            biomeSource.putString("type", "minecraft:multi_noise");
            biomeSource.putString("preset", "minecraft:nether");

            generator.put("biome_source", biomeSource);
            nether.put("generator", generator);

            dimensions.put("minecraft:the_nether", nether);
        }

        {
            NbtCompound end = new NbtCompound();
            end.putString("type", "minecraft:the_end");

            NbtCompound generator = new NbtCompound();
            generator.putString("type", "minecraft:noise");
            generator.putString("settings", "minecraft:end");

            NbtCompound biomeSource = new NbtCompound();
            biomeSource.putString("type", "minecraft:the_end");

            generator.put("biome_source", biomeSource);
            end.put("generator", generator);

            dimensions.put("minecraft:the_end", end);
        }

        worldGenSettings.put("dimensions", dimensions);
        data.put("WorldGenSettings", worldGenSettings);

        NbtCompound dragonFight = new NbtCompound();
        dragonFight.putByte("NeedsStateScanning", (byte)0);
        dragonFight.putByte("DragonKilled", (byte)0);
        dragonFight.putByte("PreviouslyKilled", (byte)0);
        dragonFight.putByte("IsRespawning", (byte)0);
        data.put("DragonFight", dragonFight);

        NbtCompound root = new NbtCompound();
        root.put("Data", data);

        Path levelDat = worldFolder.resolve("level.dat");
        NbtIo.writeCompressed(root, levelDat);

        long now = System.currentTimeMillis();
        ByteBuffer buf = ByteBuffer.allocate(8).putLong(now);
        Files.write(worldFolder.resolve("session.lock"), buf.array());
    }


    public static NbtCompound buildChunkNbt(WorldChunk wc) {
        ChunkPos pos = wc.getPos();

        NbtCompound chunk = new NbtCompound();
        chunk.putInt("DataVersion", 4671);
        chunk.putInt("xPos", pos.x);
        chunk.putInt("zPos", pos.z);
        chunk.putString("Status", "full");
        chunk.putLong("LastUpdate", 0L);
        chunk.putLong("InhabitedTime", 0L);

        NbtList sections = new NbtList();

        ChunkSection[] sectionArray = wc.getSectionArray();
        for (int secIndex = 0; secIndex < sectionArray.length; secIndex++) {
            ChunkSection section = sectionArray[secIndex];
            if (section == null || section.isEmpty()) continue;

            NbtCompound sec = new NbtCompound();
            sec.putInt("Y", secIndex + wc.getBottomSectionCoord());

            NbtList paletteList = new NbtList();
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

                            NbtCompound entry = new NbtCompound();
                            entry.putString("Name", Registries.BLOCK.getId(state.getBlock()).toString());
                            if (!state.getProperties().isEmpty()) {
                                NbtCompound props = new NbtCompound();
                                for (Property<?> prop : state.getProperties()) {
                                    props.putString(prop.getName(), String.valueOf(state.get(prop)));
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

            NbtCompound blockStates = new NbtCompound();
            blockStates.put("palette", paletteList);
            long[] data = packIndicesVanilla(indices, bits);
            blockStates.put("data", new NbtLongArray(data));
            sec.put("block_states", blockStates);

            NbtCompound biomes = new NbtCompound();
            NbtList biomePalette = new NbtList();
            biomePalette.add(NbtString.of("minecraft:plains"));
            biomes.put("palette", biomePalette);
            biomes.put("data", new NbtLongArray(new long[]{0L}));
            sec.put("biomes", biomes);

            sections.add(sec);
        }

        chunk.put("sections", sections);
        chunk.put("block_entities", new NbtList());
        chunk.put("entities", new NbtList());
        chunk.put("Heightmaps", new NbtCompound());
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

    private record ChunkSaveTask(ChunkPos pos, NbtCompound nbt) {
    }

}