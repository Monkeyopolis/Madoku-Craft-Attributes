package madoku.craft.java.attributes;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AttributesConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(AttributesConfigManager.class);
	private static final String ATTRIBUTES_CONFIG_FOLDER_NAME = "madoku-craft-attributes";
	private static final String ATTRIBUTES_CONFIG_FILE_NAME = "madoku-attributes";
	private static final boolean DEFAULT_ENABLED = true;

	private AttributesConfigManager() {
	}

	public static Settings loadSettings() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = prepareRootConfigFile(ATTRIBUTES_CONFIG_FILE_NAME);
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
			return loaded;
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load Madoku Attributes config; using defaults.", exception);
			return fallback;
		}
	}

	public static Path prepareSystemConfigFile(String systemDirectoryName, String fileName) {
		Path directory = getOrCreateSystemDirectory(systemDirectoryName);
		return resolveJsonFile(directory, fileName);
	}

	public static Path prepareRootConfigFile(String fileName) {
		Path rootDirectory = JSONAPIManager.getOrCreateGlobalSystemDirectory(ATTRIBUTES_CONFIG_FOLDER_NAME);
		return resolveJsonFile(rootDirectory, fileName);
	}

	private static Path getOrCreateSystemDirectory(String systemDirectoryName) {
		String normalizedName = systemDirectoryName == null ? "" : systemDirectoryName.trim();
		if (normalizedName.isEmpty()) {
			throw new IllegalArgumentException("Attribute system directory name must not be blank.");
		}

		Path rootDirectory = JSONAPIManager.getOrCreateGlobalSystemDirectory(ATTRIBUTES_CONFIG_FOLDER_NAME);
		Path directory = rootDirectory.resolve(normalizedName);
		try {
			Files.createDirectories(directory);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create attributes system directory: " + directory, exception);
		}
		return directory;
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		var element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		try {
			return element.getAsBoolean();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	static final class Settings {
		final boolean enabled;

		private Settings(boolean enabled) {
			this.enabled = enabled;
		}

		static Settings defaults() {
			return new Settings(DEFAULT_ENABLED);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(getBoolean(source, "enabled", defaults.enabled));
		}

		JsonObject toConfigJson() {
			return JSONFormatAPIManager.object()
				.put("enabled", enabled)
				.build();
		}
	}
}


