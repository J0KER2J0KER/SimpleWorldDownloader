package com.j0ker2j0ker.swd.client.mixin;

import com.j0ker2j0ker.swd.client.util.SwdWorldMarker;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

@Mixin(WorldOpenFlows.class)
public class WorldOpenFlowsMixin {

    @Inject(method = "askForBackup", at = @At("HEAD"), cancellable = true)
    private void swd$skipBackupPromptForMarkedWorlds(
            LevelStorageAccess levelAccess,
            boolean oldCustomized,
            Runnable proceedCallback,
            Runnable cancelCallback,
            CallbackInfo ci
    ) {
        Path worldPath;
        try {
            worldPath = levelAccess.getLevelPath(LevelResource.ROOT);
        } catch (Throwable t) {
            return;
        }

        if (SwdWorldMarker.isMarked(worldPath)) {
            proceedCallback.run();
            ci.cancel();
        }
    }
}