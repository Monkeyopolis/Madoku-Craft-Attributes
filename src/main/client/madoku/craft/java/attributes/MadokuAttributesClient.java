package madoku.craft.java.attributes;

import java.util.concurrent.CopyOnWriteArrayList;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Owns the client side of the Attributes hunger synchronization payload. */
public final class MadokuAttributesClient {
	@FunctionalInterface
	public interface HungerListener {
		void onHungerUpdated(int current, int max);
	}

	private static final CopyOnWriteArrayList<HungerListener> HUNGER_LISTENERS = new CopyOnWriteArrayList<>();
	private static volatile boolean initialized;
	private static volatile int serverHungerCurrent;
	private static volatile int serverHungerMax = 20;
	private static volatile boolean hasServerHunger;

	private MadokuAttributesClient() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;

		ClientPlayNetworking.registerGlobalReceiver(HungerPayloadManager.TYPE, (payload, context) ->
			context.client().execute(() -> applyHungerPayload(context.client(), payload))
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearHungerState());
	}

	public static void addHungerListener(HungerListener listener) {
		if (listener == null) return;
		HUNGER_LISTENERS.addIfAbsent(listener);
		if (hasServerHunger) {
			listener.onHungerUpdated(serverHungerCurrent, serverHungerMax);
		}
	}

	public static boolean hasServerHunger() {
		return hasServerHunger;
	}

	public static int getServerHungerCurrent() {
		return serverHungerCurrent;
	}

	public static int getServerHungerMax() {
		return serverHungerMax;
	}

	public static boolean canConsumeFoodClient(boolean ignoreHunger) {
		if (ignoreHunger || !hasServerHunger) return true;
		return serverHungerCurrent < serverHungerMax;
	}

	private static void applyHungerPayload(Minecraft client, HungerPayloadManager payload) {
		int current = Math.max(0, payload.current());
		int max = Math.max(1, payload.max());
		serverHungerCurrent = Math.min(current, max);
		serverHungerMax = max;
		hasServerHunger = true;

		// Keep vanilla's client FoodData current for standalone Attributes installs.
		// Compat listeners additionally consume the same authoritative values for
		// the expanded HUD bars.
		if (client.player != null) {
			client.player.getFoodData().setFoodLevel(serverHungerCurrent);
		}
		for (HungerListener listener : HUNGER_LISTENERS) {
			listener.onHungerUpdated(serverHungerCurrent, serverHungerMax);
		}
	}

	private static void clearHungerState() {
		serverHungerCurrent = 0;
		serverHungerMax = 20;
		hasServerHunger = false;
	}
}
