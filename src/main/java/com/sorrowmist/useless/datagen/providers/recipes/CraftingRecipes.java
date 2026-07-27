package com.sorrowmist.useless.datagen.providers.recipes;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.enums.EnumColor;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
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
        this.addOmniversalFurnaceRecipes(consumer);
        this.addEndlessBeafItemRecipe(consumer);
        this.addOreGeneratorBlockRecipe(consumer);
        this.addTeleportBlockRecipes(consumer);
        this.addAE2GiftPackageRecipe(consumer);
        this.addGlowPlasticRecipes(consumer);
        this.addAdvancedAlloyFurnaceRecipes(consumer);
    }

    private void addGlowPlasticRecipes(RecipeOutput consumer) {
        for (EnumColor color : EnumColor.valuesInOrder()) {
            Block concrete = getConcreteBlock(color);
            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                       GlowPlasticBlock.GLOW_PLASTIC_BLOCK_ITEMS.get(color).get(),
                                       8
                              )
                              .pattern("CCC")
                              .pattern("CGC")
                              .pattern("CCC")
                              .define('C', concrete)
                              .define('G', Items.GLOWSTONE_DUST)
                              .unlockedBy("has_" + color.getRegistryPrefix() + "_concrete", has(concrete))
                              .save(consumer, UselessMod.id("crafting/" + color.getRegistryPrefix() + "_glow_plastic"));
        }
    }

    private static Block getConcreteBlock(EnumColor color) {
        return switch (color) {
            case BLACK -> Blocks.BLACK_CONCRETE;
            case RED -> Blocks.RED_CONCRETE;
            case DARK_RED -> Blocks.RED_CONCRETE_POWDER;
            case GREEN -> Blocks.GREEN_CONCRETE;
            case BROWN -> Blocks.BROWN_CONCRETE;
            case BLUE -> Blocks.BLUE_CONCRETE;
            case PURPLE -> Blocks.PURPLE_CONCRETE;
            case CYAN -> Blocks.CYAN_CONCRETE;
            case AQUA -> Blocks.CYAN_CONCRETE_POWDER;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_CONCRETE;
            case GRAY -> Blocks.GRAY_CONCRETE;
            case PINK -> Blocks.PINK_CONCRETE;
            case LIME -> Blocks.LIME_CONCRETE;
            case YELLOW -> Blocks.YELLOW_CONCRETE;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_CONCRETE;
            case MAGENTA -> Blocks.MAGENTA_CONCRETE;
            case ORANGE -> Blocks.ORANGE_CONCRETE;
            case WHITE -> Blocks.WHITE_CONCRETE;
        };
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

    private void addOmniversalFurnaceRecipes(RecipeOutput consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                   ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get())
                           .pattern("IRI")
                           .pattern("RFR")
                           .pattern("IRI")
                           .define('I', Items.IRON_INGOT)
                           .define('R', Items.REDSTONE)
                           .define('F', ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get())
                           .unlockedBy("has_alloy_furnace", has(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get()))
                           .save(consumer, UselessMod.id("crafting/multiblock_alloy_furnace_core"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.ME_PATTERN_ASSEMBLY.get())
                           .pattern(" R ")
                           .pattern("CPC")
                           .pattern(" R ")
                           .define('R', Items.REDSTONE)
                           .define('C', Blocks.CHEST)
                           .define('P', AEBlocks.PATTERN_PROVIDER)
                           .unlockedBy("has_pattern_provider", has(AEBlocks.PATTERN_PROVIDER))
                           .save(consumer, UselessMod.id("crafting/me_pattern_assembly"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.OMNIVERSAL_MOLD_HUB.get())
                           .pattern(" R ")
                           .pattern("CHR")
                           .pattern(" R ")
                           .define('R', Items.REDSTONE)
                           .define('C', Blocks.CHEST)
                           .define('H', Blocks.HOPPER)
                           .unlockedBy("has_hopper", has(Blocks.HOPPER))
                           .save(consumer, UselessMod.id("crafting/omniversal_mold_hub"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                   ModBlocks.PASSIVE_CRAFTING_HATCH.get())
                           .pattern(" C ")
                           .pattern("EPE")
                           .pattern(" R ")
                           .define('C', Items.CLOCK)
                           .define('E', AEParts.EXPORT_BUS)
                           .define('P', ModBlocks.ME_PATTERN_ASSEMBLY.get())
                           .define('R', Items.REDSTONE)
                           .unlockedBy("has_pattern_assembly", has(ModBlocks.ME_PATTERN_ASSEMBLY.get()))
                           .save(consumer, UselessMod.id("crafting/passive_crafting_hatch"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                   ModBlocks.OMNIVERSAL_FURNACE_CASING.get(), 8)
                           .pattern("NNN")
                           .pattern("NSN")
                           .pattern("NNN")
                           .define('N', Items.IRON_NUGGET)
                           .define('S', Blocks.SMOOTH_STONE)
                           .unlockedBy("has_smooth_stone", has(Blocks.SMOOTH_STONE))
                           .save(consumer, UselessMod.id("crafting/omniversal_furnace_casing"));

        for (int tier = UselessCoilBlock.MIN_TIER; tier <= UselessCoilBlock.MAX_TIER; tier++) {
            var ingot = switch (tier) {
                case 1 -> ModItems.USELESS_INGOT_TIER_1.get();
                case 2 -> ModItems.USELESS_INGOT_TIER_2.get();
                case 3 -> ModItems.USELESS_INGOT_TIER_3.get();
                case 4 -> ModItems.USELESS_INGOT_TIER_4.get();
                case 5 -> ModItems.USELESS_INGOT_TIER_5.get();
                case 6 -> ModItems.USELESS_INGOT_TIER_6.get();
                case 7 -> ModItems.USELESS_INGOT_TIER_7.get();
                case 8 -> ModItems.USELESS_INGOT_TIER_8.get();
                case 9 -> ModItems.USELESS_INGOT_TIER_9.get();
                case UselessCoilBlock.USEFUL_TIER -> ModItems.USEFUL_INGOT.get();
                default -> throw new IllegalStateException("Unexpected coil tier: " + tier);
            };
            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                       ModBlocks.USELESS_COILS.get(tier).get(), 16)
                               .pattern("CRC")
                               .pattern("RIR")
                               .pattern("CRC")
                               .define('C', Items.COPPER_INGOT)
                               .define('R', Items.REDSTONE)
                               .define('I', ingot)
                               .unlockedBy("has_tier_ingot", has(ingot))
                               .save(consumer, UselessMod.id(
                                       "crafting/" + UselessCoilBlock.registryName(tier)));
        }

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.OMNIVERSAL_PATTERN_ENCODER.get())
                           .pattern("IRI")
                           .pattern("RPR")
                           .pattern("IRI")
                           .define('I', Items.IRON_INGOT)
                           .define('R', Items.REDSTONE)
                           .define('P', AEItems.BLANK_PATTERN)
                           .unlockedBy("has_blank_pattern", has(AEItems.BLANK_PATTERN))
                           .save(consumer, UselessMod.id("crafting/omniversal_pattern_encoder"));
    }

    private void addAE2GiftPackageRecipe(RecipeOutput consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.AE2_GIFT_PACKAGE.get(), 1)
                           .pattern("SPS")
                           .pattern("PPP")
                           .pattern("SPS")
                           .define('P', ItemTags.PLANKS)
                           .define('S', Items.STICK)
                           .unlockedBy("has_planks", has(ItemTags.PLANKS))
                           .save(consumer, UselessMod.id("crafting/ae2_gift_package"));
    }

    private void addAdvancedAlloyFurnaceRecipes(RecipeOutput output) {
        this.addUselessIngotRecipes(output);
        this.addUselessGearRecipes(output);
        this.addUselessGlassRecipes(output);
        this.addPossibleUsefulIngotRecipe(output);
    }

    private void addPossibleUsefulIngotRecipe(RecipeOutput output) {
        // Possible Useful Ingot - 工作台合成配方（9个不同等级的无用锭）
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.POSSIBLE_USEFUL_INGOT.get(), 1)
                           .pattern("123")
                           .pattern("456")
                           .pattern("789")
                           .define('1', ModItems.USELESS_INGOT_TIER_1.get())
                           .define('2', ModItems.USELESS_INGOT_TIER_2.get())
                           .define('3', ModItems.USELESS_INGOT_TIER_3.get())
                           .define('4', ModItems.USELESS_INGOT_TIER_4.get())
                           .define('5', ModItems.USELESS_INGOT_TIER_5.get())
                           .define('6', ModItems.USELESS_INGOT_TIER_6.get())
                           .define('7', ModItems.USELESS_INGOT_TIER_7.get())
                           .define('8', ModItems.USELESS_INGOT_TIER_8.get())
                           .define('9', ModItems.USELESS_INGOT_TIER_9.get())
                           .unlockedBy("has_ingot", has(ModItems.USELESS_INGOT_TIER_1.get()))
                           .save(output, UselessMod.id("crafting/possible_useful_ingot"));
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

        // Tier 2 - 使用1阶无用锭作为输入材料
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.ROTTEN_FLESH), 2)
                                         .input(Ingredient.of(Items.GUNPOWDER), 2)
                                         .input(Ingredient.of(Items.BONE), 2)
                                         .input(Ingredient.of(Items.GOLD_INGOT), 2)
                                         .input(Ingredient.of(Items.DIAMOND), 2)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_1.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_2.get(), 1)
                                         .energy(50000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_2"));

        // Tier 3 - 使用2阶无用锭作为输入材料
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.NETHER_WART), 2)
                                         .input(Ingredient.of(Items.BLAZE_ROD), 2)
                                         .input(Ingredient.of(Items.NETHER_BRICK), 2)
                                         .input(Ingredient.of(Items.GOLD_INGOT), 2)
                                         .input(Ingredient.of(Items.DIAMOND), 2)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_2.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_3.get(), 1)
                                         .energy(100000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_3"));

        // Tier 4 - 使用3阶无用锭作为输入材料
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.ENDER_PEARL), 2)
                                         .input(Ingredient.of(Items.CHORUS_FRUIT), 2)
                                         .input(Ingredient.of(Items.SHULKER_SHELL), 2)
                                         .input(Ingredient.of(Items.GOLD_BLOCK), 2)
                                         .input(Ingredient.of(Items.DIAMOND_BLOCK), 2)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_3.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_4.get(), 1)
                                         .energy(500000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_4"));

        // Tier 5 - 使用4阶无用锭作为输入材料
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.WITHER_SKELETON_SKULL), 2)
                                         .input(Ingredient.of(Items.PHANTOM_MEMBRANE), 2)
                                         .input(Ingredient.of(Items.SEA_LANTERN), 2)
                                         .input(Ingredient.of(Items.GOLD_BLOCK), 2)
                                         .input(Ingredient.of(Items.DIAMOND_BLOCK), 2)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_4.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_5.get(), 1)
                                         .energy(1000000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_5"));

        // Tier 6 - 使用5阶无用锭作为输入材料
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.DRAGON_BREATH), 2)
                                         .input(Ingredient.of(Items.TOTEM_OF_UNDYING), 2)
                                         .input(Ingredient.of(Items.ENCHANTED_GOLDEN_APPLE), 2)
                                         .input(Ingredient.of(Items.GOLD_BLOCK), 2)
                                         .input(Ingredient.of(Items.DIAMOND_BLOCK), 2)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_5.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_6.get(), 1)
                                         .energy(5000000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_6"));

        // Tier 7 - 使用6阶无用锭作为输入材料
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.NETHER_STAR), 2)
                                         .input(Ingredient.of(Items.WITHER_ROSE), 2)
                                         .input(Ingredient.of(Items.GHAST_TEAR), 2)
                                         .input(Ingredient.of(Items.DIAMOND_BLOCK), 2)
                                         .input(Ingredient.of(Items.NETHERITE_INGOT), 2)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_6.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_7.get(), 1)
                                         .energy(10000000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_7"));

        // Tier 8 - 使用7阶无用锭作为输入材料
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.DRAGON_HEAD), 2)
                                         .input(Ingredient.of(Items.ECHO_SHARD), 2)
                                         .input(Ingredient.of(Items.CONDUIT), 2)
                                         .input(Ingredient.of(Items.DIAMOND_BLOCK), 2)
                                         .input(Ingredient.of(Items.NETHERITE_INGOT), 2)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_7.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_8.get(), 1)
                                         .energy(50000000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_8"));

        // Tier 9 - 使用8阶无用锭作为输入材料
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.TURTLE_HELMET), 2)
                                         .input(Ingredient.of(Items.GOAT_HORN), 2)
                                         .input(Ingredient.of(Items.RABBIT_FOOT), 2)
                                         .input(Ingredient.of(Items.DIAMOND_BLOCK), 2)
                                         .input(Ingredient.of(Items.NETHERITE_BLOCK), 2)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_8.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_9.get(), 1)
                                         .energy(100000000)
                                         .processTime(1)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_9"));

        // Possible Useful Ingot - 合成配方（工作台）
        // 使用9个不同等级的无用锭合成

        // Useful Ingot - 高级熔炉配方
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(ModItems.POSSIBLE_USEFUL_INGOT.get()), 64)
                                         .input(Ingredient.of(Blocks.IRON_BLOCK), 1024)
                                         .input(Ingredient.of(Blocks.GOLD_BLOCK), 1024)
                                         .input(Ingredient.of(Blocks.DIAMOND_BLOCK), 1024)
                                         .input(Ingredient.of(Blocks.EMERALD_BLOCK), 1024)
                                         .input(Ingredient.of(Blocks.NETHERITE_BLOCK), 1024)
                                         .fluidInput(new FluidStack(Fluids.LAVA, 10000000))
                                         .output(ModItems.USEFUL_INGOT.get(), 1)
                                         .energy(10000000)
                                         .processTime(100)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useful_ingot"));
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
