package madoku.craft.java.attributes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Optional bridge for external or transitional systems that modify armor effectiveness. */
@FunctionalInterface
public interface ArmorFeatureAdapter {
	 double resolveBreachArmorEffectiveness(LivingEntity target, DamageSource source);
}
