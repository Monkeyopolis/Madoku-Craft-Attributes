package madoku.craft.mixin.attributes;

import madoku.craft.java.attributes.HungerAPIManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies food nutrition after vanilla food effects have been processed. */
@Mixin(FoodProperties.class)
public abstract class FoodPropertiesNutritionMixin {
	@Inject(method = "onConsume", at = @At("TAIL"))
	private void madokuCraft$applyMadokuNutrition(
		Level level,
		LivingEntity entity,
		ItemStack stack,
		Consumable consumable,
		CallbackInfo ci
	) {
		if (level == null || level.isClientSide() || !(entity instanceof ServerPlayer serverPlayer)) {
			return;
		}

		FoodProperties food = (FoodProperties) (Object) this;
		int nutrition = Math.max(0, food.nutrition());
		HungerAPIManager.onFoodConsumed(serverPlayer, nutrition);
	}
}
