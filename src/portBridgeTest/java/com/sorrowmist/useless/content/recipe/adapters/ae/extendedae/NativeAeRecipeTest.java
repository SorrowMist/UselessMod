package com.sorrowmist.useless.content.recipe.adapters.ae.extendedae;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.glodblock.github.extendedae.recipe.CircuitCutterRecipe;
import com.glodblock.github.extendedae.recipe.util.IngredientStack;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.adapters.ae.advancedae.ReactionChamberRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NativeAeRecipeTest {
    static final ResourceLocation ID=new ResourceLocation("useless_port_test:ae_native");
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    final CircuitCutterRecipeAdapter cutter=new CircuitCutterRecipeAdapter() {
        @Override public ItemStack getMoldItem() { return new ItemStack(Items.PISTON); }
    };
    final ReactionChamberRecipeAdapter chamber=new ReactionChamberRecipeAdapter() {
        @Override public ItemStack getMoldItem() { return new ItemStack(Items.PISTON); }
    };
    static CircuitCutterRecipe cutting(IngredientStack.Fluid fluid) {
        return new CircuitCutterRecipe(ID,new ItemStack(Items.GOLD_NUGGET,9),
                IngredientStack.of(Ingredient.of(Items.GOLD_BLOCK),1),fluid);
    }
    @Test void cutterRequiresFullWaterCostAndPreservesNineOutputs() {
        var recipe=cutter.convert(new RecipeHolder<>(ID,cutting(IngredientStack.of(new FluidStack(Fluids.WATER,100)))),null);
        assertNotNull(recipe);
        assertEquals(100,recipe.inputFluids().get(0).amount());
        assertEquals(9,recipe.outputs().get(0).getCount());
        var items=Map.of(Ingredient.of(Items.GOLD_BLOCK),1L);
        assertFalse(cutter.matches(recipe,items,Map.of()));
        assertFalse(cutter.matches(recipe,items,Map.of(new FluidStack(Fluids.WATER,1),99L)));
        assertTrue(cutter.matches(recipe,items,Map.of(new FluidStack(Fluids.WATER,1),100L)));
        assertFalse(cutter.matches(recipe,items,Map.of(new FluidStack(Fluids.LAVA,1),100L)));
    }
    @Test void extendedAeFluidFamiliesAndNbtFollowTheNativePredicate() {
        var required=new FluidStack(Fluids.WATER,100);
        required.getOrCreateTag().putInt("ignored_by_native",1);
        var source=com.glodblock.github.extendedae.recipe.util.FluidIngredient.of(required);
        var converted=CircuitCutterRecipeAdapter.fluid(IngredientStack.of(source,100)).ingredient();
        for (var fluid:List.of(Fluids.WATER,Fluids.FLOWING_WATER,Fluids.LAVA)) {
            var stack=new FluidStack(fluid,100);
            assertEquals(source.test(stack),converted.test(stack));
            stack.getOrCreateTag().putString("extra","ok");
            assertEquals(source.test(stack),converted.test(stack));
        }
        var restored=FluidIngredient.fromJson(converted.toJson());
        assertTrue(restored.test(new FluidStack(Fluids.FLOWING_WATER,1)));
    }
    @Test void extendedAeFluidTagsSurviveReloadAndKeepFlowingFamilySemantics() {
        TagKey<Fluid> tag=TagKey.create(Registries.FLUID,new ResourceLocation("useless_port_test:eae_fluid"));
        BuiltInRegistries.FLUID.bindTags(Map.of(tag,List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER))));
        var source=com.glodblock.github.extendedae.recipe.util.FluidIngredient.of(tag);
        var converted=CircuitCutterRecipeAdapter.fluid(IngredientStack.of(source,100)).ingredient();
        assertEquals(source.test(new FluidStack(Fluids.FLOWING_WATER,1)),converted.test(new FluidStack(Fluids.FLOWING_WATER,1)));
        var restored=FluidIngredient.fromJson(converted.toJson());
        BuiltInRegistries.FLUID.bindTags(Map.of(tag,List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.LAVA))));
        assertFalse(restored.test(new FluidStack(Fluids.WATER,1)));
        assertTrue(restored.test(new FluidStack(Fluids.FLOWING_LAVA,1)));
    }
    @Test void cutterRejectsMalformedMandatoryFluidInsteadOfDroppingIt() {
        assertNull(cutter.convert(new RecipeHolder<>(ID,cutting(null)),null));
        assertNull(cutter.convert(new RecipeHolder<>(ID,cutting(IngredientStack.of(new FluidStack(Fluids.WATER,0)))),null));
    }
    static ReactionChamberRecipe reaction(GenericStack output,int secondCount) {
        return new ReactionChamberRecipe(ID,output,List.of(
                new net.pedroksl.ae2addonlib.recipes.IngredientStack.Item(Ingredient.of(Items.IRON_INGOT),2),
                new net.pedroksl.ae2addonlib.recipes.IngredientStack.Item(Ingredient.of(Items.IRON_INGOT),secondCount)),
                new net.pedroksl.ae2addonlib.recipes.IngredientStack.Fluid(Fluids.WATER,500),12000);
    }
    @Test void reactionAllocatesDuplicateInputsAndFullFluidCost() {
        var output=new GenericStack(AEItemKey.of(Items.DIAMOND),3);
        var recipe=chamber.convert(new RecipeHolder<>(ID,reaction(output,2)),null);
        assertNotNull(recipe);
        assertFalse(chamber.matches(recipe,Map.of(Ingredient.of(Items.IRON_INGOT),3L),Map.of(new FluidStack(Fluids.WATER,1),500L)));
        assertFalse(chamber.matches(recipe,Map.of(Ingredient.of(Items.IRON_INGOT),4L),Map.of(new FluidStack(Fluids.WATER,1),499L)));
        assertTrue(chamber.matches(recipe,Map.of(Ingredient.of(Items.IRON_INGOT),4L),Map.of(new FluidStack(Fluids.WATER,1),500L)));
        assertEquals(3,recipe.outputs().get(0).getCount());
        assertEquals(12000,recipe.energy());
    }
    @Test void reactionPreservesFluidOutputAndNativeFluidPredicate() {
        var source=reaction(new GenericStack(AEFluidKey.of(Fluids.LAVA),2000),2);
        var recipe=chamber.convert(new RecipeHolder<>(ID,source),null);
        assertNotNull(recipe);
        assertTrue(recipe.outputs().isEmpty());
        assertEquals(2000,recipe.outputFluids().get(0).getAmount());
        var nbtWater=new FluidStack(Fluids.WATER,500); nbtWater.getOrCreateTag().putString("arbitrary","yes");
        assertEquals(source.getFluid().test(nbtWater),recipe.inputFluids().get(0).ingredient().test(nbtWater));
        var flowing=new FluidStack(Fluids.FLOWING_WATER,500);
        assertEquals(source.getFluid().test(flowing),recipe.inputFluids().get(0).ingredient().test(flowing));
    }
    @Test void invalidReactionInputsAndOverflowingOutputsRejectWholeRecipe() {
        assertNull(chamber.convert(new RecipeHolder<>(ID,reaction(new GenericStack(AEItemKey.of(Items.DIAMOND),1),-2)),null));
        assertNull(chamber.convert(new RecipeHolder<>(ID,reaction(new GenericStack(AEItemKey.of(Items.DIAMOND),(long)Integer.MAX_VALUE+1),2)),null));
        assertNull(chamber.convert(new RecipeHolder<>(ID,reaction(new GenericStack(AEFluidKey.of(Fluids.WATER),0),2)),null));
    }
    @Test void crystalFixingAccountsForRandomProgressAndFuelProbability() {
        // 100 / mean(nextInt(5)) calls, each burning fuel with probability 0.1,
        // plus nonzero overshoot: round expected cost up to six, never one fuel.
        assertEquals(6,CrystalFixerRecipeAdapter.fuelPerRepair());
    }
}
