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

/** Built-in provider for the Madoku luck attribute subsystem. */
public final class MadokuLuckProvider implements LuckProvider {
	@Override public void initialize() { MadokuLuckManager.initialize(); }
	@Override public boolean isEnabled() { return MadokuLuckManager.isEnabled(); }
	@Override public void applyClientSynchronizedEnabled(boolean enabled) { MadokuLuckManager.applyClientSynchronizedEnabled(enabled); }
	@Override public void resetClientSynchronizedSettings() { MadokuLuckManager.resetClientSynchronizedSettings(); }
	@Override public void handlePlayerEffectsChanged(ServerPlayer player) { MadokuLuckManager.handlePlayerEffectsChanged(player); }
	@Override public boolean shouldOverrideVanillaLuckEffect(LivingEntity entity, MobEffect effect) { return MadokuLuckManager.shouldOverrideVanillaLuckEffect(entity, effect); }
	@Override public boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) { return MadokuLuckManager.shouldOverrideVanillaEffectAttributes(entity, effect); }
	@Override public double reduceCreeperGriefChanceForTarget(LivingEntity target, double chance) { return MadokuLuckManager.reduceCreeperGriefChanceForTarget(target, chance); }
	@Override public double reduceHostileRangedAccuracyForTarget(LivingEntity target, double accuracy) { return MadokuLuckManager.reduceHostileRangedAccuracyForTarget(target, accuracy); }
	@Override public boolean shouldApplyPlayerMeleeCrit(Player player, Entity target) { return MadokuLuckManager.shouldApplyPlayerMeleeCrit(player, target); }
	@Override public float playerCritDamageMultiplier() { return MadokuLuckManager.playerCritDamageMultiplier(); }
	@Override public double resolveLootLuckStat(ServerPlayer player) { return MadokuLuckManager.resolveLootLuckStat(player); }
	@Override public ServerPlayer resolveLootPlayer(LootContext lootContext) { return MadokuLuckManager.resolveLootPlayer(lootContext); }
	@Override public ServerPlayer resolveActiveDropPlayer() { return MadokuLuckManager.resolveActiveDropPlayer(); }
	@Override public boolean isActiveDropPlayerPlacedBlock() { return MadokuLuckManager.isActiveDropPlayerPlacedBlock(); }
	@Override public void applyGeneratedLoot(LootContext lootContext, ObjectArrayList<ItemStack> stacks) { MadokuLuckManager.applyGeneratedLoot(lootContext, stacks); }
	@Override public void applyManagedCropDrops(RandomSource random, ObjectArrayList<ItemStack> stacks) { MadokuLuckManager.applyManagedCropDrops(random, stacks); }
	@Override public void applyManagedCropDrops(RandomSource random, ObjectArrayList<ItemStack> stacks, ItemStack tool) { MadokuLuckManager.applyManagedCropDrops(random, stacks, tool); }
	@Override public void applyManagedCropDrops(LootContext lootContext, ObjectArrayList<ItemStack> stacks) { MadokuLuckManager.applyManagedCropDrops(lootContext, stacks); }
	@Override public void applyManagedMobDrops(ServerPlayer player, RandomSource random, ObjectArrayList<ItemStack> stacks) { MadokuLuckManager.applyManagedMobDrops(player, random, stacks); }
	@Override public Consumer<ItemStack> wrapMobDeathLootConsumer(ServerLevel level, LivingEntity target, DamageSource damageSource, boolean causedByPlayer, Consumer<ItemStack> downstream) { return MadokuLuckManager.wrapMobDeathLootConsumer(level, target, damageSource, causedByPlayer, downstream); }
	@Override public double resolvePlayerCriticalDamageMultiplier(Player player, Entity target) { return MadokuLuckManager.resolvePlayerCriticalDamageMultiplier(player, target); }
	@Override public void beginBlockDropContext(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) { MadokuLuckManager.beginBlockDropContext(level, player, pos, state); }
	@Override public void endBlockDropContext() { MadokuLuckManager.endBlockDropContext(); }
}
