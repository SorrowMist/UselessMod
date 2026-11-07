package com.sorrowmist.useless.datagen.providers.recipes;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.EnumColor;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipeBuilder;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModItems;
import com.sorrowmist.useless.init.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

/** Forge 1.20.1 crafting recipes; acquisition/world interactions have their own provider. */
public class CraftingRecipes extends RecipeProvider {
    public CraftingRecipes(PackOutput output) { super(output); }

    @Override protected void buildRecipes(Consumer<FinishedRecipe> output) {
        addMolds(output);
        addFurnaceParts(output);
        addPlastic(output);
        addMaterialShapes(output);
        addToolsAndUtilities(output);
        AcquisitionRecipes.generate(output);
        // Maintenance/muffler recipes and their loot tables are authored in src/main/resources.
        // Do not also emit the same resource IDs into src/generated/resources.
    }

    private void addMolds(Consumer<FinishedRecipe> output) {
        Item[] molds = {ModItems.METAL_MOLD_PLATE.get(), ModItems.METAL_MOLD_ROD.get(),
                ModItems.METAL_MOLD_GEAR.get(), ModItems.METAL_MOLD_WIRE.get(), ModItems.METAL_MOLD_BLOCK.get()};
        String[][] patterns = {{"XXX"}, {"  X", " X ", "X  "}, {" X ", "X X", " X "},
                {" X ", "XXX", " X "}, {"XXX", "X X", "XXX"}};
        for (int i = 0; i < molds.length; i++) {
            ShapedRecipeBuilder recipe = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, molds[i]);
            for (String row : patterns[i]) recipe.pattern(row);
            recipe.define('X', ModItems.USELESS_INGOT_TIER_1.get())
                    .unlockedBy("has_ingot", has(ModItems.USELESS_INGOT_TIER_1.get()))
                    .save(output, UselessMod.id("mold/" + BuiltInRegistries.ITEM.getKey(molds[i]).getPath()));
        }
    }

    private void addFurnaceParts(Consumer<FinishedRecipe> output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.ME_PATTERN_ASSEMBLY.get())
                .pattern(" R ").pattern("CPC").pattern(" R ")
                .define('R', Items.REDSTONE).define('C', Blocks.CHEST).define('P', AEBlocks.PATTERN_PROVIDER)
                .unlockedBy("has_pattern_provider", has(AEBlocks.PATTERN_PROVIDER))
                .save(output, UselessMod.id("crafting/me_pattern_assembly"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.OMNIVERSAL_MOLD_HUB.get())
                .pattern(" R ").pattern("CHR").pattern(" R ")
                .define('R', Items.REDSTONE).define('C', Blocks.CHEST).define('H', Blocks.HOPPER)
                .unlockedBy("has_hopper", has(Blocks.HOPPER))
                .save(output, UselessMod.id("crafting/omniversal_mold_hub"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.PASSIVE_CRAFTING_HATCH.get())
                .pattern(" C ").pattern("EPE").pattern(" R ")
                .define('C', Items.CLOCK).define('E', AEParts.EXPORT_BUS)
                .define('P', ModBlocks.ME_PATTERN_ASSEMBLY.get()).define('R', Items.REDSTONE)
                .unlockedBy("has_pattern_assembly", has(ModBlocks.ME_PATTERN_ASSEMBLY.get()))
                .save(output, UselessMod.id("crafting/passive_crafting_hatch"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.OMNIVERSAL_FURNACE_CASING.get(), 8)
                .pattern("NNN").pattern("NSN").pattern("NNN")
                .define('N', Items.IRON_NUGGET).define('S', Blocks.SMOOTH_STONE)
                .unlockedBy("has_smooth_stone", has(Blocks.SMOOTH_STONE))
                .save(output, UselessMod.id("crafting/omniversal_furnace_casing"));
    }

    private void addPlastic(Consumer<FinishedRecipe> output) {
        for (EnumColor color : EnumColor.valuesInOrder()) {
            Block concrete = concrete(color);
            String prefix = color.getRegistryPrefix();
            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, GlowPlasticBlock.PLASTIC_BLOCK_ITEMS.get(color).get(), 8)
                    .pattern("CCC").pattern("C C").pattern("CCC").define('C', concrete)
                    .unlockedBy("has_" + prefix + "_concrete", has(concrete))
                    .save(output, UselessMod.id("crafting/" + prefix + "_plastic"));
            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, GlowPlasticBlock.GLOW_PLASTIC_BLOCK_ITEMS.get(color).get(), 8)
                    .pattern("CCC").pattern("CGC").pattern("CCC").define('C', concrete).define('G', Items.GLOWSTONE_DUST)
                    .unlockedBy("has_" + prefix + "_concrete", has(concrete))
                    .save(output, UselessMod.id("crafting/" + prefix + "_glow_plastic"));
            plasticCtm(output, GlowPlasticBlock.PLASTIC_CTM_BLOCK_ITEMS.get(color).get(),
                    GlowPlasticBlock.PLASTIC_BLOCK_ITEMS.get(color).get(), prefix + "_plastic_ctm");
            plasticCtm(output, GlowPlasticBlock.GLOW_PLASTIC_CTM_BLOCK_ITEMS.get(color).get(),
                    GlowPlasticBlock.GLOW_PLASTIC_BLOCK_ITEMS.get(color).get(), prefix + "_glow_plastic_ctm");
        }
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.TEMPERATURE_REGULATOR.get())
                .requires(ModTags.PLASTIC_BLOCKS_ITEM).unlockedBy("has_plastic", has(ModTags.PLASTIC_BLOCKS_ITEM))
                .save(output, UselessMod.id("crafting/temperature_regulator"));
    }

    private static void plasticCtm(Consumer<FinishedRecipe> output, Item result, Item input, String name) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, result, 8)
                .pattern("PPP").pattern("P P").pattern("PPP").define('P', input)
                .unlockedBy("has_" + name.replace("_ctm", ""), has(input))
                .save(output, UselessMod.id("crafting/" + name));
    }

    private void addMaterialShapes(Consumer<FinishedRecipe> output) {
        for (int tier = 1; tier <= 9; tier++) {
            Item ingot = modItem("useless_ingot_tier_" + tier);
            AdvancedAlloyFurnaceRecipeBuilder.create().input(ingot, 4)
                    .mold(Ingredient.of(ModItems.METAL_MOLD_GEAR.get()))
                    .output(modItem("useless_gear_tier_" + tier), 1).energy(1000L * tier).processTime(40)
                    .save(output, UselessMod.id("advanced_alloy/gear/useless_gear_tier_" + tier));
            AdvancedAlloyFurnaceRecipeBuilder.create().input(Items.GLASS, 2).input(ingot, 1)
                    .mold(Ingredient.of(ModItems.METAL_MOLD_BLOCK.get()))
                    .output(modItem("useless_glass_tier_" + tier), 1).energy(1000L * tier).processTime(40)
                    .save(output, UselessMod.id("advanced_alloy/glass/useless_glass_tier_" + tier));
        }
        ShapedRecipeBuilder possible = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.POSSIBLE_USEFUL_INGOT.get())
                .pattern("123").pattern("456").pattern("789");
        for (int tier = 1; tier <= 9; tier++) possible.define((char) ('0' + tier), modItem("useless_ingot_tier_" + tier));
        possible.unlockedBy("has_ingot", has(ModItems.USELESS_INGOT_TIER_1.get()))
                .save(output, UselessMod.id("crafting/possible_useful_ingot"));
    }

    private void addToolsAndUtilities(Consumer<FinishedRecipe> output) {
        if (ModItems.AE2_GIFT_PACKAGE != null) {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.AE2_GIFT_PACKAGE.get())
                    .pattern("SPS").pattern("PPP").pattern("SPS").define('P', ItemTags.PLANKS).define('S', Items.STICK)
                    .unlockedBy("has_planks", has(ItemTags.PLANKS)).save(output, UselessMod.id("crafting/ae2_gift_package"));
        }
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.OMNIVERSAL_PATTERN_CONVERTER.get())
                .pattern("GPG").pattern(" L ").define('G', Items.YELLOW_DYE)
                .define('P', AEItems.BLANK_PATTERN).define('L', AEItems.LOGIC_PROCESSOR)
                .unlockedBy("has_blank_pattern", has(AEItems.BLANK_PATTERN))
                .save(output, UselessMod.id("crafting/omniversal_pattern_converter"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ModItems.SUPERVISOR.get())
                .requires(ModItems.USEFUL_INGOT.get()).requires(Items.PINK_WOOL).requires(Items.BLACK_WOOL)
                .requires(Items.GRAY_WOOL).requires(Items.WHITE_WOOL).requires(Items.GREEN_WOOL)
                .requires(Items.BLUE_WOOL).requires(Items.YELLOW_WOOL).requires(Items.ORANGE_WOOL)
                .unlockedBy("has_useful_ingot", has(ModItems.USEFUL_INGOT.get())).save(output, UselessMod.id("crafting/supervisor"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.ORE_GENERATOR_BLOCK.get())
                .pattern("AAA").pattern("AAA").pattern("AAA").define('A', ModItems.USEFUL_INGOT.get())
                .unlockedBy("has_ingot", has(ModItems.USEFUL_INGOT.get())).save(output);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.TELEPORT_BLOCK.get())
                .pattern("AAA").pattern("ABA").pattern("AAA").define('A', ItemTags.PLANKS).define('B', Blocks.DIRT)
                .unlockedBy("has_planks", has(ItemTags.PLANKS)).save(output, UselessMod.id("teleport/teleport_block"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.TELEPORT_BLOCK_2.get())
                .pattern("BBB").pattern("BAB").pattern("BBB").define('A', ItemTags.PLANKS).define('B', Blocks.DIRT)
                .unlockedBy("has_planks", has(ItemTags.PLANKS)).save(output, UselessMod.id("teleport/teleport_block_2"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.TELEPORT_BLOCK_3.get())
                .pattern("BBB").pattern("AAA").pattern("BBB").define('B', ItemTags.PLANKS).define('A', Blocks.DIRT)
                .unlockedBy("has_planks", has(ItemTags.PLANKS)).save(output, UselessMod.id("teleport/teleport_block_3"));
    }

    private static Item modItem(String path) {
        ResourceLocation id = UselessMod.id(path);
        return BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new IllegalStateException("Missing datagen item " + id));
    }

    private static Block concrete(EnumColor color) {
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
}
