package com.sorrowmist.useless.content.recipe.adapters.justdirethings;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import com.direwolf20.justdirethings.setup.Registration;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.BlockTagIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

final class JustDireThingsRecipeAdapterUtils {
    static final int FLUID_AMOUNT = 1000;
    static final int RAW_ORE_DROP_COUNT = 4;
    private static final List<Item> GOO_ITEMS = List.of(
            Registration.GooBlock_Tier1_ITEM.get(),
            Registration.GooBlock_Tier2_ITEM.get(),
            Registration.GooBlock_Tier3_ITEM.get(),
            Registration.GooBlock_Tier4_ITEM.get());

    private JustDireThingsRecipeAdapterUtils() {
    }

    static Ingredient gooMold(int tier) {
        if (tier < 1 || tier > GOO_ITEMS.size()) {
            return Ingredient.EMPTY;
        }
        ItemStack[] molds = GOO_ITEMS.subList(tier - 1, GOO_ITEMS.size()).stream()
                .map(ItemStack::new)
                .toArray(ItemStack[]::new);
        return Ingredient.of(molds);
    }

    static boolean matchesMold(int tier, @Nullable ItemStack mold) {
        Ingredient required = gooMold(tier);
        return !required.isEmpty() && mold != null && !mold.isEmpty() && required.test(mold);
    }

    @Nullable
    static Ingredient blockInput(BlockState state) {
        if (state == null) return null;
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? null : Ingredient.of(item);
    }

    static Ingredient blockTagInput(BlockTagIngredient input) {
        if (input == null) return Ingredient.EMPTY;
        ItemStack[] items = input.getItems().toArray(ItemStack[]::new);
        return items.length == 0 ? Ingredient.EMPTY : Ingredient.of(items);
    }

    @Nullable
    static Fluid fluid(BlockState state) {
        if (state == null) return null;
        Fluid fluid = state.getFluidState().getType();
        return fluid == Fluids.EMPTY ? null : fluid;
    }

    static List<LongSizedFluidIngredient> fluidInput(Fluid fluid) {
        return List.of(LongSizedFluidIngredient.from(new FluidStack(fluid, FLUID_AMOUNT)));
    }

    static boolean matchesFluid(Fluid fluid, @Nullable Map<FluidStack, Long> available) {
        if (fluid == null || available == null || available.isEmpty()) return false;
        return FluidIngredientAllocator.matchesLong(
                fluidInput(fluid), available, 1L);
    }

    /**
     * The raw ore loot tables produce 3-4 units of the corresponding resource. The alloy furnace
     * recipe is deterministic, so use the table's maximum output rather than exposing the raw ore
     * block as an intermediate item.
     */
    static ItemStack rawOreDrop(BlockState state) {
        if (state == null) return ItemStack.EMPTY;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null || !"justdirethings".equals(blockId.getNamespace())) {
            return ItemStack.EMPTY;
        }

        String itemPath = switch (blockId.getPath()) {
            case "raw_ferricore_ore" -> "raw_ferricore";
            case "raw_blazegold_ore" -> "raw_blazegold";
            case "raw_celestigem_ore" -> "celestigem";
            case "raw_eclipsealloy_ore" -> "raw_eclipsealloy";
            case "raw_coal_t1_ore" -> "coal_t1";
            case "raw_coal_t2_ore" -> "coal_t2";
            case "raw_coal_t3_ore" -> "coal_t3";
            case "raw_coal_t4_ore" -> "coal_t4";
            default -> null;
        };
        if (itemPath == null) return ItemStack.EMPTY;

        Item item = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("justdirethings", itemPath));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, RAW_ORE_DROP_COUNT);
    }

    static boolean matchesItem(Ingredient required, @Nullable Map<Ingredient, Long> available) {
        if (required == null || required.isEmpty() || available == null || available.isEmpty()) {
            return false;
        }
        return AdapterUtils.matchesRequired(available, Map.of(required, 1L));
    }

}
