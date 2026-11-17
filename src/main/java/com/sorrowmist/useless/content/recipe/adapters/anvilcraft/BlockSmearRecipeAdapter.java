package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.anvilcraft.lib.v2.util.predicate.BlockStatePredicate;
import dev.anvilcraft.lib.v2.util.predicate.ChanceBlockState;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.BlockSmearRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 转换铁砧工艺的方块涂抹配方。 */
public final class BlockSmearRecipeAdapter implements IRecipeAdapter<BlockSmearRecipe> {
    private static final TagKey<Item> GRINDSTONES = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("useless_mod", "anvilcraft_grindstones"));

    @Override
    public String sourceId() {
        return RecipeSourceIds.ANVILCRAFT;
    }

    @Override
    public Class<BlockSmearRecipe> getRecipeClass() {
        return BlockSmearRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<BlockSmearRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        BlockSmearRecipe source = holder.value();
        if (source.getInputBlocks().size() != 2 || source.getResultBlocks().size() != 1) return List.of();
        BlockStatePredicate upper = source.getInputBlocks().get(0);
        if (upper.requiresBlockEntity()) return List.of();
        Ingredient input = Multiblock4DRecipeAdapter.blockIngredient(source.getInputBlocks().get(1));
        Ingredient grindstone = Ingredient.of(GRINDSTONES);
        if (input.isEmpty() || grindstone.isEmpty()) return List.of();
        List<Ingredient> molds = new ArrayList<>(List.of(Ingredient.of(Items.ANVIL), grindstone));
        // 上方方块在原配方中保留；砂轮之外的方块仍需作为额外条件。
        boolean upperIsGrindstone = upper.getBlocks().size() > 0
                && upper.getBlocks().stream().allMatch(block -> block.value() instanceof GrindstoneBlock);
        if (!upperIsGrindstone) {
            Ingredient retained = Multiblock4DRecipeAdapter.blockIngredient(upper);
            if (retained.isEmpty()) return List.of();
            molds.add(retained);
        }
        ChanceBlockState result = source.getResultBlocks().getFirst();
        if (!(result.chance() instanceof ConstantValue chance) || !result.nbt().isEmpty()
                || result.state().getBlock().asItem() == Items.AIR || !Float.isFinite(chance.value())
                || chance.value() < 0 || chance.value() > 1) {
            return List.of();
        }
        List<ExpectedOutputScaler.WeightedItemOutput> weighted = new ArrayList<>();
        weighted.add(new ExpectedOutputScaler.WeightedItemOutput(
                new ItemStack(result.state().getBlock()), 1, 1, chance.value()));
        if (chance.value() < 1.0f) {
            ItemStack[] alternatives = input.getItems();
            if (alternatives.length != 1) return List.of();
            // 涂抹失败时保留原方块，故须将未转换部分计入批次产物。
            weighted.add(new ExpectedOutputScaler.WeightedItemOutput(alternatives[0], 1, 1, 1.0 - chance.value()));
        }
        var scaled = ExpectedOutputScaler.scale(weighted);
        if (scaled.isEmpty() || scaled.get().outputs().isEmpty()) return List.of();
        int operations = scaled.get().operations();
        try {
            int processTime = Math.multiplyExact(AdapterUtils.DEFAULT_PROCESS_TIME, operations);
            long energy = AdapterUtils.energyForProcessTime(processTime);
            return List.of(new AdvancedAlloyFurnaceRecipe(
                    AdapterUtils.convertedId(holder.id()), List.of(new CountedIngredient(input, operations)),
                    List.of(), List.of(), scaled.get().outputs(), List.of(), List.of(),
                    energy, processTime,
                    Ingredient.EMPTY, 0, molds, AlloyFurnaceMode.NORMAL));
        } catch (ArithmeticException exception) {
            return List.of();
        }
    }
}
