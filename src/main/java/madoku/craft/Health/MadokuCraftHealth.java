package madoku.craft.Health;

import com.google.gson.JsonObject;

import madoku.craft.Health.system.HealthConfig;
import madoku.craft.Health.system.MadokuHealthManager;
import madoku.craft.API.system.MadokuSavingSystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MadokuCraftHealth implements ModInitializer {
	public static final String MOD_ID = "madoku-craft-health";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		HealthConfig config = HealthConfig.load();
		MadokuSavingSystem.MadokuData data = MadokuSavingSystem.load("madoku_craft_health", buildSavingDefaults());
		MadokuHealthManager.initialize(config, data);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			MadokuHealthManager manager = MadokuHealthManager.getInstance();
			if (manager != null) {
				manager.onPlayerJoin(handler.getPlayer());
			}
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (alive) {
				return;
			}
			MadokuHealthManager manager = MadokuHealthManager.getInstance();
			if (manager != null) {
				LOGGER.info("custom health reset triggered for {} after respawn", newPlayer.getName().getString());
				manager.onPlayerRespawn(newPlayer);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MadokuHealthManager manager = MadokuHealthManager.getInstance();
			if (manager != null) {
				manager.onServerTick(server);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MadokuHealthManager manager = MadokuHealthManager.getInstance();
			if (manager != null) {
				manager.flush();
			}
		});

		LOGGER.info("Madoku Craft Health initialized (feature enabled: {}).", config.isFeatureEnabled());
	}

	private static JsonObject buildSavingDefaults() {
		JsonObject defaults = new JsonObject();
		defaults.add("players", new JsonObject());
		return defaults;
	}
}
