package madoku.craft.mixin;

import madoku.craft.luck.MadokuPlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemPlacedBlocksMixin {
	@Inject(
		method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
		at = @At("RETURN")
	)
	private void madokuCraft$trackPlacedBlock(
		BlockPlaceContext context,
		CallbackInfoReturnable<InteractionResult> cir
	) {
		if (context == null) {
			return;
		}

		Level level = context.getLevel();
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		InteractionResult result = cir.getReturnValue();
		if (result == null || !result.consumesAction()) {
			return;
		}

		BlockPos placedPos = resolvePlacedPos(context);
		if (placedPos == null) {
			return;
		}

		BlockState placedState = serverLevel.getBlockState(placedPos);
		if (placedState.isAir()) {
			return;
		}

		MadokuPlacedBlocks.recordPlacedBlock(serverLevel, placedPos);
	}

	private static BlockPos resolvePlacedPos(BlockPlaceContext context) {
		if (context == null) {
			return null;
		}

		BlockPos placedPos = context.getClickedPos();
		return placedPos == null ? null : placedPos.immutable();
	}
}
