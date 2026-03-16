package madoku.craft.mixin;

import madoku.craft.hunger.MadokuHunger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Item.class)
public abstract class ItemFoodGateMixin {
	@Unique
	private static final Map<UUID, FoodSnapshot> MADOKU_PRE_CONSUME_SERVER_FOOD_STATE = new ConcurrentHashMap<>();
	@Unique
	private static final Map<UUID, FoodSnapshot> MADOKU_PRE_CONSUME_CLIENT_FOOD_STATE = new ConcurrentHashMap<>();

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$gateFoodUse(
		Level level,
		Player player,
		InteractionHand hand,
		CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir
	) {
		if (level == null || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		ItemStack stack = serverPlayer.getItemInHand(hand);
		if (stack.get(DataComponents.FOOD) == null) {
			return;
		}

		if (!MadokuHunger.canConsumeFood(serverPlayer, false)) {
			cir.setReturnValue(InteractionResultHolder.fail(stack));
		}
	}

	@Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$gateFoodFinishUse(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		if (level == null || entity == null) {
			return;
		}

		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food == null) {
			return;
		}

		if (level.isClientSide()) {
			if (entity instanceof Player player && MadokuHunger.isEnabled()) {
				FoodData foodData = player.getFoodData();
				MADOKU_PRE_CONSUME_CLIENT_FOOD_STATE.put(
					player.getUUID(),
					new FoodSnapshot(foodData.getFoodLevel(), foodData.getSaturationLevel())
				);
			}
			return;
		}

		if (!(entity instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (!MadokuHunger.canConsumeFood(serverPlayer, false)) {
			cir.setReturnValue(stack);
			return;
		}
		if (MadokuHunger.isEnabled()) {
			FoodData foodData = serverPlayer.getFoodData();
			MADOKU_PRE_CONSUME_SERVER_FOOD_STATE.put(
				serverPlayer.getUUID(),
				new FoodSnapshot(foodData.getFoodLevel(), foodData.getSaturationLevel())
			);
		}
	}

	@Inject(method = "finishUsingItem", at = @At("RETURN"))
	private void madokuCraft$collectPendingFromFood(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		if (level == null || entity == null) {
			return;
		}

		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food == null) {
			return;
		}

		if (level.isClientSide()) {
			if (!(entity instanceof Player player) || !MadokuHunger.isEnabled()) {
				return;
			}
			FoodSnapshot snapshot = MADOKU_PRE_CONSUME_CLIENT_FOOD_STATE.remove(player.getUUID());
			if (snapshot == null) {
				return;
			}
			FoodData foodData = player.getFoodData();
			foodData.setFoodLevel(snapshot.foodLevel());
			foodData.setSaturation(snapshot.saturationLevel());
			return;
		}

		if (!(entity instanceof ServerPlayer serverPlayer) || !MadokuHunger.isEnabled()) {
			return;
		}
		FoodSnapshot snapshot = MADOKU_PRE_CONSUME_SERVER_FOOD_STATE.remove(serverPlayer.getUUID());
		if (snapshot == null) {
			return;
		}
		FoodData foodData = serverPlayer.getFoodData();
		foodData.setFoodLevel(snapshot.foodLevel());
		foodData.setSaturation(snapshot.saturationLevel());

		if (!MadokuHunger.canConsumeFood(serverPlayer, false)) {
			return;
		}

		MadokuHunger.onFoodConsumed(serverPlayer, Math.max(0, food.nutrition()));
	}

	@Unique
	private record FoodSnapshot(int foodLevel, float saturationLevel) {
	}
}
