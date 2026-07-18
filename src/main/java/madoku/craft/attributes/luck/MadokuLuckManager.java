
package madoku.craft.attributes.luck;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import madoku.craft.attributes.MadokuCraftAttributes;
import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.data.MadokuChunkDataManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;

public final class MadokuLuckManager {
	private static final Identifier BASE_LUCK_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraftAttributes.MOD_ID, "madoku_luck");
	private static final Identifier EFFECT_LUCK_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraftAttributes.MOD_ID, "madoku_luck_effect");
	private static volatile LuckConfigManager.Settings settings = LuckConfigManager.Settings.defaults();
	private static final ThreadLocal<ActiveDropContext> ACTIVE_DROP_CONTEXT = new ThreadLocal<>();

	private MadokuLuckManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		ServerPlayerEvents.JOIN.register(MadokuLuckManager::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refreshPlayerLuck(newPlayer));
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static void handlePlayerEffectsChanged(ServerPlayer player) {
		refreshPlayerLuck(player);
	}

	public static boolean shouldOverrideVanillaLuckEffect(LivingEntity entity, MobEffect effect) {
		if (!settings.enabled || !settings.luckEffect.enabled || !(entity instanceof ServerPlayer) || effect == null) {
			return false;
		}

		return effect == MobEffects.LUCK.value();
	}

	public static boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) {
		return shouldOverrideVanillaLuckEffect(entity, effect);
	}

	public static double reduceCreeperGriefChanceForTarget(LivingEntity target, double chance) {
		return reduceChanceByTargetLuck(
			target,
			chance,
			settings.creeperGriefReduction.adjustmentMultiplier,
			"luck.creeper_grief_reduction_applied",
			"luck.creeper_grief_reduction_skipped",
			"creeper_grief_chance"
		);
	}

	public static double reduceHostileRangedAccuracyForTarget(LivingEntity target, double accuracy) {
		return reduceChanceByTargetLuck(
			target,
			accuracy,
			settings.skeletonAccuracyReduction.adjustmentMultiplier,
			"luck.ranged_accuracy_reduction_applied",
			"luck.ranged_accuracy_reduction_skipped",
			"ranged_accuracy"
		);
	}

	public static boolean shouldApplyPlayerMeleeCrit(Player player, Entity target) {
		return resolvePlayerCriticalDamageMultiplier(player, target) > 0.0d;
	}

	public static float playerCritDamageMultiplier() {
		return (float) settings.playerCriticalDamage.damageMultiplier;
	}

	public static double resolveLootLuckStat(ServerPlayer player) {
		if (!settings.enabled || !settings.luck.enabled || player == null) {
			return 0.0d;
		}
		return resolveLuckValue(player);
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		refreshPlayerLuck(player);
	}

	private static void refreshPlayerLuck(ServerPlayer player) {
		applyBaseLuck(player);
		applyLuckEffect(player);
	}

	private static void applyBaseLuck(ServerPlayer player) {
		if (player == null) {
			return;
		}

		AttributeInstance luckAttribute = player.getAttribute(Attributes.LUCK);
		if (luckAttribute == null) {
			return;
		}

		luckAttribute.removeModifier(BASE_LUCK_MODIFIER_ID);
		if (settings.enabled && settings.luck.enabled && settings.luck.startingPoints > 0.0d) {
			luckAttribute.addOrUpdateTransientModifier(
				new AttributeModifier(
					BASE_LUCK_MODIFIER_ID,
					settings.luck.startingPoints,
					AttributeModifier.Operation.ADD_VALUE
				)
			);
			emitLuckDebug(
				"luck.base_applied",
				player.level() instanceof ServerLevel serverLevel ? serverLevel : null,
				player.blockPosition(),
				"player:" + player.getScoreboardName(),
				Map.of(
					"starting_points", Double.toString(settings.luck.startingPoints),
					"max_points", Double.toString(settings.luck.maxPoints)
				)
			);
		}
	}

	private static void applyLuckEffect(ServerPlayer player) {
		if (player == null) {
			return;
		}

		AttributeInstance luckAttribute = player.getAttribute(Attributes.LUCK);
		if (luckAttribute == null) {
			return;
		}

		luckAttribute.removeModifier(EFFECT_LUCK_MODIFIER_ID);
		int effectLevel = getLuckEffectLevel(player);
		double maxBonus = Math.max(0.0d, settings.luck.maxPoints - settings.luck.startingPoints);
		double bonus = settings.enabled && settings.luckEffect.enabled
			? Math.min(maxBonus, effectLevel * settings.luckEffect.value)
			: 0.0d;
		if (bonus > 0.0d) {
			luckAttribute.addOrUpdateTransientModifier(
				new AttributeModifier(EFFECT_LUCK_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE)
			);
			emitLuckDebug(
				"luck.effect_applied",
				player.level() instanceof ServerLevel serverLevel ? serverLevel : null,
				player.blockPosition(),
				"player:" + player.getScoreboardName(),
				Map.of(
					"effect_level", Integer.toString(effectLevel),
					"bonus", Double.toString(bonus),
					"max_bonus", Double.toString(maxBonus)
				)
			);
		}
	}

	private static int getLuckEffectLevel(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		MobEffectInstance effectInstance = player.getEffect(MobEffects.LUCK);
		return effectInstance == null ? 0 : effectInstance.getAmplifier() + 1;
	}

	public static void applyGeneratedLoot(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		if (!settings.enabled || lootContext == null || stacks == null || stacks.isEmpty()) {
			return;
		}

		ActiveDropContext context = ACTIVE_DROP_CONTEXT.get();
		if (context != null) {
			applyGeneratedBlockDrops(lootContext, stacks, context);
			return;
		}

		applyGeneratedMobDrops(lootContext, stacks);
	}

	public static void applyManagedCropDrops(RandomSource random, ObjectArrayList<ItemStack> stacks) {
		if (!settings.enabled || !settings.blockDrops.enabled || stacks == null || stacks.isEmpty()) {
			return;
		}

		ActiveDropContext context = ACTIVE_DROP_CONTEXT.get();
		if (context == null) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				null,
				null,
				"block:managed_crop",
				Map.of(
					"reason", "missing_block_context",
					"managed_crop", Boolean.TRUE.toString()
				)
			);
			return;
		}

		boolean managedCrop = false;
		if (!managedCrop) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "managed_crop_not_detected",
					"player", context.player.getScoreboardName(),
					"managed_crop", Boolean.FALSE.toString(),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}
		if (context.player.isCreative()) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "creative",
					"player", context.player.getScoreboardName(),
					"managed_crop", Boolean.TRUE.toString(),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}

		double luckValue = resolveLuckValue(context.player);
		if (luckValue <= 0.0d) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "non_positive_luck",
					"player", context.player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"managed_crop", Boolean.TRUE.toString(),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}

		int stepCount = calculateLuckAdjustmentSteps(luckValue, random);
		if (stepCount <= 0) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "no_adjustment_step",
					"player", context.player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"managed_crop", Boolean.TRUE.toString(),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}

		DropScalingResult scaling = scaleStacks(stacks, settings.blockDrops.dropAdjustment, stepCount);
		emitLuckDebug(
			"luck.drop_scale_applied",
			context.level,
			context.pos,
			subjectForPos("block", context.pos),
			Map.ofEntries(
				Map.entry("player", context.player.getScoreboardName()),
				Map.entry("state", context.state.getBlock().toString()),
				Map.entry("luck", Double.toString(luckValue)),
				Map.entry("tier_size", Double.toString(resolveLuckTierSize())),
				Map.entry("steps", Integer.toString(stepCount)),
				Map.entry("managed_crop", Boolean.TRUE.toString()),
				Map.entry("stacks", Integer.toString(scaling.scaledStacks())),
				Map.entry("original_total", Integer.toString(scaling.originalTotal())),
				Map.entry("extra_total", Integer.toString(scaling.extraTotal()))
			)
		);
	}

	public static void applyManagedCropDrops(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		if (lootContext == null) {
			applyManagedCropDrops((RandomSource) null, stacks);
			return;
		}
		applyManagedCropDrops(lootContext.getRandom(), stacks);
	}

	public static Consumer<ItemStack> wrapMobDeathLootConsumer(
		ServerLevel level,
		LivingEntity target,
		DamageSource damageSource,
		boolean causedByPlayer,
		Consumer<ItemStack> downstream
	) {
		if (!settings.enabled || !settings.mobDrops.enabled || level == null || target == null || downstream == null) {
			return downstream;
		}
		return downstream;
	}

	private static void applyGeneratedBlockDrops(
		LootContext lootContext,
		ObjectArrayList<ItemStack> stacks,
		ActiveDropContext context
	) {
		if (!settings.enabled || !settings.blockDrops.enabled) {
			return;
		}
		boolean creative = context.player.isCreative();
		boolean playerPlaced = MadokuChunkDataManager.isPlayerPlacedBlock(context.level, context.pos);
		boolean managedCrop = false;
		emitLuckDebug(
			"luck.place_check",
			context.level,
			context.pos,
			subjectForPos("block", context.pos),
			Map.of(
				"player", context.player.getScoreboardName(),
				"creative", Boolean.toString(creative),
				"player_placed", Boolean.toString(playerPlaced),
				"managed_crop", Boolean.toString(managedCrop),
				"state", context.state.getBlock().toString()
			)
		);
		if (creative || (playerPlaced && !managedCrop)) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", creative ? "creative" : "player_placed",
					"player", context.player.getScoreboardName(),
					"managed_crop", Boolean.toString(managedCrop),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}
		if (managedCrop) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "managed_crop_delegate",
					"player", context.player.getScoreboardName(),
					"managed_crop", Boolean.TRUE.toString(),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}

		double luckValue = resolveLuckValue(context.player);
		if (luckValue <= 0.0d) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "non_positive_luck",
					"player", context.player.getScoreboardName(),
					"managed_crop", Boolean.toString(managedCrop),
					"luck", Double.toString(luckValue)
				)
			);
			return;
		}

		RandomSource random = lootContext.getRandom();
		int stepCount = calculateLuckAdjustmentSteps(luckValue, random);
		if (stepCount <= 0) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "no_adjustment_step",
					"player", context.player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"managed_crop", Boolean.toString(managedCrop),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}

		DropScalingResult scaling = scaleStacks(stacks, settings.blockDrops.dropAdjustment, stepCount);
		emitLuckDebug(
			"luck.drop_scale_applied",
			context.level,
			context.pos,
			subjectForPos("block", context.pos),
			Map.ofEntries(
				Map.entry("player", context.player.getScoreboardName()),
				Map.entry("state", context.state.getBlock().toString()),
				Map.entry("luck", Double.toString(luckValue)),
				Map.entry("tier_size", Double.toString(resolveLuckTierSize())),
				Map.entry("steps", Integer.toString(stepCount)),
				Map.entry("managed_crop", Boolean.toString(managedCrop)),
				Map.entry("stacks", Integer.toString(scaling.scaledStacks())),
				Map.entry("original_total", Integer.toString(scaling.originalTotal())),
				Map.entry("extra_total", Integer.toString(scaling.extraTotal()))
			)
		);
	}

	private static void applyGeneratedMobDrops(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		if (!settings.enabled || !settings.mobDrops.enabled) {
			return;
		}
		Entity entity = resolveLootContextParameter(lootContext, "THIS_ENTITY", Entity.class);
		if (entity == null) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext == null ? null : lootContext.getLevel(),
				null,
				Map.of("reason", "missing_this_entity")
			);
			return;
		}
		if (!(entity instanceof Mob mob)) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext.getLevel(),
				null,
				Map.of(
					"reason", "non_mob_entity",
					"entity", entity.getType().toShortString(),
					"entity_class", entity.getClass().getSimpleName()
				)
			);
			return;
		}

		ServerPlayer player = resolveMobLootPlayer(lootContext);
		if (player == null) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext.getLevel(),
				mob,
				Map.of("reason", "missing_player", "state", mob.getType().toShortString())
			);
			return;
		}
		if (player.isCreative()) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext.getLevel(),
				mob,
				Map.of(
					"reason", "creative",
					"player", player.getScoreboardName(),
					"mob", mob.getType().toShortString()
				)
			);
			return;
		}

		double luckValue = resolveLuckValue(player);
		if (luckValue <= 0.0d) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext.getLevel(),
				mob,
				Map.of(
					"reason", "non_positive_luck",
					"player", player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"mob", mob.getType().toShortString()
				)
			);
			return;
		}

		RandomSource random = lootContext.getRandom();
		int stepCount = calculateLuckAdjustmentSteps(luckValue, random);
		if (stepCount <= 0) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext.getLevel(),
				mob,
				Map.of(
					"reason", "no_adjustment_step",
					"player", player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"mob", mob.getType().toShortString()
				)
			);
			return;
		}

		DropScalingResult scaling = scaleStacks(stacks, settings.mobDrops.dropAdjustment, stepCount);
		emitMobLuckDebug(
			"luck.mob_drop_scale_applied",
			lootContext.getLevel(),
			mob,
			Map.of(
				"player", player.getScoreboardName(),
				"mob", mob.getType().toShortString(),
				"luck", Double.toString(luckValue),
				"tier_size", Double.toString(resolveLuckTierSize()),
				"steps", Integer.toString(stepCount),
				"stacks", Integer.toString(scaling.scaledStacks()),
				"original_total", Integer.toString(scaling.originalTotal()),
				"extra_total", Integer.toString(scaling.extraTotal())
			)
		);
	}

	private static double reduceChanceByTargetLuck(
		LivingEntity target,
		double baseValue,
		double reductionMultiplier,
		String appliedMetricId,
		String skippedMetricId,
		String valueField
	) {
		ServerLevel world = target != null && target.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		String subject = target instanceof ServerPlayer player
			? "player:" + player.getScoreboardName()
			: target == null
				? "player:unknown"
				: "entity:" + target.getType().toShortString();
		if (!settings.enabled || !settings.luck.enabled) {
			double clampedBaseValue = clampDouble(baseValue, 0.0d, 1.0d);
			emitLuckDebug(
				skippedMetricId,
				world,
				target == null ? null : target.blockPosition(),
				subject,
				Map.of(
					"reason", "disabled",
					valueField, Double.toString(clampedBaseValue)
				)
			);
			return clampedBaseValue;
		}
		double clampedBaseValue = clampDouble(baseValue, 0.0d, 1.0d);
		if (target == null) {
			emitLuckDebug(
				skippedMetricId,
				world,
				null,
				subject,
				Map.of(
					"reason", "missing_target",
					valueField, Double.toString(clampedBaseValue),
					"reduction_multiplier", Double.toString(Math.max(0.0d, reductionMultiplier))
				)
			);
			return clampedBaseValue;
		}
		if (reductionMultiplier <= 0.0d) {
			emitLuckDebug(
				skippedMetricId,
				world,
				target.blockPosition(),
				subject,
				Map.of(
					"reason", "non_positive_multiplier",
					"target", target.getScoreboardName(),
					valueField, Double.toString(clampedBaseValue),
					"reduction_multiplier", Double.toString(reductionMultiplier)
				)
			);
			return clampedBaseValue;
		}

		double luckChance = resolveTargetLuckReductionChance(target);
		double sanitizedReductionMultiplier = Math.max(0.0d, reductionMultiplier);
		double reduction = luckChance * sanitizedReductionMultiplier;
		double finalValue = clampDouble(clampedBaseValue - reduction, 0.0d, 1.0d);
		if (reduction <= 0.0d) {
			emitLuckDebug(
				skippedMetricId,
				world,
				target.blockPosition(),
				subject,
				Map.of(
					"reason", "non_positive_luck",
					"target", target.getScoreboardName(),
					"value", Double.toString(clampedBaseValue),
					valueField, Double.toString(clampedBaseValue),
					"luck", Double.toString(luckChance),
					"reduction_multiplier", Double.toString(sanitizedReductionMultiplier)
				)
			);
			return finalValue;
		}

		emitLuckDebug(
			appliedMetricId,
			world,
			target.blockPosition(),
			subject,
			Map.of(
				"target", target.getScoreboardName(),
				"value", Double.toString(clampedBaseValue),
				valueField, Double.toString(clampedBaseValue),
				"final_value", Double.toString(finalValue),
				"luck", Double.toString(luckChance),
				"reduction_multiplier", Double.toString(sanitizedReductionMultiplier),
				"reduction", Double.toString(reduction)
			)
		);
		return finalValue;
	}

	private static double resolveTargetLuckReductionChance(LivingEntity target) {
		if (!settings.enabled || !settings.luck.enabled) {
			return 0.0d;
		}
		AttributeInstance luckAttribute = target == null ? null : target.getAttribute(Attributes.LUCK);
		double luckValue = luckAttribute == null ? settings.luck.startingPoints : luckAttribute.getValue();
		if (!Double.isFinite(luckValue)) {
			return 0.0d;
		}
		return clampDouble(luckValue / resolveLuckTierSize(), 0.0d, 1.0d);
	}

	private static double resolveLuckProcChance(ServerPlayer player) {
		if (!settings.enabled || !settings.luck.enabled) {
			return 0.0d;
		}
		return clampDouble(resolveLuckValue(player) / resolveLuckTierSize(), 0.0d, 1.0d);
	}

	private static double resolveLuckValue(ServerPlayer player) {
		if (!settings.enabled || !settings.luck.enabled) {
			return 0.0d;
		}
		AttributeInstance luckAttribute = player == null ? null : player.getAttribute(Attributes.LUCK);
		double luckValue = luckAttribute == null ? settings.luck.startingPoints : luckAttribute.getValue();
		if (!Double.isFinite(luckValue)) {
			return settings.luck.startingPoints;
		}
		return luckValue;
	}

	private static double resolveLuckTierSize() {
		return Math.max(1.0d, settings.luck.maxPoints);
	}

	private static int calculateLuckAdjustmentSteps(double luckValue, RandomSource random) {
		if (!Double.isFinite(luckValue) || luckValue <= 0.0d) {
			return 0;
		}
		double tierSize = resolveLuckTierSize();
		if (tierSize <= 0.0d) {
			return 0;
		}
		double sanitizedLuck = Math.max(0.0d, luckValue);
		int guaranteedSteps = (int) Math.floor(sanitizedLuck / tierSize);
		double fractionalLuck = sanitizedLuck - (guaranteedSteps * tierSize);
		if (fractionalLuck > 0.0d && random != null && random.nextDouble() < (fractionalLuck / tierSize)) {
			guaranteedSteps++;
		}
		return guaranteedSteps;
	}

	private static DropScalingResult scaleStacks(
		ObjectArrayList<ItemStack> stacks,
		LuckConfigManager.DropAdjustmentSettings adjustment,
		int stepCount
	) {
		if (stacks == null || stacks.isEmpty() || adjustment == null || stepCount <= 0) {
			return new DropScalingResult(0, 0, 0);
		}

		int scaledStacks = 0;
		int originalTotal = 0;
		int extraTotal = 0;
		for (ItemStack stack : stacks) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}

			int originalCount = stack.getCount();
			int adjustedCount = scaleStackCount(originalCount, adjustment, stepCount);
			originalTotal += originalCount;
			if (adjustedCount <= originalCount) {
				continue;
			}

			stack.setCount(adjustedCount);
			scaledStacks++;
			extraTotal += adjustedCount - originalCount;
		}
		return new DropScalingResult(scaledStacks, originalTotal, extraTotal);
	}

	private static int scaleStackCount(int originalCount, LuckConfigManager.DropAdjustmentSettings adjustment, int stepCount) {
		if (originalCount <= 0 || adjustment == null || stepCount <= 0) {
			return originalCount;
		}

		double scaledCount = originalCount;
		if (adjustment.type == LuckConfigManager.ValueType.MULTIPLIER) {
			scaledCount *= Math.pow(Math.max(0.0d, adjustment.value), stepCount);
		} else {
			scaledCount += Math.max(0.0d, adjustment.value) * stepCount;
		}
		if (!Double.isFinite(scaledCount)) {
			return originalCount;
		}
		return Math.max(0, (int) Math.round(scaledCount));
	}

	public static double resolvePlayerCriticalDamageMultiplier(Player player, Entity target) {
		if (!settings.enabled || !settings.luck.enabled || !(player instanceof ServerPlayer serverPlayer)) {
			return 0.0d;
		}

		ServerLevel level = serverPlayer.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		double critChance = resolveLuckProcChance(serverPlayer);
		if (critChance <= 0.0d) {
			emitLuckDebug(
				"luck.player_crit_skipped",
				level,
				target == null ? player.blockPosition() : target.blockPosition(),
				subjectForCombatTarget(target),
				Map.of(
					"reason", "non_positive_chance",
					"player", serverPlayer.getScoreboardName(),
					"chance", Double.toString(critChance),
					"multiplier", Double.toString(settings.playerCriticalDamage.damageMultiplier)
				)
			);
			return 0.0d;
		}

		RandomSource random = serverPlayer.getRandom();
		double roll = random == null ? 1.0d : random.nextDouble();
		if (roll >= critChance) {
			emitLuckDebug(
				"luck.player_crit_skipped",
				level,
				target == null ? player.blockPosition() : target.blockPosition(),
				subjectForCombatTarget(target),
				Map.of(
					"reason", "chance_failed",
					"player", serverPlayer.getScoreboardName(),
					"chance", Double.toString(critChance),
					"roll", Double.toString(roll),
					"multiplier", Double.toString(settings.playerCriticalDamage.damageMultiplier)
				)
			);
			return 0.0d;
		}

		double baseMultiplier = Math.max(0.0d, settings.playerCriticalDamage.damageMultiplier);
		if (baseMultiplier <= 0.0d) {
			emitLuckDebug(
				"luck.player_crit_skipped",
				level,
				target == null ? player.blockPosition() : target.blockPosition(),
				subjectForCombatTarget(target),
				Map.of(
					"reason", "non_positive_multiplier",
					"player", serverPlayer.getScoreboardName(),
					"chance", Double.toString(critChance),
					"roll", Double.toString(roll),
					"multiplier", Double.toString(baseMultiplier)
				)
			);
			return 0.0d;
		}
		double bonusLuck = Math.max(0.0d, resolveLuckValue(serverPlayer) - resolveLuckTierSize());
		int bonusSteps = calculateLuckAdjustmentSteps(bonusLuck, random);
		double bonusMultiplier = baseMultiplier * 0.5d;
		double finalMultiplier = baseMultiplier + (bonusSteps * bonusMultiplier);
		emitLuckDebug(
			"luck.player_crit_applied",
			level,
			target == null ? player.blockPosition() : target.blockPosition(),
			subjectForCombatTarget(target),
			Map.of(
				"reason", bonusSteps > 0 ? "bonus_applied" : "chance_succeeded",
				"player", serverPlayer.getScoreboardName(),
				"chance", Double.toString(critChance),
				"roll", Double.toString(roll),
				"base_multiplier", Double.toString(baseMultiplier),
				"bonus_steps", Integer.toString(bonusSteps),
				"final_multiplier", Double.toString(finalMultiplier)
			)
		);
		return finalMultiplier;
	}

	private static void loadStaticConfig() {
		settings = LuckConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
	}

	private static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public static void beginBlockDropContext(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (level == null || player == null || pos == null || state == null) {
			ACTIVE_DROP_CONTEXT.remove();
			return;
		}

		ACTIVE_DROP_CONTEXT.set(new ActiveDropContext(level, player, pos.immutable(), state));
		emitLuckDebug(
			"luck.drop_context_begin",
			level,
			pos,
			subjectForPos("block", pos),
			Map.of(
				"player", player.getScoreboardName(),
				"state", state.getBlock().toString(),
				"luck", Double.toString(resolveLuckValue(player))
			)
		);
	}

	public static void endBlockDropContext() {
		ACTIVE_DROP_CONTEXT.remove();
	}

	static void emitLuckDebug(String metricId, ServerLevel world, BlockPos pos, String subject, Map<String, String> fields) {
		if (metricId == null || metricId.isBlank()) {
			return;
		}
		String entry = MadokuDebugManager.resolveCallerMethodName();
		if (!MadokuDebugManager.shouldEmit("attributes", "luck", entry)) {
			return;
		}

		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(metricId, "attributes", "luck", entry)
			.side(MadokuDebugManager.Side.SERVER)
			.tick(MadokuTimeManager.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(subject == null || subject.isBlank() ? "global" : subject);

		if (pos != null) {
			builder.field("pos", pos.toShortString());
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

	private static void emitMobLuckDebug(String metricId, ServerLevel world, Mob mob, Map<String, String> fields) {
		if (metricId == null || metricId.isBlank()) {
			return;
		}
		String entry = MadokuDebugManager.resolveCallerMethodName();
		if (!MadokuDebugManager.shouldEmit("attributes", "luck", entry)) {
			return;
		}

		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(metricId, "attributes", "luck", entry)
			.side(MadokuDebugManager.Side.SERVER)
			.tick(MadokuTimeManager.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(mob == null ? "mob:unknown" : "mob:" + mob.getType().toShortString());

		if (mob != null) {
			builder.field("pos", mob.blockPosition().toShortString());
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

	private static ServerPlayer resolveMobLootPlayer(LootContext lootContext) {
		ServerPlayer player = resolveLootContextParameter(lootContext, "LAST_DAMAGE_PLAYER", ServerPlayer.class);
		if (player != null) {
			return player;
		}

		player = resolveLootContextParameter(lootContext, "ATTACKING_ENTITY", ServerPlayer.class);
		if (player != null) {
			return player;
		}

		return resolveLootContextParameter(lootContext, "KILLER_ENTITY", ServerPlayer.class);
	}

	private static <T> T resolveLootContextParameter(LootContext lootContext, String fieldName, Class<T> targetType) {
		if (lootContext == null || fieldName == null || fieldName.isBlank() || targetType == null) {
			return null;
		}

		Object parameter = resolveLootContextParameterKey(fieldName);
		if (parameter == null) {
			return null;
		}

		try {
			Method hasParameter = findLootContextMethod(lootContext.getClass(), "hasParameter", parameter.getClass());
			if (hasParameter != null) {
				Object present = hasParameter.invoke(lootContext, parameter);
				if (!(present instanceof Boolean) || !((Boolean) present)) {
					return null;
				}
			}

			for (String methodName : new String[] { "getParameter", "getParam", "getOptionalParameter", "getParamOrNull", "get" }) {
				Method method = findLootContextMethod(lootContext.getClass(), methodName, parameter.getClass());
				if (method == null) {
					continue;
				}

				Object value = method.invoke(lootContext, parameter);
				if (targetType.isInstance(value)) {
					return targetType.cast(value);
				}
			}
		} catch (ReflectiveOperationException | RuntimeException exception) {
			return null;
		}

		return null;
	}

	private static Object resolveLootContextParameterKey(String fieldName) {
		try {
			Field field = LootContextParams.class.getField(fieldName);
			return field.get(null);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			return null;
		}
	}

	private static Method findLootContextMethod(Class<?> type, String name, Class<?> parameterType) {
		if (type == null || name == null || parameterType == null) {
			return null;
		}

		for (Method method : type.getMethods()) {
			if (!name.equals(method.getName()) || method.getParameterCount() != 1) {
				continue;
			}
			Class<?> candidateType = method.getParameterTypes()[0];
			if (candidateType.isAssignableFrom(parameterType) || parameterType.isAssignableFrom(candidateType)) {
				return method;
			}
		}
		return null;
	}

	private static String subjectForPos(String prefix, BlockPos pos) {
		if (pos == null) {
			return prefix == null || prefix.isBlank() ? "global" : prefix;
		}
		String safePrefix = prefix == null || prefix.isBlank() ? "block" : prefix;
		return safePrefix + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static String subjectForCombatTarget(Entity target) {
		if (target == null) {
			return "combat:unknown";
		}
		return "combat:" + target.getType().toShortString();
	}

	private record DropScalingResult(int scaledStacks, int originalTotal, int extraTotal) {
	}

	private record ActiveDropContext(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
	}

}


