package madoku.craft.java.attributes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the Attributes module's own network payloads. */
final class MadokuAttributesNetworking {
	private static boolean initialized;

	private MadokuAttributesNetworking() {
	}

	static void initialize() {
		if (initialized) return;
		PayloadTypeRegistry.clientboundPlay().register(HungerPayloadManager.TYPE, HungerPayloadManager.CODEC);
		initialized = true;
	}
}
