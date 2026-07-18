package madoku.craft.attributes;

import madoku.craft.attributes.client.HungerClientState;
import madoku.craft.attributes.hunger.HungerPayloadManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MadokuCraftAttributesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(HungerPayloadManager.TYPE, (payload, context) ->
			context.client().execute(() -> {
				HungerClientState.update(payload.current(), payload.pending(), payload.max());
				HungerClientState.updateVanillaFood(context.client().player);
			})
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HungerClientState.clear());
	}
}
