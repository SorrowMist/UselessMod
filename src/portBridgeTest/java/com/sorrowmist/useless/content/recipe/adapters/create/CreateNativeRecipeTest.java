package com.sorrowmist.useless.content.recipe.adapters.create;

import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateNativeRecipeTest {
    private static final ResourceLocation ID=new ResourceLocation("create:test_port_recipe");
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }

    @Test void plainFluidWithEmptyNativeNbtMatchesAnUntaggedStack() {
        var source=FluidIngredient.fromFluid(Fluids.WATER,250);
        SizedFluidIngredient converted=CreateRecipeAdapterUtils.nativeFluid(source);
        assertEquals(250,converted.amount());
        var input=new FluidStack(Fluids.WATER,250);
        assertTrue(converted.ingredient().test(input));
        assertFalse(converted.ingredient().test(new FluidStack(Fluids.LAVA,250)));
        assertNull(input.getTag());
    }

    @Test void nativeFluidTagsStayLiveInsteadOfBecomingAFluidSnapshot() {
        TagKey<Fluid> tag=TagKey.create(Registries.FLUID,new ResourceLocation("useless_port_test:create_liquid"));
        var nativeIngredient=FluidIngredient.fromTag(tag,125);
        var converted=CreateRecipeAdapterUtils.nativeFluid(nativeIngredient);
        assertFalse(converted.ingredient().isEmpty());
        assertEquals(List.of(tag),converted.ingredient().tagOnlyTags().orElseThrow());
        BuiltInRegistries.FLUID.bindTags(Map.of(tag,List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER))));
        assertTrue(converted.ingredient().test(new FluidStack(Fluids.WATER,1)));
        BuiltInRegistries.FLUID.bindTags(Map.of(tag,List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.LAVA))));
        assertFalse(converted.ingredient().test(new FluidStack(Fluids.WATER,1)));
        assertTrue(converted.ingredient().test(new FluidStack(Fluids.LAVA,1)));
    }

    @Test void partialNativeNbtKeepsExactListsAndSurvivesSerialization() {
        var required=new FluidStack(Fluids.WATER,100);
        required.getOrCreateTag().putString("grade","pure");
        ListTag list=new ListTag(); list.add(StringTag.valueOf("one"));
        required.getOrCreateTag().put("list",list);
        var nativeIngredient=FluidIngredient.fromFluidStack(required);
        var converted=CreateRecipeAdapterUtils.nativeFluid(nativeIngredient).ingredient();
        var extra=required.copy(); extra.getOrCreateTag().putInt("extra",42);
        assertTrue(nativeIngredient.test(extra)); assertTrue(converted.test(extra));
        var moreList=extra.copy(); moreList.getTag().getList("list",8).add(StringTag.valueOf("two"));
        assertFalse(nativeIngredient.test(moreList)); assertFalse(converted.test(moreList));
        assertFalse(converted.test(new FluidStack(Fluids.WATER,100)));
        var restored=com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient.fromJson(converted.toJson());
        assertTrue(restored.test(extra)); assertFalse(restored.test(moreList));
    }

    @Test void independentChanceScalesEveryItemAndFluidInput() {
        MillingRecipe source=mock(MillingRecipe.class);
        when(source.getProcessingDuration()).thenReturn(40);
        when(source.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.IRON_INGOT),Ingredient.of(Items.IRON_INGOT)));
        when(source.getFluidIngredients()).thenReturn(NonNullList.of(FluidIngredient.EMPTY,
                FluidIngredient.fromFluid(Fluids.WATER,100)));
        when(source.getRollableResults()).thenReturn(List.of(new ProcessingOutput(new ItemStack(Items.GOLD_INGOT),0.5F)));
        when(source.getFluidResults()).thenReturn(NonNullList.create());
        var recipe=CreateRecipeAdapterUtils.convertProcessing(ID,source,List.of(Ingredient.of(Items.PISTON)),
                LoggerFactory.getLogger(CreateNativeRecipeTest.class));
        assertNotNull(recipe);
        assertEquals(4,recipe.inputs().get(0).count());
        assertEquals(200,recipe.inputFluids().get(0).amount());
        assertEquals(80,recipe.processTime());
        assertEquals(1,recipe.outputs().get(0).getCount());
    }

    private static final class AssemblyFixture extends SequencedAssemblyRecipe {
        AssemblyFixture(PressingRecipe step) {
            super(ID,null);
            ingredient=Ingredient.of(Items.GOLD_INGOT);
            loops=5;
            sequence.add(new SequencedRecipe<>(step));
            resultPool.add(new ProcessingOutput(new ItemStack(Items.DIAMOND),4.0F));
            resultPool.add(new ProcessingOutput(new ItemStack(Items.DIRT),1.0F));
        }
    }
    @SuppressWarnings("unchecked")
    private static PressingRecipe assemblyStep() {
        PressingRecipe step=mock(PressingRecipe.class);
        when(step.supportsAssembly()).thenReturn(true);
        when(step.getProcessingDuration()).thenReturn(10);
        doAnswer(call->{ ((List<Ingredient>)call.getArgument(0)).add(Ingredient.of(Items.IRON_NUGGET)); return null; })
                .when(step).addAssemblyIngredients(anyList());
        doAnswer(call->{ ((List<FluidIngredient>)call.getArgument(0)).add(FluidIngredient.fromFluid(Fluids.WATER,100)); return null; })
                .when(step).addAssemblyFluidIngredients(anyList());
        doAnswer(call->{ ((Set<net.minecraft.world.level.ItemLike>)call.getArgument(0)).add(Items.PISTON); return null; })
                .when(step).addRequiredMachines(anySet());
        return step;
    }
    @Test void assemblyUsesExclusiveWeightsAndScalesAllFiveLoops() {
        var source=new AssemblyFixture(assemblyStep());
        var recipe=CreateSequencedAssemblyRecipeAdapter.convert(ID,source);
        assertNotNull(recipe);
        assertEquals(5,recipe.inputs().stream().filter(i->i.ingredient().test(new ItemStack(Items.GOLD_INGOT))).findFirst().orElseThrow().count());
        assertEquals(25,recipe.inputs().stream().filter(i->i.ingredient().test(new ItemStack(Items.IRON_NUGGET))).findFirst().orElseThrow().count());
        assertEquals(2500,recipe.inputFluids().get(0).amount());
        assertEquals(250,recipe.processTime());
        assertEquals(4,recipe.outputs().stream().filter(s->s.is(Items.DIAMOND)).findFirst().orElseThrow().getCount());
        assertEquals(1,recipe.outputs().stream().filter(s->s.is(Items.DIRT)).findFirst().orElseThrow().getCount());
    }
    @Test void unsupportedAssemblyStepRejectsWholeRecipeInsteadOfMakingItCheaper() {
        var step=assemblyStep(); when(step.supportsAssembly()).thenReturn(false);
        assertNull(CreateSequencedAssemblyRecipeAdapter.convert(ID,new AssemblyFixture(step)));
    }
    @Test void overflowRejectsWholeRequiredInputSet() {
        assertNull(CreateRecipeAdapterUtils.scaleItemInputs(Map.of(Ingredient.of(Items.IRON_INGOT),Long.MAX_VALUE),
                2,ID,LoggerFactory.getLogger(CreateNativeRecipeTest.class)));
    }
}
