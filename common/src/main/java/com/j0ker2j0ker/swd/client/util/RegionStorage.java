package com.j0ker2j0ker.swd.client.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Mimics vanilla Minecraft's region file IO: keeps {@link RegionFile} instances
 * open for the lifetime of this storage and overwrites chunk data in-place on
 * repeated writes to the same chunk position.
 */
public class RegionStorage implements AutoCloseable {

    private final Path directory;
    private final Map<Long, RegionFile> regionCache = new HashMap<>();

    public RegionStorage(Path directory) {
        this.directory = directory;
    }

    /**
     * Write or overwrite chunk NBT data into the appropriate region file.
     * The underlying {@link RegionFile} is cached and reused, so subsequent
     * writes to the same region do not re-open the file.
     */
    public void write(ChunkPos pos, CompoundTag nbt,
                      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) throws IOException {
        RegionFile rf = getOrOpenRegion(pos, dimension);
        // getChunkDataOutputStream overwrites any existing chunk entry for this position
        try (var out = rf.getChunkDataOutputStream(pos)) {
            NbtIo.write(nbt, (DataOutput) out);
        }
    }

    /**
     * Read existing chunk NBT data from the region file, or null if no entry exists.
     */
    public CompoundTag read(ChunkPos pos,
                            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) throws IOException {
        RegionFile rf = getOrOpenRegion(pos, dimension);
        try (var in = rf.getChunkDataInputStream(pos)) {
            if (in == null) return null;
            return NbtIo.read((DataInput) in);
        }
    }

    /**
     * Returns true if the chunk entry exists in the region file.
     */
    public boolean hasChunk(ChunkPos pos,
                            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) throws IOException {
        RegionFile rf = getOrOpenRegion(pos, dimension);
        try (var in = rf.getChunkDataInputStream(pos)) {
            return in != null;
        }
    }

    private RegionFile getOrOpenRegion(ChunkPos pos,
                                       net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) throws IOException {
        long regionKey = packRegion(pos.getRegionX(), pos.getRegionZ());
        RegionFile rf = regionCache.get(regionKey);
        if (rf == null) {
            Path path = directory.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
            rf = new RegionFile(
                    new RegionStorageInfo("swd", dimension, "chunk"),
                    path, directory, false);
            regionCache.put(regionKey, rf);
        }
        return rf;
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        for (RegionFile rf : regionCache.values()) {
            try {
                rf.close();
            } catch (IOException e) {
                if (first == null) first = e;
            }
        }
        regionCache.clear();
        if (first != null) throw first;
    }

    private static long packRegion(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }
}