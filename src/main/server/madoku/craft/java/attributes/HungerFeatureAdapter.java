package madoku.craft.java.attributes;

import net.minecraft.server.level.ServerPlayer;

/** Optional bridge for feature modules that contribute to a player's hunger maximum. */
@FunctionalInterface
public interface HungerFeatureAdapter {
	int getPlayerHungerBonusPoints(ServerPlayer player);
}
