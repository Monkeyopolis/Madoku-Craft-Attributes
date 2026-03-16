package madoku.craft.mixin;

import madoku.craft.hunger.MadokuHunger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerSprintGateMixin {
	@Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableVanillaExhaustionWithMadokuHunger(float exhaustion, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer && MadokuHunger.isEnabled()) {
			ci.cancel();
		}
	}

	@Inject(method = "canSprint", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$gateExhaustiveManoeuvresByMadokuHunger(CallbackInfoReturnable<Boolean> cir) {
		Player player = (Player) (Object) this;
		cir.setReturnValue(MadokuHunger.hasEnoughFoodToDoExhaustiveManoeuvres(player));
	}
}
