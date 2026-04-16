package madoku.craft;

import madoku.craft.network.HungerStatePayload;
import madoku.craft.network.HungerStateSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MadokuCraftAttributesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(
			HungerStatePayload.TYPE,
			(payload, context) -> HungerStateSync.updateClientState(payload.current(), payload.pending(), payload.max())
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HungerStateSync.clearClientState());
	}
}
