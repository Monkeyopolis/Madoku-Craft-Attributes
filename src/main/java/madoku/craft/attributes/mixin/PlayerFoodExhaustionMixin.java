package madoku.craft.attributes.mixin;

import madoku.craft.attributes.hunger.MadokuHungerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerFoodExhaustionMixin {
	@Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableVanillaExhaustionWithMadokuHunger(float exhaustion, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer && MadokuHungerManager.isEnabled()) {
			ci.cancel();
		}
	}
}
