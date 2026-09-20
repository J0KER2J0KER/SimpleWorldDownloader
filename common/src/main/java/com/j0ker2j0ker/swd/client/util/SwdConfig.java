package com.j0ker2j0ker.swd.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SwdConfig {

    public enum NotificationMode {
        ACTIONBAR,
        BOSSBAR,
        OFF
    }

    public String saveWorldTo = "";
    public boolean autoDownload = false;
    public boolean resumeDownloads = true;
    public NotificationMode notificationMode = NotificationMode.ACTIONBAR;
    public boolean includeEntities = true;
    public boolean includePlayerData = true;
    public boolean includeResourcePacks = true;

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private static transient Path path;

    public static SwdConfig load(Path configDir) {
        path = configDir.resolve("SimpleWorldDownloader.json");
        try {
            if (Files.exists(path)) {
                return GSON.fromJson(Files.readString(path), SwdConfig.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new SwdConfig();
    }

    public void save() {
        try {
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}