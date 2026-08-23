package madoku.craft.attributes;

import madoku.craft.attributes.hunger.HungerPayloadManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MadokuCraftAttributesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(HungerPayloadManager.TYPE, (payload, context) ->
			MadokuHungerClientState.setServerHunger(payload.current(), payload.max())
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MadokuHungerClientState.reset());
	}
}
