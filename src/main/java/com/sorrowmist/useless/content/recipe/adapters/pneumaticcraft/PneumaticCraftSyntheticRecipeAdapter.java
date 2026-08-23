package com.sorrowmist.useless.content.recipe.adapters.pneumaticcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import me.desht.pneumaticcraft.common.block.entity.processing.UVLightBoxBlockEntity;
import me.desht.pneumaticcraft.common.item.EmptyPCBItem;
import me.desht.pneumaticcraft.common.registry.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exposes PneumaticCraft's UV and etching data, which is generated from registered PCB items. */
public final class PneumaticCraftSyntheticRecipeAdapter
        implements IRecipeAdapter<PneumaticCraftSyntheticRecipe> {
    private enum Kind {
        ETCHING,
        UV_LIGHT_BOX
    }

    private final Kind kind;

    private PneumaticCraftSyntheticRecipeAdapter(Kind kind) {
        this.kind = kind;
    }

    public static PneumaticCraftSyntheticRecipeAdapter etching() {
        return new PneumaticCraftSyntheticRecipeAdapter(Kind.ETCHING);
    }

    public static PneumaticCraftSyntheticRecipeAdapter uvLightBox() {
        return new PneumaticCraftSyntheticRecipeAdapter(Kind.UV_LIGHT_BOX);
    }

    @Override
    public String sourceId() {
        return RecipeSourceIds.PNEUMATICCRAFT;
    }

    @Override
    public Class<PneumaticCraftSyntheticRecipe> getRecipeClass() {
        return PneumaticCraftSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(kind == Kind.ETCHING
                ? ModBlocks.ETCHING_TANK.get()
                : ModBlocks.UV_LIGHT_BOX.get());
    }

    @Override
    public List<RecipeHolder<PneumaticCraftSyntheticRecipe>> getGeneratedRecipes(Level level) {
        List<RecipeHolder<PneumaticCraftSyntheticRecipe>> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof EmptyPCBItem pcb)) continue;

            AdvancedAlloyFurnaceRecipe converted = kind == Kind.ETCHING
                    ? etchingRecipe(pcb, item)
                    : uvLightBoxRecipe(pcb, item);
            if (converted != null) {
                result.add(new RecipeHolder<>(converted.id(),
                        new PneumaticCraftSyntheticRecipe(converted)));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<PneumaticCraftSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<PneumaticCraftSyntheticRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();

        Map<Ingredient, Long> safeInputs = mergedInputs == null ? Map.of() : mergedInputs;
        Map<FluidStack, Long> safeFluids = mergedFluids == null ? Map.of() : mergedFluids;
        List<RecipeHolder<PneumaticCraftSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<PneumaticCraftSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (recipe != null && PneumaticCraftRecipeAdapter.matchesConverted(
                    recipe, safeInputs, safeFluids)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe etchingRecipe(EmptyPCBItem pcb, Item item) {
        ItemStack input = new ItemStack(item);
        FluidStack acid = EmptyPCBItem.getEtchingFluid();
        SizedFluidIngredient fluid = AdapterUtils.toSizedFluidIngredient(acid);
        if (fluid == null) return null;

        ResourceLocation id = itemId(item, "etching");
        return recipe(id, input, List.of(fluid), pcb.getSuccessItem().copy());
    }

    private AdvancedAlloyFurnaceRecipe uvLightBoxRecipe(EmptyPCBItem pcb, Item item) {
        ItemStack input = new ItemStack(item);
        ItemStack output = UVLightBoxBlockEntity.setExposureProgress(input.copy(), 100);
        return recipe(itemId(item, "uv_light_box"), input, List.of(), output);
    }

    private AdvancedAlloyFurnaceRecipe recipe(
            ResourceLocation id,
            ItemStack input,
            List<SizedFluidIngredient> fluids,
            ItemStack output) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(new CountedIngredient(Ingredient.of(input.copyWithCount(1)), 1L)),
                fluids,
                List.of(),
                List.of(output),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(Ingredient.of(getMoldItem())),
                AlloyFurnaceMode.NORMAL);
    }

    private static ResourceLocation itemId(Item item, String prefix) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String path = itemId == null ? "unknown" : itemId.getNamespace() + "_" + itemId.getPath();
        return ResourceLocation.fromNamespaceAndPath(
                RecipeSourceIds.PNEUMATICCRAFT, prefix + "_" + path.replace('/', '_'));
    }
}
