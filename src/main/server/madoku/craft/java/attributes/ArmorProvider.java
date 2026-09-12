package madoku.craft.java.attributes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Provider contract implemented by the module that owns Madoku armor attributes. */
public interface ArmorProvider {
	default void initialize() { }
	default boolean isEnabled() { return false; }
	default boolean isResistanceEnabled() { return false; }
	default boolean shouldOverrideVanillaArmorDamage(DamageSource source) { return false; }
	default float applyCustomArmorDamage(LivingEntity entity, DamageSource source, float amount) { return amount; }
}
