
package madoku.craft.attributes.mixin;

import madoku.craft.api.data.MadokuChunkDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
		method = "placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z",
		at = @At("RETURN")
	)
	private void madokuCraft$trackPlacedBlock(
		BlockPlaceContext context,
		BlockState state,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (context == null) {
			return;
		}

		Level level = context.getLevel();
		if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer)) {
			return;
		}

		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}

		BlockPos placedPos = resolvePlacedPos(context);
		if (placedPos == null) {
			return;
		}

		if (state == null || serverLevel.getBlockState(placedPos).isAir()) {
			return;
		}

		MadokuChunkDataManager.recordPlayerPlacedBlock(serverLevel, placedPos);
	}

	private static BlockPos resolvePlacedPos(BlockPlaceContext context) {
		if (context == null) {
			return null;
		}

		BlockPos placedPos = context.getClickedPos();
		return placedPos == null ? null : placedPos.immutable();
	}
}


