package madoku.craft.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class HungerStateSync {
	private static boolean initialized = false;
	private static volatile int clientCurrent = -1;
	private static volatile int clientPending = 0;
	private static volatile int clientMax = -1;

	private HungerStateSync() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		PayloadTypeRegistry.playS2C().register(HungerStatePayload.TYPE, HungerStatePayload.CODEC);
		initialized = true;
	}

	public static boolean send(ServerPlayer player, int current, int pending, int max) {
		if (player == null || max <= 0 || !ServerPlayNetworking.canSend(player, HungerStatePayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, new HungerStatePayload(Math.max(0, current), Math.max(0, pending), Math.max(1, max)));
		return true;
	}

	public static void updateClientState(int current, int pending, int max) {
		clientCurrent = Math.max(0, current);
		clientPending = Math.max(0, pending);
		clientMax = Math.max(1, max);
	}

	public static void clearClientState() {
		clientCurrent = -1;
		clientPending = 0;
		clientMax = -1;
	}

	public static boolean canConsumeClient() {
		if (clientMax <= 0) {
			return true;
		}
		long total = (long) Math.max(0, clientCurrent) + (long) Math.max(0, clientPending);
		return total < clientMax;
	}
}