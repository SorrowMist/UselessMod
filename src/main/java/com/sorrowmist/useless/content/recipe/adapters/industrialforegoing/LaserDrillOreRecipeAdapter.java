package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.buuz135.industrial.config.machine.resourceproduction.LaserDrillConfig;
import com.buuz135.industrial.config.machine.resourceproduction.OreLaserBaseConfig;
import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.module.ModuleResourceProduction;
import com.buuz135.industrial.recipe.LaserDrillOreRecipe;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Industrial Foregoing 矿物镭射钻配方适配器。
 *
 * <p>镭射钻用透镜颜色筛选、再加权随机出一种矿；合金炉是确定性匹配。因此这里按「透镜 + 实体需求」
 * 分组，每组合成一条配方：输入 1000mB 水，模具为矿物镭射基座 + 该组透镜（需要实体的组再加一个刷怪蛋
 * 模具），一次性产出该组所有可能的矿物。</p>
 *
 * <p>合金炉单机只支持一个模具，所以这些多模具配方只能在多方块万象炉的模具集线器里使用。</p>
 */
public final class LaserDrillOreRecipeAdapter
        implements IRecipeAdapter<IndustrialForegoingSyntheticRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WATER_AMOUNT = 1000;

    private volatile Cached cached;

    @Override
    public String sourceId() {
        return RecipeSourceIds.INDUSTRIAL_FOREGOING;
    }

    @Override
    public Class<IndustrialForegoingSyntheticRecipe> getRecipeClass() {
        return IndustrialForegoingSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModuleResourceProduction.ORE_LASER_BASE.getBlock());
    }

    @Override
    public List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();
        RecipeManager manager = level.getRecipeManager();
        if (manager == null) return List.of();

        Cached snapshot = cached;
        if (snapshot != null && snapshot.manager() == manager) return snapshot.recipes();
        synchronized (this) {
            Cached current = cached;
            if (current != null && current.manager() == manager) return current.recipes();
            List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> built = build(manager);
            cached = new Cached(manager, built);
            return built;
        }
    }

    @SuppressWarnings("unchecked")
    private List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> build(RecipeManager manager) {
        RecipeType<LaserDrillOreRecipe> type =
                (RecipeType<LaserDrillOreRecipe>) ModuleCore.LASER_DRILL_TYPE.get();

        Map<String, Group> groups = new LinkedHashMap<>();
        for (RecipeHolder<LaserDrillOreRecipe> holder : manager.getAllRecipesFor(type)) {
            LaserDrillOreRecipe source = holder == null ? null : holder.value();
            if (source == null) continue;

            String lensKey = LaserDrillAdapterSupport.ingredientKey(source.catalyst);
            if (lensKey.isEmpty()) continue;

            ItemStack output = LaserDrillAdapterSupport.representativeOutput(source.output);
            // 标签在当前整合包里没有内容时跳过该配方
            if (output.isEmpty()) continue;

            EntityType<?> entity = LaserDrillAdapterSupport.requiredEntity(source.entityData);
            String entityKey = entity == null ? "" : String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity));
            groups.computeIfAbsent(lensKey + "|" + entityKey, key -> new Group(source.catalyst, entity))
                    .add(output);
        }

        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>(groups.size());
        for (Map.Entry<String, Group> entry : groups.entrySet()) {
            AdvancedAlloyFurnaceRecipe recipe = convertGroup(entry.getKey(), entry.getValue());
            if (recipe != null) recipes.add(recipe);
        }
        return IndustrialForegoingRecipeAdapterUtils.holders(recipes);
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe convertGroup(String groupKey, Group group) {
        List<ItemStack> outputs = group.outputs();
        if (outputs.isEmpty()) return null;
        if (outputs.size() > AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_COUNT) {
            LOGGER.warn("Laser drill lens group {} produces {} results, keeping the first {}",
                    groupKey, outputs.size(), AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_COUNT);
            outputs = outputs.subList(0, AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_COUNT);
        }

        Ingredient baseMold = AdapterUtils.toMoldIngredient(getMoldItem());
        if (baseMold.isEmpty()) return null;
        List<Ingredient> molds = new ArrayList<>(3);
        molds.add(baseMold);
        molds.add(group.catalyst());
        Ingredient spawnEgg = LaserDrillAdapterSupport.spawnEggMold(group.entity());
        if (!spawnEgg.isEmpty()) molds.add(spawnEgg);

        int processTime = IndustrialForegoingRecipeAdapterUtils.positive(OreLaserBaseConfig.maxProgress);
        long energy = IndustrialForegoingRecipeAdapterUtils.energyPerTick(
                LaserDrillConfig.powerPerOperation, processTime);

        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath(RecipeSourceIds.INDUSTRIAL_FOREGOING,
                        "laser_drill_ore_" + LaserDrillAdapterSupport.sanitize(groupKey)),
                List.of(),
                List.of(LongSizedFluidIngredient.from(new FluidStack(Fluids.WATER, WATER_AMOUNT))),
                List.of(),
                List.copyOf(outputs),
                List.of(),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                List.copyOf(molds),
                AlloyFurnaceMode.NORMAL);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<IndustrialForegoingSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) return List.of();
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<IndustrialForegoingSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            if (IndustrialForegoingRecipeAdapterUtils.matches(
                    holder.value().convertedRecipe(), mergedInputs, mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    private record Cached(RecipeManager manager,
                          List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> recipes) {
    }

    /** One lens colour (plus optional entity requirement) and every result it can produce. */
    private static final class Group {
        private final Ingredient catalyst;
        private final EntityType<?> entity;
        private final List<ItemStack> outputs = new ArrayList<>();

        private Group(Ingredient catalyst, @Nullable EntityType<?> entity) {
            this.catalyst = catalyst;
            this.entity = entity;
        }

        private Ingredient catalyst() {
            return catalyst;
        }

        @Nullable
        private EntityType<?> entity() {
            return entity;
        }

        private void add(ItemStack stack) {
            for (ItemStack existing : outputs) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    existing.grow(stack.getCount());
                    return;
                }
            }
            outputs.add(stack);
        }

        private List<ItemStack> outputs() {
            List<ItemStack> sorted = new ArrayList<>(outputs);
            sorted.sort(Comparator.comparing(stack ->
                    String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()))));
            return sorted;
        }
    }
}
