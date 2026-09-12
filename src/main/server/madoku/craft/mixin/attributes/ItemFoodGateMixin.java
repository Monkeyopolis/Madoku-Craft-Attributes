package madoku.craft.mixin.attributes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import madoku.craft.java.attributes.HungerAPIManager;

@Mixin(Item.class)
public abstract class ItemFoodGateMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$gateFoodUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (level == null || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		ItemStack stack = serverPlayer.getItemInHand(hand);
		if (stack.get(DataComponents.FOOD) == null) {
			return;
		}

		if (!HungerAPIManager.canConsumeFood(serverPlayer, false)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$gateFoodFinishUse(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		if (level == null || level.isClientSide() || !(entity instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (stack.get(DataComponents.FOOD) == null) {
			return;
		}
		if (!HungerAPIManager.canConsumeFood(serverPlayer, false)) {
			cir.setReturnValue(stack);
		}
	}

	@Inject(method = "finishUsingItem", at = @At("RETURN"))
	private void madokuCraft$applyFoodNutritionToVanillaData(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		if (level == null || level.isClientSide() || !(entity instanceof ServerPlayer serverPlayer)) {
			return;
		}

		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food == null) {
			return;
		}

		HungerAPIManager.onFoodConsumed(serverPlayer, Math.max(0, food.nutrition()));
	}
}

