package com.j0ker2j0ker.swd.client.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SwdWorldMarker {
    public static final String FILE_NAME = ".swd_marker.txt";
    private static final String CONTENT = "Saved by SimpleWorldDownloader";

    private SwdWorldMarker() {}

    public static void writeMarker(Path worldPath) {
        try {
            Files.writeString(worldPath.resolve(FILE_NAME), CONTENT, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static boolean isMarked(Path worldPath) {
        return Files.isRegularFile(worldPath.resolve(FILE_NAME));
    }
}