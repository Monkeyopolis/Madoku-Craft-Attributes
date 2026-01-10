package madoku.craft.Health.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import madoku.craft.API.system.MadokuSavingSystem;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Coordinates per-player health state, saving dirty changes through the API. */
public final class MadokuHealthManager {
	private static final int SAVE_INTERVAL_TICKS = 600;
	private static final int DEFAULT_MAX_HEALTH = 20;
	private static final int MAX_HUNGER_LEVEL = 20;
	private static final int PENDING_CLEAR_DURATION = 5 * 20;
	private static final int SURPLUS_CLEAR_DURATION = 300;

	private static MadokuHealthManager INSTANCE;

	private final HealthConfig config;
	private final MadokuSavingSystem.MadokuData savingData;
	private final JsonObject playersRoot;
	private final Map<UUID, PlayerHealthState> states = new HashMap<>();
	private boolean dirty;
	private int saveCooldown = SAVE_INTERVAL_TICKS;

	private MadokuHealthManager(HealthConfig config, MadokuSavingSystem.MadokuData savingData) {
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
	public static void initialize(HealthConfig config, MadokuSavingSystem.MadokuData data) {
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
			savingData.save();
			dirty = false;
		}
	}

	public boolean addPendingFromFood(ServerPlayerEntity player, double amount) {
		if (!config.isFeatureEnabled() || player == null || amount <= 0) {
			return false;
		}
		PlayerHealthState state = getState(player);
		double maxHealth = config.getMaximumHealthPoints();
		if (state.getCurrentHealthPoints() >= maxHealth) {
			state.addHealthSurplusPoints(amount);
			return true;
		}
		double remainder = state.offerPendingPoints(amount, maxHealth, config.getPendingHealthTimer());
		if (remainder > 0.0) {
			state.addHealthSurplusPoints(remainder);
		}
		return amount > 0;
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
		double maxHealth = config.getMaximumHealthPoints();
		ensurePlayerMaxHealth(player, maxHealth);
		state.setCurrentHealthPoints(player.getHealth(), maxHealth);

		state.tickSurplusClearTimer();
		handlePendingConversion(state, player, maxHealth);

		if (state.getCurrentHealthPoints() >= maxHealth) {
			return;
		}

		if (state.getHealthSurplusPoints() > 0.0) {
			double transferred = state.moveSurplusToPending(maxHealth, config.getPendingHealthTimer());
			if (transferred > 0.0) {
				return;
			}
		}

		var hungerManager = player.getHungerManager();
		int hungerThreshold = hungerLevelThreshold(config.getHungerDepletionThreshold());
		boolean canDrainFood = hungerManager.getFoodLevel() >= hungerThreshold && state.getPendingCapacity(maxHealth) > 0.0;
		if (canDrainFood && state.tickFoodDrain()) {
			double remainder = state.offerPendingPoints(1.0, maxHealth, config.getPendingHealthTimer());
			if (remainder > 0.0) {
				state.addHealthSurplusPoints(remainder);
			}
			drainHunger(player, 1);
			if (state.getPendingCapacity(maxHealth) <= 0.0) {
				state.movePendingToSurplus();
			}
		} else if (!canDrainFood) {
			state.resetFoodDrainTimer();
		}
	}

