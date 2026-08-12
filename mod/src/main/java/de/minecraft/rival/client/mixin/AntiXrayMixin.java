package de.minecraft.rival.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.Tags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents covered ore models from becoming visible through transparent X-Ray packs. */
@Mixin(BlockRenderDispatcher.class)
public abstract class AntiXrayMixin {
    @Inject(
        method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void rival$hideCoveredOre(BlockState state, BlockPos position, BlockAndTintGetter level,
                                       PoseStack pose, VertexConsumer consumer, boolean checkSides,
                                       RandomSource random, ModelData modelData, RenderType renderType,
                                       CallbackInfo callback) {
        if (!state.is(Tags.Blocks.ORES)) return;
        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(position.relative(direction));
            if (!neighbour.canOcclude()) return;
        }
        callback.cancel();
    }
}
