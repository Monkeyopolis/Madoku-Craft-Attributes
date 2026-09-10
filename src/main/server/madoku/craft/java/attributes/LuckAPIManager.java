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

/** Public contract for the luck attribute subsystem. */
public final class LuckAPIManager {
	private static final LuckProvider UNAVAILABLE_PROVIDER = new LuckProvider() { };
	private static volatile LuckProvider provider = UNAVAILABLE_PROVIDER;
	private static final LuckFeatureAdapter NO_FEATURE_ADAPTER = new LuckFeatureAdapter() { };
	private static volatile LuckFeatureAdapter featureAdapter = NO_FEATURE_ADAPTER;
	private static final LuckEnchantmentAdapter NO_ENCHANTMENT_ADAPTER = (tool, stack, random) -> false;
	private static volatile LuckEnchantmentAdapter enchantmentAdapter = NO_ENCHANTMENT_ADAPTER;

	private LuckAPIManager() {
	}

	public static void registerProvider(LuckProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Luck provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void registerFeatureAdapter(LuckFeatureAdapter candidate) {
		if (candidate == null) throw new IllegalArgumentException("Luck feature adapter must not be null.");
		featureAdapter = candidate;
	}
	public static void unregisterFeatureAdapter() { featureAdapter = NO_FEATURE_ADAPTER; }
	public static void registerEnchantmentAdapter(LuckEnchantmentAdapter candidate) {
		if (candidate == null) throw new IllegalArgumentException("Luck enchantment adapter must not be null.");
		enchantmentAdapter = candidate;
	}
	public static void unregisterEnchantmentAdapter() { enchantmentAdapter = NO_ENCHANTMENT_ADAPTER; }
	public static boolean isManagedCrop(ServerLevel level, BlockPos pos, BlockState state) {
		return featureAdapter.isManagedCrop(level, pos, state);
	}
	public static boolean isCropHarvestReady(ServerLevel level, BlockPos pos, BlockState state) {
		return featureAdapter.isCropHarvestReady(level, pos, state);
	}
	public static boolean applyConfiguredFortune(ItemStack tool, ItemStack stack, RandomSource random) {
		return enchantmentAdapter.applyConfiguredFortune(tool, stack, random);
	}
	public static void initialize() { provider.initialize(); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static void applyClientSynchronizedEnabled(boolean enabled) { provider.applyClientSynchronizedEnabled(enabled); }
	public static void resetClientSynchronizedSettings() { provider.resetClientSynchronizedSettings(); }
	public static void handlePlayerEffectsChanged(ServerPlayer player) { provider.handlePlayerEffectsChanged(player); }
	public static boolean shouldOverrideVanillaLuckEffect(LivingEntity entity, MobEffect effect) {
		return provider.shouldOverrideVanillaLuckEffect(entity, effect);
	}
	public static boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) {
		return provider.shouldOverrideVanillaEffectAttributes(entity, effect);
	}
	public static double reduceCreeperGriefChanceForTarget(LivingEntity target, double chance) {
		return provider.reduceCreeperGriefChanceForTarget(target, chance);
	}
	public static double reduceHostileRangedAccuracyForTarget(LivingEntity target, double accuracy) {
		return provider.reduceHostileRangedAccuracyForTarget(target, accuracy);
	}
	public static boolean shouldApplyPlayerMeleeCrit(Player player, Entity target) {
		return provider.shouldApplyPlayerMeleeCrit(player, target);
	}
	public static float playerCritDamageMultiplier() { return provider.playerCritDamageMultiplier(); }
	public static double resolveLootLuckStat(ServerPlayer player) { return provider.resolveLootLuckStat(player); }
	public static ServerPlayer resolveLootPlayer(LootContext lootContext) { return provider.resolveLootPlayer(lootContext); }
	public static ServerPlayer resolveActiveDropPlayer() { return provider.resolveActiveDropPlayer(); }
	public static boolean isActiveDropPlayerPlacedBlock() { return provider.isActiveDropPlayerPlacedBlock(); }
	public static void applyGeneratedLoot(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		provider.applyGeneratedLoot(lootContext, stacks);
	}
	public static void applyManagedCropDrops(RandomSource random, ObjectArrayList<ItemStack> stacks) {
		provider.applyManagedCropDrops(random, stacks);
	}
	public static void applyManagedCropDrops(RandomSource random, ObjectArrayList<ItemStack> stacks, ItemStack tool) {
		provider.applyManagedCropDrops(random, stacks, tool);
	}
	public static void applyManagedCropDrops(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		provider.applyManagedCropDrops(lootContext, stacks);
	}
	public static void applyManagedMobDrops(ServerPlayer player, RandomSource random, ObjectArrayList<ItemStack> stacks) {
		provider.applyManagedMobDrops(player, random, stacks);
	}
	public static Consumer<ItemStack> wrapMobDeathLootConsumer(
		ServerLevel level,
		LivingEntity target,
		DamageSource damageSource,
		boolean causedByPlayer,
		Consumer<ItemStack> downstream
	) {
		return provider.wrapMobDeathLootConsumer(level, target, damageSource, causedByPlayer, downstream);
	}
	public static double resolvePlayerCriticalDamageMultiplier(Player player, Entity target) {
		return provider.resolvePlayerCriticalDamageMultiplier(player, target);
	}
	public static void beginBlockDropContext(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		provider.beginBlockDropContext(level, player, pos, state);
	}
	public static void endBlockDropContext() { provider.endBlockDropContext(); }
}
