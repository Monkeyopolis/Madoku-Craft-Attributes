package madoku.craft.mixin.attributes;

import madoku.craft.java.attributes.ArmorAPIManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the Attributes-owned armor rules when Compat is not handling cross-module damage. */
@Mixin(LivingEntity.class)
public abstract class MadokuArmorDamageMixin {
	@Shadow
	protected abstract void hurtArmor(DamageSource source, float amount);

	@Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyStandaloneArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
		if (isHandledByCompat()) {
			return;
		}

		boolean fallDamage = source != null && source.is(DamageTypeTags.IS_FALL);
		boolean bypassesArmor = source != null && source.is(DamageTypeTags.BYPASSES_ARMOR) && !fallDamage;
		if (bypassesArmor || !ArmorAPIManager.shouldOverrideVanillaArmorDamage(source)) {
			return;
		}

		LivingEntity entity = (LivingEntity) (Object) this;
		this.hurtArmor(source, amount);
		cir.setReturnValue(ArmorAPIManager.applyCustomArmorDamage(entity, source, amount));
	}

	private static boolean isHandledByCompat() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("madoku-craft")) {
			return true;
		}
		return loader.isModLoaded("madoku-craft-compat")
			&& loader.isModLoaded("madoku-craft-core")
			&& loader.isModLoaded("madoku-craft-mobs")
			&& loader.isModLoaded("madoku-craft-pets");
	}
}
