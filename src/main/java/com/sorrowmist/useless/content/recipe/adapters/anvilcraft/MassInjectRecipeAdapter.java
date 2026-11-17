package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.dubhe.anvilcraft.block.entity.SpaceOvercompressorBlockEntity;
import dev.dubhe.anvilcraft.recipe.anvil.MassInjectRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 将质量注入换算为质量整除的中子素锭批次。 */
public final class MassInjectRecipeAdapter implements IRecipeAdapter<MassInjectRecipe> {
    @Override
    public String sourceId() { return RecipeSourceIds.ANVILCRAFT; }

    @Override
    public Class<MassInjectRecipe> getRecipeClass() { return MassInjectRecipe.class; }

    @Override
    @Nullable
    public ItemStack getMoldItem() { return null; }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<MassInjectRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        MassInjectRecipe source = holder.value();
        long threshold = SpaceOvercompressorBlockEntity.NEUTRONIUM_INGOT_MASS;
        long mass = source.getMass();
        Ingredient compressor = Multiblock4DRecipeAdapter.mold("space_overcompressor");
        ItemStack[] ingot = Multiblock4DRecipeAdapter.mold("neutronium_ingot").getItems();
        if (threshold <= 0 || mass <= 0 || source.getIngredient().isEmpty() || compressor.isEmpty() || ingot.length == 0) return List.of();
        Batch batch = batch(threshold, mass);
        long count = batch.inputs();
        long outputCount = batch.outputs();
        if (outputCount > Integer.MAX_VALUE) return List.of();
        try {
            int processTime = Math.toIntExact(Math.multiplyExact((long) AdapterUtils.DEFAULT_PROCESS_TIME, count));
            long energy = AdapterUtils.energyForProcessTime(processTime);
            return List.of(new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId(holder.id()),
                    List.of(new CountedIngredient(source.getIngredient(), count)), List.of(), List.of(),
                    List.of(ingot[0].copyWithCount((int) outputCount)), List.of(), List.of(),
                    energy, processTime,
                    Ingredient.EMPTY, 0, List.of(Ingredient.of(Items.ANVIL), compressor), AlloyFurnaceMode.NORMAL));
        } catch (ArithmeticException exception) {
            return List.of();
        }
    }

    static Batch batch(long threshold, long mass) {
        if (threshold <= 0 || mass <= 0) throw new IllegalArgumentException("质量和产物阈值必须为正数");
        long divisor = AdapterUtils.gcd(threshold, mass);
        return new Batch(threshold / divisor, mass / divisor);
    }

    record Batch(long inputs, long outputs) { }
}
