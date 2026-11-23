package com.sorrowmist.useless.content.recipe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.INPUT_SLOTS_START;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.MOLD_SLOT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_START;

@GameTestHolder("useless_mod")
@PrefixGameTestTemplate(false)
public final class AlloyFurnaceRegressionGameTest {
    private AlloyFurnaceRegressionGameTest() {
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void vanillaSmeltingStillProcessesInTheAlloyFurnace(GameTestHelper helper) {
        Level level = helper.getLevel();
        BlockPos relativePos = new BlockPos(1, 1, 1);
        BlockPos furnacePos = helper.absolutePos(relativePos);
        helper.setBlock(relativePos.getX(), relativePos.getY(), relativePos.getZ(),
                ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get());

        if (!(level.getBlockEntity(furnacePos) instanceof AdvancedAlloyFurnaceBlockEntity furnace)) {
            throw new AssertionError("高级合金炉方块实体未能创建");
        }
        AdvancedAlloyFurnaceRecipe recipe = AlloyFurnaceRecipeManager.getInstance().findRecipe(
                level,
                List.of(new ItemStack(Items.RAW_IRON)),
                List.of(),
                new ItemStack(Items.FURNACE));
        if (recipe == null || recipe.outputs().stream().noneMatch(output -> output.is(Items.IRON_INGOT))) {
            throw new AssertionError("原版粗铁熔炼配方未能由合金炉解析");
        }

        furnace.setItemInSlot(INPUT_SLOTS_START, new ItemStack(Items.RAW_IRON));
        furnace.setItemInSlot(MOLD_SLOT, new ItemStack(Items.FURNACE));
        furnace.setEnergy(furnace.getMaxEnergy());
        helper.runAfterDelay(250, () -> {
            ItemStack output = furnace.getItemHandler().getStackInSlot(OUTPUT_SLOTS_START);
            if (!output.is(Items.IRON_INGOT) || output.getCount() != 1) {
                throw new AssertionError("高级合金炉未完成原版粗铁熔炼：" + output);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void omniversalPatternRemainsValidAcrossDimensions(GameTestHelper helper) {
        Level overworld = helper.getLevel();
        ResourceKey<Level> netherKey = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"));
        ServerLevel nether = overworld.getServer().getLevel(netherKey);
        if (nether == null) {
            throw new AssertionError("下界未加载，无法验证跨维度样板解析");
        }
        if (overworld.getRecipeManager() != nether.getRecipeManager()) {
            throw new AssertionError("主世界与下界未共享配方管理器");
        }
        if (!AlloyFurnaceRecipeCatalog.isReady(nether)) {
            throw new AssertionError("下界未复用已构建的合金炉配方目录");
        }

        AdvancedAlloyFurnaceRecipe recipe = AlloyFurnaceRecipeManager.getInstance().findRecipe(
                overworld,
                List.of(new ItemStack(Items.RAW_IRON)),
                List.of(),
                new ItemStack(Items.FURNACE));
        if (recipe == null) {
            throw new AssertionError("无法解析原版粗铁熔炼配方");
        }
        AlloyFurnaceRecipeCatalog.Entry entry = AlloyFurnaceRecipeCatalog.entries(overworld).stream()
                .filter(candidate -> candidate.recipe().id().equals(recipe.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("配方目录中缺少粗铁熔炼配方"));

        ItemStack processingPattern = OmniversalPatternEncoding.createProcessingPattern(recipe);
        IPatternDetails sourceDetails = PatternDetailsHelper.decodePattern(processingPattern, overworld);
        ItemStack omniversalPattern = OmniversalPatternEncoding.encode(
                processingPattern, sourceDetails, entry, overworld);
        if (omniversalPattern.isEmpty()) {
            throw new AssertionError("无法为粗铁熔炼配方编码万象样板");
        }

        IPatternDetails overworldDetails = PatternDetailsHelper.decodePattern(omniversalPattern, overworld);
        IPatternDetails netherDetails = PatternDetailsHelper.decodePattern(omniversalPattern, nether);
        if (!(overworldDetails instanceof OmniversalPatternDetails overworldPattern)
                || !(netherDetails instanceof OmniversalPatternDetails netherPattern)) {
            throw new AssertionError("万象样板未能在主世界与下界同时解码");
        }
        if (!overworldPattern.recipe().id().equals(recipe.id())
                || !netherPattern.recipe().id().equals(recipe.id())
                || !netherPattern.data().identity().equals(entry.identity())) {
            throw new AssertionError("跨维度解码后万象样板绑定的配方身份发生变化");
        }

        helper.succeed();
    }
}
