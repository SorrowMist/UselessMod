package com.sorrowmist.useless.content.recipe.adapters.astralsorcery;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import hellfirepvp.astralsorcery.common.component.AttunedConstellationComponent;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.ingredient.IngredientBridge;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将星辉祭坛（Astral Sorcery 的祭坛合成配方）转换为高级合金炉配方。
 *
 * <p>四类祭坛（启明工作台、共振工作台、辉光合成祭坛、虹彩合成祭坛）共用同一个配方类型
 * {@code astralsorcery:altar_crafting}，仅以 {@code requiredType} 字段区分所需祭坛等级。
 * 本适配器对四类祭坛统一转换，并在模具中编码祭坛等级，使同一配方不会在错误的祭坛等级下匹配。</p>
 *
 * <p>配方输入由三部分组成：3×3 祭坛网格、5×5 继电器网格，以及附加输入。继电器网格在祭坛结构中
 * 由四周的星辉继电器提供，其位置固定，因此转换为合金炉输入时按材料种类合并计数。</p>
 *
 * <p>祭坛的聚焦槽承载星座维度：配方以 {@code focusConstellation} 声明所需星座，该星座由聚焦槽中
 * 带 {@code attuned_constellation} 组件的调谐水晶石提供。要求聚焦星座的配方在合金炉中额外把
 * 对应星座的调谐水晶石登记为二级模具，使星座维度在模具槽位上得到校验。</p>
 *
 * <p>网格与继电器网格中的共鸣水晶石是普通材料，不提供星座，按普通输入处理。</p>
 */
public final class AltarRecipeAdapter implements IRecipeAdapter<AltarRecipe> {

    @Override
    public String sourceId() {
        return RecipeSourceIds.ASTRAL_SORCERY;
    }

    @Override
    public Class<AltarRecipe> getRecipeClass() {
        return AltarRecipe.class;
    }

    /**
     * 祭坛配方为动态模具：一级模具随配方所属祭坛等级变化，可能还存在二级模具，
     * 因此此处返回 {@code null} 表示不使用固定的预索引模具。
     *
     * @return 恒为 {@code null}
     */
    @Override
    public ItemStack getMoldItem() {
        return null;
    }

