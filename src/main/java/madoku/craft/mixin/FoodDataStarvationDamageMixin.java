package madoku.craft.mixin;

import madoku.craft.hunger.MadokuHunger;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FoodData.class)
public abstract class FoodDataStarvationDamageMixin {
	@Redirect(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	private boolean madokuCraft$preventStarvationDamageWhenUsingMadokuHunger(
		Player player,
		DamageSource source,
		float amount
	) {
		if (MadokuHunger.isEnabled() && source.is(DamageTypes.STARVE)) {
			return false;
		}
		return player.hurt(source, amount);
	}
}
