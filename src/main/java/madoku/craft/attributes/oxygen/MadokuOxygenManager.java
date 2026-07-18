
package madoku.craft.attributes.oxygen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.attributes.MadokuCraftAttributes;
import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.data.DataPlayerManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MadokuOxygenManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuOxygenManager.class);
	private static final String DATA_FILE_NAME = "madoku-oxygen";
	private static final String TASK_TYPE_OXYGEN_PLAYER_TICK = "oxygen_player_tick";
	private static final String OXYGEN_PLAYER_TICK_SCHEDULER_KEY = "oxygen_player_tick";
	private static final long OXYGEN_PLAYER_TICK_MIN_INTERVAL = 1L;
	private static final long OXYGEN_PLAYER_TICK_MAX_INTERVAL = 5L;
	private static final long SUFFOCATING_PENALTY_INTERVAL_TICKS = 20L;
	private static final int OXYGEN_DRAIN_PER_TICK = 1;
	private static final int OXYGEN_RECOVERY_PER_TICK = 5;
	private static final int VANILLA_MAX_AIR_SUPPLY_TICKS = 300;
	private static final Identifier CONDUIT_POWER_MINING_SPEED_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraftAttributes.MOD_ID, "madoku_oxygen_conduit_power_mining_speed");

	private static final Map<UUID, PlayerState> PLAYER_STATES = new HashMap<>();
	private static volatile OxygenConfigManager.Settings settings = OxygenConfigManager.Settings.defaults();
	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile String schedulerId = "";
	private static volatile boolean tickQueued;

	private MadokuOxygenManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		MadokuSchedulerManager.registerTaskHandler(TASK_TYPE_OXYGEN_PLAYER_TICK, MadokuOxygenManager::runPlayerTickTask);
		ServerPlayerEvents.JOIN.register(MadokuOxygenManager::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register(MadokuOxygenManager::handlePlayerRespawn);
	}

	public static void reset() {
		PLAYER_STATES.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
		schedulerId = "";
		tickQueued = false;
		MadokuSchedulerManager.clearAdaptiveDelayState(OXYGEN_PLAYER_TICK_SCHEDULER_KEY);
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
		DataPlayerManager.setSystemData(DATA_FILE_NAME, toPersistedData());
	}

	public static void onServerStarted(MinecraftServer server) {
		ensureQueued(server);
	}

	public static boolean isEnabled() {
		return settings.oxygen.enabled;
	}

	public static boolean shouldSuppressVanillaBreathingEffects(LivingEntity entity) {
		if (!settings.oxygen.enabled || !(entity instanceof Player)) {
			return false;
		}

		return (settings.waterBreathing.enabled && getEffectLevel(entity, MobEffects.WATER_BREATHING) > 0)
			|| (settings.conduitPower.enabled && getEffectLevel(entity, MobEffects.CONDUIT_POWER) > 0)
			|| (settings.breathOfTheNautilus.enabled && getEffectLevel(entity, MobEffects.BREATH_OF_THE_NAUTILUS) > 0);
	}

	public static boolean shouldSuppressVanillaConduitMiningSpeed(LivingEntity entity) {
		return settings.oxygen.enabled
			&& settings.conduitPower.enabled
			&& entity instanceof Player
			&& getEffectLevel(entity, MobEffects.CONDUIT_POWER) > 0;
	}

	public static boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) {
		if (!settings.oxygen.enabled || !(entity instanceof Player) || effect == null) {
			return false;
		}

		return (settings.waterBreathing.enabled && effect == MobEffects.WATER_BREATHING.value())
			|| (settings.conduitPower.enabled && effect == MobEffects.CONDUIT_POWER.value())
			|| (settings.dolphinsGrace.enabled && effect == MobEffects.DOLPHINS_GRACE.value())
			|| (settings.breathOfTheNautilus.enabled && effect == MobEffects.BREATH_OF_THE_NAUTILUS.value());
	}

	public static boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) {
		return shouldOverrideVanillaEffectAttributes(entity, effect);
	}

	public static boolean shouldAdjustSuffocatingPenalty(ServerPlayer player, DamageSource source) {
		if (!settings.oxygen.enabled
			|| !settings.oxygen.suffocatingPenalty.enabled
			|| player == null
			|| source == null
			|| !source.is(DamageTypes.DROWN)) {
			return false;
		}
		return player.getAirSupply() <= 0;
	}

	public static float resolveSuffocatingPenaltyDamage(ServerPlayer player) {
		if (player == null || !settings.oxygen.enabled || !settings.oxygen.suffocatingPenalty.enabled) {
			return 0.0f;
		}

		double maxHealth = Math.max(1.0d, player.getMaxHealth());
		double amount = settings.oxygen.suffocatingPenalty.type == OxygenConfigManager.ValueType.FLAT
			? settings.oxygen.suffocatingPenalty.value
			: maxHealth * settings.oxygen.suffocatingPenalty.value;
		if (!Double.isFinite(amount) || amount <= 0.0d) {
			return 0.0f;
		}
		return (float) amount;
	}

	public static int getMaximumOxygenTicksForEntity(LivingEntity entity) {
		int baseMaximumOxygenTicks = Math.max(1, settings.oxygen.maxOxygenTicks);
		if (entity == null) {
			return baseMaximumOxygenTicks;
		}

		long totalOxygenTicks = baseMaximumOxygenTicks;
		totalOxygenTicks += getOxygenContributionTicks(
			entity,
			settings.waterBreathing.enabled,
			settings.waterBreathing.oxygen,
			MobEffects.WATER_BREATHING,
			"water-breathing"
		);
		totalOxygenTicks += getOxygenContributionTicks(
			entity,
			settings.conduitPower.enabled,
			settings.conduitPower.oxygen,
			MobEffects.CONDUIT_POWER,
			"conduit-power"
		);
		totalOxygenTicks += getOxygenContributionTicks(
			entity,
			settings.dolphinsGrace.enabled,
			settings.dolphinsGrace.oxygen,
			MobEffects.DOLPHINS_GRACE,
			"dolphins-grace"
		);
		totalOxygenTicks += getOxygenContributionTicks(
			entity,
			settings.breathOfTheNautilus.enabled,
			settings.breathOfTheNautilus.oxygen,
			MobEffects.BREATH_OF_THE_NAUTILUS,
			"breath-of-the-nautilus"
		);

		if (totalOxygenTicks >= Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return Math.max(1, (int) totalOxygenTicks);
	}

	public static void handlePlayerEffectsChanged(ServerPlayer player) {
		if (player == null || !settings.oxygen.enabled) {
			return;
		}

		int oxygenCapTicks = getMaximumOxygenTicksForEntity(player);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.lastProcessedGameplayTick = MadokuTimeManager.getGameplayTicks();
		synchronizeOxygenState(player, state, oxygenCapTicks);
		refreshEffectModifiers(player);
		applyVanillaCompatibleAirSupply(player, state.oxygenTicks, oxygenCapTicks);
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
			OXYGEN_PLAYER_TICK_SCHEDULER_KEY,
			OXYGEN_PLAYER_TICK_MIN_INTERVAL,
			OXYGEN_PLAYER_TICK_MAX_INTERVAL
		);
		if (MadokuSchedulerManager.hasQueuedTask(currentSchedulerId, TASK_TYPE_OXYGEN_PLAYER_TICK)) {
			tickQueued = true;
			return;
		}
		if (enqueue(currentSchedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		schedulerId = MadokuSchedulerManager.createOrGetScheduler(
			MadokuSchedulerManager.SchedulerBinding.global(OXYGEN_PLAYER_TICK_SCHEDULER_KEY)
		);
		if (enqueue(schedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		LOGGER.error("Failed to enqueue oxygen player tick task.");
	}

	private static String ensureScheduler() {
		String current = schedulerId;
		if (current != null && !current.isBlank()) {
			return current;
		}
		schedulerId = MadokuSchedulerManager.createOrGetScheduler(
			MadokuSchedulerManager.SchedulerBinding.global(OXYGEN_PLAYER_TICK_SCHEDULER_KEY)
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
			TASK_TYPE_OXYGEN_PLAYER_TICK,
			new JsonObject(),
			MadokuSchedulerManager.TickDomain.GAMEPLAY
		);
		return status == MadokuSchedulerManager.EnqueueStatus.ACCEPTED
			|| status == MadokuSchedulerManager.EnqueueStatus.QUEUE_FULL;
	}

	private static void onGameplayTick(MinecraftServer server, long gameplayTick) {
		if (server == null || !settings.oxygen.enabled) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			processPlayer(player, gameplayTick);
		}
	}

	private static void processPlayer(ServerPlayer player, long gameplayTick) {
		if (player == null || !player.isAlive() || player.isDeadOrDying()) {
			return;
		}

		int oxygenCapTicks = getMaximumOxygenTicksForEntity(player);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeOxygenFromPlayer(player, state, oxygenCapTicks);
		long elapsedTicks = consumeElapsedTicks(state, gameplayTick);

		if (player.isSpectator() || player.getAbilities().invulnerable) {
			state.oxygenTicks = oxygenCapTicks;
			state.lastKnownMaximumOxygenTicks = oxygenCapTicks;
			state.lastDrowningDamageTick = Long.MIN_VALUE;
			state.lastProcessedGameplayTick = gameplayTick;
			refreshEffectModifiers(player);
			applyVanillaCompatibleAirSupply(player, state.oxygenTicks, oxygenCapTicks);
			emitOxygenDebug(
				"oxygen",
				"oxygen.restored",
				player,
				gameplayTick,
				Map.of(
					"oxygen_ticks", Integer.toString(state.oxygenTicks),
					"oxygen_cap_ticks", Integer.toString(oxygenCapTicks),
					"mode", player.isSpectator() ? "spectator" : "invulnerable"
				)
			);
			return;
		}

		if (shouldDrainOxygen(player)) {
			int oxygenBefore = state.oxygenTicks;
			int drained = clampInt((int) Math.min(Integer.MAX_VALUE, elapsedTicks * (long) OXYGEN_DRAIN_PER_TICK), 0, oxygenBefore);
			state.oxygenTicks = clampInt(oxygenBefore - drained, 0, oxygenCapTicks);
			if (state.oxygenTicks <= 0) {
				long zeroCrossTick = gameplayTick;
				if (oxygenBefore > 0 && drained > 0) {
					long ticksToZero = (oxygenBefore + OXYGEN_DRAIN_PER_TICK - 1L) / OXYGEN_DRAIN_PER_TICK;
					long ticksAfterZero = Math.max(0L, elapsedTicks - ticksToZero);
					zeroCrossTick = Math.max(0L, gameplayTick - ticksAfterZero);
				}
				applyCustomDrowningDamage(player, state, gameplayTick, zeroCrossTick);
			} else {
				state.lastDrowningDamageTick = Long.MIN_VALUE;
			}
			emitOxygenDebug(
				"oxygen",
				"oxygen.tick_drain",
				player,
				gameplayTick,
				Map.of(
					"oxygen_before", Integer.toString(oxygenBefore),
					"drained", Integer.toString(drained),
					"oxygen_ticks", Integer.toString(state.oxygenTicks),
					"oxygen_cap_ticks", Integer.toString(oxygenCapTicks),
					"elapsed_ticks", Long.toString(elapsedTicks),
					"action", "drain"
				)
			);
		} else {
			int oxygenBefore = state.oxygenTicks;
			int recovered = clampInt((int) Math.min(Integer.MAX_VALUE, elapsedTicks * (long) OXYGEN_RECOVERY_PER_TICK), 0, oxygenCapTicks - oxygenBefore);
			state.oxygenTicks = clampInt(oxygenBefore + recovered, 0, oxygenCapTicks);
			state.lastDrowningDamageTick = Long.MIN_VALUE;
			emitOxygenDebug(
				"oxygen",
				"oxygen.tick_recover",
				player,
				gameplayTick,
				Map.of(
					"oxygen_before", Integer.toString(oxygenBefore),
					"recovered", Integer.toString(recovered),
					"oxygen_ticks", Integer.toString(state.oxygenTicks),
					"oxygen_cap_ticks", Integer.toString(oxygenCapTicks),
					"elapsed_ticks", Long.toString(elapsedTicks),
					"action", "recover"
				)
			);
		}

		state.lastKnownMaximumOxygenTicks = oxygenCapTicks;
		state.lastProcessedGameplayTick = gameplayTick;
		refreshEffectModifiers(player);
		applyVanillaCompatibleAirSupply(player, state.oxygenTicks, oxygenCapTicks);
	}

	private static void synchronizeOxygenState(ServerPlayer player, PlayerState state, int oxygenCapTicks) {
		if (player == null || state == null) {
			return;
		}

		int safeCurrentCap = Math.max(1, oxygenCapTicks);
		int previousCap = state.lastKnownMaximumOxygenTicks > 0 ? state.lastKnownMaximumOxygenTicks : safeCurrentCap;
		if (state.oxygenTicks < 0) {
			state.oxygenTicks = decodeAirSupplyFromVanillaHud(player.getAirSupply(), safeCurrentCap);
		}

		if (safeCurrentCap != previousCap) {
			state.oxygenTicks = clampInt(state.oxygenTicks + (safeCurrentCap - previousCap), 0, safeCurrentCap);
		} else {
			state.oxygenTicks = clampInt(state.oxygenTicks, 0, safeCurrentCap);
		}

		state.lastKnownMaximumOxygenTicks = safeCurrentCap;
	}

	private static void refreshEffectModifiers(ServerPlayer player) {
		if (player == null) {
			return;
		}

		double conduitPowerBonus = resolveConduitPowerMiningSpeedBonus(player);
		applyPercentageAttributeModifier(
			player,
			Attributes.SUBMERGED_MINING_SPEED,
			CONDUIT_POWER_MINING_SPEED_MODIFIER_ID,
			conduitPowerBonus,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		if (conduitPowerBonus > 0.0d) {
			emitOxygenDebug(
				"conduit-power",
				"oxygen.conduit_power_applied",
				player,
				MadokuTimeManager.getGameplayTicks(),
				Map.of("mining_speed_bonus", Double.toString(conduitPowerBonus))
			);
		}
	}

	private static double resolveConduitPowerMiningSpeedBonus(ServerPlayer player) {
		if (player == null || !settings.oxygen.enabled || !settings.conduitPower.enabled) {
			return 0.0d;
		}

		int effectLevel = getEffectLevel(player, MobEffects.CONDUIT_POWER);
		if (effectLevel <= 0) {
			return 0.0d;
		}
		return Math.max(0.0d, settings.conduitPower.miningSpeed.value * (double) effectLevel);
	}

	public static double resolveDolphinsGraceSwimmingSpeedBonus(LivingEntity entity) {
		if (entity == null || !settings.oxygen.enabled || !settings.dolphinsGrace.enabled) {
			return 0.0d;
		}

		int effectLevel = getEffectLevel(entity, MobEffects.DOLPHINS_GRACE);
		if (effectLevel <= 0) {
			return 0.0d;
		}
		return Math.max(0.0d, settings.dolphinsGrace.swimmingSpeed.value * (double) effectLevel);
	}

	private static void applyPercentageAttributeModifier(
		ServerPlayer player,
		net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
		Identifier modifierId,
		double amount,
		AttributeModifier.Operation operation
	) {
		if (player == null || attribute == null || modifierId == null || operation == null) {
			return;
		}

		AttributeInstance attributeInstance = player.getAttribute(attribute);
		if (attributeInstance == null) {
			return;
		}

		attributeInstance.removeModifier(modifierId);
		if (!Double.isFinite(amount) || amount <= 0.0d) {
			return;
		}

		attributeInstance.addOrUpdateTransientModifier(
			new AttributeModifier(modifierId, amount, operation)
		);
	}

	private static int getOxygenContributionTicks(
		LivingEntity entity,
		boolean enabled,
		OxygenConfigManager.TicksSettings oxygen,
		Holder<MobEffect> effect,
		String group
	) {
		if (entity == null || !enabled || oxygen == null || effect == null) {
			return 0;
		}

		int level = getEffectLevel(entity, effect);
		if (level <= 0) {
			return 0;
		}

		long contribution = oxygen.value * (long) level;
		if (contribution <= 0L) {
			return 0;
		}
		if (group != null && !group.isBlank() && entity instanceof ServerPlayer player) {
			emitOxygenDebug(
				group,
				"oxygen.contribution",
				player,
				MadokuTimeManager.getGameplayTicks(),
				Map.of(
					"effect_level", Integer.toString(level),
					"contribution_ticks", Long.toString(contribution),
					"oxygen_value", Long.toString(oxygen.value)
				)
			);
		}
		if (contribution >= Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return (int) contribution;
	}

	private static int getEffectLevel(LivingEntity entity, Holder<MobEffect> effect) {
		if (entity == null || effect == null) {
			return 0;
		}
		MobEffectInstance effectInstance;
		try {
			effectInstance = entity.getEffect(effect);
		} catch (RuntimeException exception) {
			return 0;
		}
		if (effectInstance == null) {
			return 0;
		}
		return Math.max(0, effectInstance.getAmplifier() + 1);
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null || !settings.oxygen.enabled) {
			return;
		}

		int oxygenCapTicks = getMaximumOxygenTicksForEntity(player);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeOxygenFromPlayer(player, state, oxygenCapTicks);
		state.lastProcessedGameplayTick = MadokuTimeManager.getGameplayTicks();
		synchronizeOxygenState(player, state, oxygenCapTicks);
		refreshEffectModifiers(player);
		applyVanillaCompatibleAirSupply(player, state.oxygenTicks, oxygenCapTicks);
		emitOxygenDebug(
			"oxygen",
			"oxygen.join_synced",
			player,
			MadokuTimeManager.getGameplayTicks(),
			Map.of(
				"oxygen_ticks", Integer.toString(state.oxygenTicks),
				"oxygen_cap_ticks", Integer.toString(oxygenCapTicks)
			)
		);
	}

	private static void handlePlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null || alive || !settings.oxygen.enabled) {
			return;
		}

		int oxygenCapTicks = getMaximumOxygenTicksForEntity(newPlayer);
		PlayerState state = PLAYER_STATES.computeIfAbsent(newPlayer.getUUID(), ignored -> new PlayerState());
		state.oxygenTicks = oxygenCapTicks;
		state.lastDrowningDamageTick = Long.MIN_VALUE;
		state.lastKnownMaximumOxygenTicks = oxygenCapTicks;
		state.lastProcessedGameplayTick = MadokuTimeManager.getGameplayTicks();
		refreshEffectModifiers(newPlayer);
		applyVanillaCompatibleAirSupply(newPlayer, state.oxygenTicks, oxygenCapTicks);
		emitOxygenDebug(
			"oxygen",
			"oxygen.respawn_synced",
			newPlayer,
			MadokuTimeManager.getGameplayTicks(),
			Map.of(
				"oxygen_ticks", Integer.toString(state.oxygenTicks),
				"oxygen_cap_ticks", Integer.toString(oxygenCapTicks)
			)
		);
	}

	private static void applyCustomDrowningDamage(ServerPlayer player, PlayerState state, long gameplayTick, long zeroCrossTick) {
		if (player == null || state == null || state.oxygenTicks > 0) {
			return;
		}
		if (!player.isAlive() || player.isDeadOrDying()) {
			return;
		}
		if (!player.isEyeInFluid(FluidTags.WATER)) {
			return;
		}
		if (!settings.oxygen.enabled || !settings.oxygen.suffocatingPenalty.enabled) {
			return;
		}

		float adjustedDamage = resolveSuffocatingPenaltyDamage(player);
		if (adjustedDamage <= 0.0f) {
			return;
		}

		long nextDamageTick = state.lastDrowningDamageTick == Long.MIN_VALUE
			? zeroCrossTick
			: Math.max(state.lastDrowningDamageTick + SUFFOCATING_PENALTY_INTERVAL_TICKS, zeroCrossTick);
		long damageApplications = 0L;
		while (nextDamageTick <= gameplayTick
			&& player.isAlive()
			&& !player.isDeadOrDying()
			&& player.isEyeInFluid(FluidTags.WATER)
			&& state.oxygenTicks <= 0) {
			state.lastDrowningDamageTick = nextDamageTick;
			player.hurtServer(player.level(), player.damageSources().drown(), adjustedDamage);
			damageApplications++;
			nextDamageTick += SUFFOCATING_PENALTY_INTERVAL_TICKS;
		}
		if (damageApplications > 0L) {
			emitOxygenDebug(
				"suffocating-penalty",
				"oxygen.suffocating_penalty_applied",
				player,
				gameplayTick,
				Map.of(
					"damage", Float.toString(adjustedDamage),
					"oxygen_ticks", Integer.toString(state.oxygenTicks),
					"damage_applications", Long.toString(damageApplications),
					"zero_cross_tick", Long.toString(zeroCrossTick)
				)
			);
		}
	}

	private static boolean shouldDrainOxygen(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		if (!player.isEyeInFluid(FluidTags.WATER)) {
			return false;
		}
		return !player.canBreatheUnderwater();
	}

	private static void applyVanillaCompatibleAirSupply(ServerPlayer player, int oxygenTicks, int oxygenCapTicks) {
		if (player == null) {
			return;
		}

		int encodedAir = encodeAirSupplyForVanillaHud(oxygenTicks, oxygenCapTicks);
		if (player.getAirSupply() != encodedAir) {
			player.setAirSupply(encodedAir);
		}
	}

	private static void loadStaticConfig() {
		settings = OxygenConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
	}

	private static void initializeOxygenFromPlayer(ServerPlayer player, PlayerState state, int oxygenCapTicks) {
		if (player == null || state == null) {
			return;
		}

		if (state.oxygenTicks >= 0) {
			state.oxygenTicks = clampInt(state.oxygenTicks, 0, oxygenCapTicks);
			if (state.lastKnownMaximumOxygenTicks < 0) {
				state.lastKnownMaximumOxygenTicks = oxygenCapTicks;
			}
			return;
		}

		state.oxygenTicks = shouldDrainOxygen(player)
			? decodeAirSupplyFromVanillaHud(player.getAirSupply(), oxygenCapTicks)
			: oxygenCapTicks;
		state.lastKnownMaximumOxygenTicks = oxygenCapTicks;
	}

	private static JsonObject toPersistedData() {
		int oxygenCapTicks = settings.oxygen.maxOxygenTicks;
		madoku.craft.api.json.JSONFormatManager.ArrayBuilder players = madoku.craft.api.json.JSONFormatManager.array();
		for (Map.Entry<UUID, PlayerState> entry : PLAYER_STATES.entrySet()) {
			PlayerState state = entry.getValue();
			if (!state.hasPersistableState(oxygenCapTicks)) {
				continue;
			}

			players.object(player -> player
				.put("uuid", entry.getKey().toString())
				.put("oxygen-ticks", state.oxygenTicks));
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
			long persistedOxygenTicks = getLong(playerData, "oxygen-ticks", settings.oxygen.maxOxygenTicks);
			if (persistedOxygenTicks < 0L) {
				persistedOxygenTicks = 0L;
			}
			if (persistedOxygenTicks > Integer.MAX_VALUE) {
				persistedOxygenTicks = Integer.MAX_VALUE;
			}
			state.oxygenTicks = (int) persistedOxygenTicks;
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

	private static void emitOxygenDebug(String group, String metricId, ServerPlayer player, long gameplayTick, Map<String, String> fields) {
		if (player == null || metricId == null || metricId.isBlank()) {
			return;
		}
		String entry = MadokuDebugManager.resolveCallerMethodName();
		if (!MadokuDebugManager.shouldEmit("attributes", "oxygen", entry)) {
			return;
		}

		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(metricId, "attributes", "oxygen", entry)
			.side(MadokuDebugManager.Side.SERVER)
			.tick(gameplayTick)
			.subject("player:" + player.getUUID());
		if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			builder.world(serverLevel.dimension().toString());
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

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int encodeAirSupplyForVanillaHud(int oxygenTicks, int oxygenCapTicks) {
		int safeCap = Math.max(1, oxygenCapTicks);
		int safeOxygen = clampInt(oxygenTicks, 0, safeCap);
		if (safeCap <= VANILLA_MAX_AIR_SUPPLY_TICKS) {
			return safeOxygen;
		}
		double ratio = safeOxygen / (double) safeCap;
		return clampInt((int) Math.round(ratio * VANILLA_MAX_AIR_SUPPLY_TICKS), 0, VANILLA_MAX_AIR_SUPPLY_TICKS);
	}

	private static int decodeAirSupplyFromVanillaHud(int observedAirSupply, int oxygenCapTicks) {
		int safeCap = Math.max(1, oxygenCapTicks);
		int clampedObserved = clampInt(observedAirSupply, 0, VANILLA_MAX_AIR_SUPPLY_TICKS);
		if (safeCap <= VANILLA_MAX_AIR_SUPPLY_TICKS) {
			return clampInt(clampedObserved, 0, safeCap);
		}
		double ratio = clampedObserved / (double) VANILLA_MAX_AIR_SUPPLY_TICKS;
		return clampInt((int) Math.round(ratio * safeCap), 0, safeCap);
	}

	private static final class PlayerState {
		private int oxygenTicks = -1;
		private long lastDrowningDamageTick = Long.MIN_VALUE;
		private int lastKnownMaximumOxygenTicks = -1;
		private long lastProcessedGameplayTick = Long.MIN_VALUE;

		private boolean hasPersistableState(int oxygenCapTicks) {
			return oxygenTicks >= 0 && oxygenTicks != oxygenCapTicks;
		}
	}
}


