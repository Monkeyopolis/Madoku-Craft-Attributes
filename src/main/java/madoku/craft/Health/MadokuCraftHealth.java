package madoku.craft.Health;

import com.google.gson.JsonObject;

import madoku.craft.Health.system.HealthConfig;
import madoku.craft.Health.system.MadokuHealthManager;
import madoku.craft.API.system.MadokuDataSystem;
import madoku.craft.API.system.MadokuDeathSystem;
import madoku.craft.API.system.MadokuInfoDebugSystem;
import madoku.craft.API.system.MadokuTickSystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MadokuCraftHealth implements ModInitializer {
	public static final String MOD_ID = "madoku-craft-health";
	private static final String HEALTH_DATA_ID = "madoku_craft_health";
	private static final String LOG_SOURCE = "HEALTH";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		MadokuTickSystem.init();
		MadokuDeathSystem.init();

		HealthConfig config = HealthConfig.load();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MadokuDataSystem.MadokuData data = MadokuDataSystem.load(
				HEALTH_DATA_ID,
				MadokuDataSystem.StorageScope.WORLD,
				buildSavingDefaults(),
				server
			);
			MadokuHealthManager.initialize(config, data);
			MadokuInfoDebugSystem.info(LOGGER, LOG_SOURCE, "Health data ready at {}", data.getPath());
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			MadokuHealthManager manager = MadokuHealthManager.getInstance();
			if (manager != null) {
				manager.onPlayerJoin(handler.getPlayer());
			}
		});

		MadokuDeathSystem.registerDeath((player, context) -> {
			MadokuHealthManager manager = MadokuHealthManager.getInstance();
			if (manager != null) {
				manager.onPlayerDeath(player);
			}
		});

		MadokuDeathSystem.registerRespawn((oldPlayer, newPlayer, alive, lastDeath) -> {
			if (alive) {
				return;
			}
			MadokuHealthManager manager = MadokuHealthManager.getInstance();
			if (manager != null) {
				MadokuInfoDebugSystem.info(LOGGER, LOG_SOURCE, "Custom health reset triggered for {} after respawn", newPlayer.getName().getString());
				manager.onPlayerRespawn(newPlayer);
			}
		});

		MadokuTickSystem.register(MadokuTickSystem.Phase.END, server -> {
			MadokuHealthManager manager = MadokuHealthManager.getInstance();
			if (manager != null) {
				manager.onServerTick(server);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MadokuHealthManager manager = MadokuHealthManager.getInstance();
			if (manager != null) {
				manager.flushNow();
			}
		});

		MadokuInfoDebugSystem.info(LOGGER, LOG_SOURCE, "Madoku Craft Health initialized (feature enabled: {}).", config.isFeatureEnabled());
	}

	private static JsonObject buildSavingDefaults() {
		JsonObject defaults = new JsonObject();
		defaults.add("players", new JsonObject());
		return defaults;
	}
}
