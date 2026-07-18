package madoku.craft.attributes;

import madoku.craft.api.data.MadokuChunkDataManager;
import madoku.craft.api.data.MadokuDataManager;
import madoku.craft.attributes.health.MadokuHealthManager;
import madoku.craft.attributes.hunger.MadokuHungerManager;
import madoku.craft.attributes.oxygen.MadokuOxygenManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class MadokuCraftAttributes implements ModInitializer {
	public static final String MOD_ID = "madoku-craft-attributes";

	@Override
	public void onInitialize() {
		MadokuAttributesManager.initialize();
		MadokuChunkDataManager.initialize();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MadokuChunkDataManager.reset();
			MadokuChunkDataManager.loadPersistedData(server);
			MadokuHealthManager.reset();
			MadokuHungerManager.reset();
			MadokuOxygenManager.reset();
			MadokuHealthManager.loadPersistedData(server);
			MadokuHungerManager.loadPersistedData(server);
			MadokuOxygenManager.loadPersistedData(server);
			MadokuHealthManager.onServerStarted(server);
			MadokuHungerManager.onServerStarted(server);
			MadokuOxygenManager.onServerStarted(server);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MadokuHealthManager.savePersistedData(server);
			MadokuHungerManager.savePersistedData(server);
			MadokuOxygenManager.savePersistedData(server);
			MadokuChunkDataManager.savePersistedData(server);
			MadokuDataManager.savePersistedData(server);
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MadokuHealthManager.reset();
			MadokuHungerManager.reset();
			MadokuOxygenManager.reset();
			MadokuChunkDataManager.reset();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MadokuHealthManager.autosavePersistedData(server);
			MadokuHungerManager.autosavePersistedData(server);
			MadokuOxygenManager.autosavePersistedData(server);
			MadokuChunkDataManager.autosavePersistedData(server);
		});
	}
}