	private void handlePendingConversion(PlayerHealthState state, ServerPlayerEntity player, double maxHealth) {
		if (state.getPendingHealthPoints() <= 0) {
			state.resetPendingTimer(config.getPendingHealthTimer());
			state.resetPendingClearTimer();
			return;
		}

		if (state.getCurrentHealthPoints() >= maxHealth) {
			if (state.tickPendingClearTimer()) {
				state.movePendingToSurplus();
			}
			return;
		}

		state.resetPendingClearTimer();
		if (state.decrementPendingTimer()) {
			state.consumePendingPoint();
			double addition = config.getPendingHealthMultiplier();
			double target = Math.min(maxHealth, state.getCurrentHealthPoints() + addition);
			state.setCurrentHealthPoints(target, maxHealth);
			player.setHealth((float) state.getCurrentHealthPoints());
			state.resetPendingTimer(config.getPendingHealthTimer());
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
		double effectiveMax = calculateEffectiveMaxHealth(player, maxHealth);
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
			double surplusCap = Math.max(0.0, (config.getMaximumHealthSurplusPoints() / 100.0) * MAX_HUNGER_LEVEL);
		return PlayerHealthState.load(node, this::markDirty, config.getMaximumHealthPoints(), config.getPendingHealthTimer(), config.getHungerDepletionTimer(), surplusCap);
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
		private double healthSurplusPoints;
		private int pendingTimer;
		private int pendingClearTimer;
		private int surplusClearTimer;
		private final int hungerDepletionTimer;
		private final double maxSurplusPoints;
		private int foodDrainTimer;

		private PlayerHealthState(JsonObject node, Runnable markDirty, double defaultHealth, int defaultPendingTimer, int hungerDepletionTimer, double maxSurplusPoints) {
			this.node = node;
			this.markDirty = markDirty;
			this.currentHealthPoints = loadDouble("currentHealthPoints", defaultHealth);
			this.pendingHealthPoints = loadDouble("pendingHealthPoints", 0.0);
			this.healthSurplusPoints = loadDouble("healthSurplusPoints", 0.0);
			this.pendingTimer = Math.max(1, defaultPendingTimer);
			this.pendingClearTimer = PENDING_CLEAR_DURATION;
			this.surplusClearTimer = loadInt("surplusClearTimer", SURPLUS_CLEAR_DURATION);
			this.hungerDepletionTimer = Math.max(1, hungerDepletionTimer);
			this.maxSurplusPoints = Math.max(0.0, maxSurplusPoints);
			this.foodDrainTimer = this.hungerDepletionTimer;
			persist();
		}

		static PlayerHealthState load(JsonObject node, Runnable markDirty, double defaultHealth, int defaultPendingTimer, int hungerDepletionTimer, double maxSurplusPoints) {
			return new PlayerHealthState(node, markDirty, defaultHealth, defaultPendingTimer, hungerDepletionTimer, maxSurplusPoints);
		}

		double getCurrentHealthPoints() {
			return currentHealthPoints;
		}

		double getPendingHealthPoints() {
			return pendingHealthPoints;
		}

		double getHealthSurplusPoints() {
			return healthSurplusPoints;
		}

		void setCurrentHealthPoints(double value, double maxHealth) {
			double clamped = clampAndRound(value, maxHealth);
			if (Double.compare(clamped, currentHealthPoints) != 0) {
				currentHealthPoints = clamped;
				persist();
			}
		}

		double getPendingCapacity(double maxHealth) {
			double capacity = maxHealth - currentHealthPoints - pendingHealthPoints;
			return Math.max(0.0, capacity);
		}
		
		double offerPendingPoints(double amount, double maxHealth, int pendingTimer) {
			if (amount <= 0.0) {
				return 0.0;
			}
			double capacity = getPendingCapacity(maxHealth);
			if (capacity <= 0.0) {
				return amount;
			}
			double toPending = Math.min(capacity, amount);
			addPendingHealthPoints(toPending);
			resetPendingTimer(pendingTimer);
			resetPendingClearTimer();
			return amount - toPending;
		}

		void resetForRespawn(double maxHealth, int pendingTimer, double respawnHealthPercent) {
			double percent = Math.min(100.0, Math.max(0.0, respawnHealthPercent));
			double targetHealth = maxHealth * (percent / 100.0);
			currentHealthPoints = clampAndRound(targetHealth, maxHealth);
			pendingHealthPoints = 0.0;
			healthSurplusPoints = 0.0;
			this.pendingTimer = Math.max(1, pendingTimer);
			pendingClearTimer = PENDING_CLEAR_DURATION;
			surplusClearTimer = SURPLUS_CLEAR_DURATION;
			foodDrainTimer = hungerDepletionTimer;
			persist();
		}

		void addPendingHealthPoints(double value) {
			setPendingHealthPoints(pendingHealthPoints + value);
		}

		void consumePendingPoint() {
			if (pendingHealthPoints <= 0.0) {
				return;
			}
			setPendingHealthPoints(pendingHealthPoints - 1.0);
		}

		void movePendingToSurplus() {
			if (pendingHealthPoints <= 0.0) {
				return;
			}
			double amount = pendingHealthPoints;
			setPendingHealthPoints(0);
			resetPendingTimer(1);
			resetPendingClearTimer();
			addHealthSurplusPoints(amount);
		}

		double moveSurplusToPending(double maxHealth, int pendingTimer) {
			double capacity = getPendingCapacity(maxHealth);
			if (capacity <= 0.0 || healthSurplusPoints <= 0.0) {
				return 0.0;
			}
			double amount = Math.min(1.0, Math.min(capacity, healthSurplusPoints));
			if (amount <= 0.0) {
				return 0.0;
			}
			consumeHealthSurplusPoints(amount);
			addPendingHealthPoints(amount);
			resetPendingTimer(pendingTimer);
			resetPendingClearTimer();
			return amount;
		}

		void addHealthSurplusPoints(double value) {
			if (value <= 0.0) {
				return;
			}
			resetSurplusClearTimer();
			setHealthSurplusPoints(healthSurplusPoints + value);
		}

		void consumeHealthSurplusPoints(double value) {
			if (value <= 0.0) {
				return;
			}
			setHealthSurplusPoints(healthSurplusPoints - value);
		}

		boolean decrementPendingTimer() {
			if (pendingTimer > 0) {
				pendingTimer--;
			}
			return pendingTimer <= 0;
		}

		void resetPendingTimer(int timer) {
			pendingTimer = Math.max(1, timer);
		}

		void resetPendingClearTimer() {
			pendingClearTimer = PENDING_CLEAR_DURATION;
		}

		boolean tickPendingClearTimer() {
			if (pendingClearTimer > 0) {
				pendingClearTimer--;
			}
			return pendingClearTimer <= 0;
		}

		void tickSurplusClearTimer() {
			if (healthSurplusPoints <= 0.0) {
				surplusClearTimer = SURPLUS_CLEAR_DURATION;
				return;
			}
			if (surplusClearTimer > 0) {
				surplusClearTimer--;
			}
			if (surplusClearTimer <= 0) {
				setHealthSurplusPoints(0);
			}
		}

		void resetSurplusClearTimer() {
			surplusClearTimer = SURPLUS_CLEAR_DURATION;
		}

		boolean tickFoodDrain() {
			if (--foodDrainTimer <= 0) {
				foodDrainTimer = hungerDepletionTimer;
				return true;
			}
			return false;
		}

		void resetFoodDrainTimer() {
			foodDrainTimer = hungerDepletionTimer;
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

		private int loadInt(String key, int fallback) {
			JsonElement element = node.get(key);
			int value = fallback;
			if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
				value = primitive.getAsInt();
			}
			return Math.max(0, value);
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
				persist();
			}
		}

		private void setHealthSurplusPoints(double value) {
			double cap = Math.max(0.0, maxSurplusPoints);
			double sanitized = roundToIncrement(Math.min(cap, Math.max(0.0, value)));
			if (Double.compare(sanitized, healthSurplusPoints) != 0) {
				healthSurplusPoints = sanitized;
				if (healthSurplusPoints <= 0.0) {
					surplusClearTimer = SURPLUS_CLEAR_DURATION;
				}
				persist();
			}
		}

		private void persist() {
			node.addProperty("currentHealthPoints", currentHealthPoints);
			node.addProperty("pendingHealthPoints", pendingHealthPoints);
			node.addProperty("healthSurplusPoints", healthSurplusPoints);
			node.addProperty("surplusClearTimer", surplusClearTimer);
			markDirty.run();
		}

		private static final double DOUBLE_STEP = 0.125;
	}
}
