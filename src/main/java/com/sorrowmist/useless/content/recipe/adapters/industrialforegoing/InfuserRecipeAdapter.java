package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.yxiao233.ifeu.common.config.machine.InfuserConfig;
import net.yxiao233.ifeu.common.recipe.InfuserRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts the exact native Forge IFEU 2.5.000 infuser implementation. */
public final class InfuserRecipeAdapter implements IRecipeAdapter<InfuserRecipe> {
    private static final int COMPACT_RECIPE_TIME = 200;
    private static final int COMPACT_RECIPE_FLUID_AMOUNT = 1000;

    @Override
    public String sourceId() {
        return RecipeSourceIds.INDUSTRIAL_FOREGOING;
    }

    @Override
    public Class<InfuserRecipe> getRecipeClass() {
        return InfuserRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return ExtraUpgradesCraftingRecipeAdapterSupport.mold("infuser");
    }

    /**
     * InfuserEntity.checkForRecipe first searches loaded recipes, then uses the TANK FLUID's
     * getBucket(), not an arbitrary registered BucketItem's content. This includes flowing
     * fluid IDs with a bucket, but never invents milk/fish/alternate buckets for water.
     *
     * <p>Only the untagged fallback is catalogued. The native fallback copies actual tank NBT;
     * a static fluid-ID predicate would wrongly accept other NBT or bypass a declared recipe.
     * Tagged fallbacks therefore remain unsupported (declared tagged recipes are supported).
     */
    @Override
    public List<RecipeHolder<InfuserRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();
        List<RecipeHolder<InfuserRecipe>> declared =
                ExtraUpgradesCraftingRecipeAdapterSupport.recipes(level, InfuserRecipe.class);
        List<RecipeHolder<InfuserRecipe>> result = new ArrayList<>();
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            FluidStack inputFluid = new FluidStack(fluid, COMPACT_RECIPE_FLUID_AMOUNT);
            if (inputFluid.isEmpty() || shadowsBucketFallback(declared, inputFluid)) continue;
            Item bucket = fluid.getBucket();
            if (!(bucket instanceof BucketItem)
                    || !ExtraUpgradesCraftingRecipeAdapterSupport.staticDefaultStack(bucket)) continue;
            ItemStack output = bucket.getDefaultInstance();
            if (!ExtraUpgradesCraftingRecipeAdapterSupport.staticOutput(output)) continue;

            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            ResourceLocation id = new ResourceLocation("ifeu",
                    "infuser/compact/" + fluidId.getNamespace() + "/" + fluidId.getPath());
            // The full native constructor appends to the global datagen RECIPES list and
            // invokes onCraftedBy. The native ID constructor has no such side effects; the
            // callback is known to be Item's no-op here. Do not leak entries on every lookup.
            InfuserRecipe source = new InfuserRecipe(id);
            source.input = Items.BUCKET.getDefaultInstance();
            source.inputFluid = inputFluid;
            source.processingTime = COMPACT_RECIPE_TIME;
            source.output = output.copy();
            result.add(new RecipeHolder<>(id, source));
        }
        return List.copyOf(result);
    }

    private static boolean shadowsBucketFallback(
            List<RecipeHolder<InfuserRecipe>> declared, FluidStack fluid) {
        for (RecipeHolder<InfuserRecipe> holder : declared) {
            InfuserRecipe recipe = holder.value();
            // Unknown matches overrides/invalid native rows must not be bypassed by a
            // generated fallback. Even an unsupported declared output has native priority.
            if (recipe.getClass() != InfuserRecipe.class
                    || recipe.input == null || recipe.inputFluid == null) return true;
            if (recipe.input.is(Items.BUCKET) && fluid.isFluidEqual(recipe.inputFluid)) {
                // Suppress regardless of amount: a >=1000 target predicate cannot express
                // "only while the higher-priority recipe has insufficient fluid".
                return true;
            }
        }
        return false;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<InfuserRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        InfuserRecipe source = holder.value();
        if (source.getClass() != InfuserRecipe.class
                || source.input == null || source.input.isEmpty() || source.input.getCount() <= 0
                || source.inputFluid == null || source.inputFluid.isEmpty()
                || source.inputFluid.getAmount() <= 0 || source.processingTime <= 0
                || InfuserConfig.powerPerTick < 0
                || !ExtraUpgradesCraftingRecipeAdapterSupport.staticOutput(source.output)) {
            return List.of();
        }
        ItemStack mold = getMoldItem();
        if (mold.isEmpty()) return List.of();
        // InfuserRecipe.matches uses isSameItem (ignores item NBT); canIncrease and onFinish
        // separately enforce/consume input.count. Fluid drain uses exact fluid AND NBT.
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(Ingredient.of(source.input.getItem()), source.input.getCount())),
                List.of(ExtraUpgradesCraftingRecipeAdapterSupport.exactFluid(source.inputFluid)),
                List.of(), List.of(source.output.copy()), List.of(), List.of(),
                (long) InfuserConfig.powerPerTick * source.processingTime,
                source.processingTime, Ingredient.EMPTY, 0,
                List.of(AdapterUtils.toMoldIngredient(mold)), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<InfuserRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)
                || mergedInputs == null || mergedInputs.isEmpty()
                || mergedFluids == null || mergedFluids.isEmpty()) return List.of();
        List<RecipeHolder<InfuserRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<InfuserRecipe> holder :
                ExtraUpgradesCraftingRecipeAdapterSupport.recipes(level, InfuserRecipe.class)) {
            if (matchesConverted(holder, level, mergedInputs, mergedFluids)) matches.add(holder);
        }
        for (RecipeHolder<InfuserRecipe> holder : getGeneratedRecipes(level)) {
            if (matchesConverted(holder, level, mergedInputs, mergedFluids)) matches.add(holder);
        }
        return List.copyOf(matches);
    }

    private boolean matchesConverted(RecipeHolder<InfuserRecipe> holder, Level level,
                                     Map<Ingredient, Long> mergedInputs,
                                     Map<FluidStack, Long> mergedFluids) {
        List<AdvancedAlloyFurnaceRecipe> converted = convertAll(holder, level);
        return !converted.isEmpty() && ExtraUpgradesCraftingRecipeAdapterSupport.matches(
                converted.get(0), mergedInputs, mergedFluids);
    }
}
