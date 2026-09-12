package madoku.craft.mixin.attributes;

import madoku.craft.java.attributes.HealthAPIManager;
import madoku.craft.java.attributes.HungerAPIManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class FoodDataStarvationDamageMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void madokuCraft$finishStarvationAtHealthFloor(ServerPlayer player, CallbackInfo ci) {
		FoodData foodData = (FoodData) (Object) this;
		float starvationHealthFloor = switch (player.level().getDifficulty()) {
			case EASY -> 10.0f;
			case PEACEFUL -> Float.MAX_VALUE;
			default -> 1.0f;
		};
		if (!HungerAPIManager.isEnabled()
			|| foodData.getFoodLevel() > 0
			|| !HungerAPIManager.shouldApplyStarvationDamage(player)
			|| player.getHealth() > starvationHealthFloor) {
			return;
		}

		player.hurtServer(player.level(), player.damageSources().starve(), 1.0f);
	}

	@Redirect(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"
		)
	)
	private void madokuCraft$disableVanillaTickExhaustion(FoodData foodData, float amount) {
		if (!HungerAPIManager.isEnabled()) {
			foodData.addExhaustion(amount);
		}
	}

	@Redirect(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	private boolean madokuCraft$preventStarvationDamageWhenUsingMadokuHunger(
		ServerPlayer player,
		ServerLevel level,
		DamageSource source,
		float amount
	) {
		if (HungerAPIManager.isEnabled()
			&& source.is(DamageTypes.STARVE)
			&& !HungerAPIManager.shouldApplyStarvationDamage(player)) {
			return false;
		}
		return player.hurtServer(level, source, amount);
	}

	@Redirect(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"
		)
	)
	private void madokuCraft$preventVanillaNaturalRegen(
		ServerPlayer player,
		float amount
	) {
		if (HealthAPIManager.isEnabled()) {
			return;
		}
		player.heal(amount);
	}
}

