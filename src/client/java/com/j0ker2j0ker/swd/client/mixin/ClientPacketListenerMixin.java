package com.j0ker2j0ker.swd.client.mixin;

import com.j0ker2j0ker.swd.client.util.SaveManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Shadow
    private ClientLevel level;

    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void onChunkData(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        if(!SaveManager.getIsSaving()) return;

        Minecraft mc = Minecraft.getInstance();
        if(mc.isLocalServer() || mc.getCurrentServer() == null) return;
        int chunkX = packet.getX();
        int chunkZ = packet.getZ();
        LevelChunk wc = this.level.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (wc == null || wc.isEmpty()) return;

        SaveManager.saveChunkToRegion(SaveManager.path, wc, true);
    }
}