    /**
     * 判定模具槽位是否可能承载本适配器的配方。
     *
     * <p>本适配器不使用固定模具，因此默认实现会把任意模具都视为可处理。祭坛配方的模具
     * 槽位实际承载祭坛等级信息，必须放入四类祭坛物品之一才可能匹配，故在此收窄判定范围，
     * 避免与其他适配器在同一模具槽位上产生无谓的候选枚举。</p>
     *
     * <p>要求聚焦星座的配方另需一颗调谐水晶石作为第二级模具（见 {@link #buildMolds}），
     * 该模具位于模具仓的独立槽位而非当前槽位，因此本判定不将其纳入：当前槽位承载的
     * 始终是祭坛等级。</p>
     *
     * @param mold 模具槽位中的物品
     * @return 模具是否为任一等级的祭坛物品
     */
    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) return false;
        for (TileAltar.AltarType type : TileAltar.AltarType.values()) {
            if (ItemStack.isSameItem(type.getAltarItem(), mold)) return true;
        }
        return false;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<AltarRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();

        AltarRecipe recipe = holder.value();
        List<CountedIngredient> inputs = collectInputs(recipe);
        List<ItemStack> outputs = recipe.getOutputs();

        // 无输入或无产出的配方在合金炉中不可执行，直接跳过。
        if (inputs.isEmpty() || outputs.isEmpty()) return List.of();

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                buildInputFluids(recipe),
                List.of(),
                outputs.stream().map(ItemStack::copy).toList(),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                resolveProcessTime(recipe),
                Ingredient.EMPTY,
                0,
                buildMolds(recipe),
                AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<AltarRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null) return List.of();

        List<RecipeHolder<AltarRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<AltarRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(RecipeTypesAS.ALTAR_CRAFTING_TYPE.get())) {
            AltarRecipe recipe = holder.value();
            if (recipe == null) continue;

            // 模具槽位放置的祭坛物品决定可用配方等级，等级不符的配方不得参与匹配。
            if (!matchesAltarTier(recipe, mold)) continue;

            List<CountedIngredient> required = collectInputs(recipe);
            if (required.isEmpty()) continue;
            if (!AdapterUtils.matchesRequired(mergedInputs, toCountMap(required))) continue;
            if (!AdapterUtils.matchesLongFluidIngredients(mergedFluids, buildInputFluids(recipe))) continue;

            matches.add(holder);
        }
        return List.copyOf(matches);
    }

    /**
     * 判定模具槽位中的祭坛物品是否与配方的祭坛等级一致。
     *
     * <p>祭坛配方不使用固定模具，模具槽位承载的是等级信息：只有放入与 {@code requiredType} 对应的祭坛物品，
     * 该等级的配方才可被匹配。模具槽位为空或物品不是祭坛时，不返回任何配方，避免跨等级误匹配。</p>
     *
     * <p>配方要求的聚焦星座由 {@link #buildMolds} 生成的第二级模具承载，不影响此处的等级判定：
     * 该模具在模具仓中占用独立槽位，与承载等级的槽位互不干涉。</p>
     *
     * @param recipe 源祭坛配方
     * @param mold 模具槽位中的物品
     * @return 祭坛等级是否一致
     */
    private static boolean matchesAltarTier(AltarRecipe recipe, @Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) return false;
        TileAltar.AltarType requiredType = recipe.getRequiredType();
        if (requiredType == null) return false;
        return ItemStack.isSameItem(requiredType.getAltarItem(), mold);
    }

    /**
     * 收集配方的全部物品输入。
     *
     * <p>祭坛网格与继电器网格中的流体型条目（{@link IngredientBridge.Type#FLUID}）不纳入物品输入，
     * 它们在祭坛上表现为容器盛装流体，与合金炉的流体输入语义不同，交由流体输入另行处理。</p>
     *
     * @param recipe 源祭坛配方
     * @return 合并计数后的物品输入
     */
    private static List<CountedIngredient> collectInputs(AltarRecipe recipe) {
        // 网格与继电器网格中，同一字符键的多次出现必须合并为单个输入项并累计份数；
        // 若逐槽位独立登记，一份材料会被拆成多条输入，导致配方在匹配时无法满足。
        //
        // 归并依据是 Ingredient 的语义等价，而非对象身份：配方经网络同步后，各槽位由
        // 独立的流解码产生，同一材料对应的是不同实例，身份比较会失效。
        //
        // 亦不能直接复用 AdapterUtils.mergeIngredients：该实现调用的 areIngredientsEqual
        // 对自定义条目（如 neoforge:components 声明的组件型材料）直接判定为不相同，
        // 其候选物品列表在该类型下仅作展示提示，因此组件型材料无法参与合并，
        // 会退化为一材料一条输入。此处改用不排斥自定义条目的等价比较。
        List<Ingredient> gathered = new ArrayList<>();
        for (IngredientBridge bridge : recipe.getGrid().getInputs()) {
            Ingredient ingredient = itemIngredientOf(bridge);
            if (ingredient != null) gathered.add(ingredient);
        }
        for (IngredientBridge bridge : recipe.getGrid().getRelayInputs()) {
            Ingredient ingredient = itemIngredientOf(bridge);
            if (ingredient != null) gathered.add(ingredient);
        }

        // 附加输入以 count 声明份数，逐份展开后一并参与合并，保持数量语义。
        for (var additional : recipe.getRequiredAdditionalInputs()) {
            if (additional == null || additional.isEmpty()) continue;
            Ingredient ingredient = additional.ingredient();
            if (AdapterUtils.isIngredientEmpty(ingredient)) continue;
            for (int i = 0; i < additional.count(); i++) {
                gathered.add(ingredient);
            }
        }

        return mergeIngredientsIncludingCustom(gathered);
    }

    /**
     * 合并语义等价的 Ingredient 并累计份数。
     *
     * <p>与 {@link AdapterUtils#mergeIngredients(List)} 的差异在于自定义条目的处理：
     * 后者把 {@code isCustom()} 的条目视为不可比较，因而组件型材料无法合并。
     * 此处在两种条目均为自定义类型时回退到候选物品与组件的逐一比对，
     * 使组件型材料的重复出现能够归并为单条带份数的输入。</p>
     *
     * @param gathered 逐槽位收集的输入材料，可含重复项
     * @return 按语义归并计数后的输入列表
     */
    private static List<CountedIngredient> mergeIngredientsIncludingCustom(List<Ingredient> gathered) {
        List<Ingredient> keys = new ArrayList<>();
        List<Long> counts = new ArrayList<>();

        for (Ingredient ingredient : gathered) {
            if (AdapterUtils.isIngredientEmpty(ingredient)) continue;
            int matched = -1;
            for (int i = 0; i < keys.size(); i++) {
                if (sameMaterial(keys.get(i), ingredient)) {
                    matched = i;
                    break;
                }
            }
            if (matched >= 0) {
                counts.set(matched, counts.get(matched) + 1L);
            } else {
                keys.add(ingredient);
                counts.add(1L);
            }
        }

        List<CountedIngredient> merged = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            merged.add(new CountedIngredient(keys.get(i), counts.get(i)));
        }
        return List.copyOf(merged);
    }

    /**
     * 判定两个 Ingredient 是否代表同一种材料。
     *
     * <p>先交由 {@link AdapterUtils#areIngredientsEqual} 处理常规条目；该判定对自定义条目
     * 恒返回否，因此当两者均为自定义条目时，进一步比对候选物品集合：候选数量一致，
     * 且每个候选物品及其组件都能在另一方找到，即视为同一种材料。</p>
     *
     * @param a 第一个 Ingredient
     * @param b 第二个 Ingredient
     * @return 两者是否代表同一种材料
     */
    private static boolean sameMaterial(Ingredient a, Ingredient b) {
        if (AdapterUtils.areIngredientsEqual(a, b)) return true;
        if (!a.isCustom() || !b.isCustom()) return false;

        ItemStack[] stacksA = a.getItems();
        ItemStack[] stacksB = b.getItems();
        if (stacksA.length == 0 || stacksA.length != stacksB.length) return false;

        for (ItemStack stackA : stacksA) {
            boolean found = false;
            for (ItemStack stackB : stacksB) {
                if (ItemStack.isSameItem(stackA, stackB)
                        && ItemStack.isSameItemSameComponents(stackA, stackB)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /** 取出物品型桥接条目的 Ingredient；流体型或空条目返回 {@code null}。 */
    @Nullable
    private static Ingredient itemIngredientOf(@Nullable IngredientBridge bridge) {
        if (bridge == null || bridge.isEmpty()) return null;
        if (bridge.getIngredientType() != IngredientBridge.Type.ITEM) return null;
        Ingredient ingredient = bridge.getIngredient();
        return AdapterUtils.isIngredientEmpty(ingredient) ? null : ingredient;
    }

    /**
     * 构造配方所需的星能液输入。
     *
     * <p>祭坛的 {@code requiredFluid} 是逐份列出的流体需求，同一流体可能出现多条，
     * 因此按流体合并后作为合金炉的流体输入。</p>
     *
     * @param recipe 源祭坛配方
     * @return 合并后的流体输入列表；无流体需求时返回空列表
     */
    private static List<LongSizedFluidIngredient> buildInputFluids(AltarRecipe recipe) {
        List<LongSizedFluidIngredient> fluids = new ArrayList<>();
        for (FluidStack stack : recipe.getRequiredFluid()) {
            if (stack == null || stack.isEmpty() || stack.getAmount() <= 0) continue;
            fluids.add(LongSizedFluidIngredient.from(stack));
        }
        return List.copyOf(fluids);
    }

    /**
     * 构造配方模具。
     *
     * <p>第一级模具为配方所属祭坛等级对应的祭坛物品，用于把配方约束到正确的祭坛等级。</p>
     *
     * <p>第二级模具承载星座维度。祭坛的聚焦星座来自专属聚焦槽：该槽位仅接受
     * {@code #astralsorcery:attuned_crystal}（即 {@code attuned_rock_crystal} 与
     * {@code attuned_celestial_crystal}），并从物品的 {@code ATTUNED_CONSTELLATION}
     * 组件读出星座，再与 {@link AltarRecipe#getFocusConstellation()} 比对。合金炉没有
     * 等价的「玩家当前聚焦星座」输入，因此在配方要求聚焦星座时，追加一颗带该星座组件的
     * 调谐水晶石作为第二级模具，使星座限制在模具槽位上可被表达与校验。</p>
     *
     * <p>多模具配方依赖多方块结构的模具仓承载各模具槽位：普通合金炉仅暴露单个模具槽，
     * 相关配方在普通路径下不参与选择。这是二级模具语义的既定适用范围。</p>
     *
     * @param recipe 源祭坛配方
     * @return 模具列表；祭坛物品不可用时返回空列表
     */
    private static List<Ingredient> buildMolds(AltarRecipe recipe) {
        List<Ingredient> molds = new ArrayList<>();

        TileAltar.AltarType requiredType = recipe.getRequiredType();
        if (requiredType != null) {
            ItemStack altarItem = requiredType.getAltarItem();
            Ingredient altarMold = AdapterUtils.toMoldIngredient(altarItem);
            if (!AdapterUtils.isIngredientEmpty(altarMold)) molds.add(altarMold);
        }

        Ingredient constellationMold = constellationMold(recipe);
        if (constellationMold != null) molds.add(constellationMold);

        return List.copyOf(molds);
    }

    /**
     * 按配方的聚焦星座要求构造第二级模具。
     *
     * <p>模具为带 {@code ATTUNED_CONSTELLATION} 组件的调谐水晶石，组件值即配方要求的
     * 星座。模具槽位因此承载星座限制：放入的调谐水晶石必须携带同一星座才可匹配。</p>
     *
     * <p>候选物品同时覆盖 {@code attuned_rock_crystal} 与 {@code attuned_celestial_crystal}：
     * 两者同属 {@code #astralsorcery:attuned_crystal}，即 {@code TileAltar} 专属聚焦槽的准入
     * 集合，且均以 {@code ATTUNED_CONSTELLATION} 组件承载星座。模具据此按物品集合与组件
     * 共同限定，任一种调谐水晶石携带指定星座即可满足。</p>
     *
     * @param recipe 源祭坛配方
     * @return 调谐水晶石模具；配方不要求聚焦星座时返回 {@code null}
     */
    @Nullable
    private static Ingredient constellationMold(AltarRecipe recipe) {
        BaseConstellation constellation = recipe.getFocusConstellation().orElse(null);
        if (constellation == null) return null;

        List<ItemStack> candidates = new ArrayList<>(2);
        candidates.add(attunedCrystal(ItemsAS.ATTUNED_ROCK_CRYSTAL.get(), constellation));
        candidates.add(attunedCrystal(ItemsAS.ATTUNED_CELESTIAL_CRYSTAL.get(), constellation));

        Ingredient mold = Ingredient.of(candidates.stream());
        return AdapterUtils.isIngredientEmpty(mold) ? null : mold;
    }

    /**
     * 构造一颗携带指定星座组件的调谐水晶石。
     *
     * @param item 调谐水晶石物品
     * @param constellation 该水晶石承载的星座
     * @return 已写入 {@code ATTUNED_CONSTELLATION} 组件的物品栈
     */
    private static ItemStack attunedCrystal(Item item, BaseConstellation constellation) {
        ItemStack crystal = new ItemStack(item);
        crystal.set(DataComponentsAS.ATTUNED_CONSTELLATION, new AttunedConstellationComponent(constellation));
        return crystal;
    }

    /** 把已计数输入转换为匹配所需的映射形式。 */
    private static Map<Ingredient, Long> toCountMap(List<CountedIngredient> counted) {
        Map<Ingredient, Long> map = new LinkedHashMap<>();
        for (CountedIngredient entry : counted) {
            if (entry == null) continue;
            AdapterUtils.mergeIngredient(map, entry.ingredient(), entry.count());
        }
        return map;
    }

    /**
     * 解析加工耗时。
     *
     * <p>祭坛配方的 {@code duration} 单位为游戏刻，与合金炉的加工时间单位一致；
     * 非正值视为未配置并回落到默认耗时。</p>
     *
     * @param recipe 源祭坛配方
     * @return 合金炉加工耗时（刻）
     */
    private static int resolveProcessTime(AltarRecipe recipe) {
        int duration = recipe.getDuration();
        return duration > 0 ? duration : AdapterUtils.DEFAULT_PROCESS_TIME;
    }
}
