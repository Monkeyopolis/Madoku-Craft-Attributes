package madoku.craft.java.attributes;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import madoku.craft.java.core.data.ChunkDataAPIManager;
import madoku.craft.java.core.helper.BlockDropContextAPIManager;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class MadokuLuckManager {
	private static final Identifier BASE_LUCK_MODIFIER_ID =
		Identifier.fromNamespaceAndPath("madoku-craft", "madoku_luck");
	private static final Identifier EFFECT_LUCK_MODIFIER_ID =
		Identifier.fromNamespaceAndPath("madoku-craft", "madoku_luck_effect");
	private static volatile LuckConfigManager.Settings settings = LuckConfigManager.Settings.defaults();
	private static volatile Boolean clientSynchronizedEnabled;

	private MadokuLuckManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		ServerPlayerEvents.JOIN.register(MadokuLuckManager::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refreshPlayerLuck(newPlayer));
	}

	public static boolean isEnabled() {
		Boolean synchronizedEnabled = clientSynchronizedEnabled;
		return synchronizedEnabled == null ? settings.enabled : synchronizedEnabled;
	}

	public static void applyClientSynchronizedEnabled(boolean enabled) {
		clientSynchronizedEnabled = enabled;
	}

	public static void resetClientSynchronizedSettings() {
		clientSynchronizedEnabled = null;
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

	public static ServerPlayer resolveLootPlayer(LootContext lootContext) {
		return resolveMobLootPlayer(lootContext);
	}

	public static ServerPlayer resolveActiveDropPlayer() {
		return BlockDropContextAPIManager.resolvePlayer();
	}

	/** Returns whether the block currently producing drops was placed by a player. */
	public static boolean isActiveDropPlayerPlacedBlock() {
		return BlockDropContextAPIManager.isActiveDropPlayerPlacedBlock();
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

		BlockDropContextAPIManager.Context context = BlockDropContextAPIManager.current();
		if (context != null) {
			applyGeneratedBlockDrops(lootContext, stacks, context);
			return;
		}

		applyGeneratedMobDrops(lootContext, stacks);
	}

	public static void applyManagedCropDrops(RandomSource random, ObjectArrayList<ItemStack> stacks) {
		applyManagedCropDrops(random, stacks, null);
	}

	public static void applyManagedCropDrops(
		RandomSource random,
		ObjectArrayList<ItemStack> stacks,
		ItemStack tool
	) {
		if (stacks == null || stacks.isEmpty()) {
			return;
		}

		BlockDropContextAPIManager.Context context = BlockDropContextAPIManager.current();
		if (context == null) {
			return;
		}

		boolean managedCrop = LuckAPIManager.isManagedCrop(context.level(), context.pos(), context.state())
			&& LuckAPIManager.isCropHarvestReady(context.level(), context.pos(), context.state());
		if (!managedCrop || context.player().isCreative()) {
			return;
		}

		for (ItemStack stack : stacks) {
			LuckAPIManager.applyConfiguredFortune(tool, stack, random);
		}

		if (!settings.enabled || !settings.cropDrops.enabled) {
			return;
		}

		double luckValue = resolveLuckValue(context.player());
		if (luckValue <= 0.0d) {
			return;
		}

		int stepCount = calculateLuckAdjustmentSteps(luckValue, random);
		if (stepCount > 0) {
			scaleStacks(stacks, settings.cropDrops.dropAdjustment, stepCount);
		}
	}

	public static void applyManagedCropDrops(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		if (lootContext == null) {
			applyManagedCropDrops((RandomSource) null, stacks);
			return;
		}
		ItemInstance toolInstance = lootContext.getOptionalParameter(LootContextParams.TOOL);
		ItemStack tool = toolInstance instanceof ItemStack itemStack ? itemStack : null;
		applyManagedCropDrops(lootContext.getRandom(), stacks, tool);
	}

	/** Applies Madoku Luck to managed mob-drop quantities generated outside LootTable#getRandomItems. */
	public static void applyManagedMobDrops(
		ServerPlayer player,
		RandomSource random,
		ObjectArrayList<ItemStack> stacks
	) {
		if (!settings.enabled || !settings.mobDrops.enabled || player == null || player.isCreative()
			|| stacks == null || stacks.isEmpty()) {
			return;
		}

		double luckValue = resolveLuckValue(player);
		if (luckValue <= 0.0d) {
			return;
		}

		int stepCount = calculateLuckAdjustmentSteps(luckValue, random);
		if (stepCount > 0) {
			scaleStacks(stacks, settings.mobDrops.dropAdjustment, stepCount);
		}
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
		BlockDropContextAPIManager.Context context
	) {
		if (!settings.enabled || !settings.blockDrops.enabled) {
			return;
		}
		boolean creative = context.player().isCreative();
		boolean playerPlaced = ChunkDataAPIManager.isPlayerPlacedBlock(context.level(), context.pos());
		boolean managedCrop = LuckAPIManager.isManagedCrop(context.level(), context.pos(), context.state())
			&& LuckAPIManager.isCropHarvestReady(context.level(), context.pos(), context.state());
		if (creative || (playerPlaced && !managedCrop)) {
			return;
		}
		if (managedCrop) {
			return;
		}

		double luckValue = resolveLuckValue(context.player());
		if (luckValue <= 0.0d) {
			return;
		}

		RandomSource random = lootContext.getRandom();
		int stepCount = calculateLuckAdjustmentSteps(luckValue, random);
		if (stepCount <= 0) {
			return;
		}
		scaleStacks(stacks, settings.blockDrops.dropAdjustment, stepCount);
	}

	private static void applyGeneratedMobDrops(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		if (!settings.enabled || !settings.mobDrops.enabled) {
			return;
		}
		Entity entity = resolveLootContextParameter(lootContext, "THIS_ENTITY", Entity.class);
		if (entity == null) {
			return;
		}
		if (!(entity instanceof Mob)) {
			return;
		}

		applyManagedMobDrops(resolveMobLootPlayer(lootContext), lootContext.getRandom(), stacks);
	}

	private static double reduceChanceByTargetLuck(
		LivingEntity target,
		double baseValue,
		double reductionMultiplier,
		String appliedMetricId,
		String skippedMetricId,
		String valueField
	) {
		if (!settings.enabled || !settings.luck.enabled) {
			double clampedBaseValue = clampDouble(baseValue, 0.0d, 1.0d);
			return clampedBaseValue;
		}
		double clampedBaseValue = clampDouble(baseValue, 0.0d, 1.0d);
		if (target == null) {
			return clampedBaseValue;
		}
		if (reductionMultiplier <= 0.0d) {
			return clampedBaseValue;
		}

		double luckChance = resolveTargetLuckReductionChance(target);
		double sanitizedReductionMultiplier = Math.max(0.0d, reductionMultiplier);
		double reduction = luckChance * sanitizedReductionMultiplier;
		double finalValue = clampDouble(clampedBaseValue - reduction, 0.0d, 1.0d);
		if (reduction <= 0.0d) {
			return finalValue;
		}

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

		double critChance = resolveLuckProcChance(serverPlayer);
		if (critChance <= 0.0d) {
			return 0.0d;
		}

		RandomSource random = serverPlayer.getRandom();
		double roll = random == null ? 1.0d : random.nextDouble();
		if (roll >= critChance) {
			return 0.0d;
		}

		double baseMultiplier = Math.max(0.0d, settings.playerCriticalDamage.damageMultiplier);
		if (baseMultiplier <= 0.0d) {
			return 0.0d;
		}
		double bonusLuck = Math.max(0.0d, resolveLuckValue(serverPlayer) - resolveLuckTierSize());
		int bonusSteps = calculateLuckAdjustmentSteps(bonusLuck, random);
		double bonusMultiplier = baseMultiplier * 0.5d;
		double finalMultiplier = baseMultiplier + (bonusSteps * bonusMultiplier);
		return finalMultiplier;
	}

	private static void loadStaticConfig() {
		settings = LuckConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
	}

	private static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public static void beginBlockDropContext(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		BlockDropContextAPIManager.begin(level, player, pos, state);
	}

	public static void endBlockDropContext() {
		BlockDropContextAPIManager.end();
	}



	private static ServerPlayer resolveMobLootPlayer(LootContext lootContext) {
		Player lastDamagePlayer = resolveLootContextParameter(lootContext, "LAST_DAMAGE_PLAYER", Player.class);
		if (lastDamagePlayer instanceof ServerPlayer serverPlayer) {
			return serverPlayer;
		}

		ServerPlayer player = resolveLootContextParameter(lootContext, "ATTACKING_ENTITY", ServerPlayer.class);
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



	private record DropScalingResult(int scaledStacks, int originalTotal, int extraTotal) {
	}

}
