package madoku.craft.attributes.hunger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.data.DataPlayerManager;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import madoku.craft.api.sync.SyncPlayerManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MadokuHungerManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuHungerManager.class);

	private static final int VANILLA_MAX_HUNGER_POINTS = 20;
	private static final long HARDCORE_STARVATION_DELAY_TICKS = 1L * 24000L;
	private static final long HARD_STARVATION_DELAY_TICKS = 1L * 24000L;
	private static final long NORMAL_STARVATION_DELAY_TICKS = 3L * 24000L;
	private static final long EASY_STARVATION_DELAY_TICKS = 5L * 24000L;
	private static final long PEACEFUL_STARVATION_DELAY_TICKS = 7L * 24000L;
	private static final long HUNGER_EFFECT_INTERVAL_TICKS = 20L;
	private static final long SATURATION_EFFECT_INTERVAL_TICKS = 20L;
	private static final long HUNGER_PLAYER_TICK_MIN_INTERVAL = 1L;
	private static final long HUNGER_PLAYER_TICK_MAX_INTERVAL = 5L;
	private static final String DATA_FILE_NAME = "madoku-hunger";
	private static final String TASK_TYPE_HUNGER_PLAYER_TICK = "hunger_player_tick";
	private static final String HUNGER_PLAYER_TICK_SCHEDULER_KEY = "hunger_player_tick";

	private static final Map<UUID, PlayerState> PLAYER_STATES = new HashMap<>();
	private static volatile HungerConfigManager.Settings settings = HungerConfigManager.Settings.defaults();
	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile String schedulerId = "";
	private static volatile boolean tickQueued;

	private MadokuHungerManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		PayloadTypeRegistry.clientboundPlay().register(HungerPayloadManager.TYPE, HungerPayloadManager.CODEC);
		MadokuSchedulerManager.registerTaskHandler(TASK_TYPE_HUNGER_PLAYER_TICK, MadokuHungerManager::runPlayerTickTask);
		ServerPlayerEvents.JOIN.register(MadokuHungerManager::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register(MadokuHungerManager::handlePlayerRespawn);
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> handleBlockBreak(player));
	}

	public static void reset() {
		PLAYER_STATES.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
		schedulerId = "";
		tickQueued = false;
		MadokuSchedulerManager.clearAdaptiveDelayState(HUNGER_PLAYER_TICK_SCHEDULER_KEY);
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
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null || !settings.hunger.enabled) {
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
		DataPlayerManager.setSystemData(DATA_FILE_NAME, toPersistedData());
	}

	public static void onServerStarted(MinecraftServer server) {
		ensureQueued(server);
	}

	/**
	 * Re-baselines movement tracking after a server-side reposition. Teleports
	 * are not travelled distance and must not be allowed to bridge two hunger
	 * movement samples.
	 */
	public static void handlePlayerTeleport(ServerPlayer player) {
		if (player == null || !settings.hunger.enabled) {
			return;
		}

		PlayerState state = PLAYER_STATES.get(player.getUUID());
		if (state != null) {
			state.markPosition(player.getX(), player.getZ());
		}
	}

	public static boolean isEnabled() {
		return settings.hunger.enabled;
	}

	public static boolean isSaturationEnabled() {
		return settings.hunger.enabled && settings.saturation.enabled;
	}

	public static boolean isHungerEffectEnabled() {
		return settings.hunger.enabled && settings.hungerEffect.enabled;
	}

	public static int getCurrentHungerPoints(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		return normalizeFoodLevel(player);
	}

	public static int getEffectiveHungerPoints(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		return normalizeFoodLevel(player);
	}

	public static int getMaximumHungerPoints(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		return Math.max(1, settings.hunger.enabled ? resolveMaximumHungerPoints(player) : VANILLA_MAX_HUNGER_POINTS);
	}

	public static int getConfiguredMaximumHungerPoints() {
		return Math.max(1, settings.hunger.maxHunger);
	}

	public static boolean shouldApplyStarvationDamage(ServerPlayer player) {
		if (player == null
			|| !settings.hunger.enabled
			|| isExemptFromHungerDrain(player)) {
			return false;
		}

		PlayerState state = PLAYER_STATES.get(player.getUUID());
		return state != null && state.zeroHungerProgressTicks >= resolveStarvationDelayTicks(player);
	}

	private static long resolveStarvationDelayTicks(ServerPlayer player) {
		if (player == null) {
			return NORMAL_STARVATION_DELAY_TICKS;
		}
		if (player.level().getServer() != null && player.level().getServer().isHardcore()) {
			return HARDCORE_STARVATION_DELAY_TICKS;
		}

		return switch (player.level().getDifficulty()) {
			case HARD -> HARD_STARVATION_DELAY_TICKS;
			case EASY -> EASY_STARVATION_DELAY_TICKS;
			case PEACEFUL -> PEACEFUL_STARVATION_DELAY_TICKS;
			default -> NORMAL_STARVATION_DELAY_TICKS;
		};
	}

	public static void handleMaximumHungerChanged(ServerPlayer player) {
		if (player == null || !settings.hunger.enabled) {
			return;
		}

		int maxHungerPoints = resolveMaximumHungerPoints(player);
		normalizeFoodLevel(player, maxHungerPoints);
		syncFoodLevel(player, maxHungerPoints);
	}

	public static boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) {
		if (!isHungerEffectEnabled() || !(entity instanceof ServerPlayer) || effect == null) {
			return false;
		}
		return effect == MobEffects.HUNGER.value();
	}

	public static boolean canConsumeFood(ServerPlayer player, boolean ignoreHunger) {
		if (player == null || !settings.hunger.enabled || ignoreHunger) {
			return true;
		}

		int maxHungerPoints = resolveMaximumHungerPoints(player);
		return normalizeFoodLevel(player, maxHungerPoints) < maxHungerPoints;
	}

	public static void onFoodConsumed(ServerPlayer player, int nutrition) {
		if (player == null || nutrition <= 0 || !settings.hunger.enabled) {
			return;
		}

		int maxHungerPoints = resolveMaximumHungerPoints(player);
		int before = normalizeFoodLevel(player, maxHungerPoints);
		int after = Math.min(maxHungerPoints, safeAdd(before, nutrition));
		if (after == before) {
			return;
		}

		player.getFoodData().setFoodLevel(after);
		syncFoodLevel(player, maxHungerPoints);
	}

	public static int drainHunger(ServerPlayer player, int amount) {
		if (player == null || amount <= 0) {
			return 0;
		}
		if (isExemptFromHungerDrain(player)) {
			return 0;
		}

		if (!settings.hunger.enabled) {
			return drainVanillaFood(player, amount);
		}

		int maxHungerPoints = resolveMaximumHungerPoints(player);
		int current = normalizeFoodLevel(player, maxHungerPoints);
		int drained = Math.min(amount, current);
		if (drained <= 0) {
			return 0;
		}

		player.getFoodData().setFoodLevel(current - drained);
		syncFoodLevel(player, maxHungerPoints);
		return drained;
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
			HUNGER_PLAYER_TICK_SCHEDULER_KEY,
			HUNGER_PLAYER_TICK_MIN_INTERVAL,
			HUNGER_PLAYER_TICK_MAX_INTERVAL
		);
		if (MadokuSchedulerManager.hasQueuedTask(currentSchedulerId, TASK_TYPE_HUNGER_PLAYER_TICK)) {
			tickQueued = true;
			return;
		}
		if (enqueue(currentSchedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		schedulerId = MadokuSchedulerManager.createOrGetScheduler(
			MadokuSchedulerManager.SchedulerBinding.global(HUNGER_PLAYER_TICK_SCHEDULER_KEY)
		);
		if (enqueue(schedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		LOGGER.error("Failed to enqueue hunger player tick task.");
	}

	private static String ensureScheduler() {
		String current = schedulerId;
		if (current != null && !current.isBlank()) {
			return current;
		}
		schedulerId = MadokuSchedulerManager.createOrGetScheduler(
			MadokuSchedulerManager.SchedulerBinding.global(HUNGER_PLAYER_TICK_SCHEDULER_KEY)
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
			TASK_TYPE_HUNGER_PLAYER_TICK,
			new JsonObject(),
			MadokuSchedulerManager.TickDomain.GAMEPLAY
		);
		return status == MadokuSchedulerManager.EnqueueStatus.ACCEPTED
			|| status == MadokuSchedulerManager.EnqueueStatus.QUEUE_FULL;
	}

	private static void onGameplayTick(MinecraftServer server, long gameplayTick) {
		if (server == null || !settings.hunger.enabled) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			processPlayer(player, gameplayTick);
		}
	}

	private static void processPlayer(ServerPlayer player, long gameplayTick) {
		if (player == null || !player.isAlive() || player.isDeadOrDying()) {
			if (player != null) {
				PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
				state.clearPosition();
			}
			return;
		}

		int maxHungerPoints = resolveMaximumHungerPoints(player);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		normalizeFoodLevel(player, maxHungerPoints);
		long elapsedTicks = consumeElapsedTicks(state, gameplayTick);

		if (isExemptFromHungerDrain(player)) {
			state.blockBreakProgress = 0L;
			state.movementProgress = 0.0d;
			state.timeProgressTicks = 0L;
			state.hungerEffectProgressTicks = 0L;
			state.saturationEffectProgressTicks = 0L;
			state.zeroHungerProgressTicks = 0L;
			state.markPosition(player.getX(), player.getZ());
			normalizeFoodLevel(player, maxHungerPoints);
			syncFoodLevel(player, maxHungerPoints);
			return;
		}

		if (!settings.hungerDepletion.enabled) {
			state.blockBreakProgress = 0L;
			state.movementProgress = 0.0d;
			state.timeProgressTicks = 0L;
		} else {
			if (!settings.hungerDepletion.blockGoal.enabled) {
				state.blockBreakProgress = 0L;
			}
			processMovementGoal(player, state, gameplayTick, maxHungerPoints);
			processTimeGoal(player, state, gameplayTick, elapsedTicks, maxHungerPoints);
		}

		processHungerEffect(player, state, gameplayTick, elapsedTicks, maxHungerPoints);
		processSaturationEffect(player, state, gameplayTick, elapsedTicks, maxHungerPoints);
		int normalizedHunger = normalizeFoodLevel(player, maxHungerPoints);
		if (normalizedHunger <= 0) {
			state.zeroHungerProgressTicks = accumulateProgressTicks(state.zeroHungerProgressTicks, elapsedTicks);
		} else {
			state.zeroHungerProgressTicks = 0L;
		}
		syncFoodLevel(player, maxHungerPoints);
	}

	private static void processMovementGoal(ServerPlayer player, PlayerState state, long gameplayTick, int maxHungerPoints) {
		if (!settings.hungerDepletion.movementGoal.enabled) {
			state.movementProgress = 0.0d;
			state.markPosition(player.getX(), player.getZ());
			return;
		}

		double x = player.getX();
		double z = player.getZ();
		if (!state.hasPosition()) {
			state.markPosition(x, z);
			return;
		}

		double dx = x - state.lastX;
		double dz = z - state.lastZ;
		state.markPosition(x, z);
		double horizontalDistance = Math.hypot(dx, dz);
		if (horizontalDistance <= 1.0e-6d) {
			return;
		}

		state.movementProgress += horizontalDistance;
		while (state.movementProgress >= settings.hungerDepletion.movementGoal.value
			&& settings.hungerDepletion.movementGoal.value > 0.0d) {
			state.movementProgress -= settings.hungerDepletion.movementGoal.value;
			int drained = drainHunger(player, 1);
			if (drained <= 0) {
				break;
			}
		}

		if (state.movementProgress < 0.0d) {
			state.movementProgress = 0.0d;
		}
		normalizeFoodLevel(player, maxHungerPoints);
		syncFoodLevel(player, maxHungerPoints);
	}

	private static void processTimeGoal(ServerPlayer player, PlayerState state, long gameplayTick, long elapsedTicks, int maxHungerPoints) {
		if (!settings.hungerDepletion.timeGoal.enabled) {
			state.timeProgressTicks = 0L;
			return;
		}

		state.timeProgressTicks = accumulateProgressTicks(state.timeProgressTicks, elapsedTicks);
		long drainedCount = 0L;
		while (state.timeProgressTicks >= settings.hungerDepletion.timeGoal.value
			&& settings.hungerDepletion.timeGoal.value > 0L) {
			state.timeProgressTicks -= settings.hungerDepletion.timeGoal.value;
			drainedCount++;
		}

		if (drainedCount <= 0L) {
			return;
		}

		for (long i = 0L; i < drainedCount; i++) {
			int drained = drainHunger(player, 1);
			if (drained <= 0) {
				break;
			}
		}
		normalizeFoodLevel(player, maxHungerPoints);
		syncFoodLevel(player, maxHungerPoints);
	}

	private static void processHungerEffect(ServerPlayer player, PlayerState state, long gameplayTick, long elapsedTicks, int maxHungerPoints) {
		if (!settings.hungerEffect.enabled || player.isCreative() || player.isSpectator()) {
			state.hungerEffectProgressTicks = 0L;
			return;
		}
		int hungerEffectLevel = getEffectLevel(player, MobEffects.HUNGER);
		if (hungerEffectLevel <= 0) {
			state.hungerEffectProgressTicks = 0L;
			return;
		}

		state.hungerEffectProgressTicks = accumulateProgressTicks(state.hungerEffectProgressTicks, elapsedTicks);
		long effectApplications = state.hungerEffectProgressTicks / HUNGER_EFFECT_INTERVAL_TICKS;
		if (effectApplications <= 0L) {
			return;
		}
		state.hungerEffectProgressTicks %= HUNGER_EFFECT_INTERVAL_TICKS;

		int drainAmount = resolveEffectAmount(player, settings.hungerEffect, hungerEffectLevel, maxHungerPoints);
		if (drainAmount <= 0) {
			return;
		}

		for (long i = 0L; i < effectApplications; i++) {
			int drained = drainHunger(player, drainAmount);
			if (drained <= 0) {
				break;
			}
		}
		normalizeFoodLevel(player, maxHungerPoints);
	}

	private static void processSaturationEffect(ServerPlayer player, PlayerState state, long gameplayTick, long elapsedTicks, int maxHungerPoints) {
		if (!settings.saturation.enabled || player.isCreative() || player.isSpectator()) {
			state.saturationEffectProgressTicks = 0L;
			return;
		}
		int saturationEffectLevel = getEffectLevel(player, MobEffects.SATURATION);
		if (saturationEffectLevel <= 0) {
			state.saturationEffectProgressTicks = 0L;
			return;
		}

		state.saturationEffectProgressTicks = accumulateProgressTicks(state.saturationEffectProgressTicks, elapsedTicks);
		long effectApplications = state.saturationEffectProgressTicks / SATURATION_EFFECT_INTERVAL_TICKS;
		if (effectApplications <= 0L) {
			return;
		}
		state.saturationEffectProgressTicks %= SATURATION_EFFECT_INTERVAL_TICKS;

		int gainAmount = resolveEffectAmount(player, settings.saturation, saturationEffectLevel, maxHungerPoints);
		if (gainAmount <= 0) {
			return;
		}

		for (long i = 0L; i < effectApplications; i++) {
			int before = normalizeFoodLevel(player, maxHungerPoints);
			int after = Math.min(maxHungerPoints, safeAdd(before, gainAmount));
			if (after == before) {
				continue;
			}
			player.getFoodData().setFoodLevel(after);
		}
		normalizeFoodLevel(player, maxHungerPoints);
		syncFoodLevel(player, maxHungerPoints);
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null || !settings.hunger.enabled) {
			return;
		}

		boolean firstJoin = !PLAYER_STATES.containsKey(player.getUUID());
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		int maxHungerPoints = resolveMaximumHungerPoints(player);
		if (firstJoin) {
			player.getFoodData().setFoodLevel(maxHungerPoints);
		}
		normalizeFoodLevel(player, maxHungerPoints);
		state.lastProcessedGameplayTick = MadokuTimeManager.getGameplayTicks();
		state.markPosition(player.getX(), player.getZ());
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncFoodLevel(player, maxHungerPoints);
	}

	private static void handlePlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null || alive || !settings.hunger.enabled) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(newPlayer.getUUID(), ignored -> new PlayerState());
		int maxHungerPoints = resolveMaximumHungerPoints(newPlayer);
		newPlayer.getFoodData().setFoodLevel((int) Math.round(maxHungerPoints * settings.hunger.respawnHungerPercentage));
		state.blockBreakProgress = 0L;
		state.movementProgress = 0.0d;
		state.timeProgressTicks = 0L;
		state.hungerEffectProgressTicks = 0L;
		state.saturationEffectProgressTicks = 0L;
		state.zeroHungerProgressTicks = 0L;
		state.lastProcessedGameplayTick = MadokuTimeManager.getGameplayTicks();
		state.clearPosition();
		state.markPosition(newPlayer.getX(), newPlayer.getZ());
		normalizeFoodLevel(newPlayer, maxHungerPoints);
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncFoodLevel(newPlayer, maxHungerPoints);
	}

	private static void handleBlockBreak(Player player) {
		if (!settings.hunger.enabled || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (!settings.hungerDepletion.enabled || !settings.hungerDepletion.blockGoal.enabled) {
			PlayerState state = PLAYER_STATES.get(serverPlayer.getUUID());
			if (state != null) {
				state.blockBreakProgress = 0L;
			}
			return;
		}
		if (isExemptFromHungerDrain(serverPlayer)) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(serverPlayer.getUUID(), ignored -> new PlayerState());
		int maxHungerPoints = resolveMaximumHungerPoints(serverPlayer);
		normalizeFoodLevel(serverPlayer, maxHungerPoints);
		state.blockBreakProgress++;
		while (state.blockBreakProgress >= settings.hungerDepletion.blockGoal.value
			&& settings.hungerDepletion.blockGoal.value > 0L) {
			state.blockBreakProgress -= settings.hungerDepletion.blockGoal.value;
			int drained = drainHunger(serverPlayer, 1);
			if (drained <= 0) {
				break;
			}
		}
		normalizeFoodLevel(serverPlayer, maxHungerPoints);
		syncFoodLevel(serverPlayer, maxHungerPoints);
	}

	private static void syncFoodLevel(ServerPlayer player, int maxHungerPoints) {
		if (player == null || !settings.hunger.enabled) {
			return;
		}

		int currentHunger = normalizeFoodLevel(player, maxHungerPoints);
		if (playerState(player).lastSyncedCurrentHunger == currentHunger
			&& playerState(player).lastSyncedMaxHunger == maxHungerPoints) {
			return;
		}

		if (!SyncPlayerManager.send(player, new HungerPayloadManager(
			Math.max(0, currentHunger),
			Math.max(1, maxHungerPoints)
		))) {
			return;
		}
		PlayerState state = playerState(player);
		state.lastSyncedCurrentHunger = currentHunger;
		state.lastSyncedMaxHunger = maxHungerPoints;
	}

	private static PlayerState playerState(ServerPlayer player) {
		return PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
	}

	private static int normalizeFoodLevel(ServerPlayer player) {
		int maximumHungerPoints = settings.hunger.enabled
			? resolveMaximumHungerPoints(player)
			: VANILLA_MAX_HUNGER_POINTS;
		return normalizeFoodLevel(player, maximumHungerPoints);
	}

	private static int normalizeFoodLevel(ServerPlayer player, int maxHungerPoints) {
		if (player == null) {
			return 0;
		}

		FoodData foodData = player.getFoodData();
		int normalized = Math.max(0, Math.min(maxHungerPoints, foodData.getFoodLevel()));
		if (foodData.getFoodLevel() != normalized) {
			foodData.setFoodLevel(normalized);
		}
		return normalized;
	}

	private static int resolveMaximumHungerPoints(ServerPlayer player) {
		return Math.max(1, settings.hunger.maxHunger);
	}

	private static int resolveEffectAmount(ServerPlayer player, HungerConfigManager.EffectSettings effect, int effectLevel, int maxHungerPoints) {
		if (player == null || effect == null || effectLevel <= 0) {
			return 0;
		}

		double perLevelAmount = effect.type == HungerConfigManager.ValueType.FLAT
			? effect.value
			: (double) maxHungerPoints * effect.value;
		double rawAmount = perLevelAmount * (double) effectLevel;
		return Math.max(1, (int) Math.round(rawAmount));
	}


	private static boolean isExemptFromHungerDrain(ServerPlayer player) {
		return player != null && (player.isCreative() || player.isSpectator());
	}


	private static int getEffectLevel(ServerPlayer player, net.minecraft.core.Holder<MobEffect> effect) {
		if (player == null || effect == null) {
			return 0;
		}
		MobEffectInstance effectInstance = player.getEffect(effect);
		if (effectInstance == null) {
			return 0;
		}
		return Math.max(0, effectInstance.getAmplifier() + 1);
	}

	private static int drainVanillaFood(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return 0;
		}

		FoodData foodData = player.getFoodData();
		int current = Math.max(0, foodData.getFoodLevel());
		int drained = Math.min(amount, current);
		if (drained <= 0) {
			return 0;
		}

		int updated = current - drained;
		foodData.setFoodLevel(updated);
		return drained;
	}

	private static int safeAdd(int current, int delta) {
		long sum = (long) current + (long) delta;
		if (sum > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		if (sum < Integer.MIN_VALUE) {
			return Integer.MIN_VALUE;
		}
		return (int) sum;
	}

	private static JsonObject toPersistedData() {
		madoku.craft.api.json.JSONFormatManager.ArrayBuilder players = madoku.craft.api.json.JSONFormatManager.array();
		for (Map.Entry<UUID, PlayerState> entry : PLAYER_STATES.entrySet()) {
			PlayerState state = entry.getValue();
			players.object(player -> player
				.put("uuid", entry.getKey().toString())
				.put("block-break-progress", state.blockBreakProgress)
				.put("movement-progress", state.movementProgress)
				.put("time-progress-ticks", state.timeProgressTicks)
				.put("hunger-effect-progress-ticks", Math.max(0L, state.hungerEffectProgressTicks))
				.put("saturation-effect-progress-ticks", Math.max(0L, state.saturationEffectProgressTicks))
				.put("zero-hunger-progress-ticks", Math.max(0L, state.zeroHungerProgressTicks)));
		}
		return madoku.craft.api.json.JSONFormatManager.object()
			.put("players", players.build())
			.build();
	}

	private static void applyPersistedData(JsonObject source) {
		PLAYER_STATES.clear();
		if (source == null) {
			return;
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
			state.blockBreakProgress = Math.max(0L, getLong(playerData, "block-break-progress", 0L));
			state.movementProgress = Math.max(0.0d, getDouble(playerData, "movement-progress", 0.0d));
			state.timeProgressTicks = Math.max(0L, getLong(playerData, "time-progress-ticks", 0L));
			state.hungerEffectProgressTicks = Math.max(0L, getLong(playerData, "hunger-effect-progress-ticks", 0L));
			state.saturationEffectProgressTicks = Math.max(0L, getLong(playerData, "saturation-effect-progress-ticks", 0L));
			state.zeroHungerProgressTicks = Math.max(0L, getLong(playerData, "zero-hunger-progress-ticks", 0L));
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

	private static void loadStaticConfig() {
		settings = HungerConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
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

	private static final class PlayerState {
		private long blockBreakProgress;
		private double movementProgress;
		private long timeProgressTicks;
		private long lastProcessedGameplayTick = Long.MIN_VALUE;
		private long hungerEffectProgressTicks;
		private long saturationEffectProgressTicks;
		private long zeroHungerProgressTicks;
		private double lastX = Double.NaN;
		private double lastZ = Double.NaN;
		private int lastSyncedCurrentHunger = Integer.MIN_VALUE;
		private int lastSyncedMaxHunger = Integer.MIN_VALUE;

		private boolean hasPosition() {
			return !Double.isNaN(lastX) && !Double.isNaN(lastZ);
		}

		private void markPosition(double x, double z) {
			lastX = x;
			lastZ = z;
		}

		private void clearPosition() {
			lastX = Double.NaN;
			lastZ = Double.NaN;
		}
	}
}
