package madoku.craft.attributes;

import com.google.gson.JsonObject;
import madoku.craft.config.StaticJsonSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MadokuAttributes {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuAttributes.class);

	private static final String ATTRIBUTES_CONFIG_FOLDER_NAME = "madoku-craft-attributes";
	private static final String ATTRIBUTES_CONFIG_FILE_NAME = "madoku-attributes";
	private static final boolean DEFAULT_ENABLED = true;

	private static volatile Settings settings = Settings.defaults();

	private MadokuAttributes() {
	}

	public static void initialize() {
		loadStaticConfig();
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static Path prepareSystemConfigFile(String systemDirectoryName, String fileName) {
		Path directory = getOrCreateSystemDirectory(systemDirectoryName);
		return resolveJsonFile(directory, fileName);
	}

	private static void loadStaticConfig() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path directory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(ATTRIBUTES_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(directory, ATTRIBUTES_CONFIG_FILE_NAME);
			JsonObject normalized = StaticJsonSystem.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			StaticJsonSystem.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load MadokuAttributes static config; using defaults.", exception);
		}
	}

	private static Path getOrCreateSystemDirectory(String systemDirectoryName) {
		String normalizedName = systemDirectoryName == null ? "" : systemDirectoryName.trim();
		if (normalizedName.isEmpty()) {
			throw new IllegalArgumentException("Attribute system directory name must not be blank.");
		}

		Path rootDirectory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(ATTRIBUTES_CONFIG_FOLDER_NAME);
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

	private static final class Settings {
		private final boolean enabled;

		private Settings(boolean enabled) {
			this.enabled = enabled;
		}

		private static Settings defaults() {
			return new Settings(DEFAULT_ENABLED);
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(getBoolean(source, "enabled", defaults.enabled));
		}

		private JsonObject toConfigJson() {
			JsonObject root = new JsonObject();
			root.addProperty("enabled", enabled);
			return root;
		}
	}
}
