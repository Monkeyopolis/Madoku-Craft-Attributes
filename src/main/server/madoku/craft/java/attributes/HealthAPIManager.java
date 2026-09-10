package madoku.craft.java.attributes;

import madoku.craft.java.core.data.DataSaveParticipant;
import madoku.craft.java.core.data.DataSaveParticipantAPIManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/** Public contract for the health attribute subsystem. */
public final class HealthAPIManager {
	private static final HealthProvider UNAVAILABLE_PROVIDER = new HealthProvider() { };
	private static volatile HealthProvider provider = UNAVAILABLE_PROVIDER;
	private static final DataSaveParticipant DATA_SAVE_PARTICIPANT = new DataSaveParticipant() {
		@Override public String id() { return "attributes.health"; }
		@Override public void autosavePersistedData(MinecraftServer server) { HealthAPIManager.autosavePersistedData(server); }
		@Override public void savePersistedData(MinecraftServer server) { HealthAPIManager.savePersistedData(server); }
	};

	private HealthAPIManager() {
	}

	public static void registerProvider(HealthProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Health provider must not be null.");
		provider = candidate;
		DataSaveParticipantAPIManager.register(DATA_SAVE_PARTICIPANT);
	}
	public static void unregisterProvider() {
		provider = UNAVAILABLE_PROVIDER;
		DataSaveParticipantAPIManager.unregister(DATA_SAVE_PARTICIPANT.id());
	}
	public static void initialize() { provider.initialize(); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static void reset() { provider.reset(); }
	public static void loadPersistedData(MinecraftServer server) { provider.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { provider.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { provider.savePersistedData(server); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void onServerTick(MinecraftServer server) { provider.onServerTick(server); }
	public static void handlePlayerEffectsChanged(ServerPlayer player) { provider.handlePlayerEffectsChanged(player); }
	public static boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) {
		return provider.shouldOverrideVanillaEffect(entity, effect);
	}
	public static boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) {
		return provider.shouldOverrideVanillaEffectAttributes(entity, effect);
	}
	public static void restoreJoinHealth(ServerPlayer player) { provider.restoreJoinHealth(player); }
}
