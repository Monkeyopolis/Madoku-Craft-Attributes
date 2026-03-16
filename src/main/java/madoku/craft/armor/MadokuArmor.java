package madoku.craft.armor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.config.StaticJsonSystem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class MadokuArmor {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuArmor.class);

	private static final double DAMAGE_ROUND_INCREMENT = 0.05d;
	private static final double ARMOR_POINT_STEP = 0.25d;
	private static final double ARMOR_POINT_DAMAGE_REDUCTION = 0.05d;
	private static final double TOUGHNESS_POINT_STEP = 0.25d;
	private static final double TOUGHNESS_PERCENT_REDUCTION = 0.01d;
	private static final boolean DEFAULT_ENABLED = true;
	private static final int DEFAULT_ARMOR_POINTS_CLAMP_LIMIT = 100;
	private static final int DEFAULT_ARMOR_TOUGHNESS_POINTS_CLAMP_LIMIT = 100;
	private static final double DEFAULT_FALL_DAMAGE_ARMOR_EFFECTIVENESS = 0.5d;

	private static final String ARMOR_CONFIG_FOLDER_NAME = "madoku-craft-armor";
	private static final String ARMOR_CONFIG_FILE_NAME = "madoku-armor";

	private static volatile Settings settings = Settings.defaults();

	private MadokuArmor() {
	}

	public static void initialize() {
		loadStaticConfig();
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static float applyCustomArmorDamage(LivingEntity entity, DamageSource source, float amount) {
		if (entity == null || source == null || amount <= 0.0f || !settings.enabled) {
			return amount;
		}

		double armorPoints = clampToLimit(entity.getAttributeValue(Attributes.ARMOR), settings.armorPointsClampLimit);
		double armorToughnessPoints = clampToLimit(
			entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
			settings.armorToughnessPointsClampLimit
		);

		long armorPointSteps = getStepCount(armorPoints, ARMOR_POINT_STEP);
		long toughnessPointSteps = getStepCount(armorToughnessPoints, TOUGHNESS_POINT_STEP);

		double reducedDamage = Math.max(0.0d, amount - (armorPointSteps * ARMOR_POINT_DAMAGE_REDUCTION));
		double toughnessMultiplier = 1.0d - (toughnessPointSteps * TOUGHNESS_PERCENT_REDUCTION);
		toughnessMultiplier = Math.max(0.0d, toughnessMultiplier);

		double finalDamage = reducedDamage * toughnessMultiplier;
		if (source.is(DamageTypeTags.IS_FALL)) {
			double mitigatedDamage = Math.max(0.0d, amount - finalDamage);
			finalDamage = amount - (mitigatedDamage * settings.fallDamageArmorEffectiveness);
		}

		return (float) Math.max(0.0d, roundToDamageIncrement(finalDamage));
	}

	private static void loadStaticConfig() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path directory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(ARMOR_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(directory, ARMOR_CONFIG_FILE_NAME);
			JsonObject normalized = StaticJsonSystem.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			StaticJsonSystem.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load MadokuArmor static config; using defaults.", exception);
		}
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

	private static double clampToLimit(double value, int limit) {
		if (value < 0) {
			return 0.0d;
		}
		return Math.min(value, Math.max(0, limit));
	}

	private static long getStepCount(double value, double stepSize) {
		if (value <= 0.0d || stepSize <= 0.0d) {
			return 0L;
		}
		return Math.max(0L, (long) Math.floor(value / stepSize));
	}

	private static double roundToDamageIncrement(double value) {
		return Math.round(value / DAMAGE_ROUND_INCREMENT) * DAMAGE_ROUND_INCREMENT;
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

	private static final class Settings {
		private final boolean enabled;
		private final int armorPointsClampLimit;
		private final int armorToughnessPointsClampLimit;
		private final double fallDamageArmorEffectiveness;

		private Settings(
			boolean enabled,
			int armorPointsClampLimit,
			int armorToughnessPointsClampLimit,
			double fallDamageArmorEffectiveness
		) {
			this.enabled = enabled;
			this.armorPointsClampLimit = armorPointsClampLimit;
			this.armorToughnessPointsClampLimit = armorToughnessPointsClampLimit;
			this.fallDamageArmorEffectiveness = fallDamageArmorEffectiveness;
		}

		private static Settings defaults() {
			return new Settings(
				DEFAULT_ENABLED,
				DEFAULT_ARMOR_POINTS_CLAMP_LIMIT,
				DEFAULT_ARMOR_TOUGHNESS_POINTS_CLAMP_LIMIT,
				DEFAULT_FALL_DAMAGE_ARMOR_EFFECTIVENESS
			);
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();

			boolean enabled = getBoolean(source, "enabled", defaults.enabled);
			int armorPointsClampLimit = (int) clampLong(
				getLong(source, "armor_points_clamp_limit", defaults.armorPointsClampLimit),
				0L,
				100000L
			);
			int armorToughnessPointsClampLimit = (int) clampLong(
				getLong(source, "armor_toughness_points_clamp_limit", defaults.armorToughnessPointsClampLimit),
				0L,
				100000L
			);
			double fallDamageArmorEffectiveness = clampDouble(
				getDouble(source, "fall_damage_armor_effectiveness", defaults.fallDamageArmorEffectiveness),
				0.0d,
				1.0d
			);

			return new Settings(
				enabled,
				armorPointsClampLimit,
				armorToughnessPointsClampLimit,
				fallDamageArmorEffectiveness
			);
		}

		private JsonObject toConfigJson() {
			JsonObject root = new JsonObject();
			root.addProperty("enabled", enabled);
			root.addProperty("armor_points_clamp_limit", armorPointsClampLimit);
			root.addProperty("armor_toughness_points_clamp_limit", armorToughnessPointsClampLimit);
			root.addProperty("fall_damage_armor_effectiveness", fallDamageArmorEffectiveness);
			return root;
		}
	}
}
