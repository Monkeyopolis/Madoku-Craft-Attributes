package madoku.craft.Health.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import madoku.craft.API.system.MadokuDataSystem;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Coordinates per-player health state, saving dirty changes through the API. */
public final class MadokuHealthManager {
	private static final int SAVE_INTERVAL_TICKS = 180 * 20;
	private static final int DEFAULT_MAX_HEALTH = 20;
	private static final int MAX_HUNGER_LEVEL = 20;
	private static final int PENDING_IDLE_RESET_TICKS = 60 * 20;
	private static final double HEALTH_STEP = 0.125;

	private static MadokuHealthManager INSTANCE;

	private final HealthConfig config;
	private final MadokuDataSystem.MadokuData savingData;
	private final JsonObject playersRoot;
	private final Map<UUID, PlayerHealthState> states = new HashMap<>();
	private boolean dirty;
	private int saveCooldown = SAVE_INTERVAL_TICKS;

	private MadokuHealthManager(HealthConfig config, MadokuDataSystem.MadokuData savingData) {
		this.config = config;
		this.savingData = savingData;
		JsonObject root = savingData.getRoot();
		JsonElement playersElem = root.get("players");
		if (playersElem instanceof JsonObject players) {
			this.playersRoot = players;
		} else {
			this.playersRoot = new JsonObject();
			root.add("players", playersRoot);
			markDirty();
		}
	}

	/** Creates the singleton manager instance. */
	public static void initialize(HealthConfig config, MadokuDataSystem.MadokuData data) {
		INSTANCE = new MadokuHealthManager(config, data);
	}

	/** Returns the singleton manager if initialization completed. */
	public static MadokuHealthManager getInstance() {
		return INSTANCE;
	}

	public boolean isFeatureEnabled() {
		return config.isFeatureEnabled();
	}

