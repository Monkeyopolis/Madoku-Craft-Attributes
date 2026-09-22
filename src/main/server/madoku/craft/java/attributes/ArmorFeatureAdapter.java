package madoku.craft.java.attributes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Optional bridge for external or transitional systems that modify armor effectiveness. */
@FunctionalInterface
public interface ArmorFeatureAdapter {
	double resolveBreachArmorEffectiveness(LivingEntity target, DamageSource source);
	default double resolveDefensePoints(LivingEntity target, DamageSource source) { return 0.0D; }
	/** Resolves the armor-point step used by the custom armor reduction formula. */
	default double resolveArmorPointStep() { return 0.15D; }
}
