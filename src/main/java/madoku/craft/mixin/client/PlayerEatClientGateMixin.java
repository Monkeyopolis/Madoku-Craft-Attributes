package madoku.craft.mixin.client;

import madoku.craft.hunger.MadokuHunger;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEatClientGateMixin {
	@Inject(method = "canEat(Z)Z", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$gateClientCanEat(boolean ignoreHunger, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof LocalPlayer && !MadokuHunger.canConsumeFoodClient(ignoreHunger)) {
			cir.setReturnValue(false);
		}
	}
}