package madoku.craft.java.attributes;

import net.minecraft.world.entity.LivingEntity;

/** Built-in provider for the Madoku oxygen attribute subsystem. */
public final class MadokuOxygenProvider implements OxygenProvider {
	@Override public void initialize() { MadokuOxygenManager.initialize(); }
	@Override public boolean isEnabled() { return MadokuOxygenManager.isEnabled(); }
	@Override public int getMaximumOxygenTicksForEntity(LivingEntity entity) { return MadokuOxygenManager.getMaximumOxygenTicksForEntity(entity); }
	@Override public void applyClientSynchronizedSettings(boolean enabled, int maximum) { MadokuOxygenManager.applyClientSynchronizedSettings(enabled, maximum); }
	@Override public void resetClientSynchronizedSettings() { MadokuOxygenManager.resetClientSynchronizedSettings(); }
}
