package com.j0ker2j0ker.swd.client.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.RegionFile;
import net.minecraft.world.storage.StorageKey;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;

public class RegionStorage implements AutoCloseable {
    private final Path directory;

    public RegionStorage(Path directory) {
        this.directory = directory;
    }

    public void write(ChunkPos pos, NbtCompound nbt) throws IOException {
        Path path = directory.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
        try (RegionFile rf = new RegionFile(
                new StorageKey("swd", World.OVERWORLD, "chunk"),
                path, directory, false)) {
            try (var out = rf.getChunkOutputStream(pos)) {
                NbtIo.write(nbt, (DataOutput) out);
            }
        }
    }

    @Override
    public void close() throws IOException {
    }
}
