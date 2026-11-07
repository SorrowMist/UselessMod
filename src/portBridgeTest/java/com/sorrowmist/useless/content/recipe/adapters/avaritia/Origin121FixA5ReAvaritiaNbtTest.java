package com.sorrowmist.useless.content.recipe.adapters.avaritia;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingAdapterUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Origin121FixA5ReAvaritiaNbtTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }

    private static ItemStack singularity(String subtype) {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.getOrCreateTag().putString("SingularityId", subtype);
        stack.getOrCreateTag().putLong("foreign:identity", 3_000_000_007L);
        return stack;
    }

    @SuppressWarnings("unchecked")
    private static List<Ingredient> normalize(List<Ingredient> ingredients) throws Exception {
        var method = ReAvaritiaTableRecipeAdapter.class.getDeclaredMethod("normalizeNbtSensitive", List.class);
        method.setAccessible(true);
        return (List<Ingredient>) method.invoke(null, ingredients);
    }

    private static Map<Ingredient, Long> merge(List<Ingredient> ingredients) throws Exception {
        var requirements = new IdentityHashMap<Ingredient, Long>();
        var method = ReAvaritiaTableRecipeAdapter.class.getDeclaredMethod("mergeTableIngredients", Map.class, List.class);
        method.setAccessible(true);
        assertEquals(true, method.invoke(null, requirements, ingredients));
        return requirements;
    }

    @Test void distinctSubtypesBecomeStrictAndCannotAllBeSatisfiedByOneSubtype() throws Exception {
        var iron = singularity("iron");
        var gold = singularity("gold");
        var originalNbt = iron.getTag().copy();
        var normalized = normalize(List.of(Ingredient.of(iron), Ingredient.of(gold), Ingredient.of(iron.copyWithCount(64))));
        assertTrue(normalized.stream().allMatch(StrictNBTIngredient.class::isInstance));
        assertTrue(normalized.get(0).test(iron));
        assertFalse(normalized.get(0).test(gold));
        assertFalse(normalized.get(0).isSimple());
        var requirements = merge(normalized);
        assertEquals(2, requirements.size());
        var counted = ExtendedCraftingAdapterUtils.countedIngredients(requirements);
        assertEquals(3L, counted.stream().mapToLong(input -> input.count()).sum());
        assertFalse(ItemIngredientAllocator.matches(counted, List.of(iron.copyWithCount(3)), 1));
        assertTrue(ItemIngredientAllocator.matches(counted, List.of(iron.copyWithCount(2), gold), 1));
        assertEquals(originalNbt, iron.getTag());
        assertEquals(3_000_000_007L, normalized.get(0).getItems()[0].getTag().getLong("foreign:identity"));
    }

    @Test void noSubtypeDeltaLeavesOrdinaryNbtAgnosticSemanticsUntouched() throws Exception {
        var iron = Ingredient.of(singularity("iron"));
        var repeated = List.of(iron, Ingredient.of(singularity("iron").copyWithCount(7)), Ingredient.of(Items.DIAMOND));
        assertSame(repeated, normalize(repeated));
        assertTrue(iron.isSimple());
        assertTrue(iron.test(singularity("any_other_subtype")));
        assertEquals(2, merge(repeated).size());
    }

    @Test void tagsAlternativesAndOpaqueCustomPredicatesAreNotTightened() throws Exception {
        var tag = Ingredient.of(TagKey.create(Registries.ITEM, new ResourceLocation("test", "singularities")));
        var alternatives = Ingredient.of(singularity("iron"), new ItemStack(Items.GOLD_INGOT));
        var custom = mock(Ingredient.class);
        when(custom.isEmpty()).thenReturn(false);
        when(custom.isVanilla()).thenReturn(false);
        var unrelated = Ingredient.of(Items.DIAMOND);
        var normalized = normalize(List.of(Ingredient.of(singularity("iron")), Ingredient.of(singularity("gold")),
                tag, alternatives, custom, unrelated, Ingredient.EMPTY));
        assertSame(tag, normalized.get(2));
        assertSame(alternatives, normalized.get(3));
        assertSame(custom, normalized.get(4));
        assertSame(unrelated, normalized.get(5));
        assertSame(Ingredient.EMPTY, normalized.get(6));
    }

    @Test void strictAndUntaggedVariantsStayDistinctButIdenticalStrictStacksStillMerge() throws Exception {
        var exact = StrictNBTIngredient.of(singularity("iron"));
        var plain = Ingredient.of(Items.IRON_INGOT);
        var normalized = normalize(List.of(exact, plain, exact));
        assertFalse(normalized.get(1).test(singularity("iron")));
        assertTrue(normalized.get(1).test(new ItemStack(Items.IRON_INGOT)));
        assertEquals(2, merge(normalized).size());
    }
}
