package madoku.craft.Health.mixin;

import madoku.craft.Health.system.MadokuHealthManager;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Unique
	private static final Map<UUID, Integer> PRE_CONSUMPTION_FOOD = new WeakHashMap<>();

	@Inject(method = "finishUsing", at = @At("HEAD"))
	private void trackFoodLevel(World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
		if (world.isClient()) {
			return;
		}
		if (!(user instanceof ServerPlayerEntity player)) {
			return;
		}
		PRE_CONSUMPTION_FOOD.put(player.getUuid(), player.getHungerManager().getFoodLevel());
	}

	@Inject(method = "finishUsing", at = @At("RETURN"))
	private void onFinishUsing(World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
		if (world.isClient()) {
			return;
		}
		if (!(user instanceof ServerPlayerEntity player)) {
			return;
		}
		MadokuHealthManager manager = MadokuHealthManager.getInstance();
		if (manager == null) {
			return;
		}
		FoodComponent component = ((ItemStack) (Object) this).get(DataComponentTypes.FOOD);
		if (component == null) {
			PRE_CONSUMPTION_FOOD.remove(player.getUuid());
			return;
		}
		Integer beforeValue = PRE_CONSUMPTION_FOOD.remove(player.getUuid());
		int before = beforeValue != null ? beforeValue : player.getHungerManager().getFoodLevel();
		int after = player.getHungerManager().getFoodLevel();
		int gained = Math.max(0, after - before);
		if (gained <= 0) {
			return;
		}
		manager.addPendingFromFood(player, gained);
	}
}
