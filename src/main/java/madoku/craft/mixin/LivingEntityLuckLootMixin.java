package madoku.craft.mixin;

import madoku.craft.luck.MadokuLuck;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLuckLootMixin {
	@ModifyVariable(
		method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"),
		argsOnly = true,
		index = 5
	)
	private Consumer<ItemStack> madokuCraft$wrapMobLuckLootConsumer(
		Consumer<ItemStack> consumer,
		ServerLevel level,
		DamageSource damageSource,
		boolean causedByPlayer,
		ResourceKey<LootTable> lootTableKey
	) {
		return MadokuLuck.wrapMobDeathLootConsumer(
			level,
			(LivingEntity) (Object) this,
			damageSource,
			causedByPlayer,
			consumer
		);
	}
}
