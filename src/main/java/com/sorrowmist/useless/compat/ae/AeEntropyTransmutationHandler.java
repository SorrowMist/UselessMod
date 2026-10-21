package com.sorrowmist.useless.compat.ae;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IMEChest;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.EntropyTransmutationRecipe;
import com.sorrowmist.useless.init.ModRecipeTypes;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 「熵变机械臂潜行右键 ME 箱子 → 原地替换方块」的服务端逻辑。
 *
 * <p>AE2 熵变机械臂自身的 {@code EntropyRecipe} 只认方块状态与流体状态，
 * 无法表达「ME 箱子里的磁盘装了什么」，所以这里读取箱子内磁盘（槽位 0）的存储，
 * 校验 {@link EntropyTransmutationRecipe} 的全部输入是否满足（同一磁盘内合计即可），
 * 满足则扣除并原地替换方块。</p>
 *
 * <p>只操作磁盘自身（{@link IMEChest#getCellInventory(int)}），不经手整张网络，
 * 因此槽位判定与用户语义一致：看的是「这个 ME 箱子里那张磁盘」。</p>
 */
public final class AeEntropyTransmutationHandler {

    private AeEntropyTransmutationHandler() {
    }

    /**
     * 尝试对指定 ME 箱子执行熵变转化。
     *
     * @return 是否成功执行了一次转化（成功时方块已被原地替换）
     */
    public static boolean tryTransmute(ServerLevel level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof IMEChest chest)) {
            return false;
        }

        // 槽位 0 即 ME 箱子唯一的磁盘槽；未装盘或未供电时返回 null。
        MEStorage cellStorage = chest.getCellInventory(0);
        if (cellStorage == null) {
            return false;
        }

        List<RecipeHolder<EntropyTransmutationRecipe>> recipes =
                level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.ENTROPY_TRANSMUTATION_TYPE.get());
        if (recipes.isEmpty()) {
            return false;
        }

        IActionSource source = IActionSource.ofPlayer(player);
        // 取一次库存快照：校验与扣减都以同一份数据为准。
        var available = cellStorage.getAvailableStacks();

        for (RecipeHolder<EntropyTransmutationRecipe> holder : recipes) {
            EntropyTransmutationRecipe recipe = holder.value();

            BlockState outputState = blockStateOf(recipe.output());
            if (outputState == null) {
                continue; // 输出不是方块物品，本机制无法原地替换
            }

            if (!hasAll(available, recipe.inputs())) {
                continue;
            }

            for (CountedIngredient input : recipe.inputs()) {
                consume(cellStorage, available, input, source);
            }

            level.setBlock(pos, outputState, 3);
            return true;
        }

        return false;
    }

    /**
     * 只读预览：若该位置的 ME 箱子当前满足某条熵变转化配方，返回会被替换成的方块状态。
     *
     * <p>与 {@link #tryTransmute} 共享同一套校验，但<b>不扣除磁盘内容、不替换方块</b>，
     * 供客户端准星指向时提前渲染幽灵预览。不满足条件或输出非方块时返回 {@code null}。</p>
     */
    public static @Nullable BlockState previewOutput(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof IMEChest chest)) {
            return null;
        }

        MEStorage cellStorage = chest.getCellInventory(0);
        if (cellStorage == null) {
            return null;
        }

        List<RecipeHolder<EntropyTransmutationRecipe>> recipes =
                level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.ENTROPY_TRANSMUTATION_TYPE.get());
        if (recipes.isEmpty()) {
            return null;
        }

        var available = cellStorage.getAvailableStacks();
        for (RecipeHolder<EntropyTransmutationRecipe> holder : recipes) {
            EntropyTransmutationRecipe recipe = holder.value();
            BlockState outputState = blockStateOf(recipe.output());
            if (outputState == null) {
                continue;
            }
            if (hasAll(available, recipe.inputs())) {
                return outputState;
            }
        }

        return null;
    }

    /** 库存快照是否满足全部输入需求（同一快照内合计）。 */
    private static boolean hasAll(Iterable<Object2LongMap.Entry<AEKey>> available,
                                  List<CountedIngredient> inputs) {
        for (CountedIngredient input : inputs) {
            long have = 0;
            for (Object2LongMap.Entry<AEKey> entry : available) {
                if (matches(input.ingredient(), entry.getKey())) {
                    have += entry.getLongValue();
                }
            }
            if (have < input.count()) {
                return false;
            }
        }
        return true;
    }

    /** 按需从磁盘扣除一项输入；同一原料可能分散在多个 key 上，逐个扣到满足数量为止。 */
    private static void consume(MEStorage storage, Iterable<Object2LongMap.Entry<AEKey>> available,
                                CountedIngredient input, IActionSource source) {
        long remaining = input.count();
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (remaining <= 0) {
                return;
            }
            AEKey key = entry.getKey();
            if (!matches(input.ingredient(), key)) {
                continue;
            }
            long toExtract = Math.min(remaining, entry.getLongValue());
            long extracted = storage.extract(key, toExtract, Actionable.MODULATE, source);
            remaining -= extracted;
        }
    }

    /** 物品型 AE 键是否被该原料接受。 */
    private static boolean matches(Ingredient ingredient, AEKey key) {
        return key instanceof AEItemKey itemKey && ingredient.test(itemKey.getReadOnlyStack());
    }

    /** 输出物品对应的方块状态；不是方块物品时返回 null。 */
    private static BlockState blockStateOf(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                ? blockItem.getBlock().defaultBlockState()
                : null;
    }
}