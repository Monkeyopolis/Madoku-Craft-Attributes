package madoku.craft.java.attributes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/** Provider contract implemented by the module that owns Madoku health attributes. */
public interface HealthProvider {
	default void initialize() { }
	default boolean isEnabled() { return false; }
	default void reset() { }
	default void loadPersistedData(MinecraftServer server) { }
	default void autosavePersistedData(MinecraftServer server) { }
	default void savePersistedData(MinecraftServer server) { }
	default void onServerStarted(MinecraftServer server) { }
	default void onServerTick(MinecraftServer server) { }
	default void handlePlayerEffectsChanged(ServerPlayer player) { }
	default boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) { return false; }
	default boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) { return false; }
	default void restoreJoinHealth(ServerPlayer player) { }
}
