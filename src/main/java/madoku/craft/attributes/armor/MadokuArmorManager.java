package madoku.craft.attributes.armor;

import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.api.debug.MadokuDebugManager;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Map;

public final class MadokuArmorManager {
	private static final double DAMAGE_ROUND_INCREMENT = 0.05d;
	private static final double POINT_STEP = 0.25d;
	private static volatile ArmorConfigManager.Settings settings = ArmorConfigManager.Settings.defaults();

	private MadokuArmorManager() {
	}

	public static void initialize() {
		loadStaticConfig();
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static boolean isResistanceEnabled() {
		return settings.enabled && settings.main.resistance.enabled;
	}

	public static boolean shouldOverrideVanillaArmorDamage(DamageSource source) {
		if (!settings.enabled || source == null) {
			return false;
		}

		return settings.armorPoints.enabled
			|| settings.armorToughnessPoints.enabled
			|| isResistanceEnabled()
			|| (source.is(DamageTypeTags.IS_FALL) && settings.main.fallDamageReduction != 1.0d);
	}

	public static float applyCustomArmorDamage(LivingEntity entity, DamageSource source, float amount) {
		if (entity == null || source == null || amount <= 0.0f || !settings.enabled) {
			return amount;
		}

		double armorPoints = clampToRange(
			entity.getAttributeValue(Attributes.ARMOR),
			settings.armorPoints.startingArmor,
			settings.armorPoints.maxPoints
		);
		double armorToughnessPoints = clampToRange(
			entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
			settings.armorToughnessPoints.startingArmorToughness,
			settings.armorToughnessPoints.maxPoints
		);

		double damageAfterArmor = settings.armorPoints.enabled
			? applyDamageReduction(amount, armorPoints, settings.armorPoints.damageReduction)
			: amount;
		if (settings.armorPoints.enabled && Math.abs(damageAfterArmor - amount) > 1.0e-6d) {
			emitArmorDebug(
				"armor-points",
				"armor.armor_points_applied",
				entity,
				source,
				Map.of(
					"amount", Double.toString(amount),
					"after", Double.toString(damageAfterArmor),
					"armor_points", Double.toString(armorPoints),
					"reduction_type", settings.armorPoints.damageReduction.type.name()
				)
			);
		}
		double damageAfterToughness = settings.armorToughnessPoints.enabled
			? applyDamageReduction(
				damageAfterArmor,
				armorToughnessPoints,
				settings.armorToughnessPoints.damageReduction
			)
			: damageAfterArmor;
		if (settings.armorToughnessPoints.enabled && Math.abs(damageAfterToughness - damageAfterArmor) > 1.0e-6d) {
			emitArmorDebug(
				"armor-toughness-points",
				"armor.armor_toughness_points_applied",
				entity,
				source,
				Map.of(
					"amount", Double.toString(damageAfterArmor),
					"after", Double.toString(damageAfterToughness),
					"armor_toughness_points", Double.toString(armorToughnessPoints),
					"reduction_type", settings.armorToughnessPoints.damageReduction.type.name()
				)
			);
		}
		double damageAfterResistance = applyResistanceReduction(entity, source, damageAfterToughness);
		if (damageAfterResistance != damageAfterToughness) {
			emitArmorDebug(
				"resistance",
				"armor.resistance_applied",
				entity,
				source,
				Map.of(
					"amount", Double.toString(damageAfterToughness),
					"after", Double.toString(damageAfterResistance),
					"resistance_value", Double.toString(settings.main.resistance.value),
					"reduction_type", settings.main.resistance.type.name()
				)
			);
		}

		double finalDamage = damageAfterResistance;
		if (source.is(DamageTypeTags.IS_FALL)) {
			double mitigatedDamage = Math.max(0.0d, amount - finalDamage);
			finalDamage = amount - (mitigatedDamage * settings.main.fallDamageReduction);
			if (Math.abs(finalDamage - damageAfterResistance) > 1.0e-6d) {
				emitArmorDebug(
					"main",
					"armor.fall_damage_reduced",
					entity,
					source,
					Map.of(
						"amount", Double.toString(damageAfterResistance),
						"after", Double.toString(finalDamage),
						"fall_damage_reduction", Double.toString(settings.main.fallDamageReduction)
					)
				);
			}
		}

		return (float) Math.max(0.0d, roundToDamageIncrement(finalDamage));
	}

	private static void loadStaticConfig() {
		settings = ArmorConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
	}

	private static void emitArmorDebug(String group, String metricId, LivingEntity entity, DamageSource source, Map<String, String> fields) {
		if (entity == null || metricId == null || metricId.isBlank()) {
			return;
		}
		String entry = MadokuDebugManager.resolveCallerMethodName();
		if (!MadokuDebugManager.shouldEmit("attributes", "armor", entry)) {
			return;
		}

		String subject = entity instanceof net.minecraft.server.level.ServerPlayer player
			? "player:" + player.getScoreboardName()
			: "entity:" + entity.getType().toShortString();
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(metricId, "attributes", "armor", entry)
			.side(MadokuDebugManager.Side.SERVER)
			.tick(madoku.craft.api.time.MadokuTimeManager.getGameplayTicks())
			.subject(subject);
		if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			builder.world(serverLevel.dimension().toString());
		}
		if (source != null) {
			builder.field("damage_source", source.toString());
		}
		if (fields != null) {
			for (Map.Entry<String, String> fieldEntry : fields.entrySet()) {
				if (fieldEntry != null) {
					builder.field(fieldEntry.getKey(), fieldEntry.getValue());
				}
			}
		}
		builder.log();
	}

