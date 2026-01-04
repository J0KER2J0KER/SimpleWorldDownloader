package com.j0ker2j0ker.swd.client.mixin;

import com.j0ker2j0ker.swd.client.SwdClient;
import com.j0ker2j0ker.swd.client.config.SwdConfig;
import com.j0ker2j0ker.swd.client.util.SaveManager;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    protected PauseScreenMixin(Component title) {
        super(title);
    }
    private static final Identifier START = Identifier.fromNamespaceAndPath("swd", "icon/start");
    private static final Identifier STOP = Identifier.fromNamespaceAndPath("swd", "icon/stop");

    @Inject(at = @At("RETURN"), method = "createPauseMenu", cancellable = true)
    public void addSaveButton(CallbackInfo ci) {
        if (minecraft.isLocalServer()) return;

        refresh();
    }

    private String getName() {
        if(!SaveManager.getIsSaving()) return "Start Downloading Chunks";
        else return "Stop Downloading Chunks";
    }
    private void refresh() {
        Identifier icon = START;
        if(SaveManager.getIsSaving()) icon = STOP;
        SpriteIconButton iconButton = this.addRenderableWidget(SpriteIconButton.builder(Component.nullToEmpty(getName()), (button) -> {
            SwdClient.getInstance().download();
            button.setFocused(false);
            button.setMessage(Component.nullToEmpty(getName()));
            refresh();
        }, true).width(20).sprite(icon, 16, 16).build());
        iconButton.setPosition(getX(), getY());
    }

    private int getX() {
        SwdConfig config = AutoConfig.getConfigHolder(SwdConfig.class).getConfig();
        switch (config.buttonSide){
            case 1: return width/2-10;
            case 2: return width-24;
            default: return 4;
        }
    }
    private int getY() {
        SwdConfig config = AutoConfig.getConfigHolder(SwdConfig.class).getConfig();
        switch (config.buttonHeight){
            case 0: return 4;
            default: return height-24;
        }
    }

}
