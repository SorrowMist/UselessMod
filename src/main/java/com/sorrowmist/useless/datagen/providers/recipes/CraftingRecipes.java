package com.sorrowmist.useless.datagen.providers.recipes;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipeBuilder;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class CraftingRecipes extends RecipeProvider {
    public CraftingRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(@NotNull RecipeOutput consumer) {
        this.addMoldRecipes(consumer);
        this.addAdvancedAlloyFurnaceBlockRecipe(consumer);
        this.addEndlessBeafItemRecipe(consumer);
        this.addOreGeneratorBlockRecipe(consumer);
        this.addTeleportBlockRecipes(consumer);
        this.addAdvancedAlloyFurnaceRecipes(consumer);
    }

    private void addMoldRecipes(RecipeOutput consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.METAL_MOLD_PLATE.get(), 1)
                           .pattern("XXX")
                           .define('X', ModItems.USELESS_INGOT_TIER_1.get())
                           .unlockedBy("has_ingot", has(ModItems.USELESS_INGOT_TIER_1.get()))
                           .save(consumer, UselessMod.id("mold/metal_mold_plate"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.METAL_MOLD_ROD.get(), 1)
                           .pattern("  X")
                           .pattern(" X ")
                           .pattern("X  ")
                           .define('X', ModItems.USELESS_INGOT_TIER_1.get())
                           .unlockedBy("has_ingot", has(ModItems.USELESS_INGOT_TIER_1.get()))
                           .save(consumer, UselessMod.id("mold/metal_mold_rod"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.METAL_MOLD_GEAR.get(), 1)
                           .pattern(" X ")
                           .pattern("X X")
                           .pattern(" X ")
                           .define('X', ModItems.USELESS_INGOT_TIER_1.get())
                           .unlockedBy("has_ingot", has(ModItems.USELESS_INGOT_TIER_1.get()))
                           .save(consumer, UselessMod.id("mold/metal_mold_gear"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.METAL_MOLD_WIRE.get(), 1)
                           .pattern(" X ")
                           .pattern("XXX")
                           .pattern(" X ")
                           .define('X', ModItems.USELESS_INGOT_TIER_1.get())
                           .unlockedBy("has_ingot", has(ModItems.USELESS_INGOT_TIER_1.get()))
                           .save(consumer, UselessMod.id("mold/metal_mold_wire"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.METAL_MOLD_BLOCK.get(), 1)
                           .pattern("XXX")
                           .pattern("X X")
                           .pattern("XXX")
                           .define('X', ModItems.USELESS_INGOT_TIER_1.get())
                           .unlockedBy("has_ingot", has(ModItems.USELESS_INGOT_TIER_1.get()))
                           .save(consumer, UselessMod.id("mold/metal_mold_block"));
    }

    private void addAdvancedAlloyFurnaceBlockRecipe(RecipeOutput consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                   ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get(),
                                   1
                           )
                           .pattern("ABC")
                           .pattern("DEF")
                           .pattern("GHI")
                           .define('A', Blocks.EMERALD_BLOCK)
                           .define('B', Blocks.DIAMOND_BLOCK)
                           .define('C', Blocks.LAPIS_BLOCK)
                           .define('D', Blocks.IRON_BLOCK)
                           .define('E', Blocks.FURNACE)
                           .define('F', Blocks.GOLD_BLOCK)
                           .define('G', Items.WATER_BUCKET)
                           .define('H', Blocks.NETHERITE_BLOCK)
                           .define('I', Items.LAVA_BUCKET)
                           .unlockedBy("has_furnace", has(Blocks.FURNACE))
                           .save(consumer);
    }

    private void addAdvancedAlloyFurnaceRecipes(RecipeOutput output) {
        // 示例1：普通合金合成（NORMAL 模式）
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.IRON_INGOT), 3)
                                         .input(Ingredient.of(Tags.Items.INGOTS_COPPER), 2)
                                         .fluidInput(new FluidStack(Fluids.LAVA, 1000))
                                         .output(ModItems.USELESS_GEAR_TIER_1.get(), 1)
                                         .energy(4800)
                                         .processTime(300)
                                         .catalyst(Ingredient.of(ModItems.USELESS_GEAR_TIER_1.get()), 5)
                                         .mold(Ingredient.of(ModItems.METAL_MOLD_BLOCK.get()))
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/normal_alloy"));
    }

    private void addEndlessBeafItemRecipe(RecipeOutput consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ENDLESS_BEAF_ITEM.get(), 1)
                           .pattern("ABC")
                           .pattern("DEF")
                           .pattern("IGH")
                           .define('A', ModItems.USELESS_INGOT_TIER_5.get())
                           .define('B', ModItems.USELESS_INGOT_TIER_4.get())
                           .define('C', ModItems.USELESS_INGOT_TIER_3.get())
                           .define('D', Items.DIAMOND_PICKAXE)
                           .define('E', ModItems.USELESS_INGOT_TIER_2.get())
                           .define('F', Items.NETHERITE_PICKAXE)
                           .define('G', ModItems.USELESS_INGOT_TIER_1.get())
                           .define('H', Items.GOLDEN_CARROT)
                           .define('I', Items.GHAST_TEAR)
                           .unlockedBy("has_ingot", has(ModItems.USELESS_INGOT_TIER_1.get()))
                           .save(consumer);
    }

    private void addOreGeneratorBlockRecipe(RecipeOutput consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                   ModBlocks.ORE_GENERATOR_BLOCK.get(),
                                   1
                           )
                           .pattern("AAA")
                           .pattern("AAA")
                           .pattern("AAA")
                           .define('A', ModItems.USEFUL_INGOT.get())
                           .unlockedBy("has_ingot", has(ModItems.USEFUL_INGOT.get()))
                           .save(consumer);
    }

    private void addTeleportBlockRecipes(RecipeOutput consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                   ModBlocks.TELEPORT_BLOCK.get(),
                                   1
                           )
                           .pattern("AAA")
                           .pattern("ABA")
                           .pattern("AAA")
                           .define('A', ItemTags.PLANKS)
                           .define('B', Blocks.DIRT)
                           .unlockedBy("has_planks", has(ItemTags.PLANKS))
                           .save(consumer, UselessMod.id("teleport/teleport_block"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                   ModBlocks.TELEPORT_BLOCK_2.get(),
                                   1
                           )
                           .pattern("BBB")
                           .pattern("BAB")
                           .pattern("BBB")
                           .define('A', ItemTags.PLANKS)
                           .define('B', Blocks.DIRT)
                           .unlockedBy("has_planks", has(ItemTags.PLANKS))
                           .save(consumer, UselessMod.id("teleport/teleport_block_2"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                   ModBlocks.TELEPORT_BLOCK_3.get(),
                                   1
                           )
                           .pattern("BBB")
                           .pattern("AAA")
                           .pattern("BBB")
                           .define('B', ItemTags.PLANKS)
                           .define('A', Blocks.DIRT)
                           .unlockedBy("has_planks", has(ItemTags.PLANKS))
                           .save(consumer, UselessMod.id("teleport/teleport_block_3"));
    }
}
