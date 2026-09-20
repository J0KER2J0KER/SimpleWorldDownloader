package com.j0ker2j0ker.swd.client;

import com.j0ker2j0ker.swd.client.screen.SwdConfigScreen;
import com.j0ker2j0ker.swd.client.util.SaveManager;
import com.j0ker2j0ker.swd.client.util.SwdBossBar;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class SwdClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Swd.init(FabricLoader.getInstance().getConfigDir());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SaveManager.stop();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if(SaveManager.isSaving) {
                SaveManager.stop();
                SaveManager.start();
            }else {
                if(Swd.CONFIG.autoDownload && !Minecraft.getInstance().isLocalServer()) {
                    SaveManager.start();
                }
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.remove(screen).register(SaveManager::onScreenClosed);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            SaveManager.stop();
            Swd.CONFIG.save();

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
            SaveManager.onEntityInteract(entity);
            return InteractionResult.PASS;
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Swd.MOD_ID, "bossbar"), (graphics, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            SwdBossBar.render(graphics, client.font, client.getWindow().getGuiScaledWidth());
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
                                                Minecraft.getInstance().setScreenAndShow(
                                                        new SwdConfigScreen(Minecraft.getInstance().gui.screen())
                                                )
                                        );
                                        return 1;
                                    }))
            );
        });
    }
}