package com.j0ker2j0ker.swd.client.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class SwdBossBar {

    // same on-screen duration vanilla uses for setOverlayMessage (60 ticks / 3s)
    private static final long DISPLAY_DURATION_MS = 3000;

    private static volatile Component message;
    private static volatile long expiresAtMs;

    public static void show(Component msg) {
        message = msg;
        expiresAtMs = System.currentTimeMillis() + DISPLAY_DURATION_MS;
    }

    public static void hide() {
        message = null;
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, int screenWidth) {
        Component msg = message;
        if (msg == null) return;
        if (System.currentTimeMillis() >= expiresAtMs) {
            message = null;
            return;
        }

        int barWidth = 182;
        int barHeight = 5;
        int x = (screenWidth - barWidth) / 2;
        int y = 12;

        graphics.fill(x, y, x + barWidth, y + barHeight, 0xFF808080);
        graphics.fill(x + 1, y + 1, x + barWidth - 1, y + barHeight - 1, 0xFF00AA00);

        graphics.centeredText(font, msg, screenWidth / 2, y - 10, 0xFFFFFFFF);
    }
}