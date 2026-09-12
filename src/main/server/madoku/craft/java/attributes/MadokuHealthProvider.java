package madoku.craft.java.attributes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/** Built-in provider for the Madoku health attribute subsystem. */
public final class MadokuHealthProvider implements HealthProvider {
	@Override public void initialize() { MadokuHealthManager.initialize(); }
	@Override public boolean isEnabled() { return MadokuHealthManager.isEnabled(); }
	@Override public void reset() { MadokuHealthManager.reset(); }
	@Override public void loadPersistedData(MinecraftServer server) { MadokuHealthManager.loadPersistedData(server); }
	@Override public void autosavePersistedData(MinecraftServer server) { MadokuHealthManager.autosavePersistedData(server); }
	@Override public void savePersistedData(MinecraftServer server) { MadokuHealthManager.savePersistedData(server); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuHealthManager.onServerStarted(server); }
	@Override public void onServerTick(MinecraftServer server) { MadokuHealthManager.onServerTick(server); }
	@Override public void handlePlayerEffectsChanged(ServerPlayer player) { MadokuHealthManager.handlePlayerEffectsChanged(player); }
	@Override public boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) { return MadokuHealthManager.shouldOverrideVanillaEffect(entity, effect); }
	@Override public boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) { return MadokuHealthManager.shouldOverrideVanillaEffectAttributes(entity, effect); }
	@Override public void restoreJoinHealth(ServerPlayer player) { MadokuHealthManager.restoreJoinHealth(player); }
}
