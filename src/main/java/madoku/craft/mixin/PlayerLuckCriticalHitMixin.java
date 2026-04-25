package madoku.craft.mixin;

import madoku.craft.luck.MadokuLuck;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerLuckCriticalHitMixin {
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
			target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	private boolean madokuCraft$applyLuckCriticalHit(Entity entity, DamageSource source, float amount) {
		float resolvedDamage = amount;
		if (this.madokuCraft$canCriticalAttack(entity)) {
			resolvedDamage /= MadokuLuck.playerCritDamageMultiplier();
		}
		Player player = (Player) (Object) this;
		if (MadokuLuck.shouldApplyPlayerMeleeCrit(player, entity)) {
			resolvedDamage *= MadokuLuck.playerCritDamageMultiplier();
			this.madokuCraft$luckCriticalHitActive = true;
		}
		return entity.hurt(source, resolvedDamage);
	}

	@Redirect(
		method = "attack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;crit(Lnet/minecraft/world/entity/Entity;)V"
		)
	)
	private void madokuCraft$overrideCritVisualEffect(Player player, Entity target) {
		if (this.madokuCraft$luckCriticalHitActive) {
			player.crit(target);
		}
	}

	@Redirect(
		method = "attack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
			ordinal = 2
		)
	)
	private void madokuCraft$overrideCritSound(
		Level level,
		Player player,
		double x,
		double y,
		double z,
		SoundEvent sound,
		net.minecraft.sounds.SoundSource source,
		float volume,
		float pitch
	) {
		if (this.madokuCraft$luckCriticalHitActive) {
			level.playSound(player, x, y, z, sound, source, volume, pitch);
		}
	}

	@Redirect(
		method = "attack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
			ordinal = 3
		)
	)
	private void madokuCraft$overrideStrongAttackSound(
		Level level,
		Player player,
		double x,
		double y,
		double z,
		SoundEvent sound,
		net.minecraft.sounds.SoundSource source,
		float volume,
		float pitch
	) {
		if (!this.madokuCraft$luckCriticalHitActive) {
			level.playSound(player, x, y, z, sound, source, volume, pitch);
		}
	}

	@Redirect(
		method = "attack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
			ordinal = 4
		)
	)
	private void madokuCraft$overrideWeakAttackSound(
		Level level,
		Player player,
		double x,
		double y,
		double z,
		SoundEvent sound,
		net.minecraft.sounds.SoundSource source,
		float volume,
		float pitch
	) {
		if (!this.madokuCraft$luckCriticalHitActive) {
			level.playSound(player, x, y, z, sound, source, volume, pitch);
		}
	}

	@Unique
	private void madokuCraft$playAttackSound(Player player, SoundEvent sound) {
		Level level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, player.getSoundSource(), 1.0f, 1.0f);
	}

	@Unique
	private boolean madokuCraft$canCriticalAttack(Entity target) {
		Player player = (Player) (Object) this;
		if (!(target instanceof LivingEntity)) {
			return false;
		}

		return player.fallDistance > 0.0F
			&& !player.onGround()
			&& !player.onClimbable()
			&& !player.isInWater()
			&& !player.isPassenger()
			&& !player.isSprinting();
	}

	@Inject(
		method = "attack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;setLastHurtMob(Lnet/minecraft/world/entity/Entity;)V",
			shift = At.Shift.BEFORE
		)
	)
	private void madokuCraft$applyDelayedLuckCritVisuals(Entity target, CallbackInfo ci) {
		Player player = (Player) (Object) this;
		if (!this.madokuCraft$luckCriticalHitActive || this.madokuCraft$canCriticalAttack(target)) {
			return;
		}
		this.madokuCraft$playAttackSound(player, SoundEvents.PLAYER_ATTACK_CRIT);
		player.crit(target);
	}
}
