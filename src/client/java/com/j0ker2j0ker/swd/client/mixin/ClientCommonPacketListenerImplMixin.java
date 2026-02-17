package com.j0ker2j0ker.swd.client.mixin;

import com.google.common.hash.Hashing;
import com.j0ker2j0ker.swd.client.SwdClient;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;

@Mixin(ServerPackManager.class)
public class ClientCommonPacketListenerImplMixin {

    @Inject(method = "onDownload", at = @At("TAIL"))
    private void savePackToFolder(Collection<?> data, DownloadQueue.BatchResult result, CallbackInfo ci) {
        result.downloaded().forEach((uuid, tempPath) -> {
            try {

                byte[] fileBytes = Files.readAllBytes(tempPath);
                @Deprecated
                String actualSha1 = Hashing.sha1().hashBytes(fileBytes).toString();

                if(tempPath.endsWith(actualSha1)) {
                    SwdClient.resourcepack_locations = tempPath;
                }

            } catch (IOException e) {
                SwdClient.LOGGER.warn("Error trying to save server resourcepack.", e);
            }
        });
    }
}