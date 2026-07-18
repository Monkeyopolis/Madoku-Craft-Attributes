package madoku.craft.attributes.mixin;

import madoku.craft.attributes.health.MadokuHealthManager;
import madoku.craft.attributes.hunger.MadokuHungerManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FoodData.class)
public abstract class FoodDataStarvationDamageMixin {
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
		if (MadokuHungerManager.isEnabled() && source.is(DamageTypes.STARVE)) {
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
		if (MadokuHealthManager.isEnabled()) {
			return;
		}
		player.heal(amount);
	}
}
