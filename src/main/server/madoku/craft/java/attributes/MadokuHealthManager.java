package madoku.craft.java.attributes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import madoku.craft.java.core.data.PlayerDataAPIManager;
import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.runtime.AdaptiveIntervalAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MadokuHealthManager {
	private static final float EPSILON = 1.0e-4f;
	private static final float HEALTH_ROUND_STEP = 0.125f;
	private static final int VANILLA_MAX_HUNGER_POINTS = 20;

	private static final String DATA_FILE_NAME = "madoku-health";
	private static final String HEALTH_PLAYER_TICK_SYSTEM_ID = "health_player_tick";
	private static final long HEALTH_PLAYER_TICK_MIN_INTERVAL = 1L;
	private static final long HEALTH_PLAYER_TICK_MAX_INTERVAL = 5L;
	private static final int ACTION_INTERVAL_TICKS = 10;
	private static final long LOW_HUNGER_ACTION_INTERVAL_TICKS = 3L * 60L * 20L;
	private static final long POISON_TICK_INTERVAL = 10L;
	private static final long WITHER_TICK_INTERVAL = 20L;
	private static final long REGEN_TICK_INTERVAL = 20L;
	private static final Identifier LOW_HUNGER_MAX_HEALTH_MODIFIER_ID =
		Identifier.fromNamespaceAndPath("madoku-craft", "madoku_health_low_hunger_max_health");
	private static final Identifier HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID =
		Identifier.fromNamespaceAndPath("madoku-craft", "madoku_health_health_boost_max_health");
	private static final float POISON_MIN_HEALTH = 1.0f;
	private static final float LOW_HUNGER_STEP_RATIO = 0.01f;
	private static final double MAX_HEALTH_REDUCTION_PER_STEP = 0.05d;

	private static final Map<UUID, PlayerState> PLAYER_STATES = new HashMap<>();
	private static volatile HealthConfigManager.Settings settings = HealthConfigManager.Settings.defaults();
	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile long nextPlayerTick = Long.MIN_VALUE;

	private MadokuHealthManager() {
	}

	public static void initialize() {
		loadStaticConfig();
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
		nextPlayerTick = Long.MIN_VALUE;
		AdaptiveIntervalAPIManager.clearSystem(HEALTH_PLAYER_TICK_SYSTEM_ID);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		loadStaticConfig();
		JsonObject data = PlayerDataAPIManager.getSystemData(DATA_FILE_NAME);
		applyPersistedData(data);
		long autoSaveIntervalTicks = PlayerDataAPIManager.getAutoSaveIntervalTicks();
		lastAutosaveBucket = Math.floorDiv(TimeAPIManager.getGameplayTicks(), autoSaveIntervalTicks);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long autoSaveIntervalTicks = PlayerDataAPIManager.getAutoSaveIntervalTicks();
		long bucket = Math.floorDiv(TimeAPIManager.getGameplayTicks(), autoSaveIntervalTicks);
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
		PlayerDataAPIManager.setSystemData(DATA_FILE_NAME, toPersistedData());
	}

	public static void onServerStarted(MinecraftServer server) {
		nextPlayerTick = Long.MIN_VALUE;
	}

	public static void onServerTick(MinecraftServer server) {
		if (server == null) return;
		long now = Math.max(0L, TimeAPIManager.getGameplayTicks());
		if (nextPlayerTick != Long.MIN_VALUE && now < nextPlayerTick) return;
		long interval = AdaptiveIntervalAPIManager.resolve(HEALTH_PLAYER_TICK_SYSTEM_ID, server,
			HEALTH_PLAYER_TICK_MIN_INTERVAL, HEALTH_PLAYER_TICK_MAX_INTERVAL);
		nextPlayerTick = now + Math.max(1L, interval);
		onGameplayTick(server, now);
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
		}
		long elapsedTicks = consumeElapsedTicks(state, gameplayTick);
		if (!player.isAlive() || player.isDeadOrDying()) {
			// Never run the heal/drain loop while the player is dead; this can interfere with respawn.
			return;
		}

		applyLowHungerMaxHealthScaling(player, state, gameplayTick);
		applyHealthBoostScaling(player, state, gameplayTick);
		applyAbsorptionScaling(player, state, gameplayTick);
		processStatusEffects(player, state, gameplayTick, elapsedTicks);
		processHungerHealthCycles(player, state, elapsedTicks);
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
					applyRegenerationTick(player, regenerationLevel);
				}
			} else {
				state.regenerationProgressTicks = 0L;
			}
		}
	}

	private static void processHungerHealthCycles(ServerPlayer player, PlayerState state, long elapsedTicks) {
		if (player == null || state == null) {
			return;
		}

		if (hungerRatio(player) >= settings.health.hungerDrainPercentage) {
			state.lowHungerActionProgressTicks = 0L;
			state.actionProgressTicks = accumulateProgressTicks(state.actionProgressTicks, elapsedTicks);
			while (state.actionProgressTicks >= ACTION_INTERVAL_TICKS) {
				state.actionProgressTicks -= ACTION_INTERVAL_TICKS;
				convertHungerToHealth(player, true);
			}
			return;
		}

		float hungerRatio = hungerRatio(player);
		if (hungerRatio < settings.health.healthPenalty.hungerPenaltyPercentage) {
			state.lowHungerActionProgressTicks = 0L;
			return;
		}

		state.actionProgressTicks = 0L;
		state.lowHungerActionProgressTicks = accumulateProgressTicks(state.lowHungerActionProgressTicks, elapsedTicks);
		while (state.lowHungerActionProgressTicks >= LOW_HUNGER_ACTION_INTERVAL_TICKS) {
			state.lowHungerActionProgressTicks -= LOW_HUNGER_ACTION_INTERVAL_TICKS;
			convertHungerToHealth(player, false);
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
	}

	private static void applyWitherTick(ServerPlayer player, long gameplayTick, int witherLevel) {
		float damage = (float) resolveEffectAmount(player.getMaxHealth(), settings.main.wither, witherLevel);
		if (damage <= EPSILON) {
			return;
		}
		player.hurtServer(player.level(), player.damageSources().wither(), damage);
	}

	private static void applyRegenerationTick(ServerPlayer player, int regenerationLevel) {
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
	}

	private static void convertHungerToHealth(ServerPlayer player, boolean requireHighHunger) {
		float missingHealth = player.getMaxHealth() - player.getHealth();
		float hungerRatio = hungerRatio(player);
		boolean hasRecoveryNeed = missingHealth > EPSILON;
		boolean eligibleHunger = requireHighHunger
			? hungerRatio >= settings.health.hungerDrainPercentage
			: hungerRatio >= settings.health.healthPenalty.hungerPenaltyPercentage
				&& hungerRatio < settings.health.hungerDrainPercentage;

		if (!eligibleHunger || !hasRecoveryNeed) {
			return;
		}

		int drained = drainFood(player, 1);
		if (drained <= 0) {
			return;
		}

		float targetHealth = Math.min(player.getMaxHealth(), player.getHealth() + drained);
		player.setHealth(targetHealth);
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
	}

	private static int drainFood(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return 0;
		}
		if (HungerAPIManager.isEnabled()) {
			return HungerAPIManager.drainHunger(player, amount);
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
		if (!penalty.enabled || hungerRatio > penalty.hungerPenaltyPercentage) {
			return 1.0d;
		}

		double threshold = penalty.hungerPenaltyPercentage;
		double belowThreshold = Math.max(0.0d, threshold - hungerRatio);
		double maximumReduction = Math.max(0.0d, 1.0d - penalty.penaltyPercentage);
		int steps = (int) Math.ceil((belowThreshold - (double) EPSILON) / LOW_HUNGER_STEP_RATIO);
		steps = Math.max(0, steps);
		double reduction = Math.min(maximumReduction, steps * MAX_HEALTH_REDUCTION_PER_STEP);
		double multiplier = 1.0d - reduction;
		return Math.max(penalty.penaltyPercentage, Math.min(1.0d, multiplier));
	}

	private static float hungerRatio(ServerPlayer player) {
		if (player == null) {
			return 0.0f;
		}

		if (HungerAPIManager.isEnabled()) {
			int maxHungerPoints = Math.max(1, HungerAPIManager.getMaximumHungerPoints(player));
			int hungerPoints = Math.max(0, Math.min(maxHungerPoints, HungerAPIManager.getEffectiveHungerPoints(player)));
			return (float) hungerPoints / (float) maxHungerPoints;
		}

		int clamped = Math.max(0, Math.min(VANILLA_MAX_HUNGER_POINTS, player.getFoodData().getFoodLevel()));
		return (float) clamped / (float) VANILLA_MAX_HUNGER_POINTS;
	}

	private static void handlePlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null || alive || !settings.health.enabled) {
			return;
		}

		float targetHealth = Math.max(0.5f, newPlayer.getMaxHealth() * settings.health.respawnHealthPercentage);
		targetHealth = Math.min(newPlayer.getMaxHealth(), quantizeHealth(targetHealth));
		newPlayer.setHealth(targetHealth);

		PlayerState state = PLAYER_STATES.computeIfAbsent(newPlayer.getUUID(), ignored -> new PlayerState());
		state.savedHealth = quantizeHealth(Math.max(0.0f, newPlayer.getHealth()));
		state.lastProcessedGameplayTick = TimeAPIManager.getGameplayTicks();
		state.actionProgressTicks = 0L;
		state.lowHungerActionProgressTicks = 0L;
		state.poisonProgressTicks = 0L;
		state.witherProgressTicks = 0L;
		state.regenerationProgressTicks = 0L;
		state.appliedMaxHealthMultiplier = 1.0d;
		state.appliedHealthBoostAmount = 0.0d;
		state.appliedAbsorptionAmount = 0.0f;
		state.onlineThisSession = true;
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
		state.lastProcessedGameplayTick = TimeAPIManager.getGameplayTicks();

	}

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null || !settings.health.enabled) {
			return;
		}

		applyPersistedPlayerData(PlayerDataAPIManager.getSystemDataForPlayer(player, DATA_FILE_NAME, "players", "uuid"));
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.onlineThisSession = true;
		state.lastProcessedGameplayTick = TimeAPIManager.getGameplayTicks();
		applyImmediateEffectOverrides(player, state, TimeAPIManager.getGameplayTicks());
	}

	public static void handlePlayerEffectsChanged(ServerPlayer player) {
		if (player == null || !settings.health.enabled) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.onlineThisSession = true;
		long gameplayTick = TimeAPIManager.getGameplayTicks();
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
		JSONFormatAPIManager.ArrayBuilder players = JSONFormatAPIManager.array();
		for (Map.Entry<UUID, PlayerState> entry : PLAYER_STATES.entrySet()) {
			PlayerState state = entry.getValue();
			if (!state.hasPersistableState()) {
				continue;
			}

			players.object(player -> player
				.put("uuid", entry.getKey().toString())
				.put("current-health", state.savedHealth)
				.put("action-progress-ticks", Math.max(0L, state.actionProgressTicks))
				.put("low-hunger-action-progress-ticks", Math.max(0L, state.lowHungerActionProgressTicks))
				.put("poison-progress-ticks", Math.max(0L, state.poisonProgressTicks))
				.put("wither-progress-ticks", Math.max(0L, state.witherProgressTicks))
				.put("regeneration-progress-ticks", Math.max(0L, state.regenerationProgressTicks)));
		}
		return JSONFormatAPIManager.object()
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
			applyPersistedPlayerState(element.getAsJsonObject());
		}
	}

	private static void applyPersistedPlayerData(JsonObject source) {
		JsonArray players = getArray(source, "players");
		if (players == null) return;
		for (JsonElement element : players) {
			if (element != null && element.isJsonObject()) applyPersistedPlayerState(element.getAsJsonObject());
		}
	}

	private static void applyPersistedPlayerState(JsonObject playerData) {
		UUID playerId = parseUuid(getString(playerData, "uuid", ""));
		if (playerId == null) return;

		PlayerState state = new PlayerState();
		double savedHealth = getDouble(playerData, "current-health", Double.NaN);
		state.savedHealth = Double.isNaN(savedHealth) ? -1.0f : Math.max(0.0f, (float) savedHealth);
		state.actionProgressTicks = Math.max(0L, getLong(playerData, "action-progress-ticks", 0L));
		state.lowHungerActionProgressTicks = Math.max(0L, getLong(playerData, "low-hunger-action-progress-ticks", 0L));
		state.poisonProgressTicks = Math.max(0L, getLong(playerData, "poison-progress-ticks", 0L));
		state.witherProgressTicks = Math.max(0L, getLong(playerData, "wither-progress-ticks", 0L));
		state.regenerationProgressTicks = Math.max(0L, getLong(playerData, "regeneration-progress-ticks", 0L));
		PLAYER_STATES.put(playerId, state);
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
		settings = HealthConfigManager.loadSettings(AttributesConfigManager.loadSettings().enabled);
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


	private static float quantizeHealth(float value) {
		if (value <= 0.0f) {
			return 0.0f;
		}
		return Math.round(value / HEALTH_ROUND_STEP) * HEALTH_ROUND_STEP;
	}

	private static final class PlayerState {
		private float savedHealth = -1.0f;
		private long lastProcessedGameplayTick = Long.MIN_VALUE;
		private long actionProgressTicks;
		private long lowHungerActionProgressTicks;
		private long poisonProgressTicks;
		private long witherProgressTicks;
		private long regenerationProgressTicks;
		private double appliedMaxHealthMultiplier = 1.0d;
		private double appliedHealthBoostAmount;
		private float appliedAbsorptionAmount;
		private boolean onlineThisSession;

		private boolean hasPersistableState() {
			return savedHealth >= 0.0f;
		}
	}

}
