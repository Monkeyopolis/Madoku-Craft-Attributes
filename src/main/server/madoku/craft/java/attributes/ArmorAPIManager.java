package madoku.craft.java.attributes;

import madoku.craft.java.core.attribute.PlayerAttributeAPIManager;
import madoku.craft.java.core.attribute.PlayerAttributeStat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;

/** Public contract for the armor attribute subsystem. */
public final class ArmorAPIManager {
	private static final ArmorProvider UNAVAILABLE_PROVIDER = new ArmorProvider() { };
	private static volatile ArmorProvider provider = UNAVAILABLE_PROVIDER;
	private static final ArmorFeatureAdapter NO_FEATURE_ADAPTER = (target, source) -> 1.0D;
	private static volatile ArmorFeatureAdapter featureAdapter = NO_FEATURE_ADAPTER;

	private ArmorAPIManager() {
	}

	public static void registerProvider(ArmorProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Armor provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void registerFeatureAdapter(ArmorFeatureAdapter candidate) {
		if (candidate == null) throw new IllegalArgumentException("Armor feature adapter must not be null.");
		featureAdapter = candidate;
	}
	public static void unregisterFeatureAdapter() { featureAdapter = NO_FEATURE_ADAPTER; }
	public static double resolveBreachArmorEffectiveness(LivingEntity target, DamageSource source) {
		return Math.max(0.0D, featureAdapter.resolveBreachArmorEffectiveness(target, source));
	}
	public static double resolveDefensePoints(LivingEntity target, DamageSource source) {
		return target instanceof ServerPlayer player
			? PlayerAttributeAPIManager.resolveBonus(player, PlayerAttributeStat.DEFENSE)
			: 0.0D;
	}
	public static double resolveArmorPointStep() {
		double pointStep = featureAdapter.resolveArmorPointStep();
		return Double.isFinite(pointStep) && pointStep > 0.0D ? pointStep : 0.15D;
	}
	public static void initialize() { provider.initialize(); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static boolean isResistanceEnabled() { return provider.isResistanceEnabled(); }
	public static boolean shouldOverrideVanillaArmorDamage(DamageSource source) {
		return provider.shouldOverrideVanillaArmorDamage(source);
	}
	public static float applyCustomArmorDamage(LivingEntity entity, DamageSource source, float amount) {
		return provider.applyCustomArmorDamage(entity, source, amount);
	}
}
