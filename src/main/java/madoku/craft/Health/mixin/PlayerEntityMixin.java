package madoku.craft.Health.mixin;

import madoku.craft.Health.system.MadokuHealthManager;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@Inject(method = "canFoodHeal", at = @At("HEAD"), cancellable = true)
	private void blockFoodHealing(CallbackInfoReturnable<Boolean> cir) {
		MadokuHealthManager manager = MadokuHealthManager.getInstance();
		if (manager != null && manager.isFeatureEnabled()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void preventStarvationDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		MadokuHealthManager manager = MadokuHealthManager.getInstance();
		if (manager == null || !manager.isFeatureEnabled()) {
			return;
		}
		if (source != player.getDamageSources().starve()) {
			return;
		}
		if (player.getHungerManager().getFoodLevel() == 0) {
			cir.setReturnValue(false);
		}
	}
}
