package madoku.craft.java.attributes;

import net.minecraft.world.entity.LivingEntity;

/** Provides the configured vanilla oxygen maximum for player air-supply hooks and HUDs. */
public final class MadokuOxygenManager {
	private static volatile OxygenConfigManager.Settings settings = OxygenConfigManager.Settings.defaults();
	private static volatile Boolean clientSynchronizedEnabled;
	private static volatile Integer clientSynchronizedMaximum;

	private MadokuOxygenManager() {
	}

	public static void initialize() {
		settings = OxygenConfigManager.loadSettings(AttributesConfigManager.loadSettings().enabled);
	}

	public static boolean isEnabled() {
		Boolean synchronizedEnabled = clientSynchronizedEnabled;
		return synchronizedEnabled == null ? settings.oxygen.enabled : synchronizedEnabled;
	}

	public static int getMaximumOxygenTicksForEntity(LivingEntity entity) {
		Integer synchronizedMaximum = clientSynchronizedMaximum;
		return Math.max(1, synchronizedMaximum == null ? settings.oxygen.maxOxygenTicks : synchronizedMaximum);
	}

	public static void applyClientSynchronizedSettings(boolean enabled, int maximum) {
		clientSynchronizedEnabled = enabled;
		clientSynchronizedMaximum = Math.max(1, maximum);
	}

	public static void resetClientSynchronizedSettings() {
		clientSynchronizedEnabled = null;
		clientSynchronizedMaximum = null;
	}
}
