package com.j0ker2j0ker.swd.neoforge;

import com.j0ker2j0ker.swd.client.Swd;
import com.j0ker2j0ker.swd.client.screen.SwdConfigScreen;
import com.j0ker2j0ker.swd.client.util.SaveManager;
import com.j0ker2j0ker.swd.client.util.SwdBossBar;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import static net.minecraft.commands.Commands.literal;

@Mod(value = Swd.MOD_ID, dist = Dist.CLIENT)
public class SwdNeoForge {

    public SwdNeoForge(IEventBus modBus, ModContainer modContainer) {
        Swd.init(FMLPaths.CONFIGDIR.get());

        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> new SwdConfigScreen(parent)
        );

        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (SaveManager.isSaving) {
            SaveManager.stop();
            SaveManager.start();
        } else if (Swd.CONFIG.autoDownload && !Minecraft.getInstance().isLocalServer()) {
            SaveManager.start();
        }
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SaveManager.stop();
    }

    @SubscribeEvent
    public void onScreenClosing(ScreenEvent.Closing event) {
        SaveManager.onScreenClosed(event.getScreen());
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        SaveManager.lastClicked = event.getPos();
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        SaveManager.lastClicked = event.getTarget();
        SaveManager.onEntityInteract(event.getTarget());
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        SwdBossBar.render(event.getGuiGraphics(), client.font, client.getWindow().getGuiScaledWidth());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                literal("swd").then(literal("config").executes(ctx -> {
                    Minecraft.getInstance().execute(() ->
                            Minecraft.getInstance().setScreenAndShow(
                                    new SwdConfigScreen(Minecraft.getInstance().gui.screen())
                            )
                    );
                    return 1;
                }))
        );
    }

    @SubscribeEvent
    public void onShutdown(GameShuttingDownEvent event) {
        SaveManager.stop();
        Swd.CONFIG.save();

        if (SaveManager.saveThread != null && SaveManager.saveThread.isAlive()) {
            try {
                SaveManager.saveThread.join(3000);
            } catch (InterruptedException ignored) {}
        }
    }
}