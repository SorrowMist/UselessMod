package com.sorrowmist.useless.content.recipe.adapters.astralsorcery;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.recipe.infusion.InfusionRecipe;
import hellfirepvp.astralsorcery.common.tile.TileInfuser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将星能注入器（Astral Sorcery 的液态星能注入配方）转换为高级合金炉配方。
 *
 * <p>注入器以物品作为核心输入，并以星能液作为消耗对象。配方可从两条互斥路径取液：
 * 若附近的圣杯能够供给，则只扣减圣杯储罐，世界中的星能液方块不受影响；否则回落到逐个
 * 判定注液位、命中后将星能液方块替换为空气的路径。</p>
 *
 * <p>该差异无法在配方的静态数据中统一表达，因此本适配器为每个源配方产出两条独立变体，
 * 以模具构成区分取液路径，两条变体的流体折算口径不同：</p>
 * <ul>
 *   <li>方块路径变体：模具仅包含注入器，按单次执行耗尽一个星能液方块折算，即一桶容量。
 *       该变体恒存在，与 {@code accept_chalice_input} 的取值无关。</li>
 *   <li>圣杯路径变体：模具在注入器之外追加圣杯作为第二级模具，按
 *       {@link InfusionRecipe#getChaliceInputFluidStack()} 声明的取液量折算，
 *       该值即圣杯路径在单次执行中的实际扣减量，与方块路径的随机判定无关。
 *       该变体仅在配方允许圣杯供给且取液量大于零时产出。</li>
 * </ul>
 *
 * <p>两条变体的配方 id 分别以 {@code _block} 与 {@code _chalice} 为后缀，
 * 使其在配方目录中各自稳定可寻。</p>
 *
 * <p>注入器配方不产生流体输出，因此转换结果的流体输出列表恒为空。</p>
 */
public final class InfusionRecipeAdapter implements IRecipeAdapter<InfusionRecipe> {

    @Override
    public String sourceId() {
        return RecipeSourceIds.ASTRAL_SORCERY;
    }

    @Override
    public Class<InfusionRecipe> getRecipeClass() {
        return InfusionRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(BlocksAS.INFUSER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<InfusionRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();

        InfusionRecipe recipe = holder.value();
        Ingredient itemInput = recipe.getItemInput();
        ItemStack output = recipe.getOutput();

        // 物品输入与产出缺一的配方在合金炉中不可执行，直接跳过。
        if (AdapterUtils.isIngredientEmpty(itemInput) || output.isEmpty()) return List.of();

        // 方块取液路径恒存在：模具仅含注入器，按单次执行耗尽一个星能液方块折算，即固定一桶。
        // 概率非正表示配方不消耗流体，此时该路径不声明流体需求，但仍作为可执行变体保留。
        ResourceLocation baseId = AdapterUtils.convertedId(holder.id());
        List<AdvancedAlloyFurnaceRecipe> converted = new ArrayList<>(2);
        converted.add(buildVariant(recipe, baseId.withSuffix("_block"), itemInput, output,
                blockPathAmount(recipe), false));

        // 圣杯取液路径为附加变体：仅当配方允许圣杯供给时存在，模具追加圣杯作为第二级模具，
        // 流体按圣杯在单次执行中的实际取液量折算，即期望值口径。
        if (acceptsChalice(recipe)) {
            int chaliceAmount = recipe.getChaliceInputFluidStack().getAmount();
            if (chaliceAmount > 0) {
                converted.add(buildVariant(recipe, baseId.withSuffix("_chalice"), itemInput, output,
                        chaliceAmount, true));
            }
        }

        return List.copyOf(converted);
    }

    /**
     * 构造单一取液路径对应的合金炉配方。
     *
     * <p>两条路径共用物品输入与产出，差异仅在模具构成与流体折算量：方块路径的模具为注入器
     * 本体，圣杯路径额外要求圣杯作为二级模具。id 后缀与模具构成一一对应，使两条配方在配方
     * 目录中各自稳定可寻。</p>
     *
     * @param recipe 源注入配方
     * @param id 该变体的配方 ID
     * @param itemInput 物品输入
     * @param output 产出
     * @param fluidAmount 该路径的星能液折算量（毫桶）
     * @param withChalice 是否追加圣杯作为二级模具
     * @return 转换后的合金炉配方
     */
    private static AdvancedAlloyFurnaceRecipe buildVariant(InfusionRecipe recipe, ResourceLocation id,
                                                            Ingredient itemInput, ItemStack output,
                                                            int fluidAmount, boolean withChalice) {
        List<LongSizedFluidIngredient> inputFluids = fluidAmount > 0
                ? List.of(LongSizedFluidIngredient.from(new FluidStack(recipe.getFluidInput(), fluidAmount)))
                : List.of();

        return new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(new CountedIngredient(itemInput, 1L)),
                inputFluids,
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                resolveProcessTime(recipe),
                Ingredient.EMPTY,
                0,
                buildMolds(withChalice),
                AlloyFurnaceMode.NORMAL);
    }

    /**
     * 构造配方模具。
     *
     * <p>一级模具恒为注入器本体。当该变体对应圣杯取液路径时，追加圣杯作为二级模具，
     * 使配方在合金炉中显式要求圣杯参与，并与按圣杯实际取液量折算的流体需求保持一致。</p>
     *
     * <p>多模具配方依赖多方块结构的模具仓承载各模具槽位：普通合金炉仅暴露单个模具槽，
     * 相关配方在普通路径下不参与选择。这是二级模具语义的既定适用范围。</p>
     *
     * @param withChalice 是否追加圣杯作为二级模具
     * @return 模具列表
     */
    private static List<Ingredient> buildMolds(boolean withChalice) {
        List<Ingredient> molds = new ArrayList<>();
        molds.add(AdapterUtils.toMoldIngredient(new ItemStack(BlocksAS.INFUSER.get())));

        if (withChalice) {
            Ingredient chaliceMold = AdapterUtils.toMoldIngredient(new ItemStack(BlocksAS.CHALICE.get()));
            if (!AdapterUtils.isIngredientEmpty(chaliceMold)) molds.add(chaliceMold);
        }

        return List.copyOf(molds);
    }

    /**
     * 判定配方是否接受圣杯供给。
     *
     * <p>该判定同时决定模具构成与流体折算口径，是两条取液路径的分流依据。</p>
     *
     * @param recipe 源注入配方
     * @return 配方是否允许圣杯参与取液
     */
    private static boolean acceptsChalice(InfusionRecipe recipe) {
        return recipe.acceptChaliceInput() && recipe.getFluidConsumptionChance() > 0F;
    }

    @Override
    public List<RecipeHolder<InfusionRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        if (mergedInputs == null || mergedInputs.isEmpty()) return List.of();

        List<RecipeHolder<InfusionRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<InfusionRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(RecipeTypesAS.INFUSION_TYPE.get())) {
            InfusionRecipe recipe = holder.value();
            if (recipe == null || AdapterUtils.isIngredientEmpty(recipe.getItemInput())) continue;
            if (!AdapterUtils.matchesRequired(mergedInputs, Map.of(recipe.getItemInput(), 1L))) continue;

            // 一条源配方对应两条变体（方块路径与圣杯路径），各自的流体需求不同；
            // 只要任一变体的流体需求被满足，该源配方即存在可执行路径。
            boolean fluidSatisfied = AdapterUtils.matchesLongFluidIngredients(
                    mergedFluids, buildInputFluids(recipe, false));
            if (!fluidSatisfied && acceptsChalice(recipe)) {
                fluidSatisfied = AdapterUtils.matchesLongFluidIngredients(
                        mergedFluids, buildInputFluids(recipe, true));
            }
            if (!fluidSatisfied) continue;

            matches.add(holder);
        }
        return List.copyOf(matches);
    }

    /**
     * 构造配方所需的星能液输入。
     *
     * <p>折算口径随圣杯是否参与而分流，与 {@link #acceptsChalice(InfusionRecipe)} 的判定保持一致：</p>
     * <ul>
     *   <li>圣杯不参与时，注入器只走方块消耗路径：在 {@link TileInfuser#getLiquidOffsets()}
     *       的各个注液位上逐个判定，命中后把该位置的星能液方块替换为空气。此处以单次执行
     *       耗尽一个星能液方块为口径，即一桶容量；{@code consume_multiple_fluids} 为真时，
     *       多个注液位会在同一次执行中依次被耗尽。</li>
     *   <li>圣杯参与时，取液量由 {@link InfusionRecipe#getChaliceInputFluidStack()} 给出，
     *       该值是圣杯路径在单次执行中的实际扣减量，与方块路径的随机判定无关，
     *       因此直接采用而不再按 {@code fluid_consumption_chance} 二次折算。</li>
     * </ul>
     *
     * <p>两条路径各自对应一条独立的合金炉配方，因此本方法按路径分别求值。</p>
     *
     * @param recipe 源注入配方
     * @param withChalice 是否按圣杯取液路径折算
     * @return 星能液输入列表；不需要消耗流体时返回空列表
     */
    private static List<LongSizedFluidIngredient> buildInputFluids(InfusionRecipe recipe, boolean withChalice) {
        Fluid fluid = recipe.getFluidInput();
        if (fluid == null) return List.of();

        // 概率非正表示该配方不消耗流体，无需声明流体输入。
        if (recipe.getFluidConsumptionChance() <= 0F) return List.of();

        int amount = withChalice
                ? recipe.getChaliceInputFluidStack().getAmount()
                : blockPathAmount(recipe);
        if (amount <= 0) return List.of();

        return List.of(LongSizedFluidIngredient.from(new FluidStack(fluid, amount)));
    }

    /**
     * 计算方块消耗路径下单次执行的星能液折算量。
     *
     * @param recipe 源注入配方
     * @return 折算量（毫桶）
     */
    private static int blockPathAmount(InfusionRecipe recipe) {
        int liquidSlots = Math.max(1, TileInfuser.getLiquidOffsets().size());
        int consumedBlocks = recipe.consumeMultipleFluids() ? liquidSlots : 1;
        return consumedBlocks * FluidType.BUCKET_VOLUME;
    }

    /**
     * 解析加工耗时。
     *
     * <p>注入器的 {@code duration} 单位为游戏刻，合金炉以刻为加工时间单位，二者语义一致；
     * 非正值视为未配置并回落到默认耗时。</p>
     *
     * @param recipe 源注入配方
     * @return 合金炉加工耗时（刻）
     */
    private static int resolveProcessTime(InfusionRecipe recipe) {
        int duration = recipe.getDuration();
        return duration > 0 ? duration : AdapterUtils.DEFAULT_PROCESS_TIME;
    }
}
