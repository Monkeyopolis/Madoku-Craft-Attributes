package madoku.craft.java.attributes;

import net.minecraft.server.level.ServerPlayer;

/** Optional bridge for feature modules that contribute to a player's oxygen maximum. */
@FunctionalInterface
public interface OxygenFeatureAdapter {
	int getPlayerOxygenBonusTicks(ServerPlayer player);
}
