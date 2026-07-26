package madoku.craft.attributes;

import madoku.craft.attributes.armor.MadokuArmorManager;
import madoku.craft.attributes.health.MadokuHealthManager;
import madoku.craft.attributes.hunger.MadokuHungerManager;
import madoku.craft.attributes.luck.MadokuLuckManager;
import madoku.craft.attributes.oxygen.MadokuOxygenManager;

public final class MadokuAttributesManager {
	private static volatile AttributesConfigManager.Settings settings = AttributesConfigManager.Settings.defaults();

	private MadokuAttributesManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		MadokuArmorManager.initialize();
		MadokuHealthManager.initialize();
		MadokuHungerManager.initialize();
		MadokuOxygenManager.initialize();
		MadokuLuckManager.initialize();
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	private static void loadStaticConfig() {
		settings = AttributesConfigManager.loadSettings();
	}
}
