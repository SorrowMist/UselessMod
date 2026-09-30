package com.sorrowmist.useless.compat.exdeorum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import thedarkcolour.exdeorum.loot.HammerLootModifier;
import thedarkcolour.exdeorum.recipe.RecipeUtil;
import thedarkcolour.exdeorum.recipe.crook.CrookRecipe;
import thedarkcolour.exdeorum.recipe.hammer.HammerRecipe;
import thedarkcolour.exdeorum.tag.EItemTags;

import java.util.ArrayList;
import java.util.List;

/**
 * 造化杖对 Ex Deorum 三种工具效果的兼容实现。
 *
 * <p>Ex Deorum 的钩子、锤子与压缩锤子效果由 {@code GlobalLootModifier} 实现，触发条件是
 * 战利品上下文中的工具属于 {@code #exdeorum:crooks}、{@code #exdeorum:hammers} 或
 * {@code #exdeorum:compressed_hammers} 物品标签。造化杖不属于上述任一标签，
 * 其破坏路径亦不构造战利品上下文，因此无法借助对方既有的修饰器生效，
 * 只能由本层显式查询其配方缓存并在掉落阶段改写。</p>
 *
 * <p>三种模式的掉落语义与 Ex Deorum 保持一致：锤子以配方产物<b>替换</b>原有掉落，
 * 钩子在原有掉落之外<b>追加</b>配方产物。开关关闭、方块无对应配方或产物数量非正时，
 * 一律原样返回既有掉落。</p>
 *
 * <p>本类引用了 exdeorum 的类型，故不得由调用方直接链接：其方法体与栈映射表在类验证阶段
 * 即须解析 {@link HammerRecipe} 等外部类型，未安装该模组时会抛出 {@code NoClassDefFoundError}。
 * 门面 {@link ExDeorumCompat} 仅在该模组确已加载后经反射加载本类，从而将外部类型引用
 * 隔离在守卫之后。</p>
 */
public final class ExDeorumCompatImpl {

    private ExDeorumCompatImpl() {
    }

    /**
     * 按当前启用的 Ex Deorum 模式改写掉落。
     *
     * <p>三个开关相互独立，可同时启用：锤子与压缩锤均以配方产物替换掉落，
     * 命中顺序为「压缩锤优先」，因为压缩锤配方是其自身独立登记的数据；
     * 钩子在最终掉落之上追加。锤子类模式与钩子同时启用时，钩子作用于替换后的结果。</p>
     *
     * @param level     世界
     * @param state     被破坏的方块状态
     * @param original  原始掉落
     * @param tool      造化杖
     * @param hammer    是否启用锤子模式
     * @param compressedHammer 是否启用压缩锤模式
     * @param crook     是否启用钩子模式
     * @return 改写后的掉落列表
     */
    public static List<ItemStack> rewriteDrops(ServerLevel level, BlockState state, List<ItemStack> original,
                                               ItemStack tool,
                                               boolean hammer, boolean compressedHammer, boolean crook,
                                               BlockPos pos, Player player) {
        List<ItemStack> result = original;

        if (hammer || compressedHammer) {
            result = applyHammer(level, state, result, tool, compressedHammer);
        }
        if (crook) {
            result = applyCrook(level, state, result, tool, pos, player);
        }
        return result;
    }

