package com.sorrowmist.useless.tests;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.adapters.avaritia.*;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.init.ModItems;
import committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(UselessMod.MODID)
@PrefixGameTestTemplate(false)
public final class OmissionRegressionGameTests {
    @GameTest(template = "furnace_empty")
    public static void thermalUpgradesAndLegacyShortcutsLoad(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        for (int tier = 1; tier <= 9; tier++) {
            helper.assertTrue(manager.byKey(UselessMod.id("useless_integral_component_tier_" + tier)).isPresent(),
                    "Missing integral component recipe " + tier);
        }
        for (int tier = 1; tier <= 3; tier++) {
            helper.assertTrue(manager.byKey(UselessMod.id("augment_parallel_" + tier)).isPresent(),
                    "Missing parallel augment recipe " + tier);
        }
        for (String id : List.of("machine/smelter/thermal/plate/iron_plate", "machine/smelter/ae2/common/fluix_crystal")) {
            var recipe = (AdvancedAlloyFurnaceRecipe) manager.byKey(UselessMod.id(id)).orElseThrow();
            helper.assertTrue(recipe.outputs().get(0).getCount() == 64 && recipe.catalystUses() == 1,
                    "Legacy batch and catalyst costs must survive the codec conversion");
            if (id.endsWith("fluix_crystal")) {
                helper.assertTrue(recipe.inputFluids().get(0).amount() == 16000, "Fluix must consume 16 buckets");
            } else {
                helper.assertTrue(recipe.energy() == 64000, "Iron plate energy must remain 64000");
            }
        }
        int checked = 0;
        var resources = helper.getLevel().getServer().getResourceManager().listResources("recipes/machine/smelter",
                id -> id.getNamespace().equals(UselessMod.MODID)
                        && (id.getPath().startsWith("recipes/machine/smelter/thermal/")
                        || id.getPath().startsWith("recipes/machine/smelter/ae2/")));
        for (var entry : resources.entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                var conditional = json.getAsJsonArray("recipes").get(0).getAsJsonObject();
                boolean enabled = true;
                for (var element : conditional.getAsJsonArray("conditions")) {
                    var condition = element.getAsJsonObject();
                    if (condition.get("type").getAsString().equals("forge:mod_loaded")) {
                        enabled &= net.minecraftforge.fml.ModList.get().isLoaded(condition.get("modid").getAsString());
                    } else if (condition.get("type").getAsString().equals("forge:item_exists")) {
                        enabled &= net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(
                                new ResourceLocation(condition.get("item").getAsString()));
                    } else {
                        throw new AssertionError("Unexpected legacy recipe condition: " + condition);
                    }
                }
                var path = entry.getKey().getPath();
                var id = UselessMod.id(path.substring("recipes/".length(), path.length() - ".json".length()));
                var loaded = manager.byKey(id);
                helper.assertTrue(loaded.isPresent() == enabled, "Conditional loading mismatch: " + id);
                if (enabled) {
                    var recipe = (AdvancedAlloyFurnaceRecipe) loaded.orElseThrow();
                    helper.assertTrue(!recipe.outputs().isEmpty() || !recipe.outputFluids().isEmpty(), "Empty output: " + id);
                    helper.assertTrue(recipe.catalystUses() == 1 && !recipe.mold().isEmpty(), "Lost mold/catalyst: " + id);
                }
                checked++;
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }
        }
        helper.assertTrue(checked == 59, "All 59 legacy shortcuts must be checked, got " + checked);
        helper.succeed();
    }

    @GameTest(template = "furnace_empty")
    public static void nativeAvaritiaRecipesRegisterAndConvert(GameTestHelper helper) {
        var adapters = AlloyFurnaceRecipeManager.getInstance().getRegisteredAdapters();
        for (Class<?> type : List.of(ReAvaritiaTableRecipeAdapter.class, ReAvaritiaExtremeSmithingRecipeAdapter.class,
                ReAvaritiaCompressorRecipeAdapter.class, ReAvaritiaCollectorRecipeAdapter.class)) {
            helper.assertTrue(adapters.stream().anyMatch(type::isInstance), "Missing native adapter " + type.getSimpleName());
        }
        var tables = helper.getLevel().getRecipeManager().getAllRecipesFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get());
        int converted = 0;
        var tableAdapter = new ReAvaritiaTableRecipeAdapter();
        for (var recipe : tables) {
            if (!tableAdapter.convertAll(RecipeHolder.of(recipe), helper.getLevel()).isEmpty()) converted++;
        }
        helper.assertTrue(converted > 0, "Native tier table recipes must actually convert");
        var compressor = new ReAvaritiaCompressorRecipeAdapter();
        var compressions = helper.getLevel().getRecipeManager().getAllRecipesFor(ModRecipeTypes.COMPRESSOR_RECIPE.get());
        helper.assertTrue(!compressions.isEmpty(), "Native compressor recipes must load");
        helper.assertTrue(compressor.convertAll(RecipeHolder.of(compressions.get(0)), helper.getLevel()).size() == 4,
                "All four native compressor tiers must convert");
        helper.assertTrue(new ReAvaritiaCollectorRecipeAdapter().getGeneratedRecipes(helper.getLevel()).size() == 4,
                "All four native collector tiers must generate recipes");
        helper.succeed();
    }

    @GameTest(template = "furnace_empty")
    public static void tierTableKeepsDifferentNbtAndCraftingRemainders(GameTestHelper helper) {
        var first = new ItemStack(Items.PAPER);
        first.getOrCreateTag().putString("singularity", "first");
        var second = first.copy();
        second.getOrCreateTag().putString("singularity", "second");
        var slots = net.minecraft.core.NonNullList.<Ingredient>create();
        slots.add(net.minecraftforge.common.crafting.StrictNBTIngredient.of(first));
        slots.add(net.minecraftforge.common.crafting.StrictNBTIngredient.of(second));
        slots.add(net.minecraftforge.common.crafting.StrictNBTIngredient.of(first));
        slots.add(Ingredient.of(Items.WATER_BUCKET));
        var source = new committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe(
                UselessMod.id("test_table_nbt"), 2, 2, slots, new ItemStack(Items.DIAMOND), 1, false);
        var converted = new ReAvaritiaTableRecipeAdapter().convertAll(RecipeHolder.of(source), helper.getLevel());
        helper.assertTrue(converted.size() == 1, "Native NBT table recipe must convert");
        var recipe = converted.get(0);
        helper.assertTrue(recipe.inputs().size() == 3, "Different NBT must not collapse into one requirement");
        helper.assertTrue(recipe.inputs().stream().anyMatch(i -> i.count() == 2 && i.ingredient().test(first)
                && !i.ingredient().test(second)), "Repeated exact stacks must aggregate without losing NBT");
        helper.assertTrue(recipe.outputs().stream().anyMatch(s -> s.is(Items.BUCKET)), "Native bucket remainder must return");
        helper.succeed();
    }

    @GameTest(template = "furnace_empty")
    public static void extremeSmithingPreservesBaseNbtAndRequiresThreeAdditions(GameTestHelper helper) {
        var source = new ExtremeSmithingRecipe(UselessMod.id("test_smithing"), Ingredient.of(Items.PAPER),
                Ingredient.of(Items.IRON_SWORD), Ingredient.of(Items.DIAMOND), new ItemStack(Items.DIAMOND_SWORD));
        var base = new ItemStack(Items.IRON_SWORD);
        base.getOrCreateTag().putString("owner", "retained");
        var adapter = new ReAvaritiaExtremeSmithingRecipeAdapter();
        var inputs = List.of(new ItemStack(Items.PAPER), base, new ItemStack(Items.DIAMOND, 3));
        var result = adapter.convertAll(RecipeHolder.of(source), helper.getLevel(), inputs);
        helper.assertTrue(result.size() == 1, "Five native slots must convert");
        helper.assertTrue(result.get(0).inputs().size() == 5, "Smithing must require all three addition slots");
        helper.assertTrue("retained".equals(result.get(0).outputs().get(0).getTag().getString("owner")),
                "Native assemble must copy base NBT");
        helper.assertTrue(adapter.convertAll(RecipeHolder.of(source), helper.getLevel(),
                List.of(new ItemStack(Items.PAPER), base, new ItemStack(Items.DIAMOND, 2))).isEmpty(),
                "Two additions cannot satisfy three slots");
        helper.assertTrue(base.getCount() == 1 && inputs.get(2).getCount() == 3, "Conversion cannot mutate inputs");
        helper.succeed();
    }

    @GameTest(template = "furnace_empty")
    public static void forgeWrenchAliasesFollowBothToolsSwitches(GameTestHelper helper) {
        var beef = new ItemStack(ModItems.ENDLESS_BEAF_ITEM.get());
        var disabled = BeefToolVariants.withWrenchTag(beef, false);
        var staff = new ItemStack(com.sorrowmist.useless.stretcher.init.ModItems.WONDROUS_STAFF.get());
        for (String id : List.of("forge:tools/wrench", "forge:tools/wrenches", "forge:wrenches", "mekanism:configurators")) {
            var tag = TagKey.create(Registries.ITEM, new ResourceLocation(id));
            helper.assertTrue(beef.is(tag) && !disabled.is(tag), "Core wrench aliases must honor G toggle: " + id);
            ComponentAccess.set(staff, UComponents.WrenchTagEnabledComponent.get(), true);
            helper.assertTrue(staff.is(tag), "Enabled staff must match " + id);
            ComponentAccess.set(staff, UComponents.WrenchTagEnabledComponent.get(), false);
            helper.assertTrue(!staff.is(tag), "Disabled staff must reject " + id);
        }
        helper.succeed();
    }
}
