package madoku.craft.java.attributes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Built-in provider for the Madoku armor attribute subsystem. */
public final class MadokuArmorProvider implements ArmorProvider {
	@Override public void initialize() { MadokuArmorManager.initialize(); }
	@Override public boolean isEnabled() { return MadokuArmorManager.isEnabled(); }
	@Override public boolean isResistanceEnabled() { return MadokuArmorManager.isResistanceEnabled(); }
	@Override public boolean shouldOverrideVanillaArmorDamage(DamageSource source) { return MadokuArmorManager.shouldOverrideVanillaArmorDamage(source); }
	@Override public float applyCustomArmorDamage(LivingEntity entity, DamageSource source, float amount) { return MadokuArmorManager.applyCustomArmorDamage(entity, source, amount); }
}
