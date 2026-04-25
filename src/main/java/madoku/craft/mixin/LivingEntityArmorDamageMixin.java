package madoku.craft.mixin;

import madoku.craft.armor.MadokuArmor;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityArmorDamageMixin {
	@Shadow
	protected abstract void hurtArmor(DamageSource source, float amount);

	@Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyMadokuArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
		if (!MadokuArmor.isEnabled()) {
			return;
		}
		if (source != null && source.is(DamageTypeTags.BYPASSES_ARMOR) && !source.is(DamageTypeTags.IS_FALL)) {
			return;
		}

		if (source != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			this.hurtArmor(source, amount);
		}

		LivingEntity entity = (LivingEntity) (Object) this;
		cir.setReturnValue(MadokuArmor.applyCustomArmorDamage(entity, source, amount));
	}
}
