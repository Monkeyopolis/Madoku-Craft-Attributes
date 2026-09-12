package madoku.craft.mixin.attributes;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import madoku.craft.java.attributes.LuckAPIManager;

@Mixin(Player.class)
public abstract class PlayerLuckCriticalHitMixin {
	@Shadow
	private boolean canCriticalAttack(Entity target) {
		throw new AssertionError();
	}

	@Unique
	private boolean madokuCraft$luckCriticalHitActive;

	@Inject(method = "attack", at = @At("HEAD"))
	private void madokuCraft$resetLuckCriticalHit(Entity target, CallbackInfo ci) {
		this.madokuCraft$luckCriticalHitActive = false;
	}

	@Redirect(
		method = "attack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	@SuppressWarnings("deprecation")
	private boolean madokuCraft$applyLuckCriticalHit(Entity entity, DamageSource source, float amount) {
		float resolvedDamage = amount;
		float vanillaCritMultiplier = LuckAPIManager.playerCritDamageMultiplier();
		if (this.canCriticalAttack(entity) && vanillaCritMultiplier > 0.0f) {
			resolvedDamage /= vanillaCritMultiplier;
		}
		Player player = (Player) (Object) this;
		double luckCritMultiplier = LuckAPIManager.resolvePlayerCriticalDamageMultiplier(player, entity);
		if (luckCritMultiplier > 0.0d) {
			resolvedDamage *= (float) luckCritMultiplier;
			this.madokuCraft$luckCriticalHitActive = true;
		}
		return entity.hurtOrSimulate(source, resolvedDamage);
	}

	@Redirect(
		method = "attack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;attackVisualEffects(Lnet/minecraft/world/entity/Entity;ZZZZF)V"
		)
	)
	private void madokuCraft$overrideAttackVisualEffects(
		Player player,
		Entity target,
		boolean criticalHit,
		boolean sweepAttack,
		boolean strongAttack,
		boolean sprintAttack,
		float enchantedDamage
	) {
		boolean luckCriticalHit = this.madokuCraft$luckCriticalHitActive;
		if (luckCriticalHit) {
			this.madokuCraft$playAttackSound(player, SoundEvents.PLAYER_ATTACK_CRIT);
			player.crit(target);
		}
		if (!luckCriticalHit && !sweepAttack && !sprintAttack) {
			this.madokuCraft$playAttackSound(
				player,
				strongAttack ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK
			);
		}
		if (enchantedDamage > 0.0f) {
			player.magicCrit(target);
		}
	}

	@Unique
	private void madokuCraft$playAttackSound(Player player, SoundEvent sound) {
		Level level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, player.getSoundSource(), 1.0f, 1.0f);
	}
}

