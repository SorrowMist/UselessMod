package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.NbtCanonical;
import com.sorrowmist.useless.compat.forge1201.recipe.NativeIngredients;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeFingerprintPortTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    @Test void canonicalNbtPreservesTypesButIgnoresCompoundInsertionOrder() throws Exception {
        var first=TagParser.parseTag("{Aa:1,BB:2,nested:{z:3s,a:[L;1L,2L]},ordered:[1,2]}");
        var second=TagParser.parseTag("{BB:2,Aa:1,ordered:[1,2],nested:{a:[L;1L,2L],z:3s}}");
        assertEquals(NbtCanonical.snbt(first),NbtCanonical.snbt(second));
        assertEquals(first,TagParser.parseTag(NbtCanonical.snbt(first)));
        second.getCompound("nested").putInt("z",3);
        assertNotEquals(NbtCanonical.snbt(first),NbtCanonical.snbt(second));
    }

    @Test void recipeIdentityIncludesAllNbtAndLargeCountWithoutDependingOnInsertionOrder() throws Exception {
        var first=new ItemStack(Items.DIAMOND,32768);
        first.setTag(TagParser.parseTag("{Aa:1,BB:2,OtherMod:{b:3,a:4}}"));
        var second=first.copy(); second.setTag(TagParser.parseTag("{BB:2,Aa:1,OtherMod:{a:4,b:3}}"));
        var id=new ResourceLocation("kubejs:pack_recipe");
        var recipe1=AdvancedAlloyFurnaceRecipeBuilder.create().input(Items.IRON_INGOT,2).output(first).build(id);
        var recipe2=AdvancedAlloyFurnaceRecipeBuilder.create().input(Items.IRON_INGOT,2).output(second).build(id);
        assertEquals(AlloyFurnaceRecipeFingerprint.create(recipe1,RegistryAccess.EMPTY),AlloyFurnaceRecipeFingerprint.create(recipe2,RegistryAccess.EMPTY));
        second.getOrCreateTag().getCompound("OtherMod").putInt("a",5);
        var recipe3=AdvancedAlloyFurnaceRecipeBuilder.create().input(Items.IRON_INGOT,2).output(second).build(id);
        assertNotEquals(AlloyFurnaceRecipeFingerprint.create(recipe1,RegistryAccess.EMPTY),AlloyFurnaceRecipeFingerprint.create(recipe3,RegistryAccess.EMPTY));
        assertNotEquals(AlloyFurnaceRecipeFingerprint.safeItemStack(first,RegistryAccess.EMPTY),
                AlloyFurnaceRecipeFingerprint.safeItemStack(first.copyWithCount(32767),RegistryAccess.EMPTY));
    }

    @Test void ingredientPacketKeepsDirectTagReferenceRatherThanMemberSnapshot() {
        var tag=TagKey.create(Registries.ITEM,new ResourceLocation("forge:ingots/iron"));
        var ingredient=Ingredient.of(tag);
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            RecipeCodecs.INGREDIENT_STREAM.encode(buf,ingredient);
            var restored=RecipeCodecs.INGREDIENT_STREAM.decode(buf);
            assertEquals(tag,NativeIngredients.directItemTag(restored).orElseThrow());
            assertEquals(ingredient.toJson(),restored.toJson());
        } finally { buf.release(); }
    }
}