	private static double applyDamageReduction(double amount, double points, ArmorConfigManager.DamageReduction reduction) {
		if (amount <= 0.0d || reduction == null) {
			return amount;
		}

		long pointSteps = getStepCount(points, POINT_STEP);
		if (pointSteps <= 0L) {
			return amount;
		}

		return switch (reduction.type) {
			case FLAT -> Math.max(0.0d, amount - (pointSteps * reduction.value));
			case PERCENTAGE -> {
				double multiplier = Math.max(0.0d, 1.0d - (pointSteps * reduction.value));
				yield amount * multiplier;
			}
			};
	}

	private static double applyResistanceReduction(LivingEntity entity, DamageSource source, double amount) {
		if (entity == null || amount <= 0.0d || !isResistanceEnabled()) {
			return amount;
		}
		if (source != null && (source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_RESISTANCE))) {
			return amount;
		}

		MobEffectInstance resistance = entity.getEffect(MobEffects.RESISTANCE);
		if (resistance == null) {
			return amount;
		}

		int level = Math.max(0, resistance.getAmplifier() + 1);
		if (level <= 0) {
			return amount;
		}

		double perLevelValue = settings.main.resistance.value;
		double reduction = perLevelValue * level;
		return switch (settings.main.resistance.type) {
			case PERCENTAGE -> Math.max(0.0d, amount * Math.max(0.0d, 1.0d - reduction));
			case FLAT -> Math.max(0.0d, amount - reduction);
		};
	}

	private static double clampToRange(double value, double min, double max) {
		double lowerBound = Math.min(min, max);
		double upperBound = Math.max(min, max);
		if (value < lowerBound) {
			return lowerBound;
		}
		return Math.min(value, upperBound);
	}

	private static long getStepCount(double value, double stepSize) {
		if (value <= 0.0d || stepSize <= 0.0d) {
			return 0L;
		}
		return Math.max(0L, (long) Math.floor(value / stepSize));
	}

	private static double roundToDamageIncrement(double value) {
		return Math.round(value / DAMAGE_ROUND_INCREMENT) * DAMAGE_ROUND_INCREMENT;
	}
}
