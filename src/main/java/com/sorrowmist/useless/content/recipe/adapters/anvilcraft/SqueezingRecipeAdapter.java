package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.anvilcraft.lib.v2.util.predicate.ChanceBlockState;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasAnvil;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.SqueezingRecipe;
import dev.dubhe.anvilcraft.recipe.component.HasCauldronSimple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 按期望产出换算压榨配方的物品与流体批次。 */
public final class SqueezingRecipeAdapter implements IRecipeAdapter<SqueezingRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.ANVILCRAFT;
    }

    @Override
    public Class<SqueezingRecipe> getRecipeClass() {
        return SqueezingRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<SqueezingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        SqueezingRecipe source = holder.value();
        HasCauldronSimple cauldron = source.getHasCauldron();
        if (!CauldronRecipeAdapter.acceptsEmpty(cauldron) || cauldron.ignited()
                || cauldron.transforms().size() != 1 || !Float.isFinite(cauldron.chance())
                || cauldron.chance() < 0 || cauldron.chance() > 1) return List.of();
        Ingredient input = Multiblock4DRecipeAdapter.blockIngredient(source.getFirstInputBlock());
        Ingredient anvil = anvilMold(source.getHasAnvil());
        if (input.isEmpty() || anvil.isEmpty()) return List.of();
        ChanceBlockState result = source.getFirstResultBlock();
        if (!(result.chance() instanceof ConstantValue blockChance) || !Float.isFinite(blockChance.value())
                || blockChance.value() < 0 || blockChance.value() > 1 || !result.nbt().isEmpty()) return List.of();
        FluidStack fluid = cauldron.transforms().getFirst();
        List<ExpectedOutputScaler.WeightedItemOutput> weighted = new ArrayList<>();
        weighted.add(new ExpectedOutputScaler.WeightedItemOutput(new ItemStack(Items.STRUCTURE_VOID),
                fluid.getAmount(), fluid.getAmount(), cauldron.chance()));
        if (result.state().getBlock() != Blocks.AIR) {
            if (result.state().getBlock().asItem() == Items.AIR || result.state().getBlock().asItem() == Items.STRUCTURE_VOID) {
                return List.of();
            }
            weighted.add(new ExpectedOutputScaler.WeightedItemOutput(new ItemStack(result.state().getBlock()),
                    1, 1, blockChance.value()));
        }
        var scaled = ExpectedOutputScaler.scale(weighted);
        if (scaled.isEmpty()) return List.of();
        List<ItemStack> itemOutputs = new ArrayList<>();
        List<FluidStack> fluidOutputs = new ArrayList<>();
        for (ItemStack stack : scaled.get().outputs()) {
            if (stack.is(Items.STRUCTURE_VOID)) fluidOutputs.add(fluid.copyWithAmount(stack.getCount()));
            else itemOutputs.add(stack.copy());
        }
        if (itemOutputs.isEmpty() && fluidOutputs.isEmpty()) return List.of();
        int operations = scaled.get().operations();
        try {
            int processTime = Math.multiplyExact(AdapterUtils.DEFAULT_PROCESS_TIME, operations);
            long energy = AdapterUtils.energyForProcessTime(processTime);
            return List.of(new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId(holder.id()),
                    List.of(new CountedIngredient(input, operations)), List.of(), List.of(), itemOutputs,
                    fluidOutputs, List.of(), energy, processTime, Ingredient.EMPTY, 0,
                    List.of(anvil, Ingredient.of(Items.CAULDRON)), AlloyFurnaceMode.NORMAL));
        } catch (ArithmeticException exception) {
            return List.of();
        }
    }

    private static Ingredient anvilMold(HasAnvil condition) {
        if (condition == null) return Ingredient.of(Items.ANVIL);
        var predicate = condition.anvil();
        if (predicate.requiresBlockEntity() || !predicate.getProperties().isEmpty()) return Ingredient.EMPTY;
        List<ItemStack> matches = new ArrayList<>();
        List<ItemStack> options = new ArrayList<>(List.of(new ItemStack(Items.ANVIL)));
        for (String id : List.of("frost_anvil", "ember_anvil", "royal_anvil", "transcendence_anvil")) {
            options.addAll(List.of(Multiblock4DRecipeAdapter.mold(id).getItems()));
        }
        for (ItemStack option : options) {
            Block block = Block.byItem(option.getItem());
            boolean matchesPredicate = predicate.getBlocks().size() == 0
                    || predicate.getBlocks().contains(block.builtInRegistryHolder());
            if (matchesPredicate != condition.inverted()) matches.add(option);
        }
        return matches.isEmpty() ? Ingredient.EMPTY : Ingredient.of(matches.toArray(ItemStack[]::new));
    }
}
