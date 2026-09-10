package madoku.craft.java.attributes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/** Built-in provider for the Madoku hunger attribute subsystem. */
public final class MadokuHungerProvider implements HungerProvider {
	@Override public void initialize() { MadokuHungerManager.initialize(); }
	@Override public void reset() { MadokuHungerManager.reset(); }
	@Override public void loadPersistedData(MinecraftServer server) { MadokuHungerManager.loadPersistedData(server); }
	@Override public void autosavePersistedData(MinecraftServer server) { MadokuHungerManager.autosavePersistedData(server); }
	@Override public void savePersistedData(MinecraftServer server) { MadokuHungerManager.savePersistedData(server); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuHungerManager.onServerStarted(server); }
	@Override public void onServerTick(MinecraftServer server) { MadokuHungerManager.onServerTick(server); }
	@Override public void handlePlayerTeleport(ServerPlayer player) { MadokuHungerManager.handlePlayerTeleport(player); }
	@Override public boolean isEnabled() { return MadokuHungerManager.isEnabled(); }
	@Override public boolean isSaturationEnabled() { return MadokuHungerManager.isSaturationEnabled(); }
	@Override public boolean isHungerEffectEnabled() { return MadokuHungerManager.isHungerEffectEnabled(); }
	@Override public int getCurrentHungerPoints(ServerPlayer player) { return MadokuHungerManager.getCurrentHungerPoints(player); }
	@Override public int getEffectiveHungerPoints(ServerPlayer player) { return MadokuHungerManager.getEffectiveHungerPoints(player); }
	@Override public int getMaximumHungerPoints(ServerPlayer player) { return MadokuHungerManager.getMaximumHungerPoints(player); }
	@Override public int getConfiguredMaximumHungerPoints() { return MadokuHungerManager.getConfiguredMaximumHungerPoints(); }
	@Override public void applyClientSynchronizedSettings(boolean enabled, int maximum) { MadokuHungerManager.applyClientSynchronizedSettings(enabled, maximum); }
	@Override public void resetClientSynchronizedSettings() { MadokuHungerManager.resetClientSynchronizedSettings(); }
	@Override public boolean shouldApplyStarvationDamage(ServerPlayer player) { return MadokuHungerManager.shouldApplyStarvationDamage(player); }
	@Override public void handleMaximumHungerChanged(ServerPlayer player) { MadokuHungerManager.handleMaximumHungerChanged(player); }
	@Override public boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) { return MadokuHungerManager.shouldOverrideVanillaEffect(entity, effect); }
	@Override public boolean canConsumeFood(ServerPlayer player, boolean ignoreHunger) { return MadokuHungerManager.canConsumeFood(player, ignoreHunger); }
	@Override public void onFoodConsumed(ServerPlayer player, int nutrition) { MadokuHungerManager.onFoodConsumed(player, nutrition); }
	@Override public int drainHunger(ServerPlayer player, int amount) { return MadokuHungerManager.drainHunger(player, amount); }
}