	public void onServerTick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			tickPlayer(player);
		}
		if (--saveCooldown <= 0) {
			saveCooldown = SAVE_INTERVAL_TICKS;
			flush();
		}
	}

	public void onPlayerJoin(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		if (!config.isFeatureEnabled()) {
			resetPlayerMaxHealth(player);
			return;
		}
		PlayerHealthState state = getState(player);
		ensurePlayerMaxHealth(player, config.getMaximumHealthPoints());
		player.setHealth((float) state.getCurrentHealthPoints());
	}

	public void onPlayerRespawn(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		if (!config.isFeatureEnabled()) {
			resetPlayerMaxHealth(player);
			return;
		}
		PlayerHealthState state = getState(player);
		double maxHealth = config.getMaximumHealthPoints();
		state.resetForRespawn(maxHealth, config.getPendingHealthTimer(), config.getRespawnHealthPercent());
		ensurePlayerMaxHealth(player, maxHealth);
		player.setHealth((float) state.getCurrentHealthPoints());
		var hungerManager = player.getHungerManager();
		hungerManager.setFoodLevel(MAX_HUNGER_LEVEL);
		hungerManager.setSaturationLevel(MAX_HUNGER_LEVEL);
	}

	public void onPlayerDeath(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		if (!config.isFeatureEnabled()) {
			return;
		}
		PlayerHealthState state = getState(player);
		double maxHealth = config.getMaximumHealthPoints();
		state.resetForRespawn(maxHealth, config.getPendingHealthTimer(), config.getRespawnHealthPercent());
	}

	public void flush() {
		if (dirty) {
			savingData.saveQueued();
			dirty = false;
		}
	}

	public void flushNow() {
		if (dirty) {
			savingData.save();
			dirty = false;
		}
	}

	public boolean addPendingFromFood(ServerPlayerEntity player, double amount) {
		if (!config.isFeatureEnabled() || player == null || amount <= 0) {
			return false;
		}
		PlayerHealthState state = getState(player);
		double configuredMaxHealth = config.getMaximumHealthPoints();
		ensurePlayerMaxHealth(player, configuredMaxHealth);
		state.setCurrentHealthPoints(player.getHealth(), configuredMaxHealth);
		if (state.getCurrentHealthPoints() >= player.getMaxHealth()) {
			return false;
		}
		double pendingGain = amount * (config.getFoodToPendingPercent() / 100.0);
		if (pendingGain <= 0.0) {
			return false;
		}
		state.addPendingHealthPoints(pendingGain, config.getPendingHealthTimer());
		return true;
	}

	private void tickPlayer(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		// Avoid ticking dead players; healing while on the death screen can disable respawn.
		if (!player.isAlive() || player.isRemoved()) {
			return;
		}
		if (!config.isFeatureEnabled()) {
			resetPlayerMaxHealth(player);
			return;
		}
		PlayerHealthState state = getState(player);
		double configuredMaxHealth = config.getMaximumHealthPoints();
		ensurePlayerMaxHealth(player, configuredMaxHealth);
		state.setCurrentHealthPoints(player.getHealth(), configuredMaxHealth);

		state.tickPendingIdleTimeout();

		if (state.tickPendingTimer(config.getPendingHealthTimer())
				&& state.getPendingHealthPoints() > 0.0
				&& state.getCurrentHealthPoints() < player.getMaxHealth()) {
			state.consumePendingPoint(config.getPendingHealthTimer());
			double addition = config.getPendingHealthMultiplier();
			if (addition > 0.0) {
				double target = Math.min(player.getMaxHealth(), state.getCurrentHealthPoints() + addition);
				state.setCurrentHealthPoints(target, configuredMaxHealth);
				player.setHealth((float) state.getCurrentHealthPoints());
			}
		}

		if (state.getCurrentHealthPoints() >= player.getMaxHealth()) {
			state.resetFoodDrainTimer(config.getHungerDepletionTimer());
			return;
		}

		if (state.tickFoodDrain(config.getHungerDepletionTimer())) {
			var hungerManager = player.getHungerManager();
			int hungerThreshold = hungerLevelThreshold(config.getHungerDepletionThreshold());
			if (hungerManager.getFoodLevel() >= hungerThreshold) {
				drainHunger(player, 1);
				state.addPendingHealthPoints(1.0, config.getPendingHealthTimer());
				ensurePlayerMaxHealth(player, configuredMaxHealth);
				state.setCurrentHealthPoints(player.getHealth(), configuredMaxHealth);
			} else {
				state.resetFoodDrainTimer(config.getHungerDepletionTimer());
			}
		}
	}

	private static void drainHunger(ServerPlayerEntity player, int hunger) {
		var hungerManager = player.getHungerManager();
		hungerManager.setFoodLevel(Math.max(0, hungerManager.getFoodLevel() - hunger));
		hungerManager.setSaturationLevel(0f);
	}

	private void ensurePlayerMaxHealth(ServerPlayerEntity player, double maxHealth) {
		EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
		if (attribute == null) {
			return;
		}
		double effectiveMax = roundToHealthStep(calculateEffectiveMaxHealth(player, maxHealth));
		if (Double.compare(attribute.getBaseValue(), effectiveMax) != 0) {
			attribute.setBaseValue(effectiveMax);
		}
		if (player.getHealth() > effectiveMax) {
			player.setHealth((float) effectiveMax);
		}
	}

	private double calculateEffectiveMaxHealth(ServerPlayerEntity player, double baseMax) {
		int foodLevel = player.getHungerManager().getFoodLevel();
		double thresholdPercent = config.getHungerHealthReductionThreshold();
		double maxReductionPercent = config.getMaximumHealthReduction();

		if (thresholdPercent <= 0.0 || maxReductionPercent <= 0.0) {
			return baseMax;
		}

		double foodPercent = (foodLevel / (double) MAX_HUNGER_LEVEL) * 100.0;
		if (foodPercent >= thresholdPercent) {
			return baseMax;
		}

		// Default 5-step curve: divide threshold and max reduction into 5 equal steps down to 0% food.
		double stepPercent = thresholdPercent / 5.0;
		double perStepReduction = maxReductionPercent / 5.0;
		if (stepPercent <= 0.0 || perStepReduction <= 0.0) {
			return baseMax;
		}

		double deficitPercent = thresholdPercent - foodPercent;
		int steps = (int) Math.floor(deficitPercent / stepPercent + 1e-9);
		double reductionPercent = Math.min(maxReductionPercent, steps * perStepReduction);
		double factor = Math.max(0.0, 1.0 - (reductionPercent / 100.0));
		return baseMax * factor;
	}

	private static int hungerLevelThreshold(double percent) {
		return Math.max(0, (int) Math.ceil((percent / 100.0) * MAX_HUNGER_LEVEL));
	}

	private static double roundToHealthStep(double value) {
		if (Double.isNaN(value) || value <= 0.0) {
			return 0.0;
		}
		double scaled = value / HEALTH_STEP;
		double floor = Math.floor(scaled);
		double lower = floor * HEALTH_STEP;
		double upper = lower + HEALTH_STEP;
		double lowerDiff = value - lower;
		double upperDiff = upper - value;
		return lowerDiff < upperDiff ? lower : upper;
	}

	private void resetPlayerMaxHealth(ServerPlayerEntity player) {
		EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
		if (attribute == null) {
			return;
		}
		if (attribute.getBaseValue() != DEFAULT_MAX_HEALTH) {
			attribute.setBaseValue(DEFAULT_MAX_HEALTH);
		}
		if (player.getHealth() > DEFAULT_MAX_HEALTH) {
			player.setHealth(DEFAULT_MAX_HEALTH);
		}
	}

	private PlayerHealthState getState(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		return states.computeIfAbsent(uuid, id -> {
			String key = id.toString();
			JsonObject node;
			JsonElement existing = playersRoot.get(key);
			if (existing instanceof JsonObject object) {
				node = object;
			} else {
				node = new JsonObject();
				playersRoot.add(key, node);
				markDirty();
			}
			return PlayerHealthState.load(node, this::markDirty, config.getMaximumHealthPoints(), config.getPendingHealthTimer(), config.getHungerDepletionTimer());
		});
	}

	private void markDirty() {
		dirty = true;
	}

	/** Tracks the persisted portion of a player's health data. */
	private static final class PlayerHealthState {
		private final JsonObject node;
		private final Runnable markDirty;
		private double currentHealthPoints;
		private double pendingHealthPoints;
		private int pendingTimer;
		private int pendingUnchangedTimer;
		private final int hungerDepletionTimer;
		private int foodDrainTimer;

		private PlayerHealthState(JsonObject node, Runnable markDirty, double defaultHealth, int defaultPendingTimer, int hungerDepletionTimer) {
			this.node = node;
			this.markDirty = markDirty;
			this.currentHealthPoints = loadDouble("currentHealthPoints", defaultHealth);
			this.pendingHealthPoints = loadDouble("pendingHealthPoints", 0.0);
			this.pendingTimer = Math.max(1, defaultPendingTimer);
			this.pendingUnchangedTimer = PENDING_IDLE_RESET_TICKS;
			this.hungerDepletionTimer = Math.max(1, hungerDepletionTimer);
			this.foodDrainTimer = this.hungerDepletionTimer;
			persist();
		}

		static PlayerHealthState load(JsonObject node, Runnable markDirty, double defaultHealth, int defaultPendingTimer, int hungerDepletionTimer) {
			return new PlayerHealthState(node, markDirty, defaultHealth, defaultPendingTimer, hungerDepletionTimer);
		}

		void setCurrentHealthPoints(double value, double maxHealth) {
			double clamped = clampAndRound(value, maxHealth);
			if (Double.compare(clamped, currentHealthPoints) != 0) {
				currentHealthPoints = clamped;
				persist();
			}
		}

		double getCurrentHealthPoints() {
			return currentHealthPoints;
		}

		double getPendingHealthPoints() {
			return pendingHealthPoints;
		}

		void resetForRespawn(double maxHealth, int pendingTimer, double respawnHealthPercent) {
			double percent = Math.min(100.0, Math.max(0.0, respawnHealthPercent));
			double targetHealth = maxHealth * (percent / 100.0);
			currentHealthPoints = clampAndRound(targetHealth, maxHealth);
			pendingHealthPoints = 0.0;
			this.pendingTimer = Math.max(1, pendingTimer);
			pendingUnchangedTimer = PENDING_IDLE_RESET_TICKS;
			foodDrainTimer = hungerDepletionTimer;
			persist();
		}

		void addPendingHealthPoints(double value, int pendingTimer) {
			if (value <= 0.0) {
				return;
			}
			setPendingHealthPoints(pendingHealthPoints + value);
			this.pendingTimer = Math.max(1, pendingTimer);
		}

		void consumePendingPoint(int pendingTimer) {
			if (pendingHealthPoints <= 0.0) {
				return;
			}
			setPendingHealthPoints(pendingHealthPoints - 1.0);
			this.pendingTimer = Math.max(1, pendingTimer);
		}

		boolean tickPendingTimer(int interval) {
			int sanitizedInterval = Math.max(1, interval);
			if (pendingHealthPoints <= 0.0) {
				pendingTimer = sanitizedInterval;
				return false;
			}
			if (--pendingTimer <= 0) {
				pendingTimer = sanitizedInterval;
				return true;
			}
			return false;
		}

		void tickPendingIdleTimeout() {
			if (pendingHealthPoints <= 0.0) {
				pendingUnchangedTimer = PENDING_IDLE_RESET_TICKS;
				return;
			}
			if (pendingUnchangedTimer > 0) {
				pendingUnchangedTimer--;
			}
			if (pendingUnchangedTimer <= 0) {
				setPendingHealthPoints(0.0);
			}
		}

		boolean tickFoodDrain(int interval) {
			int sanitizedInterval = Math.max(1, interval);
			if (--foodDrainTimer <= 0) {
				foodDrainTimer = sanitizedInterval;
				return true;
			}
			return false;
		}

		void resetFoodDrainTimer(int interval) {
			foodDrainTimer = Math.max(1, interval);
		}

		private static double clampAndRound(double value, double max) {
			double upper = Math.min(max, value);
			upper = Math.max(0.0, upper);
			return roundToIncrement(upper);
		}

		private double loadDouble(String key, double fallback) {
			JsonElement element = node.get(key);
			double value = fallback;
			if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
				value = primitive.getAsDouble();
			}
			return roundToIncrement(Math.max(0.0, value));
		}

		private static double roundToIncrement(double value) {
			if (Double.isNaN(value) || value <= 0) {
				return 0;
			}
			double scaled = value / DOUBLE_STEP;
			double floor = Math.floor(scaled);
			double lower = floor * DOUBLE_STEP;
			double upper = lower + DOUBLE_STEP;
			double lowerDiff = value - lower;
			double upperDiff = upper - value;
			return lowerDiff < upperDiff ? lower : upper;
		}

		private void setPendingHealthPoints(double value) {
			double sanitized = roundToIncrement(Math.max(0.0, value));
			if (Double.compare(sanitized, pendingHealthPoints) != 0) {
				pendingHealthPoints = sanitized;
				pendingUnchangedTimer = PENDING_IDLE_RESET_TICKS;
				persist();
			}
		}

		private void persist() {
			node.addProperty("currentHealthPoints", currentHealthPoints);
			node.addProperty("pendingHealthPoints", pendingHealthPoints);
			markDirty.run();
		}

		private static final double DOUBLE_STEP = 0.125;
	}
}
