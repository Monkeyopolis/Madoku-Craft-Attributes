package madoku.craft.mixin.attributes;

import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import madoku.craft.java.attributes.HungerAPIManager;

@Mixin(FoodData.class)
public abstract class FoodDataSaturationDisableMixin {
	@Shadow
	private float saturationLevel;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void madokuCraft$clearInitialSaturation(CallbackInfo ci) {
		if (!HungerAPIManager.isEnabled()) {
			return;
		}
		saturationLevel = 0.0f;
	}

	@Inject(method = "eat(IF)V", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventVanillaFoodWriteByValues(int nutrition, float saturation, CallbackInfo ci) {
		if (HungerAPIManager.isEnabled()) {
			ci.cancel();
		}
	}

	@Inject(method = "eat(Lnet/minecraft/world/food/FoodProperties;)V", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventVanillaFoodWriteByProperties(net.minecraft.world.food.FoodProperties food, CallbackInfo ci) {
		if (HungerAPIManager.isEnabled()) {
			ci.cancel();
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void madokuCraft$clearLoadedSaturation(ValueInput input, CallbackInfo ci) {
		if (!HungerAPIManager.isEnabled()) {
			return;
		}
		saturationLevel = 0.0f;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
	private void madokuCraft$clearSavedSaturation(ValueOutput output, CallbackInfo ci) {
		if (!HungerAPIManager.isEnabled()) {
			return;
		}
		saturationLevel = 0.0f;
	}

	@Inject(method = "setSaturation(F)V", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableSaturationSet(float saturation, CallbackInfo ci) {
		if (!HungerAPIManager.isEnabled()) {
			return;
		}
		saturationLevel = 0.0f;
		ci.cancel();
	}

	@Inject(method = "getSaturationLevel", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$hideSaturationLevel(CallbackInfoReturnable<Float> cir) {
		if (!HungerAPIManager.isEnabled()) {
			return;
		}
		cir.setReturnValue(0.0f);
	}
}
