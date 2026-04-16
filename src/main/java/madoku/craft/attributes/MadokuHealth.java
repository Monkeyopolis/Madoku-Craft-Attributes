package madoku.craft.attributes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import madoku.craft.MadokuCraftAttributes;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.StaticJsonSystem;
import madoku.craft.data.MadokuData;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.hunger.MadokuHunger;
import madoku.craft.scheduler.MadokuScheduler;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MadokuHealth {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuHealth.class);
	private static final float EPSILON = 1.0e-4f;
	private static final float HEALTH_ROUND_STEP = 0.125f;
	private static final int VANILLA_MAX_HUNGER_POINTS = 20;

	private static final String HEALTH_CONFIG_DIRECTORY_NAME = "madoku-health";
	private static final String HEALTH_CONFIG_FILE_NAME = "madoku-health";
	private static final String DATA_FOLDER_NAME = "madoku-craft-health";
	private static final String DATA_FILE_NAME = "madoku-health";
	private static final String TASK_TYPE_HEALTH_TICK = "health_tick";
	private static final float DEATH_RESPAWN_HEALTH_RATIO = 0.5f;
	private static final long AUTOSAVE_INTERVAL_TICKS = 60L * 20L;
	private static final Identifier LOW_HUNGER_MAX_HEALTH_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraftAttributes.MOD_ID, "madoku_health_low_hunger_max_health");
	private static final Identifier HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraftAttributes.MOD_ID, "madoku_health_health_boost_max_health");
	private static final long WITHER_TICK_INTERVAL = 20L;
	private static final long REGEN_TICK_INTERVAL = 20L;
	private static final long POISON_TICK_INTERVAL = 10L;
	private static final float EFFECT_DAMAGE_AMOUNT = 1.0f;
	private static final float POISON_DAMAGE_PER_LEVEL = EFFECT_DAMAGE_AMOUNT;
	private static final float WITHER_DAMAGE_PER_LEVEL = EFFECT_DAMAGE_AMOUNT;
	private static final float REGEN_FRACTION_PER_LEVEL = 0.05f;
	private static final double HEALTH_BOOST_MAX_HEALTH_FRACTION_PER_LEVEL = 0.10d;
	private static final float ABSORPTION_FRACTION_PER_LEVEL = 0.20f;
	private static final float POISON_MIN_HEALTH = 1.0f;

	private static final Map<UUID, PlayerState> PLAYER_STATES = new HashMap<>();
	private static final Map<UUID, String> PLAYER_SCHEDULER_IDS = new HashMap<>();
	private static final Set<UUID> SCHEDULED_PLAYERS = new HashSet<>();
	private static final Map<UUID, Long> LAST_PROCESSED_TICKS_BY_PLAYER = new HashMap<>();
	private static volatile Settings settings = Settings.defaults();
	private static long lastAutosaveBucket = Long.MIN_VALUE;

	private MadokuHealth() {
	}

	public static void initialize() {
		loadStaticConfig();
		MadokuScheduler.registerTaskHandler(TASK_TYPE_HEALTH_TICK, MadokuHealth::runHealthTask);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MadokuHealth::handleAfterPlayerDamage);
		ServerPlayerEvents.JOIN.register(MadokuHealth::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register(MadokuHealth::handlePlayerRespawn);
	}

	public static void reset() {
		PLAYER_STATES.clear();
		PLAYER_SCHEDULER_IDS.clear();
		SCHEDULED_PLAYERS.clear();
		LAST_PROCESSED_TICKS_BY_PLAYER.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		loadStaticConfig();
		MadokuData.createWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		JsonObject data = MadokuData.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		applyPersistedData(data);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), AUTOSAVE_INTERVAL_TICKS);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long bucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), AUTOSAVE_INTERVAL_TICKS);
		if (bucket != lastAutosaveBucket) {
			lastAutosaveBucket = bucket;
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		MadokuData.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
	}

	private static void runHealthTask(MinecraftServer server, MadokuScheduler.TaskContext context, JsonObject payload) {
		if (server == null || context == null) {
			return;
		}

		MadokuScheduler.SchedulerOwner owner = context.getOwner();
		if (owner == null || !"player".equals(owner.getKind())) {
			return;
		}

		UUID playerId = parseUuid(owner.getOwnerId());
		if (playerId == null) {
			return;
		}

		PLAYER_SCHEDULER_IDS.put(playerId, context.getSchedulerId());
		SCHEDULED_PLAYERS.remove(playerId);
		Long lastProcessed = LAST_PROCESSED_TICKS_BY_PLAYER.get(playerId);
		if (lastProcessed != null && context.getNowTick() == lastProcessed) {
			return;
		}
		LAST_PROCESSED_TICKS_BY_PLAYER.put(playerId, context.getNowTick());

		disableVanillaNaturalRegen(server, context.getNowTick());
		if (!settings.enabled) {
			return;
		}

		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null) {
			PlayerState state = PLAYER_STATES.get(playerId);
			if (state != null) {
				state.onlineThisSession = false;
				state.appliedMaxHealthMultiplier = 1.0d;
				state.appliedHealthBoostAmount = 0.0d;
				state.appliedAbsorptionAmount = 0.0f;
			}
			return;
		}

		boolean stillActive = processPlayer(player, context.getNowTick());
		if (stillActive) {
			requestHealthProcessing(server, playerId, Math.max(1L, settings.schedulerTickInterval));
		}
	}

	private static boolean processPlayer(ServerPlayer player, long gameplayTick) {
		if (player == null) {
			return false;
		}

		UUID playerId = player.getUUID();
		boolean actionTick = Math.floorMod(gameplayTick, Math.max(1, settings.actionIntervalTicks)) == 0;
		PlayerState state = PLAYER_STATES.computeIfAbsent(playerId, ignored -> new PlayerState());
		if (!state.onlineThisSession) {
			state.onlineThisSession = true;
			state.lastPendingActivityTick = gameplayTick;
		}
		if (!player.isAlive() || player.isDeadOrDying()) {
			// Never run the heal/drain loop while the player is dead; this can interfere with respawn.
			state.pendingHealth = 0.0f;
			state.highHungerDrainActive = false;
			return false;
		}

		applyLowHungerMaxHealthScaling(player, state, gameplayTick);
		applyHealthBoostScaling(player, state, gameplayTick);
		applyAbsorptionScaling(player, state, gameplayTick);
		processStatusEffects(player, state, gameplayTick);
		if (actionTick) {
			collectPendingHealthFromHunger(player, state, gameplayTick);
			applyPendingHealth(player, state, gameplayTick);
		}
		clearIdlePendingHealth(state, playerId, gameplayTick);
		return true;
	}

	private static void processStatusEffects(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (player == null) {
			return;
		}

		int poisonLevel = getEffectLevel(player, MobEffects.POISON);
		if (poisonLevel > 0) {
			if (Math.floorMod(gameplayTick, POISON_TICK_INTERVAL) == 0L) {
				applyPoisonTick(player, gameplayTick, poisonLevel);
			}
		}

		int witherLevel = getEffectLevel(player, MobEffects.WITHER);
		if (witherLevel > 0 && Math.floorMod(gameplayTick, WITHER_TICK_INTERVAL) == 0L) {
			applyWitherTick(player, gameplayTick, witherLevel);
		}

		int regenerationLevel = getEffectLevel(player, MobEffects.REGENERATION);
		if (regenerationLevel > 0 && Math.floorMod(gameplayTick, REGEN_TICK_INTERVAL) == 0L) {
			applyRegenerationTick(player, state, gameplayTick, regenerationLevel);
		}
	}

	private static void applyPoisonTick(ServerPlayer player, long gameplayTick, int poisonLevel) {
		float current = player.getHealth();
		if (current <= POISON_MIN_HEALTH + EPSILON) {
			return;
		}

		float damage = POISON_DAMAGE_PER_LEVEL * Math.max(1, poisonLevel);
		float target = quantizeHealth(Math.max(POISON_MIN_HEALTH, current - damage));
		if (target >= current - EPSILON) {
			return;
		}

		player.setHealth(target);
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.effect_poison_tick")) {
			MadokuDebug.event("health.effect_poison_tick", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("level", poisonLevel)
				.field("damage", formatFloat(damage))
				.field("health", formatFloat(player.getHealth()))
				.log();
		}
	}

	private static void applyWitherTick(ServerPlayer player, long gameplayTick, int witherLevel) {
		float damage = WITHER_DAMAGE_PER_LEVEL * Math.max(1, witherLevel);
		player.hurtServer(player.level(), player.damageSources().wither(), damage);
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.effect_wither_tick")) {
			MadokuDebug.event("health.effect_wither_tick", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("level", witherLevel)
				.field("damage", formatFloat(damage))
				.field("health", formatFloat(player.getHealth()))
				.log();
		}
	}

	private static void applyRegenerationTick(ServerPlayer player, PlayerState state, long gameplayTick, int regenerationLevel) {
		float maxHealth = player.getMaxHealth();
		float current = player.getHealth();
		if (current >= maxHealth - EPSILON) {
			return;
		}

		float healing = maxHealth * REGEN_FRACTION_PER_LEVEL * regenerationLevel;
		if (healing <= EPSILON) {
			return;
		}

		float target = quantizeHealth(Math.min(maxHealth, current + healing));
		if (target <= current + EPSILON) {
			return;
		}

		player.setHealth(target);
		state.lastPendingActivityTick = gameplayTick;

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.effect_regeneration_tick")) {
			MadokuDebug.event("health.effect_regeneration_tick", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("healed", formatFloat(target - current))
				.field("health", formatFloat(player.getHealth()))
				.log();
		}
	}

	private static void collectPendingHealthFromHunger(ServerPlayer player, PlayerState state, long gameplayTick) {
		float missingHealth = player.getMaxHealth() - player.getHealth();
		float hungerRatio = hungerRatio(player.getFoodData().getFoodLevel());
		boolean hasRecoveryNeed = missingHealth > EPSILON;

		if (hungerRatio > settings.highHungerStartRatio && hasRecoveryNeed) {
			state.highHungerDrainActive = true;
		}
		if (hungerRatio <= settings.highHungerStopRatio || !hasRecoveryNeed) {
			state.highHungerDrainActive = false;
		}
		if (!state.highHungerDrainActive) {
			return;
		}

		int drained = drainFood(player, 1);
		if (drained <= 0) {
			state.highHungerDrainActive = false;
			return;
		}

		state.pendingHealth += drained * (float) settings.pendingHealthPerHunger;
		state.lastPendingActivityTick = gameplayTick;
		if (hungerRatio(player.getFoodData().getFoodLevel()) <= settings.highHungerStopRatio) {
			state.highHungerDrainActive = false;
		}

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.pending_collected")) {
			MadokuDebug.event("health.pending_collected", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("hunger_drained", drained)
				.field("pending_health", formatFloat(state.pendingHealth))
				.log();
		}
	}

	private static void applyPendingHealth(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (state.pendingHealth <= EPSILON) {
			return;
		}

		float missingHealth = player.getMaxHealth() - player.getHealth();
		if (missingHealth <= EPSILON) {
			return;
		}

		float healing = Math.min(
			(float) settings.pendingHealthApplyAmount,
			Math.min(state.pendingHealth, missingHealth)
		);
		if (healing <= EPSILON) {
			return;
		}

		float targetHealth = quantizeHealth(player.getHealth() + healing);
		targetHealth = Math.min(player.getMaxHealth(), targetHealth);
		targetHealth = Math.max(player.getHealth(), targetHealth);
		float appliedHealing = targetHealth - player.getHealth();
		if (appliedHealing <= EPSILON) {
			return;
		}

		player.setHealth(targetHealth);
		state.pendingHealth -= appliedHealing;
		if (state.pendingHealth < EPSILON) {
			state.pendingHealth = 0.0f;
		}
		state.lastPendingActivityTick = gameplayTick;

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.pending_applied")) {
			MadokuDebug.event("health.pending_applied", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("healed", formatFloat(appliedHealing))
				.field("pending_health", formatFloat(state.pendingHealth))
				.field("health", formatFloat(player.getHealth()))
				.log();
		}
	}

	private static void clearIdlePendingHealth(PlayerState state, UUID playerId, long gameplayTick) {
		if (state.pendingHealth <= EPSILON) {
			return;
		}

		long idleTicks = gameplayTick - state.lastPendingActivityTick;
		if (idleTicks < settings.pendingIdleTimeoutTicks) {
			return;
		}

		state.pendingHealth = 0.0f;
		state.highHungerDrainActive = false;
		state.lastPendingActivityTick = gameplayTick;

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.pending_idle_cleared")) {
			MadokuDebug.event("health.pending_idle_cleared", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + playerId)
				.log();
		}
	}

	private static void applyLowHungerMaxHealthScaling(ServerPlayer player, PlayerState state, long gameplayTick) {
		AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealthAttribute == null) {
			return;
		}

		double targetBaseMaxHealth = settings.maximumHealth;
		if (Math.abs(maxHealthAttribute.getBaseValue() - targetBaseMaxHealth) > 1.0e-5d) {
			maxHealthAttribute.setBaseValue(targetBaseMaxHealth);
		}

		double targetMultiplier = calculateMaxHealthMultiplier(hungerRatio(player.getFoodData().getFoodLevel()));
		if (Math.abs(targetMultiplier - state.appliedMaxHealthMultiplier) > 1.0e-5d) {
			if (Math.abs(targetMultiplier - 1.0d) <= 1.0e-5d) {
				maxHealthAttribute.removeModifier(LOW_HUNGER_MAX_HEALTH_MODIFIER_ID);
			} else {
				maxHealthAttribute.addOrUpdateTransientModifier(
					new AttributeModifier(
						LOW_HUNGER_MAX_HEALTH_MODIFIER_ID,
						targetMultiplier - 1.0d,
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
					)
				);
			}

			state.appliedMaxHealthMultiplier = targetMultiplier;
			if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.max_health_scaled")) {
				MadokuDebug.event("health.max_health_scaled", MadokuDebug.Domain.HEALTH)
					.side(MadokuDebug.Side.SERVER)
					.tick(gameplayTick)
					.subject("player:" + player.getUUID())
					.field("multiplier", String.format("%.3f", targetMultiplier))
					.field("food_level", player.getFoodData().getFoodLevel())
					.log();
			}
		}

		float maxHealth = player.getMaxHealth();
		if (player.getHealth() > maxHealth + EPSILON) {
			player.setHealth(Math.min(maxHealth, quantizeHealth(maxHealth)));
		}
	}

	private static void applyHealthBoostScaling(ServerPlayer player, PlayerState state, long gameplayTick) {
		AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealthAttribute == null) {
			return;
		}

		int healthBoostLevel = getEffectLevel(player, MobEffects.HEALTH_BOOST);
		double referenceMaxHealth = player.getMaxHealth() - state.appliedHealthBoostAmount;
		if (referenceMaxHealth < 0.0d) {
			referenceMaxHealth = player.getMaxHealth();
		}
		double targetAmount = healthBoostLevel <= 0
			? 0.0d
			: referenceMaxHealth * HEALTH_BOOST_MAX_HEALTH_FRACTION_PER_LEVEL * healthBoostLevel;

		boolean modifierChanged = false;
		boolean missingExpectedModifier = targetAmount > 1.0e-5d && !maxHealthAttribute.hasModifier(HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID);
		if (Math.abs(targetAmount - state.appliedHealthBoostAmount) > 1.0e-5d || missingExpectedModifier) {
			maxHealthAttribute.removeModifier(HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID);
			if (targetAmount > 1.0e-5d) {
				maxHealthAttribute.addOrUpdateTransientModifier(
					new AttributeModifier(
						HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID,
						targetAmount,
						AttributeModifier.Operation.ADD_VALUE
					)
				);
			}
			state.appliedHealthBoostAmount = targetAmount;
			modifierChanged = true;
		}

		if (player.getHealth() > player.getMaxHealth() + EPSILON) {
			player.setHealth(Math.min(player.getMaxHealth(), quantizeHealth(player.getMaxHealth())));
		}

		if (modifierChanged && MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.effect_health_boost_scaled")) {
			MadokuDebug.event("health.effect_health_boost_scaled", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("bonus_amount", String.format("%.3f", state.appliedHealthBoostAmount))
				.field("max_health", formatFloat(player.getMaxHealth()))
				.log();
		}
	}

	private static void applyAbsorptionScaling(ServerPlayer player, PlayerState state, long gameplayTick) {
		int absorptionLevel = getEffectLevel(player, MobEffects.ABSORPTION);
		float targetAbsorption = absorptionLevel <= 0
			? 0.0f
			: quantizeHealth(player.getMaxHealth() * ABSORPTION_FRACTION_PER_LEVEL * absorptionLevel);

		if (Math.abs(targetAbsorption - state.appliedAbsorptionAmount) <= EPSILON) {
			return;
		}

		player.setAbsorptionAmount(targetAbsorption);
		state.appliedAbsorptionAmount = targetAbsorption;
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.effect_absorption_scaled")) {
			MadokuDebug.event("health.effect_absorption_scaled", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("absorption", formatFloat(targetAbsorption))
				.log();
		}
	}

	private static int drainFood(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return 0;
		}
		if (MadokuHunger.isEnabled()) {
			return MadokuHunger.drainHunger(player, amount);
		}

		FoodData foodData = player.getFoodData();
		int current = Math.max(0, foodData.getFoodLevel());
		int drained = Math.min(amount, current);
		if (drained <= 0) {
			return 0;
		}

		int updated = current - drained;
		foodData.setFoodLevel(updated);
		foodData.setSaturation(Math.max(0.0f, Math.min(foodData.getSaturationLevel(), updated)));
		return drained;
	}

	private static double calculateMaxHealthMultiplier(float hungerRatio) {
		if (hungerRatio >= settings.lowHungerThresholdRatio) {
			return 1.0d;
		}

		double belowThreshold = settings.lowHungerThresholdRatio - hungerRatio;
		int steps = (int) Math.ceil((belowThreshold - EPSILON) / settings.lowHungerStepRatio);
		steps = Math.max(0, steps);
		double reduction = steps * settings.maxHealthReductionPerStep;
		double multiplier = 1.0d - reduction;
		if (multiplier < settings.minimumMaxHealthMultiplier) {
			return settings.minimumMaxHealthMultiplier;
		}
		return Math.min(1.0d, multiplier);
	}

	private static float hungerRatio(int foodLevel) {
		int clamped = Math.max(0, Math.min(VANILLA_MAX_HUNGER_POINTS, foodLevel));
		return (float) clamped / (float) VANILLA_MAX_HUNGER_POINTS;
	}

	private static String ensureSchedulerExists(UUID playerId) {
		if (playerId == null) {
			return "";
		}

		String schedulerId = PLAYER_SCHEDULER_IDS.get(playerId);
		if (schedulerId == null || schedulerId.isBlank()) {
			schedulerId = MadokuScheduler.createScheduler(
				MadokuScheduler.SchedulerOwner.of("player", playerId.toString(), null)
			);
			PLAYER_SCHEDULER_IDS.put(playerId, schedulerId);
		}
		return schedulerId;
	}

	private static void requestHealthProcessing(MinecraftServer server, UUID playerId, long delay) {
		if (server == null || playerId == null || !settings.enabled || SCHEDULED_PLAYERS.contains(playerId)) {
			return;
		}

		String schedulerId = ensureSchedulerExists(playerId);
		if (enqueueHealthTask(schedulerId, delay)) {
			SCHEDULED_PLAYERS.add(playerId);
			return;
		}

		String created = MadokuScheduler.createScheduler(
			MadokuScheduler.SchedulerOwner.of("player", playerId.toString(), null)
		);
		PLAYER_SCHEDULER_IDS.put(playerId, created);
		if (enqueueHealthTask(created, delay)) {
			SCHEDULED_PLAYERS.add(playerId);
			return;
		}

		LOGGER.error("Failed to enqueue MadokuHealth scheduler task for player={}", playerId);
	}

	private static boolean enqueueHealthTask(String targetSchedulerId, long delay) {
		if (targetSchedulerId == null || targetSchedulerId.isBlank()) {
			return false;
		}

		MadokuScheduler.EnqueueStatus status = MadokuScheduler.enqueue(
			targetSchedulerId,
			Math.max(0L, delay),
			TASK_TYPE_HEALTH_TICK,
			new JsonObject(),
			MadokuScheduler.TickDomain.GAMEPLAY
		);
		return status == MadokuScheduler.EnqueueStatus.ACCEPTED
			|| status == MadokuScheduler.EnqueueStatus.QUEUE_FULL;
	}

	private static void disableVanillaNaturalRegen(MinecraftServer server, long gameplayTick) {
		for (ServerLevel level : server.getAllLevels()) {
			boolean wasEnabled = level.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
			level.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
			if (wasEnabled && MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.vanilla_regen_disabled")) {
				MadokuDebug.event("health.vanilla_regen_disabled", MadokuDebug.Domain.HEALTH)
					.side(MadokuDebug.Side.SERVER)
					.tick(gameplayTick)
					.world(level.dimension().toString())
					.subject("world:" + level.dimension())
					.log();
			}
		}
	}

	private static void handlePlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null || alive) {
			return;
		}

		float targetHealth = Math.max(0.5f, newPlayer.getMaxHealth() * DEATH_RESPAWN_HEALTH_RATIO);
		targetHealth = Math.min(newPlayer.getMaxHealth(), quantizeHealth(targetHealth));
		newPlayer.setHealth(targetHealth);

		PlayerState state = PLAYER_STATES.computeIfAbsent(newPlayer.getUUID(), ignored -> new PlayerState());
		state.pendingHealth = 0.0f;
		state.highHungerDrainActive = newPlayer.getHealth() + EPSILON < newPlayer.getMaxHealth();
		state.lastPendingActivityTick = MadokuTicks.getGameplayTicks();
		state.appliedMaxHealthMultiplier = 1.0d;
		state.appliedHealthBoostAmount = 0.0d;
		state.appliedAbsorptionAmount = 0.0f;
		state.onlineThisSession = true;
		requestHealthProcessing(((ServerLevel) newPlayer.level()).getServer(), newPlayer.getUUID(), 1L);

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.respawn_half_health")) {
			MadokuDebug.event("health.respawn_half_health", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(MadokuTicks.getGameplayTicks())
				.subject("player:" + newPlayer.getUUID())
				.field("health", formatFloat(newPlayer.getHealth()))
				.field("max_health", formatFloat(newPlayer.getMaxHealth()))
				.log();
		}
	}

	private static void handleAfterPlayerDamage(
		net.minecraft.world.entity.LivingEntity entity,
		net.minecraft.world.damagesource.DamageSource source,
		float baseDamageTaken,
		float damageTaken,
		boolean blocked
	) {
		if (!(entity instanceof ServerPlayer player) || damageTaken <= EPSILON) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.onlineThisSession = true;
		state.lastPendingActivityTick = MadokuTicks.getGameplayTicks();
		state.highHungerDrainActive = player.getHealth() + EPSILON < player.getMaxHealth();
		requestHealthProcessing(((ServerLevel) player.level()).getServer(), player.getUUID(), 1L);

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.damage_detected")) {
			MadokuDebug.event("health.damage_detected", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(MadokuTicks.getGameplayTicks())
				.subject("player:" + player.getUUID())
				.field("damage_taken", formatFloat(damageTaken))
				.field("blocked", blocked)
				.log();
		}
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.onlineThisSession = true;
		state.lastPendingActivityTick = MadokuTicks.getGameplayTicks();
		state.highHungerDrainActive = player.getHealth() + EPSILON < player.getMaxHealth();
		applyImmediateEffectOverrides(player, state, MadokuTicks.getGameplayTicks());
		requestHealthProcessing(((ServerLevel) player.level()).getServer(), player.getUUID(), 1L);
	}

	public static void handlePlayerEffectsChanged(ServerPlayer player) {
		if (player == null) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.onlineThisSession = true;
		long gameplayTick = MadokuTicks.getGameplayTicks();
		state.lastPendingActivityTick = gameplayTick;
		applyImmediateEffectOverrides(player, state, gameplayTick);
		requestHealthProcessing(((ServerLevel) player.level()).getServer(), player.getUUID(), 1L);
	}

	public static boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) {
		if (!settings.enabled || !(entity instanceof ServerPlayer) || effect == null) {
			return false;
		}

		return effect == MobEffects.POISON.value()
			|| effect == MobEffects.WITHER.value()
			|| effect == MobEffects.REGENERATION.value()
			|| effect == MobEffects.ABSORPTION.value();
	}

	public static boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) {
		if (!settings.enabled || !(entity instanceof ServerPlayer) || effect == null) {
			return false;
		}

		return effect == MobEffects.HEALTH_BOOST.value()
			|| effect == MobEffects.ABSORPTION.value();
	}

	private static void applyImmediateEffectOverrides(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (!settings.enabled || player == null || state == null) {
			return;
		}
		applyLowHungerMaxHealthScaling(player, state, gameplayTick);
		applyHealthBoostScaling(player, state, gameplayTick);
		applyAbsorptionScaling(player, state, gameplayTick);
	}

	private static int getEffectLevel(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
		if (player == null || effect == null) {
			return 0;
		}
		MobEffectInstance effectInstance = player.getEffect(effect);
		if (effectInstance == null) {
			return 0;
		}
		return Math.max(0, effectInstance.getAmplifier() + 1);
	}

	private static JsonObject createDefaultData() {
		JsonObject root = new JsonObject();
		root.add("schedulers", new JsonArray());
		root.add("players", new JsonArray());
		return root;
	}

	private static JsonObject toPersistedData() {
		JsonObject root = new JsonObject();
		JsonArray schedulers = new JsonArray();
		for (Map.Entry<UUID, String> entry : PLAYER_SCHEDULER_IDS.entrySet()) {
			String schedulerId = entry.getValue();
			if (schedulerId == null || schedulerId.isBlank()) {
				continue;
			}
			JsonObject scheduler = new JsonObject();
			scheduler.addProperty("uuid", entry.getKey().toString());
			scheduler.addProperty("scheduler_id", schedulerId.trim());
			schedulers.add(scheduler);
		}
		root.add("schedulers", schedulers);
		JsonArray players = new JsonArray();
		for (Map.Entry<UUID, PlayerState> entry : PLAYER_STATES.entrySet()) {
			PlayerState state = entry.getValue();
			if (!state.hasPersistableState()) {
				continue;
			}

			JsonObject player = new JsonObject();
			player.addProperty("uuid", entry.getKey().toString());
			player.addProperty("pending_health", state.pendingHealth);
			player.addProperty("high_hunger_drain_active", state.highHungerDrainActive);
			player.addProperty("last_pending_activity_tick", Math.max(0L, state.lastPendingActivityTick));
			players.add(player);
		}
		root.add("players", players);
		return root;
	}

	private static void applyPersistedData(JsonObject source) {
		PLAYER_STATES.clear();
		PLAYER_SCHEDULER_IDS.clear();
		SCHEDULED_PLAYERS.clear();
		LAST_PROCESSED_TICKS_BY_PLAYER.clear();

		JsonArray schedulers = getArray(source, "schedulers");
		if (schedulers != null) {
			for (JsonElement element : schedulers) {
				if (element == null || !element.isJsonObject()) {
					continue;
				}
				JsonObject schedulerData = element.getAsJsonObject();
				UUID playerId = parseUuid(getString(schedulerData, "uuid", ""));
				String schedulerId = getString(schedulerData, "scheduler_id", "");
				if (playerId == null || schedulerId.isBlank()) {
					continue;
				}
				PLAYER_SCHEDULER_IDS.put(playerId, schedulerId);
			}
		}

		JsonArray players = getArray(source, "players");
		if (players == null) {
			return;
		}

		for (JsonElement element : players) {
			if (element == null || !element.isJsonObject()) {
				continue;
			}
			JsonObject playerData = element.getAsJsonObject();
			UUID playerId = parseUuid(getString(playerData, "uuid", ""));
			if (playerId == null) {
				continue;
			}

			PlayerState state = new PlayerState();
			state.pendingHealth = Math.max(0.0f, (float) getDouble(playerData, "pending_health", 0.0d));
			state.highHungerDrainActive = getBoolean(playerData, "high_hunger_drain_active", false);
			state.lastPendingActivityTick = Math.max(0L, getLong(playerData, "last_pending_activity_tick", 0L));
			PLAYER_STATES.put(playerId, state);
		}
	}

	private static void loadStaticConfig() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			var configFile = MadokuAttributes.prepareSystemConfigFile(HEALTH_CONFIG_DIRECTORY_NAME, HEALTH_CONFIG_FILE_NAME);
			JsonObject normalized = StaticJsonSystem.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			StaticJsonSystem.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			settings = configured.withEnabled(MadokuAttributes.isEnabled());
		} catch (IOException | RuntimeException exception) {
			settings = fallback.withEnabled(MadokuAttributes.isEnabled());
			LOGGER.error("Failed to load MadokuHealth static config; using defaults.", exception);
		}
	}

	private static String getString(JsonObject object, String key, String fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		String value = element.getAsString();
		return value == null ? fallback : value.trim();
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsLong();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static double getDouble(JsonObject object, String key, double fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsDouble();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		try {
			return element.getAsBoolean();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static JsonArray getArray(JsonObject object, String key) {
		if (object == null || key == null || key.isBlank()) {
			return null;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonArray()) {
			return null;
		}
		return element.getAsJsonArray();
	}

	private static UUID parseUuid(String rawValue) {
		if (rawValue == null || rawValue.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(rawValue.trim());
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static String formatFloat(float value) {
		return String.format("%.3f", value);
	}

	private static float quantizeHealth(float value) {
		if (value <= 0.0f) {
			return 0.0f;
		}
		return Math.round(value / HEALTH_ROUND_STEP) * HEALTH_ROUND_STEP;
	}

	private static final class PlayerState {
		private float pendingHealth;
		private boolean highHungerDrainActive;
		private long lastPendingActivityTick;
		private double appliedMaxHealthMultiplier = 1.0d;
		private double appliedHealthBoostAmount;
		private float appliedAbsorptionAmount;
		private boolean onlineThisSession;

		private boolean hasPersistableState() {
			return pendingHealth > EPSILON || highHungerDrainActive;
		}
	}

	private static final class Settings {
		private final boolean enabled;
		private final long schedulerTickInterval;
		private final int actionIntervalTicks;
		private final double maximumHealth;
		private final float highHungerStartRatio;
		private final float highHungerStopRatio;
		private final float lowHungerThresholdRatio;
		private final float lowHungerStepRatio;
		private final double maxHealthReductionPerStep;
		private final double minimumMaxHealthMultiplier;
		private final double pendingHealthPerHunger;
		private final double pendingHealthApplyAmount;
		private final long pendingIdleTimeoutTicks;

		private Settings(
			boolean enabled,
			long schedulerTickInterval,
			int actionIntervalTicks,
			double maximumHealth,
			float highHungerStartRatio,
			float highHungerStopRatio,
			float lowHungerThresholdRatio,
			float lowHungerStepRatio,
			double maxHealthReductionPerStep,
			double minimumMaxHealthMultiplier,
			double pendingHealthPerHunger,
			double pendingHealthApplyAmount,
			long pendingIdleTimeoutTicks
		) {
			this.enabled = enabled;
			this.schedulerTickInterval = schedulerTickInterval;
			this.actionIntervalTicks = actionIntervalTicks;
			this.maximumHealth = maximumHealth;
			this.highHungerStartRatio = highHungerStartRatio;
			this.highHungerStopRatio = highHungerStopRatio;
			this.lowHungerThresholdRatio = lowHungerThresholdRatio;
			this.lowHungerStepRatio = lowHungerStepRatio;
			this.maxHealthReductionPerStep = maxHealthReductionPerStep;
			this.minimumMaxHealthMultiplier = minimumMaxHealthMultiplier;
			this.pendingHealthPerHunger = pendingHealthPerHunger;
			this.pendingHealthApplyAmount = pendingHealthApplyAmount;
			this.pendingIdleTimeoutTicks = pendingIdleTimeoutTicks;
		}

		private static Settings defaults() {
			return new Settings(
				true,
				1L,
				10,
				20.0d,
				0.75f,
				0.75f,
				0.25f,
				0.05f,
				0.10d,
				0.50d,
				1.0d,
				1.0d,
				60L * 20L
			);
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();

			boolean enabled = getBoolean(source, "enabled", defaults.enabled);
			long schedulerTickInterval = clampLong(getLong(source, "scheduler_tick_interval", defaults.schedulerTickInterval), 1L, 20L);
			int actionIntervalTicks = (int) clampLong(getLong(source, "action_interval_ticks", defaults.actionIntervalTicks), 1L, 200L);
			double maximumHealth = clampDouble(getDouble(source, "maximum_health", defaults.maximumHealth), 1.0d, 1024.0d);
			float highHungerStartRatio = (float) clampDouble(getDouble(source, "high_hunger_start_ratio", defaults.highHungerStartRatio), 0.0d, 1.0d);
			float highHungerStopRatio = (float) clampDouble(getDouble(source, "high_hunger_stop_ratio", defaults.highHungerStopRatio), 0.0d, 1.0d);
			if (highHungerStopRatio > highHungerStartRatio) {
				highHungerStopRatio = highHungerStartRatio;
			}
			float lowHungerThresholdRatio = (float) clampDouble(getDouble(source, "low_hunger_threshold_ratio", defaults.lowHungerThresholdRatio), 0.0d, 1.0d);
			float lowHungerStepRatio = (float) clampDouble(getDouble(source, "low_hunger_step_ratio", defaults.lowHungerStepRatio), 0.01d, 1.0d);
			double maxHealthReductionPerStep = clampDouble(getDouble(source, "max_health_reduction_per_step", defaults.maxHealthReductionPerStep), 0.0d, 1.0d);
			double minimumMaxHealthMultiplier = clampDouble(getDouble(source, "minimum_max_health_multiplier", defaults.minimumMaxHealthMultiplier), 0.10d, 1.0d);
			double pendingHealthPerHunger = clampDouble(getDouble(source, "pending_health_per_hunger", defaults.pendingHealthPerHunger), 0.0d, 8.0d);
			double pendingHealthApplyAmount = clampDouble(getDouble(source, "pending_health_apply_amount", defaults.pendingHealthApplyAmount), 0.0d, 8.0d);
			long pendingIdleTimeoutTicks = clampLong(getLong(source, "pending_idle_timeout_ticks", defaults.pendingIdleTimeoutTicks), 20L, 20L * 60L * 10L);

			return new Settings(
				enabled,
				schedulerTickInterval,
				actionIntervalTicks,
				maximumHealth,
				highHungerStartRatio,
				highHungerStopRatio,
				lowHungerThresholdRatio,
				lowHungerStepRatio,
				maxHealthReductionPerStep,
				minimumMaxHealthMultiplier,
				pendingHealthPerHunger,
				pendingHealthApplyAmount,
				pendingIdleTimeoutTicks
			);
		}

		private JsonObject toConfigJson() {
			JsonObject root = new JsonObject();
			root.addProperty("enabled", enabled);
			root.addProperty("scheduler_tick_interval", schedulerTickInterval);
			root.addProperty("action_interval_ticks", actionIntervalTicks);
			root.addProperty("maximum_health", maximumHealth);
			root.addProperty("high_hunger_start_ratio", highHungerStartRatio);
			root.addProperty("high_hunger_stop_ratio", highHungerStopRatio);
			root.addProperty("low_hunger_threshold_ratio", lowHungerThresholdRatio);
			root.addProperty("low_hunger_step_ratio", lowHungerStepRatio);
			root.addProperty("max_health_reduction_per_step", maxHealthReductionPerStep);
			root.addProperty("minimum_max_health_multiplier", minimumMaxHealthMultiplier);
			root.addProperty("pending_health_per_hunger", pendingHealthPerHunger);
			root.addProperty("pending_health_apply_amount", pendingHealthApplyAmount);
			root.addProperty("pending_idle_timeout_ticks", pendingIdleTimeoutTicks);
			return root;
		}

		private Settings withEnabled(boolean attributesEnabled) {
			return new Settings(
				attributesEnabled && enabled,
				schedulerTickInterval,
				actionIntervalTicks,
				maximumHealth,
				highHungerStartRatio,
				highHungerStopRatio,
				lowHungerThresholdRatio,
				lowHungerStepRatio,
				maxHealthReductionPerStep,
				minimumMaxHealthMultiplier,
				pendingHealthPerHunger,
				pendingHealthApplyAmount,
				pendingIdleTimeoutTicks
			);
		}

		private static long clampLong(long value, long min, long max) {
			return Math.max(min, Math.min(max, value));
		}

		private static double clampDouble(double value, double min, double max) {
			return Math.max(min, Math.min(max, value));
		}
	}
}
