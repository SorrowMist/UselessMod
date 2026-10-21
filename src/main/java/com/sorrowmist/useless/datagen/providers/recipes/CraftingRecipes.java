package com.sorrowmist.useless.datagen.providers.recipes;

import appeng.api.util.AEColor;
import appeng.core.AppEng;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipeBuilder;
import appeng.recipes.transform.TransformCircumstance;
import appeng.recipes.transform.TransformRecipeBuilder;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.enums.EnumColor;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipeBuilder;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.EnchantedItemIngredient;
import com.sorrowmist.useless.content.recipe.EntropyTransmutationRecipe;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModItems;
import com.sorrowmist.useless.init.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.common.conditions.NotCondition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CraftingRecipes extends RecipeProvider {

    /** ExtendedAE 的模组 id；该模组提供扩展机器，安装后替换配方中的原版部件。 */
    private static final String EXTENDEDAE = "extendedae";

    /**
     * 有用线圈各阶无用齿轮的消耗量，数组下标即阶次减一。
     *
     * <p>一阶六十四个、逐阶递减、九阶四个，合计二百九十二个。数量压在低阶、价值压在高阶：
     * 一阶锭单价最低，由数量承担；高阶锭单价高，少量即可。</p>
     */
    private static final long[] USEFUL_COIL_GEAR_COUNTS = {64, 56, 48, 40, 32, 24, 16, 8, 4};

    /** 动态注册表查找入口，用于在生成期构造带附魔组件的样板物品。 */
    private final CompletableFuture<HolderLookup.Provider> registryLookup;

    public CraftingRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
        this.registryLookup = registries;
    }

    @Override
    protected void buildRecipes(@NotNull RecipeOutput consumer) {
        this.addMoldRecipes(consumer);
        this.addAdvancedAlloyFurnaceEntropyRecipe(consumer);
        this.addOmniversalFurnaceRecipes(consumer);
        this.addEndlessBeafItemRecipe(consumer);
        this.addOreGeneratorBlockRecipe(consumer);
        this.addTeleportBlockRecipes(consumer);
        this.addSupervisorRecipe(consumer);
        this.addAE2GiftPackageRecipe(consumer);
        this.addPatternConverterRecipe(consumer);
        this.addPlasticRecipes(consumer);
        this.addTemperatureRegulatorRecipe(consumer);
        this.addAdvancedAlloyFurnaceRecipes(consumer);
        this.addUselessCoilRecipes(consumer);
        this.addUsefulCoilRecipe(consumer);
        this.addAlloyFurnaceCoreRecipe(consumer);
        this.addWorldInteractionRecipes(consumer);
    }

    /**
     * 无用锭一至四阶的世界交互配方。
     * 一、二阶为浸入水中的流体条件，三、四阶为爆炸条件。
     *
     * <p>AE2 的转换配方对每个原料条目只消耗一个物品，配方所需数量因此以条目重复次数表达：
     * 同一原料重复列出 N 次即消耗 N 个。</p>
     *
     * <p>定价原则：一至四阶是整条无用锭链的起点，基础材料自 16 个起、随阶数逐级抬升；
     * 前置锭统一只吃 1 个，链式成本因此不由重复投料堆叠，而由基础材料与高阶稀有材料承担。
     * 这里的数量不能与合金炉路径（五至九阶）一样放开：世界交互要玩家亲手备料、投掷、引爆，
     * 条目重复次数直接等于手动操作量，因此基础材料以 16 为起点逐级抬升，把重压留给后期。</p>
     */
    private void addWorldInteractionRecipes(RecipeOutput consumer) {
        var water = TransformCircumstance.fluid(FluidTags.WATER);
        var explosion = TransformCircumstance.EXPLOSION;
        var certusQuartz = Ingredient.of(ItemTags.create(
                ResourceLocation.parse("c:gems/certus_quartz")));

        // 一阶：浸入。首个条目为触发物品，投掷实体必须与之匹配。
        var tier1 = new ArrayList<Ingredient>();
        repeat(tier1, Ingredient.of(Items.MUTTON), 16);
        repeat(tier1, Ingredient.of(Items.BEEF), 16);
        repeat(tier1, Ingredient.of(Items.PORKCHOP), 16);
        repeat(tier1, Ingredient.of(Items.IRON_INGOT), 16);
        repeat(tier1, Ingredient.of(Items.GOLD_INGOT), 16);
        repeat(tier1, Ingredient.of(Items.DIAMOND), 16);
        TransformRecipeBuilder.transform(consumer, AppEng.makeId("transform/useless_ingot_tier_1"),
                ModItems.USELESS_INGOT_TIER_1.get(), 1, water, tier1.toArray(Ingredient[]::new));

        // 二阶：浸入
        var tier2 = new ArrayList<Ingredient>();
        repeat(tier2, Ingredient.of(Items.ROTTEN_FLESH), 16);
        repeat(tier2, Ingredient.of(Items.GUNPOWDER), 16);
        repeat(tier2, Ingredient.of(Items.BONE), 16);
        repeat(tier2, Ingredient.of(AEItems.SKY_DUST), 64);
        repeat(tier2, certusQuartz, 16);
        repeat(tier2, Ingredient.of(ModItems.USELESS_INGOT_TIER_1.get()), 1);
        TransformRecipeBuilder.transform(consumer, AppEng.makeId("transform/useless_ingot_tier_2"),
                ModItems.USELESS_INGOT_TIER_2.get(), 1, water, tier2.toArray(Ingredient[]::new));

        // 三阶：爆炸
        var tier3 = new ArrayList<Ingredient>();
        repeat(tier3, Ingredient.of(Items.RED_NETHER_BRICKS), 16);
        repeat(tier3, Ingredient.of(Items.BLAZE_POWDER), 32);
        repeat(tier3, Ingredient.of(Items.GLOWSTONE_DUST), 32);
        repeat(tier3, Ingredient.of(AEItems.CERTUS_QUARTZ_DUST), 32);
        repeat(tier3, Ingredient.of(AEItems.MATTER_BALL), 32);
        repeat(tier3, Ingredient.of(ModItems.USELESS_INGOT_TIER_2.get()), 1);
        TransformRecipeBuilder.transform(consumer, AppEng.makeId("transform/useless_ingot_tier_3"),
                ModItems.USELESS_INGOT_TIER_3.get(), 1, explosion, tier3.toArray(Ingredient[]::new));

        // 四阶：爆炸
        // 前置链修正：原配方吃一阶锭与三阶锭、跳过二阶锭，链式成本出现断点；
        // 现改为只吃紧邻的上一阶（二阶）与三阶锭，四阶正式成为三阶的下一环。
        var tier4 = new ArrayList<Ingredient>();
        repeat(tier4, Ingredient.of(Items.ENDER_EYE), 16);
        repeat(tier4, Ingredient.of(Items.CHORUS_FLOWER), 16);
        repeat(tier4, Ingredient.of(Items.PHANTOM_MEMBRANE), 32);
        repeat(tier4, Ingredient.of(Items.GOLD_BLOCK), 16);
        repeat(tier4, Ingredient.of(ModItems.USELESS_INGOT_TIER_2.get()), 1);
        repeat(tier4, Ingredient.of(ModItems.USELESS_INGOT_TIER_3.get()), 1);
        TransformRecipeBuilder.transform(consumer, AppEng.makeId("transform/useless_ingot_tier_4"),
                ModItems.USELESS_INGOT_TIER_4.get(), 1, explosion, tier4.toArray(Ingredient[]::new));
    }

    /** 把同一原料重复加入配方条目列表；重复次数即该原料在世界交互中的消耗数量。 */
    private static void repeat(List<Ingredient> ingredients, Ingredient ingredient, int times) {
        for (int i = 0; i < times; i++) {
            ingredients.add(ingredient);
        }
    }

    /** 任意塑料方块（1 个）→ 1 个温度调节器。 */
    private void addTemperatureRegulatorRecipe(RecipeOutput consumer) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,
                        ModItems.TEMPERATURE_REGULATOR.get(), 1)
                .requires(ModTags.PLASTIC_BLOCKS_ITEM)
                .unlockedBy("has_plastic", has(ModTags.PLASTIC_BLOCKS_ITEM))
                .save(consumer, UselessMod.id("crafting/temperature_regulator"));
    }

    private void addPlasticRecipes(RecipeOutput consumer) {
        for (EnumColor color : EnumColor.valuesInOrder()) {
            Block concrete = getConcreteBlock(color);
            String prefix = color.getRegistryPrefix();

            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                       GlowPlasticBlock.PLASTIC_BLOCK_ITEMS.get(color).get(), 8)
                              .pattern("CCC")
                              .pattern("C C")
                              .pattern("CCC")
                              .define('C', concrete)
                              .unlockedBy("has_" + prefix + "_concrete", has(concrete))
                              .save(consumer, UselessMod.id("crafting/" + prefix + "_plastic"));

            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                       GlowPlasticBlock.GLOW_PLASTIC_BLOCK_ITEMS.get(color).get(), 8)
                              .pattern("CCC")
                              .pattern("CGC")
                              .pattern("CCC")
                              .define('C', concrete)
                              .define('G', Items.GLOWSTONE_DUST)
                              .unlockedBy("has_" + prefix + "_concrete", has(concrete))
                              .save(consumer, UselessMod.id("crafting/" + prefix + "_glow_plastic"));

            addPlasticConversionRecipe(consumer,
                    GlowPlasticBlock.PLASTIC_CTM_BLOCK_ITEMS.get(color).get(),
                    GlowPlasticBlock.PLASTIC_BLOCK_ITEMS.get(color).get(),
                    prefix + "_plastic_ctm");
            addPlasticConversionRecipe(consumer,
                    GlowPlasticBlock.GLOW_PLASTIC_CTM_BLOCK_ITEMS.get(color).get(),
                    GlowPlasticBlock.GLOW_PLASTIC_BLOCK_ITEMS.get(color).get(),
                    prefix + "_glow_plastic_ctm");
        }
    }

    private static void addPlasticConversionRecipe(
            RecipeOutput consumer, Item output, Item ingredient, String recipeName) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, output, 8)
                           .pattern("PPP")
                           .pattern("P P")
                           .pattern("PPP")
                           .define('P', ingredient)
                           .unlockedBy("has_" + recipeName.replace("_ctm", ""), has(ingredient))
                           .save(consumer, UselessMod.id("crafting/" + recipeName));
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

    /**
     * 万象合金炉的「熵变转化」配方：熵变机械臂潜行右键 ME 箱子时，
     * 若箱内磁盘装有 8 个一阶无用锭，以及熔炉、铁块、金块、青金石块、
     * 钻石块、绿宝石块、下界合金块各 1 个（同一磁盘内合计即可），
     * 则原地把该 ME 箱子替换为万象合金炉。
     *
     * <p>除 8 个一阶锭外，其余材料沿用旧工作台配方的基础资源，并<b>按稀有度由低到高</b>
     * 排列（熔炉 → 铁块 → 金块 → 青金石块 → 钻石块 → 绿宝石块 → 下界合金块），
     * 既保留「用一整套基础资源熔出合金炉」的仪式感，也让玩家从材料顺序读出进阶梯度。</p>
     *
     * <p>配方只承载数据，匹配与消耗由 {@code EventHandler} 中的事件逻辑完成。</p>
     */
    private void addAdvancedAlloyFurnaceEntropyRecipe(RecipeOutput consumer) {
        List<CountedIngredient> inputs = List.of(
                new CountedIngredient(Ingredient.of(ModItems.USELESS_INGOT_TIER_1.get()), 8),
                new CountedIngredient(Ingredient.of(Items.FURNACE), 1),
                new CountedIngredient(Ingredient.of(Items.IRON_BLOCK), 1),
                new CountedIngredient(Ingredient.of(Items.GOLD_BLOCK), 1),
                new CountedIngredient(Ingredient.of(Items.LAPIS_BLOCK), 1),
                new CountedIngredient(Ingredient.of(Items.DIAMOND_BLOCK), 1),
                new CountedIngredient(Ingredient.of(Items.EMERALD_BLOCK), 1),
                new CountedIngredient(Ingredient.of(Items.NETHERITE_BLOCK), 1));

        EntropyTransmutationRecipe recipe = new EntropyTransmutationRecipe(
                inputs,
                new ItemStack(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get()));

        consumer.accept(UselessMod.id("entropy_transmutation/advanced_alloy_furnace"), recipe, null);
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
                           .pattern("AEA")
                           .pattern("BCB")
                           .pattern("AFA")
                           .define('A', ModBlocks.OMNIVERSAL_FURNACE_CASING.get())
                           .define('B', AEBlocks.PATTERN_PROVIDER)
                           .define('C', AEBlocks.INTERFACE)
                           .define('E', AEBlocks.IO_PORT)
                           .define('F', AEItems.CAPACITY_CARD)
                           .unlockedBy("has_pattern_provider", has(AEBlocks.PATTERN_PROVIDER))
                           .save(withoutExtendedAE(consumer), UselessMod.id("crafting/me_pattern_assembly"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.OMNIVERSAL_MOLD_HUB.get())
                           .pattern("ABA")
                           .pattern("DCD")
                           .pattern("AFA")
                           .define('A', ModBlocks.OMNIVERSAL_FURNACE_CASING.get())
                           .define('B', AEBlocks.ME_CHEST)
                           .define('C', AEBlocks.MOLECULAR_ASSEMBLER)
                           .define('D', AEBlocks.INTERFACE)
                           .define('F', AEItems.CAPACITY_CARD)
                           .unlockedBy("has_molecular_assembler", has(AEBlocks.MOLECULAR_ASSEMBLER))
                           .save(withoutExtendedAE(consumer), UselessMod.id("crafting/omniversal_mold_hub"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                   ModBlocks.PASSIVE_CRAFTING_HATCH.get())
                           .pattern("ACA")
                           .pattern("DPE")
                           .pattern("ARA")
                           .define('A', AEBlocks.MOLECULAR_ASSEMBLER)
                           .define('C', Items.CLOCK)
                           .define('D', AEParts.IMPORT_BUS)
                           .define('E', AEParts.EXPORT_BUS)
                           .define('P', ModBlocks.ME_PATTERN_ASSEMBLY.get())
                           .define('R', AEItems.SINGULARITY)
                           .unlockedBy("has_pattern_assembly", has(ModBlocks.ME_PATTERN_ASSEMBLY.get()))
                           .save(withoutExtendedAE(consumer), UselessMod.id("crafting/passive_crafting_hatch"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                                   ModBlocks.OMNIVERSAL_FURNACE_CASING.get(), 1)
                           .pattern("ABA")
                           .pattern("BCB")
                           .pattern("ABA")
                           .define('A', AEBlocks.FLUIX_BLOCK)
                           .define('B', AEBlocks.SKY_STONE_BLOCK)
                           .define('C', AEItems.COLORED_LUMEN_PAINT_BALL.item(AEColor.GREEN))
                           .unlockedBy("has_sky_stone", has(AEBlocks.SKY_STONE_BLOCK))
                           .save(consumer, UselessMod.id("crafting/omniversal_furnace_casing"));

    }

    /**
     * 为配方输出附加「未安装 ExtendedAE」条件。
     *
     * <p>安装 ExtendedAE 后，其扩展机器替换原版对应部件，配方由 {@code CompatRecipeProvider}
     * 另行生成的扩展版本承载；两者条件互斥，同一时刻只有一条参与加载。</p>
     */
    private static RecipeOutput withoutExtendedAE(RecipeOutput consumer) {
        return consumer.withConditions(new NotCondition(new ModLoadedCondition(EXTENDEDAE)));
    }

    /**
     * 一至九阶无用线圈的合金炉配方。
     *
     * <p>每阶消耗本级无用玻璃两个、本级齿轮三个、万象炉装饰外壳两个，以及低于本级一阶的线圈一个；
     * 一阶无下级线圈，改用石英纤维四个。线缆按阶次分段：二至四阶为玻璃线缆，五至七阶为包层线缆，
     * 八至九阶为致密线缆，数量均为八个。</p>
     */
    private void addUselessCoilRecipes(RecipeOutput consumer) {
        for (int tier = UselessCoilBlock.MIN_TIER; tier <= 9; tier++) {
            var glass = switch (tier) {
                case 1 -> ModItems.USELESS_GLASS_TIER_1.get();
                case 2 -> ModItems.USELESS_GLASS_TIER_2.get();
                case 3 -> ModItems.USELESS_GLASS_TIER_3.get();
                case 4 -> ModItems.USELESS_GLASS_TIER_4.get();
                case 5 -> ModItems.USELESS_GLASS_TIER_5.get();
                case 6 -> ModItems.USELESS_GLASS_TIER_6.get();
                case 7 -> ModItems.USELESS_GLASS_TIER_7.get();
                case 8 -> ModItems.USELESS_GLASS_TIER_8.get();
                case 9 -> ModItems.USELESS_GLASS_TIER_9.get();
                default -> throw new IllegalStateException("Unexpected coil tier: " + tier);
            };
            var gear = switch (tier) {
                case 1 -> ModItems.USELESS_GEAR_TIER_1.get();
                case 2 -> ModItems.USELESS_GEAR_TIER_2.get();
                case 3 -> ModItems.USELESS_GEAR_TIER_3.get();
                case 4 -> ModItems.USELESS_GEAR_TIER_4.get();
                case 5 -> ModItems.USELESS_GEAR_TIER_5.get();
                case 6 -> ModItems.USELESS_GEAR_TIER_6.get();
                case 7 -> ModItems.USELESS_GEAR_TIER_7.get();
                case 8 -> ModItems.USELESS_GEAR_TIER_8.get();
                case 9 -> ModItems.USELESS_GEAR_TIER_9.get();
                default -> throw new IllegalStateException("Unexpected coil tier: " + tier);
            };

            var builder = AdvancedAlloyFurnaceRecipeBuilder.create()
                                                          .input(Ingredient.of(glass), 2)
                                                          .input(Ingredient.of(gear), 3)
                                                          .input(Ingredient.of(ModBlocks.OMNIVERSAL_FURNACE_CASING.get()), 2);

            if (tier == 1) {
                builder.input(Ingredient.of(AEParts.QUARTZ_FIBER), 4);
            } else {
                builder.input(Ingredient.of(ModBlocks.USELESS_COILS.get(tier - 1).get()), 1);
            }

            if (tier <= 4) {
                builder.input(Ingredient.of(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)), 8);
            } else if (tier <= 7) {
                builder.input(Ingredient.of(AEParts.COVERED_CABLE.item(AEColor.TRANSPARENT)), 8);
            } else {
                builder.input(Ingredient.of(AEParts.COVERED_DENSE_CABLE.item(AEColor.TRANSPARENT)), 8);
            }

            builder.output(ModBlocks.USELESS_COILS.get(tier).get(), 1)
                   .energy(coilEnergy(tier))
                   .processTime(40)
                   .tier(tier - 1)
                   .mode(AlloyFurnaceMode.NORMAL)
                   .save(consumer, UselessMod.id(
                           "advanced_alloy/coil/" + UselessCoilBlock.registryName(tier)));
        }
    }

    /**
     * 有用线圈的合金炉配方。
     *
     * <p>消耗全部九种无用齿轮，数量自一阶六十四起逐阶递减至九阶四个，合计二百九十二个。
     * 数量压在低阶、价值压在高阶：一阶锭单价最低，靠数量承担；高阶锭单价高，靠少量即可。
     * 早期版本自九阶起每降一阶翻倍（一阶一千零二十四个），单条线圈要三万余个一阶锭，
     * 而一阶锭只能手动浸水获取，实际上不可完成；现按 {@link #USEFUL_COIL_GEAR_COUNTS} 编排。</p>
     * <p>另有九阶无用线圈一个与万象炉装饰外壳两个。玻璃部分由致密能源元件十四个、空间塔六个、
     * 空间锚一个替代，线缆为致密线缆八个。</p>
     */
    private void addUsefulCoilRecipe(RecipeOutput consumer) {
        var builder = AdvancedAlloyFurnaceRecipeBuilder.create()
                                                      .input(Ingredient.of(ModBlocks.OMNIVERSAL_FURNACE_CASING.get()), 2)
                                                      .input(Ingredient.of(ModBlocks.USELESS_COILS.get(9).get()), 1)
                                                      .input(Ingredient.of(AEBlocks.DENSE_ENERGY_CELL), 16)
                                                      .input(Ingredient.of(AEBlocks.SPATIAL_PYLON), 8)
                                                      .input(Ingredient.of(AEBlocks.SPATIAL_ANCHOR), 8)
                                                      .input(Ingredient.of(AEParts.COVERED_DENSE_CABLE.item(AEColor.TRANSPARENT)), 8);
        for (int tier = 1; tier <= 9; tier++) {
            builder.input(Ingredient.of(uselessGear(tier)), USEFUL_COIL_GEAR_COUNTS[tier - 1]);
        }
        builder.output(ModBlocks.USELESS_COILS.get(UselessCoilBlock.USEFUL_TIER).get(), 1)
               .energy(500000000L)
               .processTime(40)
               .tier(9)
               .mode(AlloyFurnaceMode.NORMAL)
               .save(consumer, UselessMod.id(
                       "advanced_alloy/coil/" + UselessCoilBlock.registryName(UselessCoilBlock.USEFUL_TIER)));
    }

    /**
     * 多方块合金炉核心的合金炉配方。
     *
     * <p>作为工作台配方的后期替代路径，消耗万象合金炉十六个、ME 控制器六十四个、九阶无用锭两个，
     * 以及成型核心、破坏核心、并行处理单元各一千零二十四个。机器等级限定为九阶。</p>
     */
    private void addAlloyFurnaceCoreRecipe(RecipeOutput consumer) {
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get()), 16)
                                         .input(Ingredient.of(AEBlocks.CONTROLLER), 64)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_9.get()), 2)
                                         .input(Ingredient.of(AEItems.FORMATION_CORE), 1024)
                                         .input(Ingredient.of(AEItems.ANNIHILATION_CORE), 1024)
                                         .input(Ingredient.of(AEBlocks.CRAFTING_ACCELERATOR), 1024)
                                         .output(ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get(), 1)
                                         .energy(500000000L)
                                         .processTime(100)
                                         .tier(9)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(consumer, UselessMod.id("advanced_alloy/alloy_furnace_core"));
    }

    /** 无用齿轮按阶次取件；阶次范围为 1 至 9。 */
    private static Item uselessGear(int tier) {
        return switch (tier) {
            case 1 -> ModItems.USELESS_GEAR_TIER_1.get();
            case 2 -> ModItems.USELESS_GEAR_TIER_2.get();
            case 3 -> ModItems.USELESS_GEAR_TIER_3.get();
            case 4 -> ModItems.USELESS_GEAR_TIER_4.get();
            case 5 -> ModItems.USELESS_GEAR_TIER_5.get();
            case 6 -> ModItems.USELESS_GEAR_TIER_6.get();
            case 7 -> ModItems.USELESS_GEAR_TIER_7.get();
            case 8 -> ModItems.USELESS_GEAR_TIER_8.get();
            case 9 -> ModItems.USELESS_GEAR_TIER_9.get();
            default -> throw new IllegalStateException("Unexpected gear tier: " + tier);
        };
    }

    /** 线圈能耗沿用无用锭的等级序列，保持同阶产物能耗口径一致。 */
    private static long coilEnergy(int tier) {
        return switch (tier) {
            case 1 -> 10000L;
            case 2 -> 50000L;
            case 3 -> 100000L;
            case 4 -> 500000L;
            case 5 -> 1000000L;
            case 6 -> 5000000L;
            case 7 -> 10000000L;
            case 8 -> 50000000L;
            case 9 -> 100000000L;
            default -> throw new IllegalStateException("Unexpected coil tier: " + tier);
        };
    }

    private void addAE2GiftPackageRecipe(RecipeOutput consumer) {
        if (ModItems.AE2_GIFT_PACKAGE == null) {
            return;
        }

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.AE2_GIFT_PACKAGE.get(), 1)
                           .pattern("SPS")
                           .pattern("PPP")
                           .pattern("SPS")
                           .define('P', ItemTags.PLANKS)
                           .define('S', Items.STICK)
                           .unlockedBy("has_planks", has(ItemTags.PLANKS))
                           .save(consumer, UselessMod.id("crafting/ae2_gift_package"));
    }

    private void addPatternConverterRecipe(RecipeOutput consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.OMNIVERSAL_PATTERN_CONVERTER.get())
                           .pattern("GPG")
                           .pattern(" L ")
                           .define('G', Items.YELLOW_DYE)
                           .define('P', AEItems.BLANK_PATTERN)
                           .define('L', AEItems.LOGIC_PROCESSOR)
                           .unlockedBy("has_blank_pattern", has(AEItems.BLANK_PATTERN))
                           .save(consumer, UselessMod.id("crafting/omniversal_pattern_converter"));
    }

    private void addSupervisorRecipe(RecipeOutput consumer) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ModItems.SUPERVISOR.get())
                               .requires(ModItems.USEFUL_INGOT.get())
                               .requires(Items.PINK_WOOL)
                               .requires(Items.BLACK_WOOL)
                               .requires(Items.GRAY_WOOL)
                               .requires(Items.WHITE_WOOL)
                               .requires(Items.GREEN_WOOL)
                               .requires(Items.BLUE_WOOL)
                               .requires(Items.YELLOW_WOOL)
                               .requires(Items.ORANGE_WOOL)
                               .unlockedBy("has_useful_ingot", has(ModItems.USEFUL_INGOT.get()))
                               .save(consumer, UselessMod.id("crafting/supervisor"));
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

    /**
     * 无用锭五至九阶与有用锭的合金炉配方。
     *
     * <p>一至四阶锭改由世界交互配方产出，不再提供合金炉路径。</p>
     */
    private void addUselessIngotRecipes(RecipeOutput output) {
        // Tier 5 - 使用4阶无用锭作为输入材料；幻翼膜替换为潜影壳，机器等级限定为 LV.4
        // 能量卡 LV.4 容量上限：BASE_ENERGY_CAPACITY(100000) * 8 * 4 = 3200000
        // 造价：上一阶锭 1 个，各类材料 32 个
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.WITHER_SKELETON_SKULL), 32)
                                         .input(Ingredient.of(Items.SHULKER_SHELL), 32)
                                         .input(Ingredient.of(Items.SEA_LANTERN), 32)
                                         .input(Ingredient.of(Items.GOLD_BLOCK), 32)
                                         .input(Ingredient.of(Items.DIAMOND_BLOCK), 32)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_4.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_5.get(), 1)
                                         .energy(3200000)
                                         .processTime(1)
                                         .tier(4)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_5"));

        // Tier 6 - 使用5阶无用锭作为输入材料；追加福鲁伊克斯粉，机器等级限定为 LV.5
        // 能量卡 LV.5 容量上限：BASE_ENERGY_CAPACITY(100000) * 8 * 16 = 12800000
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.DRAGON_BREATH), 16)
                                         .input(Ingredient.of(Items.TOTEM_OF_UNDYING), 8)
                                         .input(Ingredient.of(Items.ENCHANTED_GOLDEN_APPLE), 8)
                                         .input(Ingredient.of(AEItems.FLUIX_DUST), 128)
                                         .input(Ingredient.of(Items.DIAMOND_BLOCK), 64)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_5.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_6.get(), 1)
                                         .energy(12800000)
                                         .processTime(1)
                                         .tier(5)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_6"));

        // Tier 7 - 使用6阶无用锭作为输入材料；钻石块替换为奇点，机器等级限定为 LV.6
        // 能量卡 LV.6 容量上限：BASE_ENERGY_CAPACITY(100000) * 8 * 64 = 51200000
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.NETHER_STAR), 8)
                                         .input(Ingredient.of(Items.WITHER_ROSE), 32)
                                         .input(Ingredient.of(Items.GHAST_TEAR), 32)
                                         .input(Ingredient.of(AEItems.SINGULARITY), 128)
                                         .input(Ingredient.of(Items.NETHERITE_INGOT), 32)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_6.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_7.get(), 1)
                                         .energy(51200000)
                                         .processTime(1)
                                         .tier(6)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_7"));

        // Tier 8 - 使用7阶无用锭作为输入材料；龙首替换为海龟壳，机器等级限定为 LV.7
        // 能量卡 LV.7 容量上限：BASE_ENERGY_CAPACITY(100000) * 8 * 256 = 204800000
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.TURTLE_HELMET), 8)
                                         .input(Ingredient.of(Items.ECHO_SHARD), 16)
                                         .input(Ingredient.of(Items.CONDUIT), 16)
                                         .input(Ingredient.of(Items.NETHERITE_INGOT), 64)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_7.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_8.get(), 1)
                                         .energy(204800000)
                                         .processTime(1)
                                         .tier(7)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_8"));

        // Tier 9 - 使用8阶无用锭作为输入材料；追加沉重核心与猪灵的头，海龟壳替换为龙首，钻石块替换为没那么神秘的方块，机器等级限定为 LV.8
        // 能量卡 LV.8 容量上限：BASE_ENERGY_CAPACITY(100000) * 8 * 1024 = 819200000
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(Items.DRAGON_HEAD), 8)
                                         .input(Ingredient.of(Items.GOAT_HORN), 8)
                                         .input(Ingredient.of(Items.RABBIT_FOOT), 8)
                                         .input(Ingredient.of(AEBlocks.NOT_SO_MYSTERIOUS_CUBE), 16)
                                         .input(Ingredient.of(Items.NETHERITE_BLOCK), 16)
                                         .input(Ingredient.of(Items.HEAVY_CORE), 8)
                                         .input(Ingredient.of(Items.PIGLIN_HEAD), 8)
                                         .input(Ingredient.of(ModItems.USELESS_INGOT_TIER_8.get()), 1)
                                         .output(ModItems.USELESS_INGOT_TIER_9.get(), 1)
                                         .energy(819200000)
                                         .processTime(1)
                                         .tier(8)
                                         .mode(AlloyFurnaceMode.NORMAL)
                                         .save(output, UselessMod.id("advanced_alloy/ingot/useless_ingot_tier_9"));

        // Possible Useful Ingot - 合成配方（工作台）
        // 使用9个不同等级的无用锭合成

        // Useful Ingot - 高级熔炉配方；由可能有用锭与 AE2 高阶材料合成，机器等级限定为 LV.9
        // 能量卡 LV.9 容量上限：BASE_ENERGY_CAPACITY(100000) * 8 * 4096 = 3276800000
        AdvancedAlloyFurnaceRecipeBuilder.create()
                                         .input(Ingredient.of(ModItems.POSSIBLE_USEFUL_INGOT.get()), 32)
                                         .input(Ingredient.of(AEBlocks.FLAWLESS_BUDDING_QUARTZ), 32)
                                         .input(Ingredient.of(AEItems.CELL_COMPONENT_256K), 64)
                                         .input(Ingredient.of(AEItems.SPATIAL_128_CELL_COMPONENT), 64)
                                         .input(Ingredient.of(AEBlocks.DENSE_ENERGY_CELL), 64)
                                         .input(Ingredient.of(AEItems.ENDER_DUST), 512)
                                         .input(Ingredient.of(AEItems.SINGULARITY), 1024)
                                         .output(ModItems.USEFUL_INGOT.get(), 1)
                                         .energy(3276800000L)
                                         .processTime(100)
                                         .tier(9)
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

    /**
     * 造化垂青之杖：在 AE2 压印器中制作。
     *
     * <p>中层为五阶无用锭，上层为时运 3 的下界合金镐，下层为抢夺 3 与精准采集的下界合金斧。
     * 采用 {@link InscriberProcessType#PRESS} 模式，三个槽位的材料都会被消耗；
     * 附魔要求由 {@link EnchantedItemIngredient} 承载，只校验物品种类与各附魔的最低等级，
     * 不要求工具耐久或其它组件完全一致。</p>
     */
    private void addEndlessBeafItemRecipe(RecipeOutput consumer) {
        HolderLookup.RegistryLookup<Enchantment> enchantments =
                this.registryLookup.join().lookupOrThrow(Registries.ENCHANTMENT);

        ItemStack fortunePickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
        ItemEnchantments.Mutable pickaxeEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        pickaxeEnchantments.set(enchantments.getOrThrow(Enchantments.FORTUNE), 3);
        fortunePickaxe.set(DataComponents.ENCHANTMENTS, pickaxeEnchantments.toImmutable());

        ItemStack lootingAxe = new ItemStack(Items.NETHERITE_AXE);
        ItemEnchantments.Mutable axeEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        axeEnchantments.set(enchantments.getOrThrow(Enchantments.LOOTING), 3);
        axeEnchantments.set(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
        lootingAxe.set(DataComponents.ENCHANTMENTS, axeEnchantments.toImmutable());

        InscriberRecipeBuilder.inscribe(Ingredient.of(ModItems.USELESS_INGOT_TIER_5.get()),
                                        ModItems.ENDLESS_BEAF_ITEM.get(), 1)
                               .setTop(EnchantedItemIngredient.of(fortunePickaxe))
                               .setBottom(EnchantedItemIngredient.of(lootingAxe))
                               .setMode(InscriberProcessType.PRESS)
                               .save(consumer, UselessMod.id("inscriber/endless_beaf_item"));
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
