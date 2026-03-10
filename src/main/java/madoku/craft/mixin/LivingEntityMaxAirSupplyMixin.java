package madoku.craft.mixin;

import madoku.craft.oxygen.MadokuOxygen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class LivingEntityMaxAirSupplyMixin {
	@Inject(method = "getMaxAirSupply", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$useMadokuOxygenCapForPlayersOnly(CallbackInfoReturnable<Integer> cir) {
		Entity entity = (Entity) (Object) this;
		if (!(entity instanceof Player player) || !MadokuOxygen.isEnabled()) {
			return;
		}
		cir.setReturnValue(MadokuOxygen.getMaximumOxygenTicksForEntity(player));
	}
}