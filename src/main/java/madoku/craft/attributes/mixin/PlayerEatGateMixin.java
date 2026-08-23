package madoku.craft.attributes.mixin;

import madoku.craft.attributes.hunger.MadokuHungerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEatGateMixin {
	@Inject(method = "canEat(Z)Z", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$gateEatByVanillaFoodLevel(boolean ignoreHunger, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player && MadokuHungerManager.isEnabled()) {
			cir.setReturnValue(MadokuHungerManager.canConsumeFood(player, ignoreHunger));
		}
	}
}
