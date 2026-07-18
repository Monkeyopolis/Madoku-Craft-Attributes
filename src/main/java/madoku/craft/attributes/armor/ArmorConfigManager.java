package madoku.craft.attributes.armor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.attributes.AttributesConfigManager;
import madoku.craft.api.json.JSONFormatManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class ArmorConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ArmorConfigManager.class);

	private static final boolean DEFAULT_ENABLED = true;
	private static final double DEFAULT_STARTING_ARMOR_POINTS = 0.0d;
	private static final double DEFAULT_MAX_ARMOR_POINTS = 100.0d;
	private static final DamageReductionType DEFAULT_ARMOR_POINTS_REDUCTION_TYPE = DamageReductionType.FLAT;
	private static final double DEFAULT_ARMOR_POINTS_REDUCTION_VALUE = 0.05d;

	private static final double DEFAULT_STARTING_ARMOR_TOUGHNESS_POINTS = 0.0d;
	private static final double DEFAULT_MAX_ARMOR_TOUGHNESS_POINTS = 100.0d;
	private static final DamageReductionType DEFAULT_ARMOR_TOUGHNESS_REDUCTION_TYPE = DamageReductionType.PERCENTAGE;
	private static final double DEFAULT_ARMOR_TOUGHNESS_REDUCTION_VALUE = 0.01d;

	private static final double DEFAULT_FALL_DAMAGE_REDUCTION = 0.5d;
	private static final DamageReductionType DEFAULT_RESISTANCE_REDUCTION_TYPE = DamageReductionType.PERCENTAGE;
	private static final double DEFAULT_RESISTANCE_REDUCTION_VALUE = 0.2d;

	private static final String ARMOR_CONFIG_FILE_NAME = "madoku-armor";

	private ArmorConfigManager() {
	}

	static Settings loadSettings(boolean systemEnabled) {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = AttributesConfigManager.prepareRootConfigFile(ARMOR_CONFIG_FILE_NAME);
			JsonObject normalized = JSONFormatManager.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			JSONFormatManager.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			return configured.withEnabled(systemEnabled);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load MadokuArmorManager config; using defaults.", exception);
			return fallback.withEnabled(systemEnabled);
		}
	}

	static final class Settings {
		final boolean enabled;
		final MainSettings main;
		final ArmorPointsSettings armorPoints;
		final ArmorToughnessPointsSettings armorToughnessPoints;

		private Settings(
			boolean enabled,
			MainSettings main,
			ArmorPointsSettings armorPoints,
			ArmorToughnessPointsSettings armorToughnessPoints
		) {
			this.enabled = enabled;
			this.main = main;
			this.armorPoints = armorPoints;
			this.armorToughnessPoints = armorToughnessPoints;
		}

		static Settings defaults() {
			return new Settings(
				DEFAULT_ENABLED,
				MainSettings.defaults(),
				ArmorPointsSettings.defaults(),
				ArmorToughnessPointsSettings.defaults()
			);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();

			boolean enabled = getBoolean(source, "enabled", defaults.enabled);
			MainSettings main = MainSettings.fromJson(source);
			ArmorPointsSettings armorPoints = ArmorPointsSettings.fromJson(readObject(source, "armor-points"));
			ArmorToughnessPointsSettings armorToughnessPoints = ArmorToughnessPointsSettings.fromJson(
				readObject(source, "armor-toughness-points")
			);

			return new Settings(enabled, main, armorPoints, armorToughnessPoints);
		}

		JsonObject toConfigJson() {
			return JSONFormatManager.object()
				.put("enabled", enabled)
				.put("fall-damage-reduction", main.fallDamageReduction)
				.object("resistance", resistance -> {
					resistance.put("enabled", main.resistance.enabled);
					resistance.put("type", main.resistance.type.configValue);
					resistance.put("value", main.resistance.value);
				})
				.object("armor-points", group -> {
					group.put("enabled", armorPoints.enabled);
					group.put("starting-points", armorPoints.startingArmor);
					group.put("max-points", armorPoints.maxPoints);
					group.object("damage-reduction", damageReduction -> {
						damageReduction.put("type", armorPoints.damageReduction.type.configValue);
						damageReduction.put("value", armorPoints.damageReduction.value);
					});
				})
				.object("armor-toughness-points", group -> {
					group.put("enabled", armorToughnessPoints.enabled);
					group.put("starting-points", armorToughnessPoints.startingArmorToughness);
					group.put("max-points", armorToughnessPoints.maxPoints);
					group.object("damage-reduction", damageReduction -> {
						damageReduction.put("type", armorToughnessPoints.damageReduction.type.configValue);
						damageReduction.put("value", armorToughnessPoints.damageReduction.value);
					});
				})
				.build();
		}

		Settings withEnabled(boolean attributesEnabled) {
			return new Settings(attributesEnabled && enabled, main, armorPoints, armorToughnessPoints);
		}

		private static JsonObject readObject(JsonObject object, String key) {
			if (object == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			JsonElement element = object.get(key);
			if (element == null || !element.isJsonObject()) {
				return new JsonObject();
			}
			return element.getAsJsonObject();
		}

		private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
			if (object == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = object.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			try {
				return element.getAsBoolean();
			} catch (RuntimeException exception) {
				return fallback;
			}
		}

		private static double getDouble(JsonObject object, String key, double fallback) {
			if (object == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = object.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
				return fallback;
			}
			try {
				return element.getAsDouble();
			} catch (RuntimeException exception) {
				return fallback;
			}
		}

		private static String getString(JsonObject object, String key, String fallback) {
			if (object == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = object.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				return fallback;
			}
			try {
				return element.getAsString();
			} catch (RuntimeException exception) {
				return fallback;
			}
		}

		private static double clampDouble(double value, double min, double max) {
			return Math.max(min, Math.min(max, value));
		}

		private static DamageReductionType readDamageReductionType(JsonObject source, DamageReductionType fallback) {
			String normalized = getString(source, "type", fallback.configValue).trim().toLowerCase(Locale.ROOT);
			return DamageReductionType.fromConfigValue(normalized, fallback);
		}

		private static double readDamageReductionValue(JsonObject source, DamageReductionType type, double fallback) {
			double value = getDouble(source, "value", fallback);
			if (type == DamageReductionType.PERCENTAGE) {
				return clampDouble(value, 0.0d, 1.0d);
			}
			return Math.max(0.0d, value);
		}
	}

	static final class MainSettings {
		final double fallDamageReduction;
		final ResistanceSettings resistance;

		private MainSettings(double fallDamageReduction, ResistanceSettings resistance) {
			this.fallDamageReduction = fallDamageReduction;
			this.resistance = resistance;
		}

		static MainSettings defaults() {
			return new MainSettings(
				DEFAULT_FALL_DAMAGE_REDUCTION,
				ResistanceSettings.defaults()
			);
		}

		static MainSettings fromJson(JsonObject source) {
			MainSettings defaults = defaults();
			double fallDamageReduction = Settings.clampDouble(
				Settings.getDouble(source, "fall-damage-reduction", defaults.fallDamageReduction),
				0.0d,
				1.0d
			);
			ResistanceSettings resistance = ResistanceSettings.fromJson(Settings.readObject(source, "resistance"));
			return new MainSettings(fallDamageReduction, resistance);
		}
	}

	static final class ResistanceSettings {
		final boolean enabled;
		final DamageReductionType type;
		final double value;

		private ResistanceSettings(boolean enabled, DamageReductionType type, double value) {
			this.enabled = enabled;
			this.type = type;
			this.value = value;
		}

		static ResistanceSettings defaults() {
			return new ResistanceSettings(
				true,
				DEFAULT_RESISTANCE_REDUCTION_TYPE,
				DEFAULT_RESISTANCE_REDUCTION_VALUE
			);
		}

		static ResistanceSettings fromJson(JsonObject source) {
			ResistanceSettings defaults = defaults();
			boolean enabled = Settings.getBoolean(source, "enabled", defaults.enabled);
			DamageReductionType type = Settings.readDamageReductionType(source, defaults.type);
			double value = Settings.readDamageReductionValue(source, type, defaults.value);
			return new ResistanceSettings(enabled, type, value);
		}
	}

	static final class ArmorPointsSettings {
		final boolean enabled;
		final double startingArmor;
		final double maxPoints;
		final DamageReduction damageReduction;

		private ArmorPointsSettings(boolean enabled, double startingArmor, double maxPoints, DamageReduction damageReduction) {
			this.enabled = enabled;
			this.startingArmor = startingArmor;
			this.maxPoints = maxPoints;
			this.damageReduction = damageReduction;
		}

		static ArmorPointsSettings defaults() {
			return new ArmorPointsSettings(
				true,
				DEFAULT_STARTING_ARMOR_POINTS,
				DEFAULT_MAX_ARMOR_POINTS,
				DamageReduction.defaults(DEFAULT_ARMOR_POINTS_REDUCTION_TYPE, DEFAULT_ARMOR_POINTS_REDUCTION_VALUE)
			);
		}

		static ArmorPointsSettings fromJson(JsonObject source) {
			ArmorPointsSettings defaults = defaults();
			boolean enabled = Settings.getBoolean(source, "enabled", defaults.enabled);
			double startingArmor = Settings.clampDouble(
				Settings.getDouble(source, "starting-points", defaults.startingArmor),
				0.0d,
				100000.0d
			);
			double maxPoints = Settings.clampDouble(
				Settings.getDouble(source, "max-points", defaults.maxPoints),
				startingArmor,
				100000.0d
			);
			JsonObject damageReductionRoot = Settings.readObject(source, "damage-reduction");
			DamageReduction damageReduction = DamageReduction.fromJson(
				damageReductionRoot,
				DEFAULT_ARMOR_POINTS_REDUCTION_TYPE,
				DEFAULT_ARMOR_POINTS_REDUCTION_VALUE
			);
			return new ArmorPointsSettings(enabled, startingArmor, maxPoints, damageReduction);
		}
	}

	static final class ArmorToughnessPointsSettings {
		final boolean enabled;
		final double startingArmorToughness;
		final double maxPoints;
		final DamageReduction damageReduction;

		private ArmorToughnessPointsSettings(
			boolean enabled,
			double startingArmorToughness,
			double maxPoints,
			DamageReduction damageReduction
		) {
			this.enabled = enabled;
			this.startingArmorToughness = startingArmorToughness;
			this.maxPoints = maxPoints;
			this.damageReduction = damageReduction;
		}

		static ArmorToughnessPointsSettings defaults() {
			return new ArmorToughnessPointsSettings(
				true,
				DEFAULT_STARTING_ARMOR_TOUGHNESS_POINTS,
				DEFAULT_MAX_ARMOR_TOUGHNESS_POINTS,
				DamageReduction.defaults(DEFAULT_ARMOR_TOUGHNESS_REDUCTION_TYPE, DEFAULT_ARMOR_TOUGHNESS_REDUCTION_VALUE)
			);
		}

		static ArmorToughnessPointsSettings fromJson(JsonObject source) {
			ArmorToughnessPointsSettings defaults = defaults();
			boolean enabled = Settings.getBoolean(source, "enabled", defaults.enabled);
			double startingArmorToughness = Settings.clampDouble(
				Settings.getDouble(source, "starting-points", defaults.startingArmorToughness),
				0.0d,
				100000.0d
			);
			double maxPoints = Settings.clampDouble(
				Settings.getDouble(source, "max-points", defaults.maxPoints),
				startingArmorToughness,
				100000.0d
			);
			JsonObject damageReductionRoot = Settings.readObject(source, "damage-reduction");
			DamageReduction damageReduction = DamageReduction.fromJson(
				damageReductionRoot,
				DEFAULT_ARMOR_TOUGHNESS_REDUCTION_TYPE,
				DEFAULT_ARMOR_TOUGHNESS_REDUCTION_VALUE
			);
			return new ArmorToughnessPointsSettings(enabled, startingArmorToughness, maxPoints, damageReduction);
		}
	}

	static final class DamageReduction {
		final DamageReductionType type;
		final double value;

		private DamageReduction(DamageReductionType type, double value) {
			this.type = type;
			this.value = value;
		}

		static DamageReduction defaults(DamageReductionType type, double value) {
			return new DamageReduction(type, value);
		}

		static DamageReduction fromJson(JsonObject source, DamageReductionType fallbackType, double fallbackValue) {
			DamageReductionType type = Settings.readDamageReductionType(source, fallbackType);
			double value = Settings.readDamageReductionValue(source, type, fallbackValue);
			return new DamageReduction(type, value);
		}
	}

	enum DamageReductionType {
		PERCENTAGE("percentage"),
		FLAT("flat");

		final String configValue;

		DamageReductionType(String configValue) {
			this.configValue = configValue;
		}

		static DamageReductionType fromConfigValue(String value, DamageReductionType fallback) {
			if (value == null) {
				return fallback;
			}
			return switch (value) {
				case "percentage" -> PERCENTAGE;
				case "flat" -> FLAT;
				default -> fallback;
			};
		}
	}
}
