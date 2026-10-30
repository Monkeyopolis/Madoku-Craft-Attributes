package madoku.craft.java.attributes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;

/** Public contract for the oxygen attribute subsystem. */
public final class OxygenAPIManager {
	private static final OxygenProvider UNAVAILABLE_PROVIDER = new OxygenProvider() { };
	private static volatile OxygenProvider provider = UNAVAILABLE_PROVIDER;

	private OxygenAPIManager() {
	}

	public static void registerProvider(OxygenProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Oxygen provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void handleMaximumOxygenChanged(ServerPlayer player) { provider.handleMaximumOxygenChanged(player); }
	public static void initialize() { provider.initialize(); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static int getMaximumOxygenTicksForEntity(LivingEntity entity) {
		return provider.getMaximumOxygenTicksForEntity(entity);
	}
	public static void applyClientSynchronizedSettings(boolean enabled, int maximum) {
		provider.applyClientSynchronizedSettings(enabled, maximum);
	}
	public static void applyClientSynchronizedPlayerBonus(int bonusTicks) {
		provider.applyClientSynchronizedPlayerBonus(bonusTicks);
	}
	public static void resetClientSynchronizedSettings() {
		provider.resetClientSynchronizedSettings();
	}
}
