
package madoku.craft.attributes.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemFoodClientPredictionMixin {
	@Unique
	private static final ThreadLocal<FoodSnapshot> MADOKU_CRAFT$FOOD_SNAPSHOT = new ThreadLocal<>();

	@Inject(method = "finishUsingItem", at = @At("HEAD"))
	private void madokuCraft$storeClientFoodState(
		ItemStack stack,
		Level level,
		LivingEntity entity,
		CallbackInfoReturnable<ItemStack> cir
	) {
		if (level == null || !level.isClientSide() || !(entity instanceof LocalPlayer player)) {
			MADOKU_CRAFT$FOOD_SNAPSHOT.remove();
			return;
		}
		if (stack.get(DataComponents.FOOD) == null) {
			MADOKU_CRAFT$FOOD_SNAPSHOT.remove();
			return;
		}

		FoodData foodData = player.getFoodData();
		MADOKU_CRAFT$FOOD_SNAPSHOT.set(new FoodSnapshot(foodData.getFoodLevel(), foodData.getSaturationLevel()));
	}

	@Inject(method = "finishUsingItem", at = @At("RETURN"))
	private void madokuCraft$restoreClientFoodState(
		ItemStack stack,
		Level level,
		LivingEntity entity,
		CallbackInfoReturnable<ItemStack> cir
	) {
		FoodSnapshot snapshot = MADOKU_CRAFT$FOOD_SNAPSHOT.get();
		MADOKU_CRAFT$FOOD_SNAPSHOT.remove();
		if (snapshot == null || level == null || !level.isClientSide() || !(entity instanceof LocalPlayer player)) {
			return;
		}
		if (stack.get(DataComponents.FOOD) == null) {
			return;
		}

		FoodData foodData = player.getFoodData();
		foodData.setFoodLevel(snapshot.foodLevel());
		foodData.setSaturation(snapshot.saturationLevel());
	}

	@Unique
	private record FoodSnapshot(int foodLevel, float saturationLevel) {
	}
}


