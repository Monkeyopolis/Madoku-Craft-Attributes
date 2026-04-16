package madoku.craft.mixin;

import madoku.craft.luck.MadokuLuck;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockLuckContextMixin {
	@Inject(
		method = "playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V",
		at = @At("HEAD")
	)
	private void madokuCraft$beginLuckDropContext(
		Level level,
		Player player,
		BlockPos pos,
		BlockState state,
		BlockEntity blockEntity,
		ItemStack tool,
		CallbackInfo ci
	) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			MadokuLuck.beginBlockDropContext(serverLevel, serverPlayer, pos, state);
		} else {
			MadokuLuck.endBlockDropContext();
		}
	}

	@Inject(
		method = "playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V",
		at = @At("RETURN")
	)
	private void madokuCraft$endLuckDropContext(
		Level level,
		Player player,
		BlockPos pos,
		BlockState state,
		BlockEntity blockEntity,
		ItemStack tool,
		CallbackInfo ci
	) {
		MadokuLuck.endBlockDropContext();
	}
}
