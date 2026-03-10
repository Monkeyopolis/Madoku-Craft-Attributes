package madoku.craft.mixin;

import madoku.craft.health.MadokuHealth;
import madoku.craft.hunger.MadokuHunger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {
	"net.minecraft.world.effect.PoisonMobEffect",
	"net.minecraft.world.effect.WitherMobEffect",
	"net.minecraft.world.effect.RegenerationMobEffect",
	"net.minecraft.world.effect.AbsorptionMobEffect",
	"net.minecraft.world.effect.HungerMobEffect"
})
public abstract class MobEffectTickOverrideMixin {
	@Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$overrideVanillaEffectTick(
		ServerLevel level,
		LivingEntity livingEntity,
		int amplifier,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (MadokuHealth.shouldOverrideVanillaEffect(livingEntity, (MobEffect) (Object) this)
			|| MadokuHunger.shouldOverrideVanillaEffect(livingEntity, (MobEffect) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
