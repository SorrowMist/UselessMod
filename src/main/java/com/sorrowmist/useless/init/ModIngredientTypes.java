package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.enderio.SoulVialSetIngredient;
import com.sorrowmist.useless.content.recipe.adapters.hostilenetworks.DataModelRangeIngredient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** NeoForge custom ingredient types used by generated compatibility recipes. */
public final class ModIngredientTypes {
    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, UselessMod.MODID);

    public static final DeferredHolder<IngredientType<?>, IngredientType<SoulVialSetIngredient>>
            SOUL_VIAL_SET = INGREDIENT_TYPES.register("soul_vial_set", () -> SoulVialSetIngredient.TYPE);

    /**
     * 按数据区间匹配 HNN 数据模型的原料。
     *
     * <p>{@code DataModelRangeIngredient} 的类层级引用了 Placebo 的 {@code DynamicHolder}，
     * 而 Placebo 只随 Hostile Neural Networks 一起出现。若在 HNN 缺席时仍然注册这个类型，
     * {@code DeferredRegister} 的 supplier 会在 RegisterEvent 派发时加载该类并抛出
     * {@code NoClassDefFoundError: dev/shadowsoffire/placebo/codec/CodecProvider}，
     * 整个 RegisterEvent 派发随之失败，NeoForge 把本模组标记为 broken mod state 并崩溃。
     *
     * <p>因此注册本身也必须放在加载检查之后：HNN 缺席时该字段为 {@code null}，
     * supplier 根本不会被创建，HNN 与 Placebo 的类永远不会被解析。
     * 使用方见 {@code DataModelRangeIngredient#getType()}。
     */
    @Nullable
    public static final DeferredHolder<IngredientType<?>, IngredientType<DataModelRangeIngredient>>
            DATA_MODEL_RANGE = ModList.get().isLoaded(RecipeSourceIds.HOSTILE_NETWORKS)
                    ? INGREDIENT_TYPES.register("data_model_range", () -> DataModelRangeIngredient.TYPE)
                    : null;

    private ModIngredientTypes() {
    }
}
