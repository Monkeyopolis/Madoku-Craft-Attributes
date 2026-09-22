package madoku.craft.java.attributes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;

/** Provider contract implemented by the module that owns Madoku oxygen attributes. */
public interface OxygenProvider {
	default void initialize() { }
	default boolean isEnabled() { return false; }
	default int getMaximumOxygenTicksForEntity(LivingEntity entity) { return 0; }
	default void handleMaximumOxygenChanged(ServerPlayer player) { }
	default void applyClientSynchronizedSettings(boolean enabled, int maximum) { }
	default void applyClientSynchronizedPlayerBonus(int bonusTicks) { }
	default void resetClientSynchronizedSettings() { }
}
