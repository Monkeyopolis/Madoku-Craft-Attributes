package madoku.craft.java.attributes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class LuckConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(LuckConfigManager.class);

	private static final String LUCK_CONFIG_FILE_NAME = "madoku-luck";

	private static final boolean DEFAULT_ENABLED = true;
	private static final double DEFAULT_STARTING_POINTS = 5.0d;
	private static final double DEFAULT_MAX_POINTS = 100.0d;
	private static final double DEFAULT_LUCK_EFFECT_VALUE = 10.0d;

	private static final ValueType DEFAULT_DROP_ADJUSTMENT_TYPE = ValueType.MULTIPLIER;
	private static final double DEFAULT_BLOCK_DROP_ADJUSTMENT_VALUE = 2.0d;
	private static final double DEFAULT_CROP_DROP_ADJUSTMENT_VALUE = 1.5d;
	private static final double DEFAULT_MOB_DROP_ADJUSTMENT_VALUE = 1.5d;
	private static final double DEFAULT_CREEPER_GRIEF_REDUCTION_MULTIPLIER = 0.5d;
	private static final double DEFAULT_SKELETON_ACCURACY_REDUCTION_MULTIPLIER = 0.5d;
	private static final double DEFAULT_PLAYER_CRITICAL_DAMAGE_MULTIPLIER = 1.5d;

	private LuckConfigManager() {
	}

	static Settings loadSettings(boolean systemEnabled) {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = AttributesConfigManager.prepareRootConfigFile(LUCK_CONFIG_FILE_NAME);
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
			return loaded.withEnabled(systemEnabled);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load MadokuLuckManager config; using defaults.", exception);
			return fallback.withEnabled(systemEnabled);
		}
	}

	static final class Settings {
		final boolean enabled;
		final LuckSettings luck;
		final LuckEffectSettings luckEffect;
		final DropGroupSettings blockDrops;
		final DropGroupSettings cropDrops;
		final DropGroupSettings mobDrops;
		final ReductionGroupSettings creeperGriefReduction;
		final ReductionGroupSettings skeletonAccuracyReduction;
		final CriticalDamageSettings playerCriticalDamage;

		private Settings(
			boolean enabled,
			LuckSettings luck,
			LuckEffectSettings luckEffect,
			DropGroupSettings blockDrops,
			DropGroupSettings cropDrops,
			DropGroupSettings mobDrops,
			ReductionGroupSettings creeperGriefReduction,
			ReductionGroupSettings skeletonAccuracyReduction,
			CriticalDamageSettings playerCriticalDamage
		) {
			this.enabled = enabled;
			this.luck = luck;
			this.luckEffect = luckEffect;
			this.blockDrops = blockDrops;
			this.cropDrops = cropDrops;
			this.mobDrops = mobDrops;
			this.creeperGriefReduction = creeperGriefReduction;
			this.skeletonAccuracyReduction = skeletonAccuracyReduction;
			this.playerCriticalDamage = playerCriticalDamage;
		}

		static Settings defaults() {
			return new Settings(
				DEFAULT_ENABLED,
				LuckSettings.defaults(),
				LuckEffectSettings.defaults(),
				DropGroupSettings.defaults(DEFAULT_BLOCK_DROP_ADJUSTMENT_VALUE),
				DropGroupSettings.defaults(DEFAULT_CROP_DROP_ADJUSTMENT_VALUE),
				DropGroupSettings.defaults(DEFAULT_MOB_DROP_ADJUSTMENT_VALUE),
				ReductionGroupSettings.defaults(DEFAULT_CREEPER_GRIEF_REDUCTION_MULTIPLIER),
				ReductionGroupSettings.defaults(DEFAULT_SKELETON_ACCURACY_REDUCTION_MULTIPLIER),
				CriticalDamageSettings.defaults(DEFAULT_PLAYER_CRITICAL_DAMAGE_MULTIPLIER)
			);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(
				getBoolean(source, "enabled", defaults.enabled),
				LuckSettings.fromJson(readObject(source, "luck"), defaults.luck),
				LuckEffectSettings.fromJson(readObject(source, "luck-effect"), defaults.luckEffect),
				DropGroupSettings.fromJson(readObject(source, "block-drops"), defaults.blockDrops),
				DropGroupSettings.fromJson(readObject(source, "crop-drops"), defaults.cropDrops),
				DropGroupSettings.fromJson(readObject(source, "mob-drops"), defaults.mobDrops),
				ReductionGroupSettings.fromJson(
					readObject(source, "creeper-grief-reduction"),
					defaults.creeperGriefReduction
				),
				ReductionGroupSettings.fromJson(
					readObject(source, "skeleton-accuracy-reduction"),
					defaults.skeletonAccuracyReduction
				),
				CriticalDamageSettings.fromJson(
					readObject(source, "player-critical-damage"),
					defaults.playerCriticalDamage
				)
			);
		}

		JsonObject toConfigJson() {
			return JSONFormatAPIManager.object()
				.put("enabled", enabled)
				.object("luck", luck -> this.luck.toConfigJson(luck))
				.object("luck-effect", luckEffect -> this.luckEffect.toConfigJson(luckEffect))
				.object("block-drops", blockDrops -> this.blockDrops.toConfigJson(blockDrops))
				.object("crop-drops", cropDrops -> this.cropDrops.toConfigJson(cropDrops))
				.object("mob-drops", mobDrops -> this.mobDrops.toConfigJson(mobDrops))
				.object("creeper-grief-reduction", creeper -> this.creeperGriefReduction.toConfigJson(creeper))
				.object("skeleton-accuracy-reduction", skeleton -> this.skeletonAccuracyReduction.toConfigJson(skeleton))
				.object("player-critical-damage", criticalDamage -> this.playerCriticalDamage.toConfigJson(criticalDamage))
				.build();
		}

		Settings withEnabled(boolean systemEnabled) {
			return new Settings(
				systemEnabled && enabled,
				luck,
				luckEffect,
				blockDrops,
				cropDrops,
				mobDrops,
				creeperGriefReduction,
				skeletonAccuracyReduction,
				playerCriticalDamage
			);
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
				String value = element.getAsString();
				return value == null ? fallback : value.trim();
			} catch (RuntimeException exception) {
				return fallback;
			}
		}

		private static double clampDouble(double value, double min, double max) {
			return Math.max(min, Math.min(max, value));
		}
	}

	static final class LuckSettings {
		final boolean enabled;
		final double startingPoints;
		final double maxPoints;

		private LuckSettings(boolean enabled, double startingPoints, double maxPoints) {
			this.enabled = enabled;
			this.startingPoints = startingPoints;
			this.maxPoints = maxPoints;
		}

		static LuckSettings defaults() {
			return new LuckSettings(true, DEFAULT_STARTING_POINTS, DEFAULT_MAX_POINTS);
		}

		static LuckSettings fromJson(JsonObject source, LuckSettings defaults) {
			LuckSettings base = defaults == null ? defaults() : defaults;
			boolean enabled = Settings.getBoolean(source, "enabled", base.enabled);
			double maxPoints = Settings.clampDouble(Settings.getDouble(source, "max-points", base.maxPoints), 1.0d, 1024.0d);
			double startingPoints = Settings.clampDouble(
				Settings.getDouble(source, "starting-points", base.startingPoints),
				0.0d,
				maxPoints
			);
			return new LuckSettings(enabled, startingPoints, maxPoints);
		}

		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("starting-points", startingPoints)
				.put("max-points", maxPoints);
			return builder.build();
		}
	}

	static final class LuckEffectSettings {
		final boolean enabled;
		final double value;

		private LuckEffectSettings(boolean enabled, double value) {
			this.enabled = enabled;
			this.value = value;
		}

		static LuckEffectSettings defaults() {
			return new LuckEffectSettings(true, DEFAULT_LUCK_EFFECT_VALUE);
		}

		static LuckEffectSettings fromJson(JsonObject source, LuckEffectSettings defaults) {
			LuckEffectSettings base = defaults == null ? defaults() : defaults;
			boolean enabled = Settings.getBoolean(source, "enabled", base.enabled);
			double value = Settings.clampDouble(Settings.getDouble(source, "value", base.value), 0.0d, 1024.0d);
			return new LuckEffectSettings(enabled, value);
		}

		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("value", value);
			return builder.build();
		}
	}

	static final class DropGroupSettings {
		final boolean enabled;
		final DropAdjustmentSettings dropAdjustment;

		private DropGroupSettings(boolean enabled, DropAdjustmentSettings dropAdjustment) {
			this.enabled = enabled;
			this.dropAdjustment = dropAdjustment;
		}

		static DropGroupSettings defaults(double value) {
			return new DropGroupSettings(true, DropAdjustmentSettings.defaults(DEFAULT_DROP_ADJUSTMENT_TYPE, value));
		}

		static DropGroupSettings fromJson(JsonObject source, DropGroupSettings defaults) {
			DropGroupSettings base = defaults == null ? defaults(1.0d) : defaults;
			boolean enabled = Settings.getBoolean(source, "enabled", base.enabled);
			DropAdjustmentSettings dropAdjustment = DropAdjustmentSettings.fromJson(
				Settings.readObject(source, "drop-adjustment"),
				base.dropAdjustment
			);
			return new DropGroupSettings(enabled, dropAdjustment);
		}

		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.object("drop-adjustment", dropAdjustment -> this.dropAdjustment.toConfigJson(dropAdjustment));
			return builder.build();
		}
	}

	static final class DropAdjustmentSettings {
		final ValueType type;
		final double value;

		private DropAdjustmentSettings(ValueType type, double value) {
			this.type = type;
			this.value = value;
		}

		static DropAdjustmentSettings defaults(ValueType type, double value) {
			return new DropAdjustmentSettings(type, value);
		}

		static DropAdjustmentSettings fromJson(JsonObject source, DropAdjustmentSettings defaults) {
			DropAdjustmentSettings base = defaults == null ? defaults(DEFAULT_DROP_ADJUSTMENT_TYPE, 1.0d) : defaults;
			ValueType type = ValueType.fromJson(Settings.getString(source, "type", base.type.configValue), base.type);
			double value = Settings.clampDouble(Settings.getDouble(source, "value", base.value), 0.0d, 1024.0d);
			return new DropAdjustmentSettings(type, value);
		}

		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put("type", type.configValue)
				.put("value", value);
			return builder.build();
		}
	}

	static final class ReductionGroupSettings {
		final boolean enabled;
		final double adjustmentMultiplier;

		private ReductionGroupSettings(boolean enabled, double adjustmentMultiplier) {
			this.enabled = enabled;
			this.adjustmentMultiplier = adjustmentMultiplier;
		}

		static ReductionGroupSettings defaults(double multiplier) {
			return new ReductionGroupSettings(true, multiplier);
		}

		static ReductionGroupSettings fromJson(JsonObject source, ReductionGroupSettings defaults) {
			ReductionGroupSettings base = defaults == null ? defaults(0.5d) : defaults;
			boolean enabled = Settings.getBoolean(source, "enabled", base.enabled);
			JsonObject multiplier = Settings.readObject(source, "adjustment-multiplier");
			double value = Settings.clampDouble(
				Settings.getDouble(multiplier, "value", base.adjustmentMultiplier),
				0.0d,
				1.0d
			);
			return new ReductionGroupSettings(enabled, value);
		}

		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.object("adjustment-multiplier", multiplier -> multiplier.put("value", adjustmentMultiplier));
			return builder.build();
		}
	}

	static final class CriticalDamageSettings {
		final boolean enabled;
		final double damageMultiplier;

		private CriticalDamageSettings(boolean enabled, double damageMultiplier) {
			this.enabled = enabled;
			this.damageMultiplier = damageMultiplier;
		}

		static CriticalDamageSettings defaults(double damageMultiplier) {
			return new CriticalDamageSettings(true, damageMultiplier);
		}

		static CriticalDamageSettings fromJson(JsonObject source, CriticalDamageSettings defaults) {
			CriticalDamageSettings base = defaults == null ? defaults(DEFAULT_PLAYER_CRITICAL_DAMAGE_MULTIPLIER) : defaults;
			boolean enabled = Settings.getBoolean(source, "enabled", base.enabled);
			JsonObject damageMultiplier = Settings.readObject(source, "damage-multiplier");
			double value = Settings.clampDouble(
				Settings.getDouble(damageMultiplier, "value", base.damageMultiplier),
				0.0d,
				1024.0d
			);
			return new CriticalDamageSettings(enabled, value);
		}

		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.object("damage-multiplier", damageMultiplier -> damageMultiplier.put("value", this.damageMultiplier));
			return builder.build();
		}
	}

	enum ValueType {
		MULTIPLIER("multiplier"),
		FLAT("flat");

		final String configValue;

		ValueType(String configValue) {
			this.configValue = configValue;
		}

		static ValueType fromJson(String rawValue, ValueType fallback) {
			if (rawValue == null || rawValue.isBlank()) {
				return fallback;
			}
			String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
			for (ValueType valueType : values()) {
				if (valueType.configValue.equals(normalized)) {
					return valueType;
				}
			}
			return fallback;
		}
	}
}
