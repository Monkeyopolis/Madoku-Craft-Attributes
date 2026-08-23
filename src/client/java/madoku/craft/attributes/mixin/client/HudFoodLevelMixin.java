package madoku.craft.attributes.mixin.client;

import madoku.craft.attributes.MadokuHungerClientState;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
public abstract class HudFoodLevelMixin {
	@Redirect(
		method = "extractFood",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;getFoodLevel()I"
		)
	)
	private int madokuCraft$normalizeFoodLevelForVanillaBars(FoodData foodData) {
		if (!MadokuHungerClientState.hasServerHunger()) {
			return Math.max(0, Math.min(20, foodData.getFoodLevel()));
		}

		int maxHunger = Math.max(1, MadokuHungerClientState.getServerHungerMax());
		int currentHunger = Math.max(0, Math.min(maxHunger, MadokuHungerClientState.getServerHungerCurrent()));
		return Math.max(0, Math.min(20, Math.round(currentHunger * 20.0F / maxHunger)));
	}
}
