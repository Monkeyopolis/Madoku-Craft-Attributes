package madoku.craft.attributes.client;

import net.minecraft.client.player.LocalPlayer;

public final class HungerClientState {
	private static final int VANILLA_MAX_FOOD_LEVEL = 20;
	private static volatile int current = -1;
	private static volatile int pending;
	private static volatile int max = -1;

	private HungerClientState() {
	}

	public static void update(int currentHunger, int pendingHunger, int maximumHunger) {
		current = Math.max(0, currentHunger);
		pending = Math.max(0, pendingHunger);
		max = Math.max(1, maximumHunger);
	}

	public static void updateVanillaFood(LocalPlayer player) {
		if (player == null || max <= 0) {
			return;
		}

		long effectiveHunger = Math.min((long) max, (long) Math.max(0, current) + (long) Math.max(0, pending));
		int vanillaFood = (int) Math.round((double) effectiveHunger * VANILLA_MAX_FOOD_LEVEL / (double) max);
		player.getFoodData().setFoodLevel(Math.max(0, Math.min(VANILLA_MAX_FOOD_LEVEL, vanillaFood)));
		player.getFoodData().setSaturation(0.0f);
	}

	public static boolean canConsume(boolean ignoreHunger) {
		if (ignoreHunger || max <= 0) {
			return true;
		}
		return (long) Math.max(0, current) + (long) Math.max(0, pending) < (long) max;
	}

	public static boolean shouldIgnoreVanillaFoodUpdate() {
		return max > 0;
	}

	public static void clear() {
		current = -1;
		pending = 0;
		max = -1;
	}
}
