package com.j0ker2j0ker.swd.client.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;

public class RegionStorage implements AutoCloseable {
    private final Path directory;

    public RegionStorage(Path directory) {
        this.directory = directory;
    }

    public void write(ChunkPos pos, CompoundTag nbt) throws IOException {
        Path path = directory.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
        try (RegionFile rf = new RegionFile(
                new RegionStorageInfo("swd", Level.OVERWORLD, "chunk"),
                path, directory, false)) {
            try (var out = rf.getChunkDataOutputStream(pos)) {
                NbtIo.write(nbt, (DataOutput) out);
            }
        }
    }

    @Override
    public void close() throws IOException {
    }
}