    /**
     * 按锤子或压缩锤配方改写掉落。
     *
     * <p>压缩锤配方与普通锤配方是两套独立数据，同一方块可能只登记其中一套，
     * 因此两者不可互相回退。</p>
     *
     * @param level      世界
     * @param state      被破坏的方块状态
     * @param original   原始掉落
     * @param tool       造化杖（用于读取时运等级）
     * @param compressed 是否查询压缩锤配方
     * @return 命中配方时为配方产物，否则为原始掉落
     */
    private static List<ItemStack> applyHammer(ServerLevel level, BlockState state, List<ItemStack> original,
                                               ItemStack tool, boolean compressed) {
        // 锤子配方以方块的物品形式为索引键；无法取得物品形式的方块没有配方可查
        Item itemForm = state.getBlock().asItem();
        if (itemForm == Items.AIR) {
            return original;
        }

        var caches = RecipeUtil.getCaches(level);
        HammerRecipe recipe = compressed
                ? caches.getCompressedHammerRecipe(itemForm)
                : caches.getHammerRecipe(itemForm);
        if (recipe == null) {
            return original;
        }

        int amount;
        try {
            // 产物数量由战利品数值提供器决定；此处不构造完整的战利品上下文，
            // 仅提供世界参数，取值只依赖随机源与配置常量，缺失方块与工具参数不影响结果。
            amount = recipe.resultAmount.getInt(RecipeUtil.emptyLootContext(level));
        } catch (Throwable ignored) {
            // 提供器需要战利品上下文中的其它参数时无法取值，此时保留原有掉落
            return original;
        }

        // 时运加成：与 Ex Deorum 的 HammerLootModifier 一致。
        // 被列入时运黑名单的方块不享受加成，该判定必须在计算前进行，
        // 否则黑名单方块会因时运附魔获得额外产出。
        TagKey<Item> fortuneBlacklist = compressed
                ? EItemTags.COMPRESSED_HAMMER_FORTUNE_BLACKLIST
                : EItemTags.HAMMER_FORTUNE_BLACKLIST;
        if (getFortuneLevel(level, tool) > 0 && !itemForm.builtInRegistryHolder().is(fortuneBlacklist)) {
            amount += HammerLootModifier.calculateFortuneBonus(
                    level.registryAccess(), tool, level.getRandom(), amount == 0);
        }

        // 命中配方后一律以配方产物替换原有掉落。产出为非正数时结果为空列表而非保留原掉落，
        // 与 Ex Deorum 的锤子一致：其修饰器命中配方后同样返回新建列表，产出为零时即为空掉落。
        // 该语义是整合包以零产出屏蔽特定方块的手段，不可改为回退原有掉落。
        if (amount <= 0) {
            return List.of();
        }

        List<ItemStack> result = new ArrayList<>(1);
        result.add(recipe.result.copyWithCount(amount));
        return result;
    }

    /**
     * 在既有掉落之上追加钩子配方产物。
     *
     * <p>与锤子不同，钩子是「在方块原有掉落之外按概率追加」，因此保留 {@code original}。</p>
     *
     * <p>钩子是不依赖附魔模式的独立开关：造化杖的精准采集与时运是并列的模式选项，
     * 而非钩子生效的前置条件，若在此因精准采集模式短路，该开关将只在时运模式下有效。</p>
     *
     * <p>时运提升重掷次数（每 3 级增加一次），树叶额外重掷原版掉落表，
     * 两者均对齐 {@code CrookLootModifier} 的行为。</p>
     *
     * @param level     世界
     * @param state     被破坏的方块状态
     * @param original  原始掉落
     * @param tool      造化杖
     * @return 追加产物后的掉落列表
     */
    private static List<ItemStack> applyCrook(ServerLevel level, BlockState state, List<ItemStack> original,
                                              ItemStack tool, BlockPos pos, Player player) {
        List<CrookRecipe> recipes = RecipeUtil.getCaches(level).getCrookRecipes(state);
        boolean leaves = state.is(BlockTags.LEAVES);
        if (recipes.isEmpty() && !leaves) {
            return original;
        }

        RandomSource random = level.getRandom();
        int fortune = getFortuneLevel(level, tool);
        int rolls = Math.max(1, Mth.ceil(fortune / 3f));

        List<ItemStack> result = new ArrayList<>(original);
        for (CrookRecipe recipe : recipes) {
            for (int i = 0; i < rolls; i++) {
                if (random.nextFloat() < recipe.chance()) {
                    result.add(recipe.result().copy());
                }
            }
        }

        // 树叶额外重掷原版掉落：钩子相对其它工具的核心收益即在于此
        if (leaves) {
            for (int i = 0; i < rolls; i++) {
                result.addAll(reRollLeafDrops(level, state, tool, pos, player));
            }
        }
        return result;
    }

    /**
     * 重掷树叶的原版掉落表。
     *
     * <p>重掷使用的工具被替换为屏障，以阻止重掷过程再次进入钩子配方分支，
     * 否则会形成递归并无限产出。</p>
     */
    private static List<ItemStack> reRollLeafDrops(ServerLevel level, BlockState state, ItemStack tool,
                                                   BlockPos pos, Player player) {
        try {
            ItemStack nonCrook = tool.transmuteCopy(Items.BARRIER, 1);
            LootParams.Builder builder = new LootParams.Builder(level)
                    .withParameter(LootContextParams.BLOCK_STATE, state)
                    .withParameter(LootContextParams.TOOL, nonCrook)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos));
            if (player != null) {
                builder.withParameter(LootContextParams.THIS_ENTITY, player);
            }
            return state.getDrops(builder);
        } catch (Throwable ignored) {
            // 单个方块的重掷失败不应影响整批掉落物
            return List.of();
        }
    }

    /** 读取造化杖的时运附魔等级；无该附魔时返回 0。 */
    private static int getFortuneLevel(ServerLevel level, ItemStack tool) {
        try {
            return tool.getEnchantmentLevel(
                    level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE));
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
