package com.j0ker2j0ker.swd.client.mixin;

import com.j0ker2j0ker.swd.client.util.SaveManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "sendOpenInventory", at = @At("HEAD"))
    public void sendOpenInventory(CallbackInfo ci) {
        SaveManager.lastClicked = Minecraft.getInstance().player.getVehicle();
    }
}
