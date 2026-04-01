package madoku.craft.attributes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import madoku.craft.armor.MadokuArmor;
import madoku.craft.health.MadokuHealth;
import madoku.craft.hunger.MadokuHunger;
import madoku.craft.luck.MadokuLuck;
import madoku.craft.luck.MadokuPlacedBlocks;
import madoku.craft.oxygen.MadokuOxygen;

public class MadokuCraftAttributes implements ModInitializer {
	public static final String MOD_ID = "madoku-craft-attributes";

	@Override
	public void onInitialize() {
		MadokuAttributes.initialize();
		MadokuArmor.initialize();
		MadokuHealth.initialize();
		MadokuHunger.initialize();
		MadokuLuck.initialize();
		MadokuPlacedBlocks.initialize();
		MadokuOxygen.initialize();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MadokuHealth.reset();
			MadokuHunger.reset();
			MadokuPlacedBlocks.reset();
			MadokuOxygen.reset();
			MadokuHealth.loadPersistedData(server);
			MadokuHunger.loadPersistedData(server);
			MadokuPlacedBlocks.loadPersistedData(server);
			MadokuOxygen.loadPersistedData(server);
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MadokuHealth.savePersistedData(server);
			MadokuHunger.savePersistedData(server);
			MadokuPlacedBlocks.savePersistedData(server);
			MadokuOxygen.savePersistedData(server);
			MadokuHealth.reset();
			MadokuHunger.reset();
			MadokuPlacedBlocks.reset();
			MadokuOxygen.reset();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MadokuHealth.autosavePersistedData(server);
			MadokuHunger.autosavePersistedData(server);
			MadokuPlacedBlocks.autosavePersistedData(server);
			MadokuOxygen.autosavePersistedData(server);
		});
	}
}
