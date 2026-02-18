package madoku.craft.Health.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import madoku.craft.API.system.MadokuJSONSystem;

/** Reads the JSON config that controls the custom health system. */
public final class HealthConfig {
	private static final String FEATURE_ID = "madoku_craft_health";
	private static final String JSON_FOLDER_ID = "Health";
	private static final String JSON_FILE_ID = FEATURE_ID;
	private static final double DOUBLE_INCREMENT = 0.125;

	private final JsonObject root;
	private final boolean enableFeature;
	private final int maximumHealthPoints;
	private final double pendingHealthMultiplier;
	private final int pendingHealthTimer;
	private final int hungerDepletionTimer;
	private final double hungerDepletionThreshold;
	private final double foodToPendingPercent;
	private final double hungerHealthReductionThreshold;
	private final double maximumHealthReduction;
	private final double respawnHealthPercent;
	private boolean dirty;

	private HealthConfig(MadokuJSONSystem.ManagedJSON feature) {
		this.root = feature.getRoot();

		this.enableFeature = readBoolean("enableFeature", true);
		this.maximumHealthPoints = readInt("maximumHealthPoints", 20);
		this.pendingHealthMultiplier = readDouble("pendingHealthMultiplier", 1.0);
		int pendingHealthTimerValue = readInt("pendingHealthTimer", 10);
		this.pendingHealthTimer = Math.max(1, pendingHealthTimerValue);
		if (this.pendingHealthTimer != pendingHealthTimerValue) {
			root.addProperty("pendingHealthTimer", this.pendingHealthTimer);
			dirty = true;
		}
		int hungerDepletionValue = readInt("hungerDepletionTimer", 10);
		this.hungerDepletionTimer = Math.max(1, hungerDepletionValue);
		if (this.hungerDepletionTimer != hungerDepletionValue) {
			root.addProperty("hungerDepletionTimer", this.hungerDepletionTimer);
			dirty = true;
		}
		this.hungerDepletionThreshold = readSteppedBoundedDouble("hungerDepletionThreshold", 75.0, 10.0, 90.0, 5.0);
		this.foodToPendingPercent = readSteppedBoundedDouble("foodToPendingPercent", 50.0, 0.0, 100.0, 5.0);
		this.hungerHealthReductionThreshold = readSteppedBoundedDouble("hungerHealthReductionThreshold", 25.0, 10.0, 90.0, 5.0);
		this.maximumHealthReduction = readSteppedBoundedDouble("maximumHealthReduction", 50.0, 20.0, 80.0, 5.0);
		this.respawnHealthPercent = readSteppedBoundedDouble("respawnHealthPercent", 50.0, 0.0, 100.0, 5.0);

		if (dirty) {
			feature.save();
			dirty = false;
		}
	}

	/** Creates a config that loads the defaults and persists any missing keys. */
	public static HealthConfig load() {
		JsonObject defaults = buildDefaults();
		return new HealthConfig(MadokuJSONSystem.load(JSON_FOLDER_ID, JSON_FILE_ID, defaults));
	}

	public boolean isFeatureEnabled() {
		return enableFeature;
	}

	public int getMaximumHealthPoints() {
		return maximumHealthPoints;
	}

	public double getPendingHealthMultiplier() {
		return pendingHealthMultiplier;
	}

	public int getPendingHealthTimer() {
		return pendingHealthTimer;
	}

	public int getHungerDepletionTimer() {
		return hungerDepletionTimer;
	}

	public double getHungerDepletionThreshold() {
		return hungerDepletionThreshold;
	}

	public double getFoodToPendingPercent() {
		return foodToPendingPercent;
	}

	public double getHungerHealthReductionThreshold() {
		return hungerHealthReductionThreshold;
	}

	public double getMaximumHealthReduction() {
		return maximumHealthReduction;
	}

	public double getRespawnHealthPercent() {
		return respawnHealthPercent;
	}

	private static JsonObject buildDefaults() {
		JsonObject defaults = new JsonObject();
		defaults.addProperty("enableFeature", true);
		defaults.addProperty("maximumHealthPoints", 20);
		defaults.addProperty("pendingHealthMultiplier", 1.0);
		defaults.addProperty("pendingHealthTimer", 10);
		defaults.addProperty("hungerDepletionTimer", 10);
		defaults.addProperty("hungerDepletionThreshold", 75.0);
		defaults.addProperty("foodToPendingPercent", 50.0);
		defaults.addProperty("hungerHealthReductionThreshold", 25.0);
		defaults.addProperty("maximumHealthReduction", 50.0);
		defaults.addProperty("respawnHealthPercent", 50.0);
		return defaults;
	}

	private boolean readBoolean(String key, boolean fallback) {
		JsonElement element = root.get(key);
		boolean value = fallback;
		if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
			value = primitive.getAsBoolean();
		}
		if (!(element instanceof JsonPrimitive primitive && primitive.isBoolean() && primitive.getAsBoolean() == value)) {
			root.addProperty(key, value);
			dirty = true;
		}
		return value;
	}

	private int readInt(String key, int fallback) {
		JsonElement element = root.get(key);
		int value = fallback;
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			value = primitive.getAsInt();
		}
		if (!(element instanceof JsonPrimitive primitive && primitive.isNumber() && primitive.getAsInt() == value)) {
			root.addProperty(key, value);
			dirty = true;
		}
		return value;
	}

	private double readDouble(String key, double fallback) {
		JsonElement element = root.get(key);
		double value = fallback;
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			value = primitive.getAsDouble();
		}
		double sanitized = roundToIncrement(value, DOUBLE_INCREMENT);
		if (!(element instanceof JsonPrimitive primitive && primitive.isNumber() && primitive.getAsDouble() == sanitized)) {
			root.addProperty(key, sanitized);
			dirty = true;
		}
		return sanitized;
	}

	private double readSteppedBoundedDouble(String key, double fallback, double min, double max, double step) {
		JsonElement element = root.get(key);
		double value = fallback;
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			value = primitive.getAsDouble();
		}
		double sanitized = roundToIncrement(value, step);
		sanitized = Math.min(max, Math.max(min, sanitized));
		if (!(element instanceof JsonPrimitive primitive && primitive.isNumber() && primitive.getAsDouble() == sanitized)) {
			root.addProperty(key, sanitized);
			dirty = true;
		}
		return sanitized;
	}

	private static double roundToIncrement(double value, double increment) {
		if (Double.isNaN(value) || value <= 0 || increment <= 0) {
			return 0;
		}
		double clipped = Math.max(0.0, value);
		double scaled = clipped / increment;
		double floor = Math.floor(scaled);
		double lowerValue = floor * increment;
		double upperValue = lowerValue + increment;
		double lowerDiff = clipped - lowerValue;
		double upperDiff = upperValue - clipped;
		return lowerDiff < upperDiff ? lowerValue : upperValue;
	}
}
