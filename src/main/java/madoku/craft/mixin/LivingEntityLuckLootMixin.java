package madoku.craft.mixin;

import madoku.craft.luck.MadokuLuck;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLuckLootMixin {
	@Redirect(
		method = "dropFromLootTable(Lnet/minecraft/world/damagesource/DamageSource;Z)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"
		)
	)
	private void madokuCraft$wrapMobLuckLootConsumer(
		LootTable lootTable,
		LootParams lootParams,
		long seed,
		Consumer<ItemStack> consumer,
		DamageSource damageSource,
		boolean causedByPlayer
	) {
		LivingEntity entity = (LivingEntity) (Object) this;
		ServerLevel level = entity.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		Consumer<ItemStack> wrappedConsumer = MadokuLuck.wrapMobDeathLootConsumer(
			level,
			entity,
			damageSource,
			causedByPlayer,
			consumer
		);
		lootTable.getRandomItems(lootParams, seed, wrappedConsumer);
	}
}
