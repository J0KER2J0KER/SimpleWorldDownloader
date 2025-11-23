package com.j0ker2j0ker.swd.client.mixin;

import com.j0ker2j0ker.swd.client.SwdClient;
import com.j0ker2j0ker.swd.client.util.SaveManager;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextIconButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen{

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }
    private static final Identifier START = Identifier.of("swd", "icon/start");
    private static final Identifier STOP = Identifier.of("swd", "icon/stop");

    @Inject(at = @At("RETURN"), method = "initWidgets", cancellable = true)
    public void addSaveButton(CallbackInfo ci) {
        if (client.isInSingleplayer()) return;

        refresh();
    }

    private String getName() {
        if(!SaveManager.getIsSaving()) return "Start Downloading Chunks";
        else return "Stop Downloading Chunks";
    }
    private void refresh() {
        Identifier icon = START;
        if(SaveManager.getIsSaving()) icon = STOP;
        TextIconButtonWidget iconButton = this.addDrawableChild(TextIconButtonWidget.builder(Text.of(getName()), (button) -> {
            SwdClient.getInstance().download();
            button.setFocused(false);
            button.setMessage(Text.of(getName()));
            refresh();
        }, true).width(20).texture(icon, 16, 16).build());
        iconButton.setPosition(4, height-24);
    }

}
