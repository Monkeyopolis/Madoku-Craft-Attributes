package madoku.craft.mixin.attributes;

import madoku.craft.java.attributes.MadokuAttributesClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class HudFoodLevelMixin {
	@Redirect(
		method = "renderFood",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;getFoodLevel()I"
		)
	)
	private int madokuCraft$normalizeFoodLevelForVanillaBars(FoodData foodData) {
		if (!MadokuAttributesClient.hasServerHunger()) {
			return Math.max(0, Math.min(20, foodData.getFoodLevel()));
		}

		int maxHunger = Math.max(1, MadokuAttributesClient.getServerHungerMax());
		int currentHunger = Math.max(0, Math.min(maxHunger, MadokuAttributesClient.getServerHungerCurrent()));
		return Math.max(0, Math.min(20, Math.round(currentHunger * 20.0F / maxHunger)));
	}
}
