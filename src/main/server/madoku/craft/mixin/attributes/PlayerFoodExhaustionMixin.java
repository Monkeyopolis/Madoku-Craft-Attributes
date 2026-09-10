package madoku.craft.mixin.attributes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import madoku.craft.java.attributes.HungerAPIManager;

@Mixin(Player.class)
public abstract class PlayerFoodExhaustionMixin {
	@Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableVanillaExhaustionWithMadokuHunger(float exhaustion, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer && HungerAPIManager.isEnabled()) {
			ci.cancel();
		}
	}
}
