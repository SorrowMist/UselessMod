package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.adapters.enderio.SoulVialSetIngredient;
import com.sorrowmist.useless.content.recipe.adapters.hostilenetworks.DataModelRangeIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** NeoForge custom ingredient types used by generated compatibility recipes. */
public final class ModIngredientTypes {
    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, UselessMod.MODID);

    public static final DeferredHolder<IngredientType<?>, IngredientType<SoulVialSetIngredient>>
            SOUL_VIAL_SET = INGREDIENT_TYPES.register("soul_vial_set", () -> SoulVialSetIngredient.TYPE);

    /**
     * 按数据区间匹配 HNN 数据模型的原料。
     *
     * <p>该类型的编解码只使用原版类型，静态初始化不引用 HNN 的类，
     * 因此 HNN 未安装时注册也不会失败。
     */
    public static final DeferredHolder<IngredientType<?>, IngredientType<DataModelRangeIngredient>>
            DATA_MODEL_RANGE = INGREDIENT_TYPES.register("data_model_range", () -> DataModelRangeIngredient.TYPE);

    private ModIngredientTypes() {
    }
}
