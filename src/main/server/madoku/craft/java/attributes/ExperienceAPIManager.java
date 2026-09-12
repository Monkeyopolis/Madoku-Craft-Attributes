package madoku.craft.java.attributes;

import net.minecraft.world.entity.player.Player;

/** Public contract for the experience attribute subsystem. */
public final class ExperienceAPIManager {
	private static final ExperienceProvider UNAVAILABLE_PROVIDER = new ExperienceProvider() { };
	private static volatile ExperienceProvider provider = UNAVAILABLE_PROVIDER;

	private ExperienceAPIManager() {
	}

	public static void registerProvider(ExperienceProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Experience provider must not be null.");
		provider = candidate;
	}

	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static int getMaxLevel() { return provider.getMaxLevel(); }
	public static int getXpNeededForNextLevel(Player player) { return provider.getXpNeededForNextLevel(player); }
	public static int getXpNeededForLevel(int level) { return provider.getXpNeededForLevel(level); }
	public static void applyDeathPenalty(Player player) { provider.applyDeathPenalty(player); }
	public static void clampPlayerLevel(Player player) { provider.clampPlayerLevel(player); }
}
