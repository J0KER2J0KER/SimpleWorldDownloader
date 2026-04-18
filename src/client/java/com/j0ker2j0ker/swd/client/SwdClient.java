package com.j0ker2j0ker.swd.client;

import com.j0ker2j0ker.swd.client.screen.SwdConfigScreen;
import com.j0ker2j0ker.swd.client.util.SaveManager;
import com.j0ker2j0ker.swd.client.util.SwdConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class SwdClient implements ClientModInitializer {

    public static SwdConfig CONFIG;
    public static Path resourcepack_locations;

    public static final String MOD_ID = "swd";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        CONFIG = SwdConfig.load();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SaveManager.stop();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if(SaveManager.isSaving) {
                SaveManager.stop();
                SaveManager.start();
            }else {
                if(CONFIG.autoDownload && !Minecraft.getInstance().isLocalServer()) {
                    SaveManager.start();
                }
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.remove(screen).register((closedScreen) -> {
                SaveManager.onScreenClosed(closedScreen);
            });
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            SaveManager.stop();
            CONFIG.save();

            if (SaveManager.saveThread != null && SaveManager.saveThread.isAlive()) {
                try {
                    SaveManager.saveThread.join(3000);
                } catch (InterruptedException ignored) {}
            }
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            SaveManager.lastClicked = hitResult.getBlockPos();
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            SaveManager.lastClicked = entity;
            return InteractionResult.PASS;
        });

        registerCommands();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("swd")
                            .then(literal("config")
                                    .executes(ctx -> {
                                        Minecraft.getInstance().execute(() ->
                                                Minecraft.getInstance().setScreen(
                                                        new SwdConfigScreen(Minecraft.getInstance().screen)
                                                )
                                        );
                                        return 1;
                                    }))
            );
        });
    }
}
