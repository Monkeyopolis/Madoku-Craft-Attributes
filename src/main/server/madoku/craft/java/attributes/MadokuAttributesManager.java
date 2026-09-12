package madoku.craft.java.attributes;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.sync.SyncConfigAPIManager;
import net.minecraft.server.MinecraftServer;

public final class MadokuAttributesManager {
	private static volatile AttributesConfigManager.Settings settings = AttributesConfigManager.Settings.defaults();
	private static volatile Boolean clientSynchronizedEnabled;

	private MadokuAttributesManager() {
	}

	public static void initialize() {
		MadokuAttributesNetworking.initialize();
		loadStaticConfig();
		ArmorAPIManager.registerProvider(new MadokuArmorProvider());
		HealthAPIManager.registerProvider(new MadokuHealthProvider());
		HungerAPIManager.registerProvider(new MadokuHungerProvider());
		OxygenAPIManager.registerProvider(new MadokuOxygenProvider());
		LuckAPIManager.registerProvider(new MadokuLuckProvider());
		ExperienceAPIManager.registerProvider(new MadokuExperienceProvider());
		ExperienceAPIManager.initialize();
		ArmorAPIManager.initialize();
		HealthAPIManager.initialize();
		HungerAPIManager.initialize();
		OxygenAPIManager.initialize();
		LuckAPIManager.initialize();
		SyncConfigAPIManager.register(
			"attributes",
			MadokuAttributesManager::createClientSyncSnapshot,
			MadokuAttributesManager::applyClientSyncSnapshot,
			MadokuAttributesManager::resetClientSyncState
		);
	}

	public static boolean isEnabled() {
		Boolean synchronizedEnabled = clientSynchronizedEnabled;
		return synchronizedEnabled == null ? settings.enabled : synchronizedEnabled;
	}

	public static void reset() {
		HealthAPIManager.reset();
		HungerAPIManager.reset();
	}

	public static void loadPersistedData(MinecraftServer server) {
		HealthAPIManager.loadPersistedData(server);
		HungerAPIManager.loadPersistedData(server);
	}

	public static void onServerStarted(MinecraftServer server) {
		HungerAPIManager.onServerStarted(server);
		HealthAPIManager.onServerStarted(server);
	}

	public static void onServerTick(MinecraftServer server) {
		HealthAPIManager.onServerTick(server);
		HungerAPIManager.onServerTick(server);
	}

	public static String createClientSyncSnapshot() {
		return JSONFormatAPIManager.object()
			.put("enabled", settings.enabled)
			.object("hunger", hunger -> hunger
				.put("enabled", HungerAPIManager.isEnabled())
				.put("max", HungerAPIManager.getConfiguredMaximumHungerPoints()))
			.object("oxygen", oxygen -> oxygen
				.put("enabled", OxygenAPIManager.isEnabled())
				.put("max", OxygenAPIManager.getMaximumOxygenTicksForEntity(null)))
			.put("luck-enabled", LuckAPIManager.isEnabled())
			.build()
			.toString();
	}

	public static void applyClientSyncSnapshot(String snapshot) {
		JsonObject root = JsonParser.parseString(snapshot == null ? "" : snapshot).getAsJsonObject();
		boolean enabled = readBoolean(root, "enabled", settings.enabled);
		JsonObject hunger = readObject(root, "hunger");
		JsonObject oxygen = readObject(root, "oxygen");
		clientSynchronizedEnabled = enabled;
		HungerAPIManager.applyClientSynchronizedSettings(
			readBoolean(hunger, "enabled", HungerAPIManager.isEnabled()),
			readInt(hunger, "max", HungerAPIManager.getConfiguredMaximumHungerPoints())
		);
		OxygenAPIManager.applyClientSynchronizedSettings(
			readBoolean(oxygen, "enabled", OxygenAPIManager.isEnabled()),
			readInt(oxygen, "max", OxygenAPIManager.getMaximumOxygenTicksForEntity(null))
		);
		LuckAPIManager.applyClientSynchronizedEnabled(
			readBoolean(root, "luck-enabled", LuckAPIManager.isEnabled())
		);
	}

	public static void resetClientSyncState() {
		clientSynchronizedEnabled = null;
		HungerAPIManager.resetClientSynchronizedSettings();
		OxygenAPIManager.resetClientSynchronizedSettings();
		LuckAPIManager.resetClientSynchronizedSettings();
	}

	private static JsonObject readObject(JsonObject source, String key) {
		if (source == null || !source.has(key) || !source.get(key).isJsonObject()) return new JsonObject();
		return source.getAsJsonObject(key);
	}

	private static boolean readBoolean(JsonObject source, String key, boolean fallback) {
		try { return source != null && source.has(key) ? source.get(key).getAsBoolean() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static int readInt(JsonObject source, String key, int fallback) {
		try { return source != null && source.has(key) ? Math.max(1, source.get(key).getAsInt()) : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static void loadStaticConfig() {
		settings = AttributesConfigManager.loadSettings();
	}
}
