package com.j0ker2j0ker.swd.client.mixin;

import com.j0ker2j0ker.swd.client.util.SaveManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen{

    protected PauseScreenMixin(Component title) {
        super(title);
    }
    @Unique
    private static final Identifier START = Identifier.fromNamespaceAndPath("swd", "icon/start");
    @Unique
    private static final Identifier STOP = Identifier.fromNamespaceAndPath("swd", "icon/stop");

    @Inject(at = @At("RETURN"), method = "createPauseMenu")
    public void addSaveButton(CallbackInfo ci) {
        // only shows the button if the player is on a multiplayer server or in a flashback replay
        if (Minecraft.getInstance().isLocalServer() && !Minecraft.getInstance().getSingleplayerServer().getWorldData().getLevelName().equalsIgnoreCase("Replay")) return;

        refresh();
    }

    @Unique
    private String getName() {
        if(!SaveManager.isSaving) return "Start Downloading Chunks";
        else return "Stop Downloading Chunks";
    }
    @Unique
    private void refresh() {
        Identifier icon = START;
        if(SaveManager.isSaving) icon = STOP;
        SpriteIconButton iconButton = this.addRenderableWidget(SpriteIconButton.builder(Component.nullToEmpty(getName()), (button) -> {
            SaveManager.toggle();
            button.setFocused(false);
            button.setMessage(Component.nullToEmpty(getName()));
            refresh();
        }, true).width(20).sprite(icon, 16, 16).build());
        iconButton.setPosition(4, height-24);
    }

}
