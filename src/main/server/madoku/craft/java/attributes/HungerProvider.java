package madoku.craft.java.attributes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/** Provider contract implemented by the module that owns Madoku hunger attributes. */
public interface HungerProvider {
	default void initialize() { }
	default void reset() { }
	default void loadPersistedData(MinecraftServer server) { }
	default void autosavePersistedData(MinecraftServer server) { }
	default void savePersistedData(MinecraftServer server) { }
	default void onServerStarted(MinecraftServer server) { }
	default void onServerTick(MinecraftServer server) { }
	default void handlePlayerTeleport(ServerPlayer player) { }
	default boolean isEnabled() { return false; }
	default boolean isSaturationEnabled() { return false; }
	default boolean isHungerEffectEnabled() { return false; }
	default int getCurrentHungerPoints(ServerPlayer player) { return 0; }
	default int getEffectiveHungerPoints(ServerPlayer player) { return 0; }
	default int getMaximumHungerPoints(ServerPlayer player) { return 0; }
	default int getConfiguredMaximumHungerPoints() { return 0; }
	default void applyClientSynchronizedSettings(boolean enabled, int maximum) { }
	default void resetClientSynchronizedSettings() { }
	default boolean shouldApplyStarvationDamage(ServerPlayer player) { return false; }
	default void handleMaximumHungerChanged(ServerPlayer player) { }
	default boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) { return false; }
	default boolean canConsumeFood(ServerPlayer player, boolean ignoreHunger) { return false; }
	default void onFoodConsumed(ServerPlayer player, int nutrition) { }
	default int drainHunger(ServerPlayer player, int amount) { return 0; }
}
