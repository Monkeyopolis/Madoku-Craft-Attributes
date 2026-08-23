package madoku.craft.attributes.oxygen;

import madoku.craft.attributes.MadokuAttributesManager;
import net.minecraft.world.entity.LivingEntity;

/** Provides the configured vanilla oxygen maximum for player entities. */
public final class MadokuOxygenManager {
	private static volatile OxygenConfigManager.Settings settings = OxygenConfigManager.Settings.defaults();

	private MadokuOxygenManager() {
	}

	public static void initialize() {
		settings = OxygenConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
	}

	public static boolean isEnabled() {
		return settings.oxygen.enabled;
	}

	public static int getMaximumOxygenTicksForEntity(LivingEntity entity) {
		return Math.max(1, settings.oxygen.maxOxygenTicks);
	}
}
