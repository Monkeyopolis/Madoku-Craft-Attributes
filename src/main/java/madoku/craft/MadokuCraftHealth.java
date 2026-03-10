package madoku.craft;

import madoku.craft.armor.MadokuArmor;
import madoku.craft.health.MadokuHealth;
import madoku.craft.hunger.MadokuHunger;
import madoku.craft.network.HungerStateSync;
import madoku.craft.oxygen.MadokuOxygen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class MadokuCraftHealth implements ModInitializer {
	public static final String MOD_ID = "madoku-craft-health";

	@Override
	public void onInitialize() {
		MadokuArmor.initialize();
		MadokuHealth.initialize();
		MadokuHunger.initialize();
		MadokuOxygen.initialize();
		HungerStateSync.initialize();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MadokuHealth.reset();
			MadokuHunger.reset();
			MadokuOxygen.reset();
			MadokuHealth.loadPersistedData(server);
			MadokuHunger.loadPersistedData(server);
			MadokuOxygen.loadPersistedData(server);
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MadokuHealth.savePersistedData(server);
			MadokuHunger.savePersistedData(server);
			MadokuOxygen.savePersistedData(server);
			MadokuHealth.reset();
			MadokuHunger.reset();
			MadokuOxygen.reset();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MadokuHealth.autosavePersistedData(server);
			MadokuHunger.autosavePersistedData(server);
			MadokuOxygen.autosavePersistedData(server);
		});
	}
}