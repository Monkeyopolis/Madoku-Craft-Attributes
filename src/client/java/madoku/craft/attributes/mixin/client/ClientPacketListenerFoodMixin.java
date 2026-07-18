package madoku.craft.attributes.mixin.client;

import madoku.craft.attributes.client.HungerClientState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerFoodMixin {
	@Redirect(
		method = "handleSetHealth",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;setFoodLevel(I)V"
		)
	)
	private void madokuCraft$ignoreVanillaFoodUpdateWhenCustomHungerActive(FoodData foodData, int foodLevel) {
		if (!HungerClientState.shouldIgnoreVanillaFoodUpdate()) {
			foodData.setFoodLevel(foodLevel);
		}
	}
}
