package madoku.craft.attributes;

import madoku.craft.api.metadata.MadokuMetaDataManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.attributes.armor.MadokuArmorManager;
import madoku.craft.attributes.health.MadokuHealthManager;
import madoku.craft.attributes.hunger.MadokuHungerManager;
import madoku.craft.attributes.luck.MadokuLuckManager;
import madoku.craft.attributes.oxygen.MadokuOxygenManager;

public final class MadokuAttributesManager {
	private static final MadokuMetaDataManager.MainSystemMetadata ATTRIBUTES_METADATA = MadokuMetaDataManager.mainSystem(
		"attributes",
		MadokuMetaDataManager.subSystem(
			"health",
			MadokuMetaDataManager.entriesFromClass(madoku.craft.attributes.health.MadokuHealthManager.class),
			MadokuMetaDataManager.group("health-config-manager", MadokuMetaDataManager.entriesFromClass(madoku.craft.attributes.health.HealthConfigManager.class))
		),
		MadokuMetaDataManager.subSystem(
			"hunger",
			MadokuMetaDataManager.entriesFromClass(madoku.craft.attributes.hunger.MadokuHungerManager.class),
			MadokuMetaDataManager.group("hunger-config-manager", MadokuMetaDataManager.entriesFromClass(madoku.craft.attributes.hunger.HungerConfigManager.class))
		),
		MadokuMetaDataManager.subSystem(
			"armor",
			MadokuMetaDataManager.entriesFromClass(madoku.craft.attributes.armor.MadokuArmorManager.class),
			MadokuMetaDataManager.group("armor-config-manager", MadokuMetaDataManager.entriesFromClass(madoku.craft.attributes.armor.ArmorConfigManager.class))
		),
		MadokuMetaDataManager.subSystem(
			"oxygen",
			MadokuMetaDataManager.entriesFromClass(madoku.craft.attributes.oxygen.MadokuOxygenManager.class),
			MadokuMetaDataManager.group("oxygen-config-manager", MadokuMetaDataManager.entriesFromClass(madoku.craft.attributes.oxygen.OxygenConfigManager.class))
		),
		MadokuMetaDataManager.subSystem(
			"luck",
			MadokuMetaDataManager.entriesFromClass(madoku.craft.attributes.luck.MadokuLuckManager.class),
			MadokuMetaDataManager.group("luck-config-manager", MadokuMetaDataManager.entriesFromClass(madoku.craft.attributes.luck.LuckConfigManager.class))
		)
	);
	private static volatile AttributesConfigManager.Settings settings = AttributesConfigManager.Settings.defaults();

	private MadokuAttributesManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		MadokuMetaDataManager.registerMainSystem(ATTRIBUTES_METADATA);
		MadokuDebugManager.bootstrapMainSystem(ATTRIBUTES_METADATA);
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

