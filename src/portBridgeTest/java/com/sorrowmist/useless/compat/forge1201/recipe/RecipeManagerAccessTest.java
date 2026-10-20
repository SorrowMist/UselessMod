package com.sorrowmist.useless.compat.forge1201.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RecipeManagerAccessTest {
    private static final RecipeType<TestRecipe> TYPE=new RecipeType<>() {};
    private record TestRecipe(ResourceLocation id) implements Recipe<Container> {
        public boolean matches(Container input,Level level) { return false; }
        public ItemStack assemble(Container input,RegistryAccess registry) { return ItemStack.EMPTY; }
        public boolean canCraftInDimensions(int width,int height) { return true; }
        public ItemStack getResultItem(RegistryAccess registry) { return ItemStack.EMPTY; }
        public ResourceLocation getId() { return id; }
        public RecipeSerializer<?> getSerializer() { return null; }
        public RecipeType<?> getType() { return TYPE; }
    }
    @Test void runtimeIdsArePreservedAndReloadDoesNotReturnStaleRecipes() {
        RecipeManager manager=new RecipeManager();
        var original=new TestRecipe(new ResourceLocation("kubejs","pack_generated_original"));
        var replacement=new TestRecipe(new ResourceLocation("kubejs","pack_reloaded_replacement"));
        manager.replaceRecipes(List.of(original));
        assertEquals(original.id(),RecipeManagerAccess.getAllRecipesFor(manager,TYPE).get(0).id());
        assertSame(original,RecipeManagerAccess.byKey(manager,original.id()).orElseThrow().value());
        manager.replaceRecipes(List.of(replacement));
        assertTrue(RecipeManagerAccess.byKey(manager,original.id()).isEmpty());
        assertEquals(List.of(RecipeHolder.of(replacement)),RecipeManagerAccess.getAllRecipesFor(manager,TYPE));
        assertEquals(1,RecipeManagerAccess.getRecipes(manager).size());
    }
}
