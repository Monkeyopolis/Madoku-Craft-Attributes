package madoku.craft.java.attributes;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;

/** Provider contract implemented by the module that owns Madoku luck attributes. */
public interface LuckProvider {
	default void initialize() { }
	default boolean isEnabled() { return false; }
	default void applyClientSynchronizedEnabled(boolean enabled) { }
	default void resetClientSynchronizedSettings() { }
	default void handlePlayerEffectsChanged(ServerPlayer player) { }
	default boolean shouldOverrideVanillaLuckEffect(LivingEntity entity, MobEffect effect) { return false; }
	default boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) { return false; }
	default double reduceCreeperGriefChanceForTarget(LivingEntity target, double chance) { return chance; }
	default double reduceHostileRangedAccuracyForTarget(LivingEntity target, double accuracy) { return accuracy; }
	default boolean shouldApplyPlayerMeleeCrit(Player player, Entity target) { return false; }
	default float playerCritDamageMultiplier() { return 1.0f; }
	default double resolveLootLuckStat(ServerPlayer player) { return 0.0d; }
	default ServerPlayer resolveLootPlayer(LootContext lootContext) { return null; }
	default ServerPlayer resolveActiveDropPlayer() { return null; }
	default boolean isActiveDropPlayerPlacedBlock() { return false; }
	default void applyGeneratedLoot(LootContext lootContext, ObjectArrayList<ItemStack> stacks) { }
	default void applyManagedCropDrops(RandomSource random, ObjectArrayList<ItemStack> stacks) { }
	default void applyManagedCropDrops(RandomSource random, ObjectArrayList<ItemStack> stacks, ItemStack tool) { }
	default void applyManagedCropDrops(LootContext lootContext, ObjectArrayList<ItemStack> stacks) { }
	default void applyManagedMobDrops(ServerPlayer player, RandomSource random, ObjectArrayList<ItemStack> stacks) { }
	default Consumer<ItemStack> wrapMobDeathLootConsumer(ServerLevel level, LivingEntity target, DamageSource damageSource, boolean causedByPlayer, Consumer<ItemStack> downstream) { return downstream; }
	default double resolvePlayerCriticalDamageMultiplier(Player player, Entity target) { return 1.0d; }
	default void beginBlockDropContext(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) { }
	default void endBlockDropContext() { }
}
