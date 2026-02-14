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
        this.addUselessIngotRecipes(output);
        this.addUselessGearRecipes(output);
        this.addUselessGlassRecipes(output);
    }

    private void addUselessIngotRecipes(RecipeOutput output) {
        // Tier 1 - 基础合成
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.MUTTON), 2)
                                         .input(Ingredient.of(Items.BEEF), 2)
                                         .input(Ingredient.of(Items.CHICKEN), 2)
                                         .input(Ingredient.of(Items.IRON_INGOT), 2)
                                         .input(Ingredient.of(Items.GOLD_INGOT), 2)
                                         .input(Ingredient.of(Items.DIAMOND), 2)
                                         .output(ModItems.USELESS_INGOT_TIER_1.get(), 1)
                                         .energy(10000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_1"));

        // Tier 2
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.ROTTEN_FLESH), 2)
                                         .input(Ingredient.of(Items.GUNPOWDER), 2)
                                         .input(Ingredient.of(Items.BONE), 2)
                                         .input(Ingredient.of(Items.GOLD_INGOT), 2)
                                         .input(Ingredient.of(Items.DIAMOND), 2)
                                         .catalyst(Ingredient.of(ModItems.USELESS_INGOT_TIER_1.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_2.get(), 1)
                                         .energy(50000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_2"));

        // Tier 3
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.SPIDER_EYE), 2)
                                         .input(Ingredient.of(Items.STRING), 4)
                                         .input(Ingredient.of(Items.FEATHER), 4)
                                         .input(Ingredient.of(Items.EMERALD), 2)
                                         .input(Ingredient.of(Items.DIAMOND), 4)
                                         .catalyst(Ingredient.of(ModItems.USELESS_INGOT_TIER_2.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_3.get(), 1)
                                         .energy(100000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_3"));

        // Tier 4
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.BLAZE_ROD), 2)
                                         .input(Ingredient.of(Items.ENDER_PEARL), 2)
                                         .input(Ingredient.of(Items.GHAST_TEAR), 2)
                                         .input(Ingredient.of(Items.NETHERITE_INGOT), 1)
                                         .input(Ingredient.of(Items.DIAMOND_BLOCK), 2)
                                         .catalyst(Ingredient.of(ModItems.USELESS_INGOT_TIER_3.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_4.get(), 1)
                                         .energy(500000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_4"));

        // Tier 5
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.WITHER_SKELETON_SKULL), 3)
                                         .input(Ingredient.of(Items.NETHER_STAR), 1)
                                         .input(Ingredient.of(Items.DRAGON_BREATH), 4)
                                         .input(Ingredient.of(Items.NETHERITE_BLOCK), 2)
                                         .input(Ingredient.of(Items.EMERALD_BLOCK), 4)
                                         .catalyst(Ingredient.of(ModItems.USELESS_INGOT_TIER_4.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_5.get(), 1)
                                         .energy(1000000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_5"));

        // Tier 6
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.DRAGON_HEAD), 1)
                                         .input(Ingredient.of(Items.ELYTRA), 1)
                                         .input(Ingredient.of(Items.TOTEM_OF_UNDYING), 1)
                                         .input(Ingredient.of(Items.ENCHANTED_GOLDEN_APPLE), 4)
                                         .input(Ingredient.of(Items.NETHERITE_BLOCK), 8)
                                         .catalyst(Ingredient.of(ModItems.USELESS_INGOT_TIER_5.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_6.get(), 1)
                                         .energy(5000000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_6"));

        // Tier 7
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.BEACON), 1)
                                         .input(Ingredient.of(Items.CONDUIT), 1)
                                         .input(Ingredient.of(Items.END_CRYSTAL), 4)
                                         .input(Ingredient.of(Items.DRAGON_EGG), 1)
                                         .input(Ingredient.of(Items.NETHERITE_BLOCK), 16)
                                         .catalyst(Ingredient.of(ModItems.USELESS_INGOT_TIER_6.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_7.get(), 1)
                                         .energy(10000000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_7"));

        // Tier 8
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.ENCHANTED_BOOK), 8)
                                         .input(Ingredient.of(Items.HEART_OF_THE_SEA), 4)
                                         .input(Ingredient.of(Items.TRIDENT), 1)
                                         .input(Ingredient.of(Items.SHULKER_SHELL), 8)
                                         .input(Ingredient.of(Items.NETHERITE_BLOCK), 32)
                                         .catalyst(Ingredient.of(ModItems.USELESS_INGOT_TIER_7.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_8.get(), 1)
                                         .energy(50000000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_8"));

        // Tier 9
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.DRAGON_HEAD), 8)
                                         .input(Ingredient.of(Items.NETHER_STAR), 8)
                                         .input(Ingredient.of(Items.ENCHANTED_GOLDEN_APPLE), 16)
                                         .input(Ingredient.of(Items.TOTEM_OF_UNDYING), 8)
                                         .input(Ingredient.of(Items.NETHERITE_BLOCK), 64)
                                         .catalyst(Ingredient.of(ModItems.USELESS_INGOT_TIER_8.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_9.get(), 1)
                                         .energy(100000000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_9"));
    }

    private void addUselessGearRecipes(RecipeOutput output) {
        // Gear Tier 1-9
        for (int i = 1; i <= 9; i++) {
            var ingot = switch (i) {
                case 1 -> ModItems.USELESS_INGOT_TIER_1.get();
                case 2 -> ModItems.USELESS_INGOT_TIER_2.get();
                case 3 -> ModItems.USELESS_INGOT_TIER_3.get();
                case 4 -> ModItems.USELESS_INGOT_TIER_4.get();
                case 5 -> ModItems.USELESS_INGOT_TIER_5.get();
                case 6 -> ModItems.USELESS_INGOT_TIER_6.get();
                case 7 -> ModItems.USELESS_INGOT_TIER_7.get();
                case 8 -> ModItems.USELESS_INGOT_TIER_8.get();
                case 9 -> ModItems.USELESS_INGOT_TIER_9.get();
                default -> ModItems.USELESS_INGOT_TIER_1.get();
            };
            var gear = switch (i) {
                case 1 -> ModItems.USELESS_GEAR_TIER_1.get();
                case 2 -> ModItems.USELESS_GEAR_TIER_2.get();
                case 3 -> ModItems.USELESS_GEAR_TIER_3.get();
                case 4 -> ModItems.USELESS_GEAR_TIER_4.get();
                case 5 -> ModItems.USELESS_GEAR_TIER_5.get();
                case 6 -> ModItems.USELESS_GEAR_TIER_6.get();
                case 7 -> ModItems.USELESS_GEAR_TIER_7.get();
                case 8 -> ModItems.USELESS_GEAR_TIER_8.get();
                case 9 -> ModItems.USELESS_GEAR_TIER_9.get();
                default -> ModItems.USELESS_GEAR_TIER_1.get();
            };

            AdvancedAlloyFurnaceRecipeBuilder.create()
                                             .input(Ingredient.of(ingot), 4)
                                             .mold(Ingredient.of(ModItems.METAL_MOLD_GEAR.get()))
                                             .output(gear, 1)
                                             .energy(1000 * i)
                                             .processTime(40)
                                             .mode(AlloyFurnaceMode.NORMAL)
                                             .save(output, UselessMod.id("advanced_alloy/gear/useless_gear_tier_" + i));
        }
    }

    private void addUselessGlassRecipes(RecipeOutput output) {
        // Glass Tier 1-9
        for (int i = 1; i <= 9; i++) {
            var ingot = switch (i) {
                case 1 -> ModItems.USELESS_INGOT_TIER_1.get();
                case 2 -> ModItems.USELESS_INGOT_TIER_2.get();
                case 3 -> ModItems.USELESS_INGOT_TIER_3.get();
                case 4 -> ModItems.USELESS_INGOT_TIER_4.get();
                case 5 -> ModItems.USELESS_INGOT_TIER_5.get();
                case 6 -> ModItems.USELESS_INGOT_TIER_6.get();
                case 7 -> ModItems.USELESS_INGOT_TIER_7.get();
                case 8 -> ModItems.USELESS_INGOT_TIER_8.get();
                case 9 -> ModItems.USELESS_INGOT_TIER_9.get();
                default -> ModItems.USELESS_INGOT_TIER_1.get();
            };
            var glass = switch (i) {
                case 1 -> ModItems.USELESS_GLASS_TIER_1.get();
                case 2 -> ModItems.USELESS_GLASS_TIER_2.get();
                case 3 -> ModItems.USELESS_GLASS_TIER_3.get();
                case 4 -> ModItems.USELESS_GLASS_TIER_4.get();
                case 5 -> ModItems.USELESS_GLASS_TIER_5.get();
                case 6 -> ModItems.USELESS_GLASS_TIER_6.get();
                case 7 -> ModItems.USELESS_GLASS_TIER_7.get();
                case 8 -> ModItems.USELESS_GLASS_TIER_8.get();
                case 9 -> ModItems.USELESS_GLASS_TIER_9.get();
                default -> ModItems.USELESS_GLASS_TIER_1.get();
            };

            AdvancedAlloyFurnaceRecipeBuilder.create()
                                             .input(Ingredient.of(Items.GLASS), 2)
                                             .input(Ingredient.of(ingot), 1)
                                             .mold(Ingredient.of(ModItems.METAL_MOLD_BLOCK.get()))
                                             .output(glass, 1)
                                             .energy(1000 * i)
                                             .processTime(40)
                                             .mode(AlloyFurnaceMode.NORMAL)
                                             .save(output, UselessMod.id("advanced_alloy/glass/useless_glass_tier_" + i));
        }
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
