package madoku.craft.attributes.oxygen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.attributes.AttributesConfigManager;
import madoku.craft.api.json.JSONFormatManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class OxygenConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(OxygenConfigManager.class);

	private static final String OXYGEN_CONFIG_FILE_NAME = "madoku-oxygen";
	private static final int DEFAULT_MAXIMUM_OXYGEN_TICKS = 600;
	private static final int MAXIMUM_CONFIG_OXYGEN_TICKS = (int) (20L * 60L * 60L);
	private static final double DEFAULT_SUFFOCATING_PENALTY_VALUE = 0.05d;
	private static final long DEFAULT_WATER_BREATHING_OXYGEN_VALUE = 1200L;
	private static final long DEFAULT_EFFECT_OXYGEN_VALUE = 600L;
	private static final long DEFAULT_BREATH_OF_THE_NAUTILUS_OXYGEN_VALUE = 1200L;
	private static final double DEFAULT_EFFECT_SPEED_VALUE = 0.2d;
	private static final long DEFAULT_OXYGEN_VALUE_MIN = 1L;
	private static final long DEFAULT_OXYGEN_VALUE_MAX = MAXIMUM_CONFIG_OXYGEN_TICKS;

	private OxygenConfigManager() {
	}

	static Settings loadSettings(boolean systemEnabled) {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = AttributesConfigManager.prepareRootConfigFile(OXYGEN_CONFIG_FILE_NAME);
			JsonObject normalized = JSONFormatManager.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			JSONFormatManager.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			return configured.withEnabled(systemEnabled);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load MadokuOxygenManager config; using defaults.", exception);
			return fallback.withEnabled(systemEnabled);
		}
	}

	static final class Settings {
		final OxygenSettings oxygen;
		final WaterBreathingSettings waterBreathing;
		final ConduitPowerSettings conduitPower;
		final DolphinsGraceSettings dolphinsGrace;
		final BreathOfTheNautilusSettings breathOfTheNautilus;

		private Settings(
			OxygenSettings oxygen,
			WaterBreathingSettings waterBreathing,
			ConduitPowerSettings conduitPower,
			DolphinsGraceSettings dolphinsGrace,
			BreathOfTheNautilusSettings breathOfTheNautilus
		) {
			this.oxygen = oxygen;
			this.waterBreathing = waterBreathing;
			this.conduitPower = conduitPower;
			this.dolphinsGrace = dolphinsGrace;
			this.breathOfTheNautilus = breathOfTheNautilus;
		}

		static Settings defaults() {
			return new Settings(
				OxygenSettings.defaults(),
				WaterBreathingSettings.defaults(),
				ConduitPowerSettings.defaults(),
				DolphinsGraceSettings.defaults(),
				BreathOfTheNautilusSettings.defaults()
			);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(
				OxygenSettings.fromJson(readObject(source, "oxygen"), defaults.oxygen),
				WaterBreathingSettings.fromJson(readObject(source, "water-breathing"), defaults.waterBreathing),
				ConduitPowerSettings.fromJson(readObject(source, "conduit-power"), defaults.conduitPower),
				DolphinsGraceSettings.fromJson(readObject(source, "dolphins-grace"), defaults.dolphinsGrace),
				BreathOfTheNautilusSettings.fromJson(
					readObject(source, "breath-of-the-nautilus"),
					defaults.breathOfTheNautilus
				)
			);
		}

		JsonObject toConfigJson() {
			return JSONFormatManager.object()
				.object("oxygen", oxygen -> this.oxygen.toConfigJson(oxygen))
				.object("water-breathing", waterBreathing -> this.waterBreathing.toConfigJson(waterBreathing))
				.object("conduit-power", conduitPower -> this.conduitPower.toConfigJson(conduitPower))
				.object("dolphins-grace", dolphinsGrace -> this.dolphinsGrace.toConfigJson(dolphinsGrace))
				.object("breath-of-the-nautilus", breathOfTheNautilus -> this.breathOfTheNautilus.toConfigJson(breathOfTheNautilus))
				.build();
		}

		Settings withEnabled(boolean systemEnabled) {
			return new Settings(
				oxygen.withEnabled(systemEnabled),
				waterBreathing.withEnabled(systemEnabled),
				conduitPower.withEnabled(systemEnabled),
				dolphinsGrace.withEnabled(systemEnabled),
				breathOfTheNautilus.withEnabled(systemEnabled)
			);
		}
	}

	static final class OxygenSettings {
		final boolean enabled;
		final int maxOxygenTicks;
		final SuffocatingPenaltySettings suffocatingPenalty;

		private OxygenSettings(boolean enabled, int maxOxygenTicks, SuffocatingPenaltySettings suffocatingPenalty) {
			this.enabled = enabled;
			this.maxOxygenTicks = maxOxygenTicks;
			this.suffocatingPenalty = suffocatingPenalty;
		}

		static OxygenSettings defaults() {
			return new OxygenSettings(true, DEFAULT_MAXIMUM_OXYGEN_TICKS, SuffocatingPenaltySettings.defaults());
		}

		static OxygenSettings fromJson(JsonObject source, OxygenSettings defaults) {
			OxygenSettings base = defaults == null ? defaults() : defaults;
			boolean enabled = getBoolean(source, "enabled", base.enabled);
			int maxOxygenTicks = (int) clampLong(
				getLong(source, "max-oxygen", base.maxOxygenTicks),
				1L,
				MAXIMUM_CONFIG_OXYGEN_TICKS
			);
			SuffocatingPenaltySettings suffocatingPenalty = SuffocatingPenaltySettings.fromJson(
				readObject(source, "suffocating-penalty"),
				base.suffocatingPenalty
			);
			return new OxygenSettings(enabled, maxOxygenTicks, suffocatingPenalty);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("max-oxygen", maxOxygenTicks)
				.object("suffocating-penalty", suffocatingPenalty -> this.suffocatingPenalty.toConfigJson(suffocatingPenalty));
			return builder.build();
		}

		OxygenSettings withEnabled(boolean systemEnabled) {
			return new OxygenSettings(
				systemEnabled && enabled,
				maxOxygenTicks,
				suffocatingPenalty.withEnabled(systemEnabled)
			);
		}
	}

	static final class SuffocatingPenaltySettings {
		final boolean enabled;
		final ValueType type;
		final double value;

		private SuffocatingPenaltySettings(boolean enabled, ValueType type, double value) {
			this.enabled = enabled;
			this.type = type;
			this.value = value;
		}

		static SuffocatingPenaltySettings defaults() {
			return new SuffocatingPenaltySettings(true, ValueType.PERCENTAGE, DEFAULT_SUFFOCATING_PENALTY_VALUE);
		}

		static SuffocatingPenaltySettings fromJson(JsonObject source, SuffocatingPenaltySettings defaults) {
			SuffocatingPenaltySettings base = defaults == null ? defaults() : defaults;
			boolean enabled = getBoolean(source, "enabled", base.enabled);
			ValueType type = ValueType.fromJson(getString(source, "type", base.type.configValue), base.type);
			double value = clampPenaltyValue(getDouble(source, "value", base.value), type);
			return new SuffocatingPenaltySettings(enabled, type, value);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("type", type.configValue)
				.put("value", value);
			return builder.build();
		}

		SuffocatingPenaltySettings withEnabled(boolean systemEnabled) {
			return new SuffocatingPenaltySettings(systemEnabled && enabled, type, value);
		}
	}

	static final class WaterBreathingSettings {
		final boolean enabled;
		final TicksSettings oxygen;

		private WaterBreathingSettings(boolean enabled, TicksSettings oxygen) {
			this.enabled = enabled;
			this.oxygen = oxygen;
		}

		static WaterBreathingSettings defaults() {
			return new WaterBreathingSettings(true, new TicksSettings(DEFAULT_WATER_BREATHING_OXYGEN_VALUE));
		}

		static WaterBreathingSettings fromJson(JsonObject source, WaterBreathingSettings defaults) {
			WaterBreathingSettings base = defaults == null ? defaults() : defaults;
			return new WaterBreathingSettings(
				getBoolean(source, "enabled", base.enabled),
				TicksSettings.fromJson(readObject(source, "oxygen"), base.oxygen)
			);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.object("oxygen", oxygen -> this.oxygen.toConfigJson(oxygen));
			return builder.build();
		}

		WaterBreathingSettings withEnabled(boolean systemEnabled) {
			return new WaterBreathingSettings(systemEnabled && enabled, oxygen);
		}
	}

	static final class ConduitPowerSettings {
		final boolean enabled;
		final TicksSettings oxygen;
		final PercentageSettings miningSpeed;

		private ConduitPowerSettings(boolean enabled, TicksSettings oxygen, PercentageSettings miningSpeed) {
			this.enabled = enabled;
			this.oxygen = oxygen;
			this.miningSpeed = miningSpeed;
		}

		static ConduitPowerSettings defaults() {
			return new ConduitPowerSettings(true, TicksSettings.defaults(), PercentageSettings.defaults());
		}

		static ConduitPowerSettings fromJson(JsonObject source, ConduitPowerSettings defaults) {
			ConduitPowerSettings base = defaults == null ? defaults() : defaults;
			return new ConduitPowerSettings(
				getBoolean(source, "enabled", base.enabled),
				TicksSettings.fromJson(readObject(source, "oxygen"), base.oxygen),
				PercentageSettings.fromJson(readObject(source, "mining-speed"), base.miningSpeed)
			);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.object("oxygen", oxygen -> this.oxygen.toConfigJson(oxygen))
				.object("mining-speed", miningSpeed -> this.miningSpeed.toConfigJson(miningSpeed));
			return builder.build();
		}

		ConduitPowerSettings withEnabled(boolean systemEnabled) {
			return new ConduitPowerSettings(systemEnabled && enabled, oxygen, miningSpeed);
		}
	}

	static final class DolphinsGraceSettings {
		final boolean enabled;
		final TicksSettings oxygen;
		final PercentageSettings swimmingSpeed;

		private DolphinsGraceSettings(boolean enabled, TicksSettings oxygen, PercentageSettings swimmingSpeed) {
			this.enabled = enabled;
			this.oxygen = oxygen;
			this.swimmingSpeed = swimmingSpeed;
		}

		static DolphinsGraceSettings defaults() {
			return new DolphinsGraceSettings(true, TicksSettings.defaults(), PercentageSettings.defaults());
		}

		static DolphinsGraceSettings fromJson(JsonObject source, DolphinsGraceSettings defaults) {
			DolphinsGraceSettings base = defaults == null ? defaults() : defaults;
			return new DolphinsGraceSettings(
				getBoolean(source, "enabled", base.enabled),
				TicksSettings.fromJson(readObject(source, "oxygen"), base.oxygen),
				PercentageSettings.fromJson(readObject(source, "swimming-speed"), base.swimmingSpeed)
			);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.object("oxygen", oxygen -> this.oxygen.toConfigJson(oxygen))
				.object("swimming-speed", swimmingSpeed -> this.swimmingSpeed.toConfigJson(swimmingSpeed));
			return builder.build();
		}

		DolphinsGraceSettings withEnabled(boolean systemEnabled) {
			return new DolphinsGraceSettings(systemEnabled && enabled, oxygen, swimmingSpeed);
		}
	}

	static final class BreathOfTheNautilusSettings {
		final boolean enabled;
		final TicksSettings oxygen;

		private BreathOfTheNautilusSettings(boolean enabled, TicksSettings oxygen) {
			this.enabled = enabled;
			this.oxygen = oxygen;
		}

		static BreathOfTheNautilusSettings defaults() {
			return new BreathOfTheNautilusSettings(true, new TicksSettings(DEFAULT_BREATH_OF_THE_NAUTILUS_OXYGEN_VALUE));
		}

		static BreathOfTheNautilusSettings fromJson(JsonObject source, BreathOfTheNautilusSettings defaults) {
			BreathOfTheNautilusSettings base = defaults == null ? defaults() : defaults;
			return new BreathOfTheNautilusSettings(
				getBoolean(source, "enabled", base.enabled),
				TicksSettings.fromJson(readObject(source, "oxygen"), base.oxygen)
			);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.object("oxygen", oxygen -> this.oxygen.toConfigJson(oxygen));
			return builder.build();
		}

		BreathOfTheNautilusSettings withEnabled(boolean systemEnabled) {
			return new BreathOfTheNautilusSettings(systemEnabled && enabled, oxygen);
		}
	}

	static final class TicksSettings {
		final long value;

		private TicksSettings(long value) {
			this.value = value;
		}

		static TicksSettings defaults() {
			return new TicksSettings(DEFAULT_EFFECT_OXYGEN_VALUE);
		}

		static TicksSettings fromJson(JsonObject source, TicksSettings defaults) {
			TicksSettings base = defaults == null ? defaults() : defaults;
			long value = clampLong(getLong(source, "value", base.value), DEFAULT_OXYGEN_VALUE_MIN, DEFAULT_OXYGEN_VALUE_MAX);
			return new TicksSettings(value);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("value", value);
			return builder.build();
		}
	}

	static final class PercentageSettings {
		final double value;

		private PercentageSettings(double value) {
			this.value = value;
		}

		static PercentageSettings defaults() {
			return new PercentageSettings(DEFAULT_EFFECT_SPEED_VALUE);
		}

		static PercentageSettings fromJson(JsonObject source, PercentageSettings defaults) {
			PercentageSettings base = defaults == null ? defaults() : defaults;
			double value = clampPercentageValue(getDouble(source, "value", base.value));
			return new PercentageSettings(value);
		}

		JsonObject toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put("value", value);
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

	private static double clampPenaltyValue(double value, ValueType type) {
		if (type == ValueType.FLAT) {
			return clampDouble(value, 0.0d, 1024.0d);
		}
		return clampDouble(value, 0.0d, 1.0d);
	}

	private static double clampPercentageValue(double value) {
		return clampDouble(value, 0.0d, 1.0d);
	}

	private static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
