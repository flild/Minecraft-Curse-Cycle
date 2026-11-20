package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.FireTrailChallenge;
import com.cursecycle.challenge.FloorIsLavaPlacements;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
public abstract class FireTrailWorldChunkMixin {
    @Redirect(method = "setBlockState", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/chunk/ChunkSection;setBlockState(IIILnet/minecraft/block/BlockState;)Lnet/minecraft/block/BlockState;"))
    private BlockState cursecycle$observeReplacement(ChunkSection section, int x, int y, int z, BlockState next,
                                                    BlockPos pos, BlockState state, boolean moved) {
        var old = section.setBlockState(x, y, z, next);
        FloorIsLavaPlacements.replaced((WorldChunk) (Object) this, pos, old, next);
        FireTrailChallenge.replaced((WorldChunk) (Object) this, pos, old, next);
        return old;
    }
    @Inject(method = "setLoadedToWorld", at = @At("HEAD"))
    private void cursecycle$beforeUnload(boolean loaded, CallbackInfo ci) {
        if (!loaded) FireTrailChallenge.unloading((WorldChunk) (Object) this);
    }
}
