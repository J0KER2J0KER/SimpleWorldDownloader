package com.j0ker2j0ker.swd.client;

import com.j0ker2j0ker.swd.client.util.SaveManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class SwdClient implements ClientModInitializer {

    private static SwdClient instance;

    @Override
    public void onInitializeClient() {
        initialize();
    }

    private void initialize() {
        instance = this;

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.getServer() == null) {
                SaveManager.stop();
            }
        });
    }

    public static SwdClient getInstance() {
        return instance;
    }

    public void download() {
        SaveManager.toggle();
    }
}
