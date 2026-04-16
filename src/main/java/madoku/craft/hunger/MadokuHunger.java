package madoku.craft.hunger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.attributes.MadokuAttributes;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.StaticJsonSystem;
import madoku.craft.data.MadokuData;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.network.HungerStateSync;
import madoku.craft.scheduler.MadokuScheduler;
import madoku.craft.time.MadokuTime;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MadokuHunger {
private static final Logger LOGGER = LoggerFactory.getLogger(MadokuHunger.class);

private static final int VANILLA_MAX_HUNGER_POINTS = 20;
	private static final int MAX_CONFIG_HUNGER_POINTS = 8192;
private static final int MADOKU_HOURS_PER_DAY = 24;
private static final int MINECRAFT_TICKS_PER_DAY = 24000;
private static final int DEFAULT_TIME_GOAL_CLOCK_TICKS = 6 * MINECRAFT_TICKS_PER_DAY / MADOKU_HOURS_PER_DAY;
private static final double RESPAWN_HUNGER_RATIO = 0.5d;
private static final long HUNGER_EFFECT_INTERVAL_TICKS = 20L;
private static final long SATURATION_HUNGER_INTERVAL_TICKS = 20L;
private static final long AUTOSAVE_INTERVAL_TICKS = 60L * 20L;

	private static final String HUNGER_CONFIG_DIRECTORY_NAME = "madoku-hunger";
	private static final String HUNGER_CONFIG_FILE_NAME = "madoku-hunger";
	private static final String DATA_FOLDER_NAME = "madoku-craft-hunger";
	private static final String DATA_FILE_NAME = "madoku-hunger";
	private static final String TASK_TYPE_HUNGER_TICK = "hunger_tick";

	private static final Map<UUID, PlayerState> PLAYER_STATES = new HashMap<>();
	private static final Map<UUID, String> PLAYER_SCHEDULER_IDS = new HashMap<>();
	private static final Set<UUID> SCHEDULED_PLAYERS = new HashSet<>();
	private static final Map<UUID, Long> LAST_PROCESSED_TICKS_BY_PLAYER = new HashMap<>();
	private static volatile Settings settings = Settings.defaults();
	private static long lastAutosaveBucket = Long.MIN_VALUE;

	private MadokuHunger() {
	}

	public static void initialize() {
		loadStaticConfig();
		MadokuScheduler.registerTaskHandler(TASK_TYPE_HUNGER_TICK, MadokuHunger::runHungerTask);
		ServerPlayerEvents.JOIN.register(MadokuHunger::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register(MadokuHunger::handlePlayerRespawn);
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> handleBlockBreak(player));
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
		if (server == null || !settings.enabled) {
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

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static int getConfiguredMaximumHungerPoints() {
		return Math.max(1, settings.maximumHungerPoints);
	}

	public static boolean hasEnoughFoodToDoExhaustiveManoeuvres(Player player) {
		if (player == null || !settings.enabled) {
			return true;
		}
		if (player.getAbilities().mayfly) {
			return true;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			int maxHungerPoints = Math.max(1, settings.maximumHungerPoints);
			PlayerState state = PLAYER_STATES.computeIfAbsent(serverPlayer.getUUID(), ignored -> new PlayerState());
			initializeHungerFromPlayer(serverPlayer, state, maxHungerPoints);
			return !isAtOrBelowSprintThreshold(state.hungerPoints, maxHungerPoints);
		}

		// Client fallback: mirror the 25% threshold in vanilla 0-20 food scale.
		int vanillaFood = clampVanillaFood(player.getFoodData().getFoodLevel());
		return (long) vanillaFood * 4L > (long) VANILLA_MAX_HUNGER_POINTS;
	}

	public static boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) {
		if (!settings.enabled || !(entity instanceof ServerPlayer) || effect == null) {
			return false;
		}
		return effect == MobEffects.HUNGER.value();
	}

	public static boolean canConsumeFood(ServerPlayer player, boolean ignoreHunger) {
		if (player == null || !settings.enabled || ignoreHunger) {
			return true;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		int maxHungerPoints = Math.max(1, settings.maximumHungerPoints);
		initializeHungerFromPlayer(player, state, maxHungerPoints);
		long total = (long) Math.max(0, state.hungerPoints) + (long) Math.max(0, state.pendingHunger);
		return total < maxHungerPoints;
	}

	public static boolean canConsumeFoodClient(boolean ignoreHunger) {
		if (ignoreHunger || !settings.enabled) {
			return true;
		}
		return HungerStateSync.canConsumeClient();
	}

	public static void onFoodConsumed(ServerPlayer player, int nutrition) {
		if (player == null || nutrition <= 0 || !settings.enabled) {
			return;
		}

		int maxHungerPoints = Math.max(1, settings.maximumHungerPoints);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeHungerFromPlayer(player, state, maxHungerPoints);

		state.pendingHunger = safeAdd(state.pendingHunger, nutrition);
		long gameplayTick = MadokuTicks.getGameplayTicks();
		state.lastPendingActivityTick = gameplayTick;
		scheduleNextPendingAllocation(state, gameplayTick);
		applyFoodState(player, state.hungerPoints);
		syncHudState(player, state, maxHungerPoints);
		requestHungerProcessing(((net.minecraft.server.level.ServerLevel) player.level()).getServer(), player.getUUID(), 1L);

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HUNGER, "hunger.pending_collected")) {
			MadokuDebug.event("hunger.pending_collected", MadokuDebug.Domain.HUNGER)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.world(player.level().dimension().toString())
				.subject("player:" + player.getUUID())
				.field("source", "food_nutrition")
				.field("added_pending", nutrition)
				.field("pending_hunger", state.pendingHunger)
				.log();
		}
	}

	public static int drainHunger(ServerPlayer player, int amount) {
		if (player == null || amount <= 0) {
			return 0;
		}
		if (isExemptFromHungerDrain(player)) {
			return 0;
		}

		if (!settings.enabled) {
			return drainVanillaFood(player, amount);
		}

		int maxHungerPoints = Math.max(1, settings.maximumHungerPoints);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeHungerFromPlayer(player, state, maxHungerPoints);
		int drained = Math.min(amount, state.hungerPoints);
		if (drained <= 0) {
			return 0;
		}
		state.hungerPoints -= drained;
		state.lastPendingActivityTick = MadokuTicks.getGameplayTicks();
		applyFoodState(player, state.hungerPoints);
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedPendingHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncHudState(player, state, maxHungerPoints);
		requestHungerProcessing(((net.minecraft.server.level.ServerLevel) player.level()).getServer(), player.getUUID(), 1L);
		return drained;
	}

	public static boolean applySaturationEffectTick(ServerPlayer player, int amplifier) {
		if (player == null || !settings.enabled) {
			return false;
		}

		int maxHungerPoints = Math.max(1, settings.maximumHungerPoints);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeHungerFromPlayer(player, state, maxHungerPoints);

		long gameplayTick = MadokuTicks.getGameplayTicks();
		if (state.lastSaturationGainTick != Long.MIN_VALUE
			&& gameplayTick - state.lastSaturationGainTick < SATURATION_HUNGER_INTERVAL_TICKS) {
			return true;
		}

		state.lastSaturationGainTick = gameplayTick;
		int gainAmount = Math.max(1, amplifier + 1);
		int before = state.hungerPoints;
		int clamped = clampInternalHunger(safeAdd(before, gainAmount), maxHungerPoints);
		int applied = clamped - before;
		if (applied > 0) {
			state.hungerPoints = clamped;
			state.lastPendingActivityTick = gameplayTick;
			applyFoodState(player, state.hungerPoints);
			syncHudState(player, state, maxHungerPoints);

			if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HUNGER, "hunger.saturation_applied")) {
				MadokuDebug.event("hunger.saturation_applied", MadokuDebug.Domain.HUNGER)
					.side(MadokuDebug.Side.SERVER)
					.tick(gameplayTick)
					.world(player.level().dimension().toString())
					.subject("player:" + player.getUUID())
					.field("amplifier", amplifier)
					.field("added_hunger", applied)
					.field("hunger", state.hungerPoints)
					.log();
			}
		}

		return true;
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null || !settings.enabled) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeHungerFromPlayer(player, state, settings.maximumHungerPoints);
		applyFoodState(player, state.hungerPoints);
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedPendingHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncHudState(player, state, settings.maximumHungerPoints);
		requestHungerProcessing(((net.minecraft.server.level.ServerLevel) player.level()).getServer(), player.getUUID(), 1L);
	}

	private static void handlePlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null || alive || !settings.enabled) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(newPlayer.getUUID(), ignored -> new PlayerState());
		int maxHungerPoints = Math.max(1, settings.maximumHungerPoints);
		state.hungerPoints = Math.max(1, (int) Math.round(maxHungerPoints * RESPAWN_HUNGER_RATIO));
		state.pendingHunger = 0;
		state.blockBreakProgress = 0;
		state.travelProgress = 0.0d;
		state.timeProgressTicks = 0;
		state.nextPendingAllocationTick = 0L;
		state.lastSaturationGainTick = Long.MIN_VALUE;
		state.lastObservedAbsoluteDayTime = MadokuTime.getCurrentAbsoluteDayTime();
		state.lastPendingActivityTick = MadokuTicks.getGameplayTicks();
		state.clearPosition();
		applyFoodState(newPlayer, state.hungerPoints);
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedPendingHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncHudState(newPlayer, state, maxHungerPoints);
		requestHungerProcessing(((net.minecraft.server.level.ServerLevel) newPlayer.level()).getServer(), newPlayer.getUUID(), 1L);
	}

	private static void handleBlockBreak(net.minecraft.world.entity.player.Player player) {
		if (!settings.enabled || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (isExemptFromHungerDrain(serverPlayer)) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(serverPlayer.getUUID(), ignored -> new PlayerState());
		initializeHungerFromPlayer(serverPlayer, state, settings.maximumHungerPoints);
		state.blockBreakProgress++;
		long gameplayTick = MadokuTicks.getGameplayTicks();
		while (state.blockBreakProgress >= settings.blockBreakGoal && settings.blockBreakGoal > 0) {
			state.blockBreakProgress -= settings.blockBreakGoal;
			int drained = drainStateHunger(state, 1);
			if (drained > 0 && MadokuDebug.shouldEmit(MadokuDebug.Domain.HUNGER, "hunger.drain_block_goal")) {
				MadokuDebug.event("hunger.drain_block_goal", MadokuDebug.Domain.HUNGER)
					.side(MadokuDebug.Side.SERVER)
					.tick(gameplayTick)
					.world(serverPlayer.level().dimension().toString())
					.subject("player:" + serverPlayer.getUUID())
					.field("drained", drained)
					.field("hunger", state.hungerPoints)
					.field("block_break_progress", state.blockBreakProgress)
					.log();
			}
		}
		applyFoodState(serverPlayer, state.hungerPoints);
		requestHungerProcessing(((net.minecraft.server.level.ServerLevel) serverPlayer.level()).getServer(), serverPlayer.getUUID(), 1L);
	}

	private static void runHungerTask(MinecraftServer server, MadokuScheduler.TaskContext context, JsonObject payload) {
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

		if (!settings.enabled) {
			return;
		}

		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null) {
			PlayerState state = PLAYER_STATES.get(playerId);
			if (state != null) {
				state.clearPosition();
			}
			return;
		}

		boolean stillActive = processPlayer(player, context.getNowTick(), MadokuTime.getCurrentAbsoluteDayTime());
		if (stillActive) {
			requestHungerProcessing(server, playerId, 1L);
		}
	}

	private static boolean processPlayer(ServerPlayer player, long gameplayTick, long currentAbsoluteDayTime) {
		if (player == null) {
			return false;
		}

		int maxHungerPoints = settings.maximumHungerPoints;
		UUID playerId = player.getUUID();
		PlayerState state = PLAYER_STATES.computeIfAbsent(playerId, ignored -> new PlayerState());
		initializeHungerFromPlayer(player, state, maxHungerPoints);
		if (!player.isAlive() || player.isDeadOrDying()) {
			state.clearPosition();
			state.lastObservedAbsoluteDayTime = currentAbsoluteDayTime;
			return false;
		}
		if (isExemptFromHungerDrain(player)) {
			state.blockBreakProgress = 0;
			state.travelProgress = 0.0d;
			state.timeProgressTicks = 0;
			state.lastObservedAbsoluteDayTime = currentAbsoluteDayTime;
			state.markPosition(player.getX(), player.getZ());
			applyFoodState(player, state.hungerPoints);
			syncHudState(player, state, maxHungerPoints);
			return true;
		}
		enforceSprintThreshold(player, state, maxHungerPoints, gameplayTick);
		processFoodChanges(player, state, gameplayTick, maxHungerPoints);
		processTravelGoal(player, state, gameplayTick);
		processTimeGoal(player, state, currentAbsoluteDayTime, gameplayTick);
		processHungerEffect(player, state, gameplayTick);
		allocatePendingHunger(player, state, gameplayTick, maxHungerPoints);
		clearIdlePendingHunger(player, state, gameplayTick);
		applyFoodState(player, state.hungerPoints);
		syncHudState(player, state, maxHungerPoints);
		return true;
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

	private static void requestHungerProcessing(MinecraftServer server, UUID playerId, long delay) {
		if (server == null || playerId == null || !settings.enabled || SCHEDULED_PLAYERS.contains(playerId)) {
			return;
		}

		String schedulerId = ensureSchedulerExists(playerId);
		if (enqueueHungerTask(schedulerId, delay)) {
			SCHEDULED_PLAYERS.add(playerId);
			return;
		}

		String created = MadokuScheduler.createScheduler(
			MadokuScheduler.SchedulerOwner.of("player", playerId.toString(), null)
		);
		PLAYER_SCHEDULER_IDS.put(playerId, created);
		if (enqueueHungerTask(created, delay)) {
			SCHEDULED_PLAYERS.add(playerId);
			return;
		}
		LOGGER.error("Failed to enqueue MadokuHunger scheduler task for player={}", playerId);
	}

	private static boolean enqueueHungerTask(String targetSchedulerId, long delay) {
		if (targetSchedulerId == null || targetSchedulerId.isBlank()) {
			return false;
		}

		MadokuScheduler.EnqueueStatus status = MadokuScheduler.enqueue(
			targetSchedulerId,
			Math.max(0L, delay),
			TASK_TYPE_HUNGER_TICK,
			new JsonObject(),
			MadokuScheduler.TickDomain.GAMEPLAY
		);
		return status == MadokuScheduler.EnqueueStatus.ACCEPTED
			|| status == MadokuScheduler.EnqueueStatus.QUEUE_FULL;
	}

	private static void processFoodChanges(ServerPlayer player, PlayerState state, long gameplayTick, int maxHungerPoints) {
		FoodData foodData = player.getFoodData();
		int observedFood = clampVanillaFood(foodData.getFoodLevel());
		int expectedFood = toVanillaFood(state.hungerPoints, maxHungerPoints);
		if (observedFood > expectedFood) {
			int gainedVanilla = observedFood - expectedFood;
			int gainedInternal = gainedVanilla;
			state.pendingHunger = safeAdd(state.pendingHunger, gainedInternal);
			state.lastPendingActivityTick = gameplayTick;
			scheduleNextPendingAllocation(state, gameplayTick);
			if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HUNGER, "hunger.pending_collected")) {
				MadokuDebug.event("hunger.pending_collected", MadokuDebug.Domain.HUNGER)
					.side(MadokuDebug.Side.SERVER)
					.tick(gameplayTick)
					.world(player.level().dimension().toString())
					.subject("player:" + player.getUUID())
					.field("source", "vanilla_food_delta")
					.field("vanilla_delta", gainedVanilla)
					.field("added_pending", gainedInternal)
					.field("pending_hunger", state.pendingHunger)
					.log();
			}
		}
	}

	private static void processTravelGoal(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (isExemptFromHungerDrain(player)) {
			state.markPosition(player.getX(), player.getZ());
			return;
		}
		double x = player.getX();
		double z = player.getZ();
		if (!state.hasPosition()) {
			state.markPosition(x, z);
			return;
		}

		if (player.isSpectator()) {
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
		if (player.getVehicle() != null || horizontalDistance > settings.teleportDistanceThreshold) {
			return;
		}

		state.travelProgress += horizontalDistance;
		while (state.travelProgress >= settings.travelGoalDistance && settings.travelGoalDistance > 0.0d) {
			state.travelProgress -= settings.travelGoalDistance;
			int drained = drainStateHunger(state, 1);
			if (drained > 0 && MadokuDebug.shouldEmit(MadokuDebug.Domain.HUNGER, "hunger.drain_travel_goal")) {
				MadokuDebug.event("hunger.drain_travel_goal", MadokuDebug.Domain.HUNGER)
					.side(MadokuDebug.Side.SERVER)
					.tick(gameplayTick)
					.world(player.level().dimension().toString())
					.subject("player:" + player.getUUID())
					.field("drained", drained)
					.field("hunger", state.hungerPoints)
					.field("travel_progress", formatDouble(state.travelProgress))
					.log();
			}
		}
	}

	private static void processTimeGoal(ServerPlayer player, PlayerState state, long currentAbsoluteDayTime, long gameplayTick) {
		if (isExemptFromHungerDrain(player)) {
			state.lastObservedAbsoluteDayTime = currentAbsoluteDayTime;
			state.timeProgressTicks = 0;
			return;
		}
		if (state.lastObservedAbsoluteDayTime < 0L) {
			state.lastObservedAbsoluteDayTime = currentAbsoluteDayTime;
			return;
		}

		long elapsed = currentAbsoluteDayTime - state.lastObservedAbsoluteDayTime;
		state.lastObservedAbsoluteDayTime = currentAbsoluteDayTime;
		if (elapsed <= 0L) {
			return;
		}

		state.timeProgressTicks = safeAdd(state.timeProgressTicks, (int) Math.min(Integer.MAX_VALUE, elapsed));
		while (state.timeProgressTicks >= settings.timeGoalTicks && settings.timeGoalTicks > 0) {
			state.timeProgressTicks -= settings.timeGoalTicks;
			int drained = drainStateHunger(state, 1);
			if (drained > 0 && MadokuDebug.shouldEmit(MadokuDebug.Domain.HUNGER, "hunger.drain_time_goal")) {
				MadokuDebug.event("hunger.drain_time_goal", MadokuDebug.Domain.HUNGER)
					.side(MadokuDebug.Side.SERVER)
					.tick(gameplayTick)
					.world(player.level().dimension().toString())
					.subject("player:" + player.getUUID())
					.field("drained", drained)
					.field("hunger", state.hungerPoints)
					.field("time_progress_ticks", state.timeProgressTicks)
					.log();
			}
		}
	}

	private static void processHungerEffect(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (player == null || state == null || player.isCreative() || player.isSpectator()) {
			return;
		}
		if (Math.floorMod(gameplayTick, HUNGER_EFFECT_INTERVAL_TICKS) != 0L) {
			return;
		}

		int hungerEffectLevel = getEffectLevel(player, MobEffects.HUNGER);
		if (hungerEffectLevel <= 0) {
			return;
		}

		int drained = drainStateHunger(state, hungerEffectLevel);
		if (drained <= 0) {
			return;
		}

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HUNGER, "hunger.drain_hunger_effect")) {
			MadokuDebug.event("hunger.drain_hunger_effect", MadokuDebug.Domain.HUNGER)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.world(player.level().dimension().toString())
				.subject("player:" + player.getUUID())
				.field("effect_level", hungerEffectLevel)
				.field("drained", drained)
				.field("hunger", state.hungerPoints)
				.log();
		}
	}

	private static void enforceSprintThreshold(ServerPlayer player, PlayerState state, int maxHungerPoints, long gameplayTick) {
		if (player == null || state == null) {
			return;
		}
		if (!isAtOrBelowSprintThreshold(state.hungerPoints, maxHungerPoints)) {
			return;
		}
		if (!player.isSprinting()) {
			return;
		}

		player.setSprinting(false);
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HUNGER, "hunger.sprint_blocked")) {
			MadokuDebug.event("hunger.sprint_blocked", MadokuDebug.Domain.HUNGER)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.world(player.level().dimension().toString())
				.subject("player:" + player.getUUID())
				.field("hunger", state.hungerPoints)
				.field("max_hunger", maxHungerPoints)
				.log();
		}
	}

	private static void scheduleNextPendingAllocation(PlayerState state, long gameplayTick) {
		if (state == null) {
			return;
		}
		if (state.pendingHunger <= 0) {
			state.nextPendingAllocationTick = 0L;
			return;
		}

		if (state.nextPendingAllocationTick > gameplayTick) {
			return;
		}
		state.nextPendingAllocationTick = gameplayTick + Math.max(1, settings.pendingAllocationIntervalTicks);
	}

	private static void allocatePendingHunger(ServerPlayer player, PlayerState state, long gameplayTick, int maxHungerPoints) {
		if (state.pendingHunger <= 0 || state.hungerPoints >= maxHungerPoints) {
			if (state.pendingHunger <= 0) {
				state.nextPendingAllocationTick = 0L;
			}
			return;
		}
		if (state.nextPendingAllocationTick <= 0L) {
			state.nextPendingAllocationTick = gameplayTick + Math.max(1, settings.pendingAllocationIntervalTicks);
			return;
		}
		if (gameplayTick < state.nextPendingAllocationTick) {
			return;
		}

		int moved = Math.min(1, Math.min(state.pendingHunger, maxHungerPoints - state.hungerPoints));
		if (moved <= 0) {
			return;
		}

		state.pendingHunger -= moved;
		state.hungerPoints += moved;
		state.lastPendingActivityTick = gameplayTick;
		state.nextPendingAllocationTick = gameplayTick + Math.max(1, settings.pendingAllocationIntervalTicks);
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HUNGER, "hunger.pending_applied")) {
			MadokuDebug.event("hunger.pending_applied", MadokuDebug.Domain.HUNGER)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.world(player.level().dimension().toString())
				.subject("player:" + player.getUUID())
				.field("applied", moved)
				.field("pending_hunger", state.pendingHunger)
				.field("hunger", state.hungerPoints)
				.log();
		}
	}

	private static void clearIdlePendingHunger(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (state.pendingHunger <= 0) {
			return;
		}
		long idleTicks = gameplayTick - state.lastPendingActivityTick;
		if (idleTicks < settings.pendingIdleTimeoutTicks) {
			return;
		}
		int cleared = state.pendingHunger;
		state.pendingHunger = 0;
		state.nextPendingAllocationTick = 0L;
		state.lastPendingActivityTick = gameplayTick;
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HUNGER, "hunger.pending_idle_cleared")) {
			MadokuDebug.event("hunger.pending_idle_cleared", MadokuDebug.Domain.HUNGER)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.world(player.level().dimension().toString())
				.subject("player:" + player.getUUID())
				.field("cleared_pending", cleared)
				.log();
		}
	}

	private static void initializeHungerFromPlayer(ServerPlayer player, PlayerState state, int maxHungerPoints) {
		if (state.hungerPoints >= 0) {
			state.hungerPoints = clampInternalHunger(state.hungerPoints, maxHungerPoints);
			state.pendingHunger = Math.max(0, state.pendingHunger);
			return;
		}

		state.hungerPoints = fromVanillaFood(player.getFoodData().getFoodLevel(), maxHungerPoints);
		state.pendingHunger = Math.max(0, state.pendingHunger);
		state.lastObservedAbsoluteDayTime = MadokuTime.getCurrentAbsoluteDayTime();
		state.lastPendingActivityTick = MadokuTicks.getGameplayTicks();
	}

	private static void applyFoodState(ServerPlayer player, int hungerPoints) {
		FoodData foodData = player.getFoodData();
		int vanillaFood = toVanillaFood(hungerPoints, settings.maximumHungerPoints);
		if (foodData.getFoodLevel() != vanillaFood) {
			foodData.setFoodLevel(vanillaFood);
		}
		if (foodData.getSaturationLevel() != 0.0f) {
			foodData.setSaturation(0.0f);
		}
	}

	private static void syncHudState(ServerPlayer player, PlayerState state, int maxHungerPoints) {
		if (player == null || state == null) {
			return;
		}
		if (state.lastSyncedCurrentHunger == state.hungerPoints
			&& state.lastSyncedPendingHunger == state.pendingHunger
			&& state.lastSyncedMaxHunger == maxHungerPoints) {
			return;
		}
		if (!HungerStateSync.send(player, state.hungerPoints, state.pendingHunger, maxHungerPoints)) {
			return;
		}
		state.lastSyncedCurrentHunger = state.hungerPoints;
		state.lastSyncedPendingHunger = state.pendingHunger;
		state.lastSyncedMaxHunger = maxHungerPoints;
	}

	private static int drainStateHunger(PlayerState state, int amount) {
		if (state == null || amount <= 0) {
			return 0;
		}
		int drained = Math.min(amount, state.hungerPoints);
		if (drained <= 0) {
			return 0;
		}
		state.hungerPoints -= drained;
		return drained;
	}

	private static boolean isExemptFromHungerDrain(ServerPlayer player) {
		return player != null && (player.isCreative() || player.isSpectator());
	}

	private static boolean isAtOrBelowSprintThreshold(int hungerPoints, int maxHungerPoints) {
		int safeMax = Math.max(1, maxHungerPoints);
		int safeHunger = Math.max(0, hungerPoints);
		return (long) safeHunger * 4L <= (long) safeMax;
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
		foodData.setSaturation(Math.max(0.0f, Math.min(foodData.getSaturationLevel(), updated)));
		return drained;
	}

	private static int clampInternalHunger(int value, int maxHungerPoints) {
		int max = Math.max(1, maxHungerPoints);
		return Math.max(0, Math.min(max, value));
	}

	private static int clampVanillaFood(int value) {
		return Math.max(0, Math.min(VANILLA_MAX_HUNGER_POINTS, value));
	}

	private static int toVanillaFood(int internalHunger, int maxHungerPoints) {
		int clampedInternal = clampInternalHunger(internalHunger, maxHungerPoints);
		if (maxHungerPoints <= 0) {
			return 0;
		}
		double ratio = (double) clampedInternal / (double) maxHungerPoints;
		return clampVanillaFood((int) Math.round(ratio * VANILLA_MAX_HUNGER_POINTS));
	}

	private static int fromVanillaFood(int vanillaFood, int maxHungerPoints) {
		int clampedVanilla = clampVanillaFood(vanillaFood);
		double ratio = (double) clampedVanilla / (double) VANILLA_MAX_HUNGER_POINTS;
		return clampInternalHunger((int) Math.round(ratio * maxHungerPoints), maxHungerPoints);
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
			JsonObject player = new JsonObject();
			player.addProperty("uuid", entry.getKey().toString());
			player.addProperty("hunger_points", state.hungerPoints);
			player.addProperty("pending_hunger", state.pendingHunger);
			player.addProperty("block_break_progress", state.blockBreakProgress);
			player.addProperty("travel_progress", state.travelProgress);
			player.addProperty("time_progress_ticks", state.timeProgressTicks);
			player.addProperty("last_observed_absolute_day_time", state.lastObservedAbsoluteDayTime);
			player.addProperty("last_pending_activity_tick", Math.max(0L, state.lastPendingActivityTick));
			player.addProperty("next_pending_allocation_tick", Math.max(0L, state.nextPendingAllocationTick));
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
		if (source == null) {
			return;
		}

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
			state.hungerPoints = clampInternalHunger((int) getLong(playerData, "hunger_points", settings.maximumHungerPoints), settings.maximumHungerPoints);
			state.pendingHunger = Math.max(0, (int) getLong(playerData, "pending_hunger", 0L));
			state.blockBreakProgress = Math.max(0, (int) getLong(playerData, "block_break_progress", 0L));
			state.travelProgress = Math.max(0.0d, getDouble(playerData, "travel_progress", 0.0d));
			state.timeProgressTicks = Math.max(0, (int) getLong(playerData, "time_progress_ticks", 0L));
			state.lastObservedAbsoluteDayTime = getLong(playerData, "last_observed_absolute_day_time", -1L);
			state.lastPendingActivityTick = Math.max(0L, getLong(playerData, "last_pending_activity_tick", 0L));
			state.nextPendingAllocationTick = Math.max(0L, getLong(playerData, "next_pending_allocation_tick", 0L));
			PLAYER_STATES.put(playerId, state);
		}
	}

	private static void loadStaticConfig() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			var configFile = MadokuAttributes.prepareSystemConfigFile(HUNGER_CONFIG_DIRECTORY_NAME, HUNGER_CONFIG_FILE_NAME);
			JsonObject normalized = StaticJsonSystem.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			StaticJsonSystem.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			settings = configured.withEnabled(MadokuAttributes.isEnabled());
		} catch (IOException | RuntimeException exception) {
			settings = fallback.withEnabled(MadokuAttributes.isEnabled());
			LOGGER.error("Failed to load MadokuHunger static config; using defaults.", exception);
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

	private static String formatDouble(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private static final class PlayerState {
		private int hungerPoints = -1;
		private int pendingHunger;
		private int blockBreakProgress;
		private double travelProgress;
		private int timeProgressTicks;
		private long lastObservedAbsoluteDayTime = -1L;
		private long lastPendingActivityTick;
		private long nextPendingAllocationTick;
		private long lastSaturationGainTick = Long.MIN_VALUE;
		private int lastSyncedCurrentHunger = Integer.MIN_VALUE;
		private int lastSyncedPendingHunger = Integer.MIN_VALUE;
		private int lastSyncedMaxHunger = Integer.MIN_VALUE;
		private double lastX = Double.NaN;
		private double lastZ = Double.NaN;
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

	private static final class Settings {
		private final boolean enabled;
		private final int maximumHungerPoints;
		private final int pendingAllocationIntervalTicks;
		private final long pendingIdleTimeoutTicks;
		private final int blockBreakGoal;
		private final double travelGoalDistance;
		private final int timeGoalTicks;
		private final double teleportDistanceThreshold;

		private Settings(
			boolean enabled,
			int maximumHungerPoints,
			int pendingAllocationIntervalTicks,
			long pendingIdleTimeoutTicks,
			int blockBreakGoal,
			double travelGoalDistance,
			int timeGoalTicks,
			double teleportDistanceThreshold
		) {
			this.enabled = enabled;
			this.maximumHungerPoints = maximumHungerPoints;
			this.pendingAllocationIntervalTicks = pendingAllocationIntervalTicks;
			this.pendingIdleTimeoutTicks = pendingIdleTimeoutTicks;
			this.blockBreakGoal = blockBreakGoal;
			this.travelGoalDistance = travelGoalDistance;
			this.timeGoalTicks = timeGoalTicks;
			this.teleportDistanceThreshold = teleportDistanceThreshold;
		}

		private static Settings defaults() {
			return new Settings(
				true,
				30,
				10,
				60L * 20L,
				128,
				150.0d,
				DEFAULT_TIME_GOAL_CLOCK_TICKS,
				16.0d
			);
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();

			boolean enabled = getBoolean(source, "enabled", defaults.enabled);
			int maximumHungerPoints = (int) clampLong(getLong(source, "maximum_hunger_points", defaults.maximumHungerPoints), 1L, MAX_CONFIG_HUNGER_POINTS);
			int pendingAllocationIntervalTicks = (int) clampLong(
				getLong(source, "pending_allocation_interval_ticks", defaults.pendingAllocationIntervalTicks),
				1L,
				200L
			);
			long pendingIdleTimeoutTicks = clampLong(
				getLong(source, "pending_idle_timeout_ticks", defaults.pendingIdleTimeoutTicks),
				20L,
				20L * 60L * 10L
			);
			int blockBreakGoal = (int) clampLong(getLong(source, "block_break_goal", defaults.blockBreakGoal), 1L, 100000L);
			double travelGoalDistance = clampDouble(getDouble(source, "travel_goal_distance", defaults.travelGoalDistance), 1.0d, 1000000.0d);
			int timeGoalTicks = (int) clampLong(getLong(source, "time_goal_ticks", defaults.timeGoalTicks), 1L, 20L * 60L * 60L * 24L);
			double teleportDistanceThreshold = clampDouble(
				getDouble(source, "teleport_distance_threshold", defaults.teleportDistanceThreshold),
				1.0d,
				1024.0d
			);

			return new Settings(
				enabled,
				maximumHungerPoints,
				pendingAllocationIntervalTicks,
				pendingIdleTimeoutTicks,
				blockBreakGoal,
				travelGoalDistance,
				timeGoalTicks,
				teleportDistanceThreshold
			);
		}

		private JsonObject toConfigJson() {
			JsonObject root = new JsonObject();
			root.addProperty("enabled", enabled);
			root.addProperty("maximum_hunger_points", maximumHungerPoints);
			root.addProperty("pending_allocation_interval_ticks", pendingAllocationIntervalTicks);
			root.addProperty("pending_idle_timeout_ticks", pendingIdleTimeoutTicks);
			root.addProperty("block_break_goal", blockBreakGoal);
			root.addProperty("travel_goal_distance", travelGoalDistance);
			root.addProperty("time_goal_ticks", timeGoalTicks);
			root.addProperty("teleport_distance_threshold", teleportDistanceThreshold);
			return root;
		}

		private Settings withEnabled(boolean attributesEnabled) {
			return new Settings(
				attributesEnabled && enabled,
				maximumHungerPoints,
				pendingAllocationIntervalTicks,
				pendingIdleTimeoutTicks,
				blockBreakGoal,
				travelGoalDistance,
				timeGoalTicks,
				teleportDistanceThreshold
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
