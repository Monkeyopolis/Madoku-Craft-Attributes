package madoku.craft.java.attributes;

import net.minecraft.world.entity.player.Player;

/** Provider contract implemented by the module that owns Madoku experience attributes. */
public interface ExperienceProvider {
	default void initialize() { }
	default boolean isEnabled() { return false; }
	default int getMaxLevel() { return 0; }
	default int getXpNeededForNextLevel(Player player) { return 0; }
	default int getXpNeededForLevel(int level) { return 0; }
	default void applyDeathPenalty(Player player) { }
	default void clampPlayerLevel(Player player) { }
}
