package madoku.craft.mixin.attributes;

import net.minecraft.server.commands.EffectCommands;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import madoku.craft.java.attributes.HungerAPIManager;

@Mixin(EffectCommands.class)
public abstract class EffectCommandsSaturationDurationMixin {
	@Redirect(
		method = "giveEffect",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/effect/MobEffect;isInstantaneous()Z"
		)
	)
	private static boolean madokuCraft$overrideSaturationInstantDuration(MobEffect effect) {
		if (HungerAPIManager.isSaturationEnabled() && effect == MobEffects.SATURATION.value()) {
			// Treat saturation as a timed effect for command duration conversion (seconds -> ticks).
			return false;
		}
		return effect.isInstantaneous();
	}
}

