package com.j0ker2j0ker.swd.client.mixin;

import com.j0ker2j0ker.swd.client.util.SaveManager;
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

    @Inject(at = @At("RETURN"), method = "createPauseMenu")
    public void addSaveButton(CallbackInfo ci) {
        if (minecraft.isLocalServer()) return;

        refresh();
    }
    private void refresh() {
        Identifier icon = START;
        if(SaveManager.getIsSaving()) icon = STOP;

        SpriteIconButton iconButton = this.addRenderableWidget(SpriteIconButton.builder(Component.nullToEmpty(""), (button) -> {
            SaveManager.toggle();
            button.setFocused(false);
            button.setMessage(Component.nullToEmpty(""));
            refresh();
        }, true).width(20).sprite(icon, 16, 16).build());
        iconButton.setPosition(4, height-24);
    }

}
