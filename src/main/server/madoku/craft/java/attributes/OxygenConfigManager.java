package madoku.craft.java.attributes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class OxygenConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(OxygenConfigManager.class);

	private static final String OXYGEN_CONFIG_FILE_NAME = "madoku-oxygen";
	private static final int DEFAULT_MAXIMUM_OXYGEN_TICKS = 600;
	private static final int MAXIMUM_CONFIG_OXYGEN_TICKS = (int) (20L * 60L * 60L);

	private OxygenConfigManager() {
	}

	static Settings loadSettings(boolean systemEnabled) {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = AttributesConfigManager.prepareRootConfigFile(OXYGEN_CONFIG_FILE_NAME);
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			return configured.withEnabled(systemEnabled);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load Madoku Oxygen config; using defaults.", exception);
			return fallback.withEnabled(systemEnabled);
		}
	}

	static final class Settings {
		final OxygenSettings oxygen;

		private Settings(OxygenSettings oxygen) {
			this.oxygen = oxygen;
		}

		static Settings defaults() {
			return new Settings(OxygenSettings.defaults());
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(OxygenSettings.fromJson(readObject(source, "oxygen"), defaults.oxygen));
		}

		JsonObject toConfigJson() {
			return JSONFormatAPIManager.object()
				.object("oxygen", oxygen -> this.oxygen.toConfigJson(oxygen))
				.build();
		}

		Settings withEnabled(boolean systemEnabled) {
			return new Settings(oxygen.withEnabled(systemEnabled));
		}
	}

	static final class OxygenSettings {
		final boolean enabled;
		final int maxOxygenTicks;

		private OxygenSettings(boolean enabled, int maxOxygenTicks) {
			this.enabled = enabled;
			this.maxOxygenTicks = maxOxygenTicks;
		}

		static OxygenSettings defaults() {
			return new OxygenSettings(true, DEFAULT_MAXIMUM_OXYGEN_TICKS);
		}

		static OxygenSettings fromJson(JsonObject source, OxygenSettings defaults) {
			OxygenSettings base = defaults == null ? defaults() : defaults;
			boolean enabled = getBoolean(source, "enabled", base.enabled);
			int maxOxygenTicks = (int) clampLong(
				getLong(source, "max-oxygen", base.maxOxygenTicks),
				1L,
				MAXIMUM_CONFIG_OXYGEN_TICKS
			);
			return new OxygenSettings(enabled, maxOxygenTicks);
		}

		JsonObject toConfigJson(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put("enabled", enabled)
				.put("max-oxygen", maxOxygenTicks);
			return builder.build();
		}

		OxygenSettings withEnabled(boolean systemEnabled) {
			return new OxygenSettings(systemEnabled && enabled, maxOxygenTicks);
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

	private static long clampLong(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}
}

