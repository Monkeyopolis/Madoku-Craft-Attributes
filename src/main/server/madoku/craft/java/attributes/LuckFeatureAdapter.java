package madoku.craft.java.attributes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Optional bridge for feature modules that provide managed crop state to Luck. */
public interface LuckFeatureAdapter {
	default boolean isManagedCrop(ServerLevel level, BlockPos pos, BlockState state) {
		return false;
	}

	default boolean isCropHarvestReady(ServerLevel level, BlockPos pos, BlockState state) {
		return false;
	}
}
