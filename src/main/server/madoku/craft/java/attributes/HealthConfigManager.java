package madoku.craft.java.attributes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class HealthConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(HealthConfigManager.class);

	private static final String HEALTH_CONFIG_FILE_NAME = "madoku-health";
	private static final double DEFAULT_MAXIMUM_HEALTH = 20.0d;
	private static final float DEFAULT_HUNGER_DRAIN_PERCENTAGE = 0.70f;
	private static final float DEFAULT_HUNGER_PENALTY_PERCENTAGE = 0.20f;
	private static final double DEFAULT_HEALTH_PENALTY_PERCENTAGE = 0.50d;
	private static final float DEFAULT_RESPAWN_HEALTH_PERCENTAGE = 0.50f;
	private static final double DEFAULT_EFFECT_VALUE = 0.05d;
	private static final double DEFAULT_ABSORPTION_VALUE = 0.20d;
	private static final double DEFAULT_HEALTH_BOOST_VALUE = 0.10d;
	private static final double DEFAULT_WITHER_VALUE = 0.05d;
	private static final double DEFAULT_POISON_VALUE = 0.05d;
	private static final double DEFAULT_REGENERATION_VALUE = 0.05d;
	private static final double DEFAULT_POISON_PENALTY_PERCENTAGE = 0.25d;

	private HealthConfigManager() {
	}

	static Settings loadSettings(boolean systemEnabled) {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = AttributesConfigManager.prepareRootConfigFile(HEALTH_CONFIG_FILE_NAME);
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			return configured.withEnabled(systemEnabled);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load MadokuHealthManager config; using defaults.", exception);
			return fallback.withEnabled(systemEnabled);
		}
	}

	static final class Settings {
		final HealthSettings health;
		final MainSettings main;

		private Settings(HealthSettings health, MainSettings main) {
			this.health = health;
			this.main = main;
		}

		static Settings defaults() {
			return new Settings(HealthSettings.defaults(), MainSettings.defaults());
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(
				HealthSettings.fromJson(readObject(source, "health"), defaults.health),
				MainSettings.fromJson(source, defaults.main)
			);
		}

		JsonObject toConfigJson() {
			return JSONFormatAPIManager.object()
				.object("health", health -> this.health.toConfigJson(health))
				.object("absorption", absorption -> this.main.absorption.toConfigJson(absorption))
				.object("health-boost", healthBoost -> this.main.healthBoost.toConfigJson(healthBoost))
				.object("poison", poison -> this.main.poison.toConfigJson(poison))
				.object("regeneration", regeneration -> this.main.regeneration.toConfigJson(regeneration))
				.object("wither", wither -> this.main.wither.toConfigJson(wither))
				.build();
		}

		Settings withEnabled(boolean systemEnabled) {
			return new Settings(health.withEnabled(systemEnabled), main);
		}
	}

	static final class HealthSettings {
		final boolean enabled;
		final double maximumHealth;
		final float hungerDrainPercentage;
		final float respawnHealthPercentage;
		final HealthPenaltySettings healthPenalty;

		private HealthSettings(
			boolean enabled,
			double maximumHealth,
			float hungerDrainPercentage,
			float respawnHealthPercentage,
			HealthPenaltySettings healthPenalty
		) {
			this.enabled = enabled;
			this.maximumHealth = maximumHealth;
			this.hungerDrainPercentage = hungerDrainPercentage;
			this.respawnHealthPercentage = respawnHealthPercentage;
			this.healthPenalty = healthPenalty;
		}

		static HealthSettings defaults() {
			return new HealthSettings(
				true,
				DEFAULT_MAXIMUM_HEALTH,
				DEFAULT_HUNGER_DRAIN_PERCENTAGE,
				DEFAULT_RESPAWN_HEALTH_PERCENTAGE,
				HealthPenaltySettings.defaults()
			);
		}

		static HealthSettings fromJson(JsonObject source, HealthSettings defaults) {
			HealthSettings base = defaults == null ? defaults() : defaults;
			boolean enabled = getBoolean(source, "enabled", base.enabled);
			double maximumHealth = clampDouble(getDouble(source, "max-health", base.maximumHealth), 1.0d, 1024.0d);
			float hungerDrainPercentage = (float) clampDouble(
				getDouble(source, "hunger-drain-percentage", base.hungerDrainPercentage),
				0.0d,
				1.0d
			);
			float respawnHealthPercentage = (float) clampDouble(
				getDouble(source, "respawn-health-percentage", base.respawnHealthPercentage),
				0.0d,
				1.0d
			);
			HealthPenaltySettings healthPenalty = HealthPenaltySettings.fromJson(
				readObject(source, "health-penalty"),
				base.healthPenalty
			);
			return new HealthSettings(
				enabled,
				maximumHealth,
				hungerDrainPercentage,
				respawnHealthPercentage,
				healthPenalty
			);
		}

		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("max-health", maximumHealth)
				.put("hunger-drain-percentage", hungerDrainPercentage)
				.put("respawn-health-percentage", respawnHealthPercentage)
				.object("health-penalty", penalty -> healthPenalty.toConfigJson(penalty));
			return builder.build();
		}

		HealthSettings withEnabled(boolean systemEnabled) {
			return new HealthSettings(
				systemEnabled && enabled,
				maximumHealth,
				hungerDrainPercentage,
				respawnHealthPercentage,
				healthPenalty
			);
		}
	}

	static final class HealthPenaltySettings {
		final boolean enabled;
		final float hungerPenaltyPercentage;
		final double penaltyPercentage;

		private HealthPenaltySettings(boolean enabled, float hungerPenaltyPercentage, double penaltyPercentage) {
			this.enabled = enabled;
			this.hungerPenaltyPercentage = hungerPenaltyPercentage;
			this.penaltyPercentage = penaltyPercentage;
		}

		static HealthPenaltySettings defaults() {
			return new HealthPenaltySettings(true, DEFAULT_HUNGER_PENALTY_PERCENTAGE, DEFAULT_HEALTH_PENALTY_PERCENTAGE);
		}

		static HealthPenaltySettings fromJson(JsonObject source, HealthPenaltySettings defaults) {
			HealthPenaltySettings base = defaults == null ? defaults() : defaults;
			boolean enabled = getBoolean(source, "enabled", base.enabled);
			float hungerPenaltyPercentage = (float) clampDouble(
				getDouble(source, "hunger-penalty-percentage", base.hungerPenaltyPercentage),
				0.0d,
				1.0d
			);
			double penaltyPercentage = clampDouble(
				getDouble(source, "penalty-percentage", base.penaltyPercentage),
				0.10d,
				1.0d
			);
			return new HealthPenaltySettings(enabled, hungerPenaltyPercentage, penaltyPercentage);
		}

		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("hunger-penalty-percentage", hungerPenaltyPercentage)
				.put("penalty-percentage", penaltyPercentage);
			return builder.build();
		}
	}

	static final class MainSettings {
		final EffectSettings absorption;
		final EffectSettings healthBoost;
		final PoisonSettings poison;
		final EffectSettings regeneration;
		final EffectSettings wither;

		private MainSettings(
			EffectSettings absorption,
			EffectSettings healthBoost,
			PoisonSettings poison,
			EffectSettings regeneration,
			EffectSettings wither
		) {
			this.absorption = absorption;
			this.healthBoost = healthBoost;
			this.poison = poison;
			this.regeneration = regeneration;
			this.wither = wither;
		}

		static MainSettings defaults() {
			return new MainSettings(
				EffectSettings.defaults(DEFAULT_ABSORPTION_VALUE),
				EffectSettings.defaults(DEFAULT_HEALTH_BOOST_VALUE),
				PoisonSettings.defaults(),
				EffectSettings.defaults(DEFAULT_REGENERATION_VALUE),
				EffectSettings.defaults(DEFAULT_WITHER_VALUE)
			);
		}

		static MainSettings fromJson(JsonObject source, MainSettings defaults) {
			MainSettings base = defaults == null ? defaults() : defaults;
			return new MainSettings(
				EffectSettings.fromJson(readObject(source, "absorption"), base.absorption),
				EffectSettings.fromJson(readObject(source, "health-boost"), base.healthBoost),
				PoisonSettings.fromJson(readObject(source, "poison"), base.poison),
				EffectSettings.fromJson(readObject(source, "regeneration"), base.regeneration),
				EffectSettings.fromJson(readObject(source, "wither"), base.wither)
			);
		}

	}

	static class EffectSettings {
		final boolean enabled;
		final ValueType type;
		final double value;

		EffectSettings(boolean enabled, ValueType type, double value) {
			this.enabled = enabled;
			this.type = type;
			this.value = value;
		}

		static EffectSettings defaults(double value) {
			return new EffectSettings(true, ValueType.PERCENTAGE, value);
		}

		static EffectSettings fromJson(JsonObject source, EffectSettings defaults) {
			EffectSettings base = defaults == null ? defaults(DEFAULT_EFFECT_VALUE) : defaults;
			boolean enabled = getBoolean(source, "enabled", base.enabled);
			ValueType type = ValueType.fromJson(getString(source, "type", base.type.configValue), base.type);
			double value = clampEffectValue(getDouble(source, "value", base.value), type);
			return new EffectSettings(enabled, type, value);
		}

		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("type", type.configValue)
				.put("value", value);
			return builder.build();
		}
	}

	static final class PoisonSettings extends EffectSettings {
		final PoisonPenaltySettings poisonPenalty;

		private PoisonSettings(boolean enabled, ValueType type, double value, PoisonPenaltySettings poisonPenalty) {
			super(enabled, type, value);
			this.poisonPenalty = poisonPenalty;
		}

		static PoisonSettings defaults() {
			return new PoisonSettings(true, ValueType.PERCENTAGE, DEFAULT_POISON_VALUE, PoisonPenaltySettings.defaults());
		}

		static PoisonSettings fromJson(JsonObject source, PoisonSettings defaults) {
			PoisonSettings base = defaults == null ? defaults() : defaults;
			boolean enabled = getBoolean(source, "enabled", base.enabled);
			ValueType type = ValueType.fromJson(getString(source, "type", base.type.configValue), base.type);
			double value = clampEffectValue(getDouble(source, "value", base.value), type);
			PoisonPenaltySettings poisonPenalty = PoisonPenaltySettings.fromJson(
				readObject(source, "poison-penalty"),
				base.poisonPenalty
			);
			return new PoisonSettings(enabled, type, value, poisonPenalty);
		}

		@Override
		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("type", type.configValue)
				.put("value", value)
				.object("poison-penalty", penalty -> {
					penalty.put("enabled", poisonPenalty.enabled)
						.put("penalty-percentage", poisonPenalty.penaltyPercentage);
				});
			return builder.build();
		}
	}

	static final class PoisonPenaltySettings {
		final boolean enabled;
		final double penaltyPercentage;

		private PoisonPenaltySettings(boolean enabled, double penaltyPercentage) {
			this.enabled = enabled;
			this.penaltyPercentage = penaltyPercentage;
		}

		static PoisonPenaltySettings defaults() {
			return new PoisonPenaltySettings(true, DEFAULT_POISON_PENALTY_PERCENTAGE);
		}

		static PoisonPenaltySettings fromJson(JsonObject source, PoisonPenaltySettings defaults) {
			PoisonPenaltySettings base = defaults == null ? defaults() : defaults;
			boolean enabled = getBoolean(source, "enabled", base.enabled);
			double penaltyPercentage = clampDouble(getDouble(source, "penalty-percentage", base.penaltyPercentage), 0.0d, 1.0d);
			return new PoisonPenaltySettings(enabled, penaltyPercentage);
		}

		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("penalty-percentage", penaltyPercentage);
			return builder.build();
		}

		JsonObject toConfigJson() {
			return JSONFormatAPIManager.object()
				.put("enabled", enabled)
				.put("penalty-percentage", penaltyPercentage)
				.build();
		}
	}

	enum ValueType {
		PERCENTAGE("percentage"),
		FLAT("flat");

		final String configValue;

		ValueType(String configValue) {
			this.configValue = configValue;
		}

		static ValueType fromJson(String rawValue, ValueType fallback) {
			if (rawValue == null || rawValue.isBlank()) {
				return fallback;
			}
			String normalized = rawValue.trim().toLowerCase();
			for (ValueType valueType : values()) {
				if (valueType.configValue.equals(normalized)) {
					return valueType;
				}
			}
			return fallback;
		}
	}

	private static JsonObject readObject(JsonObject object, String key) {
		if (object == null || key == null || key.isBlank()) {
			return null;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonObject()) {
			return null;
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

	private static String getString(JsonObject object, String key, String fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		try {
			String value = element.getAsString();
			return value == null ? fallback : value.trim();
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

	private static double clampEffectValue(double value, ValueType type) {
		if (type == ValueType.FLAT) {
			return clampDouble(value, 0.0d, 1024.0d);
		}
		return clampDouble(value, 0.0d, 1.0d);
	}

	private static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
