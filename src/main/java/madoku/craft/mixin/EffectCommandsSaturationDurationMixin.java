package madoku.craft.mixin;

import madoku.craft.hunger.MadokuHunger;
import net.minecraft.server.commands.EffectCommands;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EffectCommands.class)
public abstract class EffectCommandsSaturationDurationMixin {
	@Redirect(
		method = "giveEffect",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/effect/MobEffect;isInstantenous()Z"
		)
	)
	private static boolean madokuCraft$overrideSaturationInstantDuration(MobEffect effect) {
		if (MadokuHunger.isEnabled() && effect == MobEffects.SATURATION.value()) {
			// Treat saturation as a timed effect for command duration conversion (seconds -> ticks).
			return false;
		}
		return effect.isInstantenous();
	}
}
