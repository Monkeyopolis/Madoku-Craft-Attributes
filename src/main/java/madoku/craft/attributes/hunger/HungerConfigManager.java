package madoku.craft.attributes.hunger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.attributes.AttributesConfigManager;
import madoku.craft.api.json.JSONFormatManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class HungerConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(HungerConfigManager.class);

	private static final int MAX_CONFIG_HUNGER_POINTS = 8192;
	private static final double DEFAULT_STARVATION_PENALTY_PERCENTAGE = 0.25d;
	private static final double DEFAULT_RESPAWN_HUNGER_PERCENTAGE = 0.50d;
	private static final double DEFAULT_EFFECT_VALUE = 0.05d;
	private static final long DEFAULT_BLOCK_GOAL_VALUE = 128L;
	private static final double DEFAULT_MOVEMENT_GOAL_VALUE = 150.0d;
	private static final long DEFAULT_TIME_GOAL_VALUE = 6000L;
	private static final String HUNGER_CONFIG_FILE_NAME = "madoku-hunger";

	private HungerConfigManager() {
	}

	static Settings loadSettings(boolean systemEnabled) {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = AttributesConfigManager.prepareRootConfigFile(HUNGER_CONFIG_FILE_NAME);
			JsonObject normalized = JSONFormatManager.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			JSONFormatManager.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			return configured.withEnabled(systemEnabled);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load MadokuHungerManager config; using defaults.", exception);
			return fallback.withEnabled(systemEnabled);
		}
	}

	static final class Settings {
		final HungerSettings hunger;
		final HungerDepletionSettings hungerDepletion;
		final EffectSettings saturation;
		final EffectSettings hungerEffect;

		private Settings(
			HungerSettings hunger,
			HungerDepletionSettings hungerDepletion,
			EffectSettings saturation,
			EffectSettings hungerEffect
		) {
			this.hunger = hunger;
			this.hungerDepletion = hungerDepletion;
			this.saturation = saturation;
			this.hungerEffect = hungerEffect;
		}

		static Settings defaults() {
			return new Settings(
				HungerSettings.defaults(),
				HungerDepletionSettings.defaults(),
				EffectSettings.defaults(DEFAULT_EFFECT_VALUE),
				EffectSettings.defaults(DEFAULT_EFFECT_VALUE)
			);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(
				HungerSettings.fromJson(readObject(source, "hunger"), defaults.hunger),
				HungerDepletionSettings.fromJson(readObject(source, "hunger-depletion"), defaults.hungerDepletion),
				EffectSettings.fromJson(readObject(source, "saturation"), defaults.saturation),
				EffectSettings.fromJson(readObject(source, "hunger-effect"), defaults.hungerEffect)
			);
		}

		JsonObject toConfigJson() {
			return JSONFormatManager.object()
				.object("hunger", hunger -> this.hunger.toConfigJson(hunger))
				.object("hunger-depletion", hungerDepletion -> this.hungerDepletion.toConfigJson(hungerDepletion))
				.object("saturation", saturation -> this.saturation.toConfigJson(saturation))
				.object("hunger-effect", hungerEffect -> this.hungerEffect.toConfigJson(hungerEffect))
				.build();
		}

		Settings withEnabled(boolean systemEnabled) {
			return new Settings(
				hunger.withEnabled(systemEnabled),
				hungerDepletion,
				saturation,
				hungerEffect
			);
		}
	}

	static final class HungerSettings {
		final boolean enabled;
		final int maxHunger;
		final StarvationPenaltySettings starvationPenalty;
		final double respawnHungerPercentage;

		private HungerSettings(
			boolean enabled,
			int maxHunger,
			StarvationPenaltySettings starvationPenalty,
			double respawnHungerPercentage
		) {
			this.enabled = enabled;
			this.maxHunger = maxHunger;
			this.starvationPenalty = starvationPenalty;
			this.respawnHungerPercentage = respawnHungerPercentage;
		}

		static HungerSettings defaults() {
			return new HungerSettings(
				true,
				30,
				StarvationPenaltySettings.defaults(),
				DEFAULT_RESPAWN_HUNGER_PERCENTAGE
			);
		}

		static HungerSettings fromJson(JsonObject source, HungerSettings defaults) {
			HungerSettings base = defaults == null ? defaults() : defaults;
			boolean enabled = getBoolean(source, "enabled", base.enabled);
			int maxHunger = (int) clampLong(
				getLong(source, "max-hunger", base.maxHunger),
				1L,
				MAX_CONFIG_HUNGER_POINTS
			);
			StarvationPenaltySettings starvationPenalty = StarvationPenaltySettings.fromJson(
				readObject(source, "starvation-penalty"),
				base.starvationPenalty
			);
			double respawnHungerPercentage = clampDouble(
				getDouble(source, "respawn-hunger-percentage", base.respawnHungerPercentage),
				0.0d,
				1.0d
			);
			return new HungerSettings(enabled, maxHunger, starvationPenalty, respawnHungerPercentage);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("max-hunger", maxHunger)
				.object("starvation-penalty", starvationPenalty -> this.starvationPenalty.toConfigJson(starvationPenalty))
				.put("respawn-hunger-percentage", respawnHungerPercentage);
			return builder.build();
		}

		HungerSettings withEnabled(boolean systemEnabled) {
			return new HungerSettings(
				systemEnabled && enabled,
				maxHunger,
				starvationPenalty,
				respawnHungerPercentage
			);
		}
	}

	static final class StarvationPenaltySettings {
		final boolean enabled;
		final double starvationPenaltyPercentage;

		private StarvationPenaltySettings(boolean enabled, double starvationPenaltyPercentage) {
			this.enabled = enabled;
			this.starvationPenaltyPercentage = starvationPenaltyPercentage;
		}

		static StarvationPenaltySettings defaults() {
			return new StarvationPenaltySettings(true, DEFAULT_STARVATION_PENALTY_PERCENTAGE);
		}

		static StarvationPenaltySettings fromJson(JsonObject source, StarvationPenaltySettings defaults) {
			StarvationPenaltySettings base = defaults == null ? defaults() : defaults;
			boolean enabled = getBoolean(source, "enabled", base.enabled);
			double starvationPenaltyPercentage = clampDouble(
				getDouble(source, "starvation-penalty-percentage", base.starvationPenaltyPercentage),
				0.0d,
				1.0d
			);
			return new StarvationPenaltySettings(enabled, starvationPenaltyPercentage);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("starvation-penalty-percentage", starvationPenaltyPercentage);
			return builder.build();
		}
	}

	static final class HungerDepletionSettings {
		final boolean enabled;
		final BlockGoalSettings blockGoal;
		final MovementGoalSettings movementGoal;
		final TimeGoalSettings timeGoal;

		private HungerDepletionSettings(
			boolean enabled,
			BlockGoalSettings blockGoal,
			MovementGoalSettings movementGoal,
			TimeGoalSettings timeGoal
		) {
			this.enabled = enabled;
			this.blockGoal = blockGoal;
			this.movementGoal = movementGoal;
			this.timeGoal = timeGoal;
		}

		static HungerDepletionSettings defaults() {
			return new HungerDepletionSettings(
				true,
				BlockGoalSettings.defaults(),
				MovementGoalSettings.defaults(),
				TimeGoalSettings.defaults()
			);
		}

		static HungerDepletionSettings fromJson(JsonObject source, HungerDepletionSettings defaults) {
			HungerDepletionSettings base = defaults == null ? defaults() : defaults;
			return new HungerDepletionSettings(
				getBoolean(source, "enabled", base.enabled),
				BlockGoalSettings.fromJson(readObject(source, "block-goal"), base.blockGoal),
				MovementGoalSettings.fromJson(readObject(source, "movement-goal"), base.movementGoal),
				TimeGoalSettings.fromJson(readObject(source, "time-goal"), base.timeGoal)
			);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.object("block-goal", blockGoal -> this.blockGoal.toConfigJson(blockGoal))
				.object("movement-goal", movementGoal -> this.movementGoal.toConfigJson(movementGoal))
				.object("time-goal", timeGoal -> this.timeGoal.toConfigJson(timeGoal));
			return builder.build();
		}
	}

	static final class BlockGoalSettings {
		final boolean enabled;
		final long value;

		private BlockGoalSettings(boolean enabled, long value) {
			this.enabled = enabled;
			this.value = value;
		}

		static BlockGoalSettings defaults() {
			return new BlockGoalSettings(true, DEFAULT_BLOCK_GOAL_VALUE);
		}

		static BlockGoalSettings fromJson(JsonObject source, BlockGoalSettings defaults) {
			BlockGoalSettings base = defaults == null ? defaults() : defaults;
			return new BlockGoalSettings(
				getBoolean(source, "enabled", base.enabled),
				clampLong(getLong(source, "value", base.value), 1L, 100000L)
			);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("value", value);
			return builder.build();
		}
	}

	static final class MovementGoalSettings {
		final boolean enabled;
		final double value;

		private MovementGoalSettings(boolean enabled, double value) {
			this.enabled = enabled;
			this.value = value;
		}

		static MovementGoalSettings defaults() {
			return new MovementGoalSettings(true, DEFAULT_MOVEMENT_GOAL_VALUE);
		}

		static MovementGoalSettings fromJson(JsonObject source, MovementGoalSettings defaults) {
			MovementGoalSettings base = defaults == null ? defaults() : defaults;
			return new MovementGoalSettings(
				getBoolean(source, "enabled", base.enabled),
				clampDouble(getDouble(source, "value", base.value), 1.0d, 1000000.0d)
			);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("value", value);
			return builder.build();
		}
	}

	static final class TimeGoalSettings {
		final boolean enabled;
		final long value;

		private TimeGoalSettings(boolean enabled, long value) {
			this.enabled = enabled;
			this.value = value;
		}

		static TimeGoalSettings defaults() {
			return new TimeGoalSettings(true, DEFAULT_TIME_GOAL_VALUE);
		}

		static TimeGoalSettings fromJson(JsonObject source, TimeGoalSettings defaults) {
			TimeGoalSettings base = defaults == null ? defaults() : defaults;
			return new TimeGoalSettings(
				getBoolean(source, "enabled", base.enabled),
				clampLong(getLong(source, "value", base.value), 1L, 20L * 60L * 60L * 24L)
			);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("value", value);
			return builder.build();
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
			ValueType type = ValueType.fromJson(HungerConfigManager.getString(source, "type", base.type.configValue), base.type);
			double value = clampEffectValue(getDouble(source, "value", base.value), type);
			return new EffectSettings(enabled, type, value);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("type", type.configValue)
				.put("value", value);
			return builder.build();
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

	private static long getLong(JsonObject object, String key, long fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsLong();
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

	private static long clampLong(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clampEffectValue(double value, ValueType type) {
		if (type == ValueType.FLAT) {
			return clampDouble(value, 0.0d, 1024.0d);
		}
		return clampDouble(value, 0.0d, 1.0d);
	}
}
