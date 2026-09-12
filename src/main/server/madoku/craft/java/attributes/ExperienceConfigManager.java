package madoku.craft.java.attributes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/** Owns the managed configuration for Madoku's vanilla experience curve. */
public final class ExperienceConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ExperienceConfigManager.class);
	private static final String EXPERIENCE_CONFIG_FILE_NAME = "madoku-experience";
	private static final int DEFAULT_MAX_LEVEL = 46_080;
	private static final int DEFAULT_XP_REQUIRED = 30;
	private static final double DEFAULT_DEATH_PENALTY = 0.5d;
	private static final int MAX_CONFIG_LEVEL = 1_000_000;
	private static final int MAX_CONFIG_XP_REQUIRED = Integer.MAX_VALUE;

	private ExperienceConfigManager() { }

	static Settings loadSettings(boolean systemEnabled) {
		Settings fallback = Settings.defaults();
		JsonObject defaults = fallback.toConfigJson();
		try {
			Path configFile = AttributesConfigManager.prepareRootConfigFile(EXPERIENCE_CONFIG_FILE_NAME);
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
			return loaded.withEnabled(systemEnabled);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load Madoku Experience config; using defaults.", exception);
			return fallback.withEnabled(systemEnabled);
		}
	}

	static final class Settings {
		final boolean enabled;
		final LevelsSettings levels;

		private Settings(boolean enabled, LevelsSettings levels) {
			this.enabled = enabled;
			this.levels = levels;
		}

		static Settings defaults() {
			return new Settings(true, LevelsSettings.defaults());
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(
				defaults.enabled,
				LevelsSettings.fromJson(readObject(source, "levels"), defaults.levels)
			);
		}

		JsonObject toConfigJson() {
			return JSONFormatAPIManager.object()
				.object("levels", levels -> this.levels.toConfigJson(levels))
				.build();
		}

		Settings withEnabled(boolean systemEnabled) {
			return new Settings(systemEnabled && enabled, levels);
		}
	}

	static final class LevelsSettings {
		final int maxLevel;
		final int xpRequired;
		final double deathPenalty;

		private LevelsSettings(int maxLevel, int xpRequired, double deathPenalty) {
			this.maxLevel = maxLevel;
			this.xpRequired = xpRequired;
			this.deathPenalty = deathPenalty;
		}

		static LevelsSettings defaults() {
			return new LevelsSettings(DEFAULT_MAX_LEVEL, DEFAULT_XP_REQUIRED, DEFAULT_DEATH_PENALTY);
		}

		static LevelsSettings fromJson(JsonObject source, LevelsSettings defaults) {
			LevelsSettings base = defaults == null ? defaults() : defaults;
			return new LevelsSettings(
				readInt(source, "max-level", base.maxLevel, 0, MAX_CONFIG_LEVEL),
				readInt(source, "xp-required", base.xpRequired, 1, MAX_CONFIG_XP_REQUIRED),
				readDouble(source, "death-penalty", base.deathPenalty, 0.0d, 1.0d)
			);
		}

		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			return builder
				.put("max-level", maxLevel)
				.put("xp-required", xpRequired)
				.put("death-penalty", deathPenalty)
				.build();
		}
	}

	private static JsonObject readObject(JsonObject source, String key) {
		JsonElement element = source == null ? null : source.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static int readInt(JsonObject source, String key, int fallback, int minimum, int maximum) {
		try {
			int value = source != null && source.has(key) ? source.get(key).getAsInt() : fallback;
			return Math.max(minimum, Math.min(maximum, value));
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static double readDouble(JsonObject source, String key, double fallback, double minimum, double maximum) {
		try {
			double value = source != null && source.has(key) ? source.get(key).getAsDouble() : fallback;
			return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}
}

