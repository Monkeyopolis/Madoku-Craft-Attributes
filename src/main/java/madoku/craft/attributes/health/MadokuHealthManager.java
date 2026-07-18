package madoku.craft.attributes.health;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import madoku.craft.attributes.MadokuCraftAttributes;
import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.data.DataPlayerManager;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.attributes.hunger.MadokuHungerManager;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MadokuHealthManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuHealthManager.class);
	private static final float EPSILON = 1.0e-4f;
	private static final float HEALTH_ROUND_STEP = 0.125f;
	private static final int VANILLA_MAX_HUNGER_POINTS = 20;

	private static final String DATA_FILE_NAME = "madoku-health";
	private static final String TASK_TYPE_HEALTH_PLAYER_TICK = "health_player_tick";
	private static final String HEALTH_PLAYER_TICK_SCHEDULER_KEY = "health_player_tick";
	private static final long HEALTH_PLAYER_TICK_MIN_INTERVAL = 1L;
	private static final long HEALTH_PLAYER_TICK_MAX_INTERVAL = 5L;
	private static final int ACTION_INTERVAL_TICKS = 10;
	private static final long POISON_TICK_INTERVAL = 10L;
	private static final long WITHER_TICK_INTERVAL = 20L;
	private static final long REGEN_TICK_INTERVAL = 20L;
	private static final long PENDING_IDLE_TIMEOUT_TICKS = 1500L;
	private static final Identifier LOW_HUNGER_MAX_HEALTH_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraftAttributes.MOD_ID, "madoku_health_low_hunger_max_health");
	private static final Identifier HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraftAttributes.MOD_ID, "madoku_health_health_boost_max_health");
	private static final float POISON_MIN_HEALTH = 1.0f;
	private static final float LOW_HUNGER_STEP_RATIO = 0.05f;
	private static final double MAX_HEALTH_REDUCTION_PER_STEP = 0.10d;
	private static final double PENDING_HEALTH_PER_HUNGER = 1.0d;
	private static final double PENDING_HEALTH_APPLY_AMOUNT = 1.0d;

	private static final Map<UUID, PlayerState> PLAYER_STATES = new HashMap<>();
	private static volatile HealthConfigManager.Settings settings = HealthConfigManager.Settings.defaults();
	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile String schedulerId = "";
	private static volatile boolean tickQueued;

	private MadokuHealthManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		MadokuSchedulerManager.registerTaskHandler(TASK_TYPE_HEALTH_PLAYER_TICK, MadokuHealthManager::runPlayerTickTask);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MadokuHealthManager::handleAfterPlayerDamage);
		ServerPlayerEvents.JOIN.register(MadokuHealthManager::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register(MadokuHealthManager::handlePlayerRespawn);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler == null || handler.player == null) {
				return;
			}
			capturePlayerHealth(handler.player);

		});
	}

	public static boolean isEnabled() {
		return settings.health.enabled;
	}

	public static void reset() {
		PLAYER_STATES.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
		schedulerId = "";
		tickQueued = false;
		MadokuSchedulerManager.clearAdaptiveDelayState(HEALTH_PLAYER_TICK_SCHEDULER_KEY);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		loadStaticConfig();
		JsonObject data = DataPlayerManager.getSystemData(DATA_FILE_NAME);
		applyPersistedData(data);
		long autoSaveIntervalTicks = DataPlayerManager.getAutoSaveIntervalTicks();
		lastAutosaveBucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
		emitHealthSystemDebug(
			"health.persisted_state_loaded",
			Map.of(
				"player_states", Integer.toString(PLAYER_STATES.size()),
				"online_players", Integer.toString(server.getPlayerList().getPlayers().size()),
				"health_enabled", Boolean.toString(settings.health.enabled),
				"health_boost_enabled", Boolean.toString(settings.main.healthBoost.enabled),
				"absorption_enabled", Boolean.toString(settings.main.absorption.enabled)
			)
		);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long autoSaveIntervalTicks = DataPlayerManager.getAutoSaveIntervalTicks();
		long bucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
		if (bucket != lastAutosaveBucket) {
			lastAutosaveBucket = bucket;
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		syncTrackedPlayerHealth(server);
		DataPlayerManager.setSystemData(DATA_FILE_NAME, toPersistedData());
	}

	public static void onServerStarted(MinecraftServer server) {
		ensureQueued(server);
	}

	private static void runPlayerTickTask(MinecraftServer server, MadokuSchedulerManager.TaskContext context, JsonObject payload) {
		tickQueued = false;
		if (server == null || context == null) {
			return;
		}

		schedulerId = context.getSchedulerId();
		long gameplayTick = Math.max(0L, context.getNowTick());
		onGameplayTick(server, gameplayTick);
		ensureQueued(server);
	}

	private static void ensureQueued(MinecraftServer server) {
		if (server == null || tickQueued) {
			return;
		}

		String currentSchedulerId = ensureScheduler();

		long delayTicks = MadokuSchedulerManager.resolveAdaptiveDelayTicks(
			server,
			HEALTH_PLAYER_TICK_SCHEDULER_KEY,
			HEALTH_PLAYER_TICK_MIN_INTERVAL,
			HEALTH_PLAYER_TICK_MAX_INTERVAL
		);
		if (MadokuSchedulerManager.hasQueuedTask(currentSchedulerId, TASK_TYPE_HEALTH_PLAYER_TICK)) {
			tickQueued = true;
			return;
		}
		if (enqueue(currentSchedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		schedulerId = MadokuSchedulerManager.createOrGetScheduler(
			MadokuSchedulerManager.SchedulerBinding.global(HEALTH_PLAYER_TICK_SCHEDULER_KEY)
		);
		if (enqueue(schedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		LOGGER.error("Failed to enqueue health player tick task.");
	}

	private static String ensureScheduler() {
		String current = schedulerId;
		if (current != null && !current.isBlank()) {
			return current;
		}
		schedulerId = MadokuSchedulerManager.createOrGetScheduler(
			MadokuSchedulerManager.SchedulerBinding.global(HEALTH_PLAYER_TICK_SCHEDULER_KEY)
		);
		return schedulerId;
	}

	private static boolean enqueue(String targetSchedulerId, long delayTicks) {
		if (targetSchedulerId == null || targetSchedulerId.isBlank()) {
			return false;
		}
		MadokuSchedulerManager.EnqueueStatus status = MadokuSchedulerManager.enqueue(
			targetSchedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_HEALTH_PLAYER_TICK,
			new JsonObject(),
			MadokuSchedulerManager.TickDomain.GAMEPLAY
		);
		return status == MadokuSchedulerManager.EnqueueStatus.ACCEPTED
			|| status == MadokuSchedulerManager.EnqueueStatus.QUEUE_FULL;
	}

	private static void onGameplayTick(MinecraftServer server, long gameplayTick) {
		if (server == null) {
			return;
		}
		if (!settings.health.enabled) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			processPlayer(player, gameplayTick);
		}
	}

	private static void processPlayer(ServerPlayer player, long gameplayTick) {
		if (player == null) {
			return;
		}

		UUID playerId = player.getUUID();
		PlayerState state = PLAYER_STATES.computeIfAbsent(playerId, ignored -> new PlayerState());
		if (!state.onlineThisSession) {
			state.onlineThisSession = true;
			state.lastPendingActivityTick = gameplayTick;
		}
		long elapsedTicks = consumeElapsedTicks(state, gameplayTick);
		if (!player.isAlive() || player.isDeadOrDying()) {
			// Never run the heal/drain loop while the player is dead; this can interfere with respawn.
			state.pendingHealth = 0.0f;

			state.highHungerDrainActive = false;
			return;
		}

		applyLowHungerMaxHealthScaling(player, state, gameplayTick);
		applyHealthBoostScaling(player, state, gameplayTick);
		applyAbsorptionScaling(player, state, gameplayTick);
		processStatusEffects(player, state, gameplayTick, elapsedTicks);
		processPendingHealthCycles(player, state, gameplayTick, elapsedTicks);
		clearIdlePendingHealth(state, playerId, gameplayTick);
		state.savedHealth = player.getHealth();
	}

	private static void processStatusEffects(ServerPlayer player, PlayerState state, long gameplayTick, long elapsedTicks) {
		if (player == null) {
			return;
		}

		if (settings.main.poison.enabled) {
			int poisonLevel = getEffectLevel(player, MobEffects.POISON);
			if (poisonLevel > 0) {
				state.poisonProgressTicks = accumulateProgressTicks(state.poisonProgressTicks, elapsedTicks);
				while (state.poisonProgressTicks >= POISON_TICK_INTERVAL) {
					state.poisonProgressTicks -= POISON_TICK_INTERVAL;
					applyPoisonTick(player, gameplayTick, poisonLevel);
				}
			} else {
				state.poisonProgressTicks = 0L;
			}
		}

		if (settings.main.wither.enabled) {
			int witherLevel = getEffectLevel(player, MobEffects.WITHER);
			if (witherLevel > 0) {
				state.witherProgressTicks = accumulateProgressTicks(state.witherProgressTicks, elapsedTicks);
				while (state.witherProgressTicks >= WITHER_TICK_INTERVAL) {
					state.witherProgressTicks -= WITHER_TICK_INTERVAL;
					applyWitherTick(player, gameplayTick, witherLevel);
				}
			} else {
				state.witherProgressTicks = 0L;
			}
		}

		if (settings.main.regeneration.enabled) {
			int regenerationLevel = getEffectLevel(player, MobEffects.REGENERATION);
			if (regenerationLevel > 0) {
				state.regenerationProgressTicks = accumulateProgressTicks(state.regenerationProgressTicks, elapsedTicks);
				while (state.regenerationProgressTicks >= REGEN_TICK_INTERVAL) {
					state.regenerationProgressTicks -= REGEN_TICK_INTERVAL;
					applyRegenerationTick(player, state, gameplayTick, regenerationLevel);
				}
			} else {
				state.regenerationProgressTicks = 0L;
			}
		}
	}

	private static void processPendingHealthCycles(ServerPlayer player, PlayerState state, long gameplayTick, long elapsedTicks) {
		if (player == null || state == null) {
			return;
		}

		state.actionProgressTicks = accumulateProgressTicks(state.actionProgressTicks, elapsedTicks);
		while (state.actionProgressTicks >= ACTION_INTERVAL_TICKS) {
			state.actionProgressTicks -= ACTION_INTERVAL_TICKS;
			collectPendingHealthFromHunger(player, state, gameplayTick);
			applyPendingHealth(player, state, gameplayTick);
		}
	}

	private static void applyPoisonTick(ServerPlayer player, long gameplayTick, int poisonLevel) {
		float current = player.getHealth();
		float maxHealth = player.getMaxHealth();
		if (maxHealth <= EPSILON) {
			return;
		}

		if (settings.main.poison.poisonPenalty.enabled
			&& current < (float) (maxHealth * settings.main.poison.poisonPenalty.penaltyPercentage) - EPSILON) {

			return;
		}

		float damage = (float) resolveEffectAmount(maxHealth, settings.main.poison, poisonLevel);
		if (damage <= EPSILON) {
			return;
		}

		float target = quantizeHealth(Math.max(POISON_MIN_HEALTH, current - damage));
		if (target >= current - EPSILON) {
			return;
		}

		player.setHealth(target);
		String debugEntry = MadokuDebugManager.resolveCallerMethodName(0);
		if (MadokuDebugManager.shouldEmit("attributes", "health", debugEntry)) {
			MadokuDebugManager.event("health.effect_poison_tick", "attributes", "health", debugEntry)
				.side(MadokuDebugManager.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("health_before", formatFloat(current))
				.field("health_after", formatFloat(player.getHealth()))
				.field("max_health", formatFloat(maxHealth))
				.field("health_ratio", Double.toString(maxHealth <= EPSILON ? 0.0d : (double) current / (double) maxHealth))
				.field("penalty_threshold", Double.toString(settings.main.poison.poisonPenalty.penaltyPercentage))
				.field("level", poisonLevel)
				.field("damage", formatFloat(damage))
				.log();
		}
	}

	private static void applyWitherTick(ServerPlayer player, long gameplayTick, int witherLevel) {
		float healthBefore = player.getHealth();
		float damage = (float) resolveEffectAmount(player.getMaxHealth(), settings.main.wither, witherLevel);
		if (damage <= EPSILON) {
			return;
		}
		player.hurtServer(player.level(), player.damageSources().wither(), damage);
		String debugEntry = MadokuDebugManager.resolveCallerMethodName(0);
		if (MadokuDebugManager.shouldEmit("attributes", "health", debugEntry)) {
			MadokuDebugManager.event("health.effect_wither_tick", "attributes", "health", debugEntry)
				.side(MadokuDebugManager.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("health_before", formatFloat(healthBefore))
				.field("health_after", formatFloat(player.getHealth()))
				.field("max_health", formatFloat(player.getMaxHealth()))
				.field("level", witherLevel)
				.field("damage", formatFloat(damage))
				.log();
		}
	}

	private static void applyRegenerationTick(ServerPlayer player, PlayerState state, long gameplayTick, int regenerationLevel) {
		float maxHealth = player.getMaxHealth();
		float current = player.getHealth();
		if (current >= maxHealth - EPSILON) {
			return;
		}

		float healing = (float) resolveEffectAmount(maxHealth, settings.main.regeneration, regenerationLevel);
		if (healing <= EPSILON) {
			return;
		}

		float target = quantizeHealth(Math.min(maxHealth, current + healing));
		if (target <= current + EPSILON) {
			return;
		}

		player.setHealth(target);
		state.lastPendingActivityTick = gameplayTick;

		String debugEntry = MadokuDebugManager.resolveCallerMethodName(0);
		if (MadokuDebugManager.shouldEmit("attributes", "health", debugEntry)) {
			MadokuDebugManager.event("health.effect_regeneration_tick", "attributes", "health", debugEntry)
				.side(MadokuDebugManager.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("health_before", formatFloat(current))

				.field("health_after", formatFloat(player.getHealth()))
				.field("max_health", formatFloat(maxHealth))
				.field("healed", formatFloat(target - current))
				.log();
		}
	}

	private static void collectPendingHealthFromHunger(ServerPlayer player, PlayerState state, long gameplayTick) {
		float pendingBefore = state.pendingHealth;
		float missingHealth = player.getMaxHealth() - player.getHealth();
		float hungerRatio = hungerRatio(player);
		boolean hasRecoveryNeed = missingHealth > EPSILON;

		if (hungerRatio > settings.health.hungerDrainPercentage && hasRecoveryNeed) {
			state.highHungerDrainActive = true;
		}
		if (hungerRatio <= settings.health.hungerDrainPercentage || !hasRecoveryNeed) {
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

		state.pendingHealth += drained * (float) PENDING_HEALTH_PER_HUNGER;
		state.lastPendingActivityTick = gameplayTick;
		if (hungerRatio(player) <= settings.health.hungerDrainPercentage) {
			state.highHungerDrainActive = false;
		}

		String debugEntry = MadokuDebugManager.resolveCallerMethodName(0);
		if (MadokuDebugManager.shouldEmit("attributes", "health", debugEntry)) {
			MadokuDebugManager.event("health.pending_collected", "attributes", "health", debugEntry)
				.side(MadokuDebugManager.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("health_before", formatFloat(player.getHealth()))
				.field("max_health", formatFloat(player.getMaxHealth()))
				.field("missing_health", formatFloat(missingHealth))
				.field("hunger_ratio", formatFloat(hungerRatio))
				.field("drain_threshold", Double.toString(settings.health.hungerDrainPercentage))
				.field("high_hunger_drain_active", Boolean.toString(state.highHungerDrainActive))
				.field("pending_health_before", formatFloat(pendingBefore))
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

		float healing = Math.min((float) PENDING_HEALTH_APPLY_AMOUNT, Math.min(state.pendingHealth, missingHealth));
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

		float healthBefore = player.getHealth();
		float pendingBefore = state.pendingHealth;
		player.setHealth(targetHealth);

		state.pendingHealth -= appliedHealing;
		if (state.pendingHealth < EPSILON) {
			state.pendingHealth = 0.0f;
		}
		state.lastPendingActivityTick = gameplayTick;

		String debugEntry = MadokuDebugManager.resolveCallerMethodName(0);
		if (MadokuDebugManager.shouldEmit("attributes", "health", debugEntry)) {
			MadokuDebugManager.event("health.pending_applied", "attributes", "health", debugEntry)
				.side(MadokuDebugManager.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("health_before", formatFloat(healthBefore))
				.field("health_after", formatFloat(player.getHealth()))
				.field("max_health", formatFloat(player.getMaxHealth()))
				.field("pending_health_before", formatFloat(pendingBefore))
				.field("pending_health_after", formatFloat(state.pendingHealth))
				.field("healed", formatFloat(appliedHealing))
				.log();
		}
	}

	private static void clearIdlePendingHealth(PlayerState state, UUID playerId, long gameplayTick) {
		if (state.pendingHealth <= EPSILON) {
			return;
		}

		long idleTicks = gameplayTick - state.lastPendingActivityTick;
		if (idleTicks < PENDING_IDLE_TIMEOUT_TICKS) {
			return;
		}

		float pendingBefore = state.pendingHealth;
		state.pendingHealth = 0.0f;
		state.highHungerDrainActive = false;
		state.lastPendingActivityTick = gameplayTick;

		String debugEntry = MadokuDebugManager.resolveCallerMethodName(0);
		if (MadokuDebugManager.shouldEmit("attributes", "health", debugEntry)) {
			MadokuDebugManager.event("health.pending_idle_cleared", "attributes", "health", debugEntry)
				.side(MadokuDebugManager.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + playerId)
				.field("idle_ticks", Long.toString(idleTicks))
				.field("timeout_ticks", Long.toString(PENDING_IDLE_TIMEOUT_TICKS))
				.field("pending_before", formatFloat(pendingBefore))
				.field("pending_after", formatFloat(state.pendingHealth))
				.log();
		}
	}

	private static void applyLowHungerMaxHealthScaling(ServerPlayer player, PlayerState state, long gameplayTick) {
		AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealthAttribute == null) {
			return;
		}

		double targetBaseMaxHealth = settings.health.maximumHealth;
		if (Math.abs(maxHealthAttribute.getBaseValue() - targetBaseMaxHealth) > 1.0e-5d) {
			maxHealthAttribute.setBaseValue(targetBaseMaxHealth);
		}

		double targetMultiplier = calculateMaxHealthMultiplier(hungerRatio(player));
		if (Math.abs(targetMultiplier - state.appliedMaxHealthMultiplier) > 1.0e-5d) {
			double previousMultiplier = state.appliedMaxHealthMultiplier;
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
			String debugEntry = MadokuDebugManager.resolveCallerMethodName(0);
			if (MadokuDebugManager.shouldEmit("attributes", "health", debugEntry)) {

				MadokuDebugManager.event("health.max_health_scaled", "attributes", "health", debugEntry)
					.side(MadokuDebugManager.Side.SERVER)
					.tick(gameplayTick)
					.subject("player:" + player.getUUID())
					.field("base_max_health", Double.toString(targetBaseMaxHealth))
					.field("current_health", formatFloat(player.getHealth()))
					.field("hunger_ratio", Double.toString(hungerRatio(player)))
					.field("threshold", Double.toString(settings.health.healthPenalty.hungerPenaltyPercentage))
					.field("previous_multiplier", String.format("%.3f", previousMultiplier))
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

		if (!settings.main.healthBoost.enabled) {
			if (Math.abs(state.appliedHealthBoostAmount) > EPSILON || maxHealthAttribute.hasModifier(HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID)) {
				maxHealthAttribute.removeModifier(HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID);
				state.appliedHealthBoostAmount = 0.0d;
			}
			float maxHealth = player.getMaxHealth();
			if (player.getHealth() > maxHealth + EPSILON) {
				player.setHealth(Math.min(maxHealth, quantizeHealth(maxHealth)));
			}
			return;
		}

		int healthBoostLevel = getEffectLevel(player, MobEffects.HEALTH_BOOST);
		double referenceMaxHealth = player.getMaxHealth() - state.appliedHealthBoostAmount;
		if (referenceMaxHealth < 0.0d) {
			referenceMaxHealth = player.getMaxHealth();
		}
		double targetAmount = resolveEffectAmount(referenceMaxHealth, settings.main.healthBoost, healthBoostLevel);
		double previousBonusAmount = state.appliedHealthBoostAmount;

		boolean modifierChanged = false;
		boolean missingExpectedModifier = targetAmount > 1.0e-5d && !maxHealthAttribute.hasModifier(HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID);
		if (Math.abs(targetAmount - previousBonusAmount) > 1.0e-5d || missingExpectedModifier) {
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

		if (modifierChanged) {
			String debugEntry = MadokuDebugManager.resolveCallerMethodName(0);
			if (MadokuDebugManager.shouldEmit("attributes", "health", debugEntry)) {
				MadokuDebugManager.event("health.effect_health_boost_scaled", "attributes", "health", debugEntry)
				.side(MadokuDebugManager.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("effect_level", healthBoostLevel)
				.field("reference_max_health", Double.toString(referenceMaxHealth))
				.field("current_max_health", formatFloat(player.getMaxHealth()))
				.field("previous_bonus_amount", String.format("%.3f", previousBonusAmount))
				.field("bonus_amount", String.format("%.3f", state.appliedHealthBoostAmount))

				.log();
			}
		}
	}

	private static void applyAbsorptionScaling(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (!settings.main.absorption.enabled) {
			if (Math.abs(state.appliedAbsorptionAmount) > EPSILON) {
				player.setAbsorptionAmount(0.0f);
				state.appliedAbsorptionAmount = 0.0f;
			}
			return;
		}

		int absorptionLevel = getEffectLevel(player, MobEffects.ABSORPTION);
		float targetAbsorption = quantizeHealth((float) resolveEffectAmount(player.getMaxHealth(), settings.main.absorption, absorptionLevel));
		float previousAbsorption = state.appliedAbsorptionAmount;

		if (Math.abs(targetAbsorption - previousAbsorption) <= EPSILON) {
			return;
		}

		player.setAbsorptionAmount(targetAbsorption);
		state.appliedAbsorptionAmount = targetAbsorption;
		String debugEntry = MadokuDebugManager.resolveCallerMethodName(0);
		if (MadokuDebugManager.shouldEmit("attributes", "health", debugEntry)) {
			MadokuDebugManager.event("health.effect_absorption_scaled", "attributes", "health", debugEntry)
				.side(MadokuDebugManager.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("effect_level", absorptionLevel)
				.field("current_max_health", formatFloat(player.getMaxHealth()))
				.field("previous_absorption", formatFloat(previousAbsorption))
				.field("absorption", formatFloat(targetAbsorption))
				.log();
		}
	}

	private static int drainFood(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return 0;
		}
		if (MadokuHungerManager.isEnabled()) {
			return MadokuHungerManager.drainHunger(player, amount);
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
		HealthConfigManager.HealthPenaltySettings penalty = settings.health.healthPenalty;
		if (!penalty.enabled || hungerRatio >= settings.health.healthPenalty.hungerPenaltyPercentage) {
			return 1.0d;
		}

		double belowThreshold = settings.health.healthPenalty.hungerPenaltyPercentage - hungerRatio;
		int steps = (int) Math.ceil((belowThreshold - EPSILON) / LOW_HUNGER_STEP_RATIO);
		steps = Math.max(0, steps);
		double reduction = steps * MAX_HEALTH_REDUCTION_PER_STEP;
		double multiplier = 1.0d - reduction;
		if (multiplier < penalty.penaltyPercentage) {
			return penalty.penaltyPercentage;
		}
		return Math.min(1.0d, multiplier);
	}

	private static float hungerRatio(ServerPlayer player) {
		if (player == null) {
			return 0.0f;
		}


		if (MadokuHungerManager.isEnabled()) {
			int maxHungerPoints = Math.max(1, MadokuHungerManager.getMaximumHungerPoints(player));
			int hungerPoints = Math.max(0, Math.min(maxHungerPoints, MadokuHungerManager.getEffectiveHungerPoints(player)));
			return (float) hungerPoints / (float) maxHungerPoints;
		}

		int clamped = Math.max(0, Math.min(VANILLA_MAX_HUNGER_POINTS, player.getFoodData().getFoodLevel()));
		return (float) clamped / (float) VANILLA_MAX_HUNGER_POINTS;
	}

	private static void handlePlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null || alive || !settings.health.enabled) {
			return;
		}

		float healthBefore = newPlayer.getHealth();
		float targetHealth = Math.max(0.5f, newPlayer.getMaxHealth() * settings.health.respawnHealthPercentage);
		targetHealth = Math.min(newPlayer.getMaxHealth(), quantizeHealth(targetHealth));
		newPlayer.setHealth(targetHealth);

		PlayerState state = PLAYER_STATES.computeIfAbsent(newPlayer.getUUID(), ignored -> new PlayerState());
		state.pendingHealth = 0.0f;
		state.highHungerDrainActive = newPlayer.getHealth() + EPSILON < newPlayer.getMaxHealth();
		state.savedHealth = quantizeHealth(Math.max(0.0f, newPlayer.getHealth()));
		state.lastPendingActivityTick = MadokuTimeManager.getGameplayTicks();
		state.lastProcessedGameplayTick = MadokuTimeManager.getGameplayTicks();
		state.actionProgressTicks = 0L;
		state.poisonProgressTicks = 0L;
		state.witherProgressTicks = 0L;
		state.regenerationProgressTicks = 0L;
		state.appliedMaxHealthMultiplier = 1.0d;
		state.appliedHealthBoostAmount = 0.0d;
		state.appliedAbsorptionAmount = 0.0f;
		state.onlineThisSession = true;
		emitHealthSystemDebug(
			"health.player_respawn_snapshot",
			Map.of(
				"player", newPlayer.getScoreboardName(),
				"health", formatFloat(newPlayer.getHealth()),
				"max_health", formatFloat(newPlayer.getMaxHealth()),
				"saved_health", formatFloat(state.savedHealth),
				"pending_health", formatFloat(state.pendingHealth),
				"high_hunger_drain_active", Boolean.toString(state.highHungerDrainActive)
			)
		);

		String debugEntry = MadokuDebugManager.resolveCallerMethodName(0);
		if (MadokuDebugManager.shouldEmit("attributes", "health", debugEntry)) {
			MadokuDebugManager.event("health.respawn_half_health", "attributes", "health", debugEntry)
				.side(MadokuDebugManager.Side.SERVER)
				.tick(MadokuTimeManager.getGameplayTicks())
				.subject("player:" + newPlayer.getUUID())
				.field("respawn_percentage", Float.toString(settings.health.respawnHealthPercentage))
				.field("health_before", formatFloat(healthBefore))
				.field("health_after", formatFloat(newPlayer.getHealth()))
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
		if (!(entity instanceof ServerPlayer player) || damageTaken <= EPSILON || !settings.health.enabled) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.onlineThisSession = true;
		state.lastPendingActivityTick = MadokuTimeManager.getGameplayTicks();
		state.lastProcessedGameplayTick = MadokuTimeManager.getGameplayTicks();
		state.highHungerDrainActive = player.getHealth() + EPSILON < player.getMaxHealth();

		String debugEntry = MadokuDebugManager.resolveCallerMethodName(0);

		if (MadokuDebugManager.shouldEmit("attributes", "health", debugEntry)) {
			MadokuDebugManager.event("health.damage_detected", "attributes", "health", debugEntry)
				.side(MadokuDebugManager.Side.SERVER)
				.tick(MadokuTimeManager.getGameplayTicks())
				.subject("player:" + player.getUUID())
				.field("damage_source", source == null ? "unknown" : source.toString())
				.field("health_after", formatFloat(player.getHealth()))
				.field("max_health", formatFloat(player.getMaxHealth()))
				.field("high_hunger_drain_active", Boolean.toString(state.highHungerDrainActive))
				.field("damage_taken", formatFloat(damageTaken))
				.field("blocked", blocked)
				.log();
		}
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null || !settings.health.enabled) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.onlineThisSession = true;
		state.lastPendingActivityTick = MadokuTimeManager.getGameplayTicks();
		state.lastProcessedGameplayTick = MadokuTimeManager.getGameplayTicks();
		state.highHungerDrainActive = player.getHealth() + EPSILON < player.getMaxHealth();
		applyImmediateEffectOverrides(player, state, MadokuTimeManager.getGameplayTicks());
		emitHealthSystemDebug(
			"health.player_join_snapshot",
			Map.of(
				"player", player.getScoreboardName(),
				"health", formatFloat(player.getHealth()),
				"max_health", formatFloat(player.getMaxHealth()),
				"pending_health", formatFloat(state.pendingHealth),
				"high_hunger_drain_active", Boolean.toString(state.highHungerDrainActive)
			)
		);
	}

	public static void handlePlayerEffectsChanged(ServerPlayer player) {
		if (player == null || !settings.health.enabled) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.onlineThisSession = true;
		long gameplayTick = MadokuTimeManager.getGameplayTicks();
		state.lastPendingActivityTick = gameplayTick;
		state.lastProcessedGameplayTick = gameplayTick;
		applyImmediateEffectOverrides(player, state, gameplayTick);
	}

	public static boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) {
		if (!settings.health.enabled || !(entity instanceof ServerPlayer) || effect == null) {
			return false;
		}

		return (settings.main.poison.enabled && effect == MobEffects.POISON.value())
			|| (settings.main.wither.enabled && effect == MobEffects.WITHER.value())
			|| (settings.main.regeneration.enabled && effect == MobEffects.REGENERATION.value())
			|| (settings.main.absorption.enabled && effect == MobEffects.ABSORPTION.value());
	}

	public static boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) {
		if (!settings.health.enabled || !(entity instanceof ServerPlayer) || effect == null) {
			return false;
		}

		return (settings.main.healthBoost.enabled && effect == MobEffects.HEALTH_BOOST.value())
			|| (settings.main.absorption.enabled && effect == MobEffects.ABSORPTION.value());
	}

	public static void restoreJoinHealth(ServerPlayer player) {
		if (player == null || !settings.health.enabled) {
			return;
		}

		PlayerState state = PLAYER_STATES.get(player.getUUID());
		if (state == null || state.savedHealth < 0.0f) {
			return;
		}


		float targetHealth = quantizeHealth(Math.min(player.getMaxHealth(), state.savedHealth));
		if (Math.abs(targetHealth - player.getHealth()) > EPSILON) {
			player.setHealth(targetHealth);
			emitHealthSystemDebug(
				"health.join_health_restored",
				Map.of(
					"player", player.getScoreboardName(),
					"saved_health", formatFloat(state.savedHealth),
					"restored_health", formatFloat(targetHealth),
					"max_health", formatFloat(player.getMaxHealth())
				)
			);
		}
	}

	private static void applyImmediateEffectOverrides(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (!settings.health.enabled || player == null || state == null) {
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

	private static double resolveEffectAmount(double maxHealth, HealthConfigManager.EffectSettings effect, int level) {
		if (effect == null || level <= 0) {
			return 0.0d;
		}

		double perLevelAmount = effect.type == HealthConfigManager.ValueType.FLAT
			? effect.value
			: maxHealth * effect.value;
		return perLevelAmount * Math.max(1, level);
	}

	private static JsonObject toPersistedData() {
		JSONFormatManager.ArrayBuilder players = JSONFormatManager.array();
		for (Map.Entry<UUID, PlayerState> entry : PLAYER_STATES.entrySet()) {
			PlayerState state = entry.getValue();
			if (!state.hasPersistableState()) {
				continue;
			}

			players.object(player -> player
				.put("uuid", entry.getKey().toString())
				.put("pending-health", state.pendingHealth)
				.put("current-health", state.savedHealth)
				.put("high-hunger-drain-active", state.highHungerDrainActive)
				.put("last-pending-activity-tick", Math.max(0L, state.lastPendingActivityTick))
				.put("action-progress-ticks", Math.max(0L, state.actionProgressTicks))
				.put("poison-progress-ticks", Math.max(0L, state.poisonProgressTicks))
				.put("wither-progress-ticks", Math.max(0L, state.witherProgressTicks))
				.put("regeneration-progress-ticks", Math.max(0L, state.regenerationProgressTicks)));
		}
		return JSONFormatManager.object()
			.put("players", players.build())
			.build();
	}

	private static void applyPersistedData(JsonObject source) {
		PLAYER_STATES.clear();

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
			state.pendingHealth = Math.max(0.0f, (float) getDouble(playerData, "pending-health", 0.0d));
			double savedHealth = getDouble(playerData, "current-health", Double.NaN);
			state.savedHealth = Double.isNaN(savedHealth) ? -1.0f : Math.max(0.0f, (float) savedHealth);
			state.highHungerDrainActive = getBoolean(playerData, "high-hunger-drain-active", false);
			state.lastPendingActivityTick = Math.max(0L, getLong(playerData, "last-pending-activity-tick", 0L));
			state.actionProgressTicks = Math.max(0L, getLong(playerData, "action-progress-ticks", 0L));
			state.poisonProgressTicks = Math.max(0L, getLong(playerData, "poison-progress-ticks", 0L));
			state.witherProgressTicks = Math.max(0L, getLong(playerData, "wither-progress-ticks", 0L));
			state.regenerationProgressTicks = Math.max(0L, getLong(playerData, "regeneration-progress-ticks", 0L));
			PLAYER_STATES.put(playerId, state);
		}
	}

	private static long consumeElapsedTicks(PlayerState state, long gameplayTick) {
		if (state == null) {
			return 0L;
		}

		long previousTick = state.lastProcessedGameplayTick;
		state.lastProcessedGameplayTick = gameplayTick;
		if (previousTick == Long.MIN_VALUE || gameplayTick <= previousTick) {
			return 0L;
		}
		return gameplayTick - previousTick;
	}

	private static long accumulateProgressTicks(long currentProgressTicks, long elapsedTicks) {
		if (elapsedTicks <= 0L) {
			return Math.max(0L, currentProgressTicks);
		}
		long safeCurrent = Math.max(0L, currentProgressTicks);
		if (safeCurrent >= Long.MAX_VALUE - elapsedTicks) {
			return Long.MAX_VALUE;
		}
		return safeCurrent + elapsedTicks;
	}

	private static void syncTrackedPlayerHealth(MinecraftServer server) {
		if (server == null) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			capturePlayerHealth(player);
		}
	}

	private static void capturePlayerHealth(ServerPlayer player) {
		if (player == null) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.savedHealth = quantizeHealth(Math.max(0.0f, player.getHealth()));
	}

	private static void loadStaticConfig() {
		settings = HealthConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
	}

	private static void emitHealthSystemDebug(String metricId, Map<String, String> fields) {
		if (metricId == null || metricId.isBlank()) {
			return;
		}
		String entry = MadokuDebugManager.resolveCallerMethodName();
		if (!MadokuDebugManager.shouldEmit("attributes", "health", entry)) {
			return;
		}

		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(metricId, "attributes", "health", entry)

			.side(MadokuDebugManager.Side.SERVER)
			.tick(MadokuTimeManager.getGameplayTicks())
			.subject("server");
		if (fields != null) {
			for (Map.Entry<String, String> fieldEntry : fields.entrySet()) {
				if (fieldEntry != null) {
					builder.field(fieldEntry.getKey(), fieldEntry.getValue());
				}
			}
		}
		builder.log();
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
		private float savedHealth = -1.0f;
		private long lastPendingActivityTick;
		private long lastProcessedGameplayTick = Long.MIN_VALUE;
		private long actionProgressTicks;
		private long poisonProgressTicks;
		private long witherProgressTicks;
		private long regenerationProgressTicks;
		private double appliedMaxHealthMultiplier = 1.0d;
		private double appliedHealthBoostAmount;
		private float appliedAbsorptionAmount;
		private boolean onlineThisSession;

		private boolean hasPersistableState() {
			return pendingHealth > EPSILON || highHungerDrainActive || savedHealth >= 0.0f;
		}
	}

}
