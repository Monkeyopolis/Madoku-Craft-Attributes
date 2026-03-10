package madoku.craft.mixin.client;

import madoku.craft.hunger.MadokuHunger;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSprintGateMixin {
	@Inject(method = "hasEnoughFoodToStartSprinting", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$gateSprintStartByMadokuHunger(CallbackInfoReturnable<Boolean> cir) {
		LocalPlayer player = (LocalPlayer) (Object) this;
		cir.setReturnValue(MadokuHunger.hasEnoughFoodToDoExhaustiveManoeuvres(player));
	}
}
