package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.dubhe.anvilcraft.api.recipe.result.modifier.ChangeDataType;
import dev.dubhe.anvilcraft.api.recipe.result.modifier.RemoveAttribute;
import dev.dubhe.anvilcraft.api.recipe.result.modifier.RemoveData;
import dev.dubhe.anvilcraft.api.recipe.slot.RecipeInputSlot;
import dev.dubhe.anvilcraft.recipe.frost.CustomFrostMaterialPredicate;
import dev.dubhe.anvilcraft.recipe.frost.DeformationRecipe;
import dev.dubhe.anvilcraft.recipe.frost.EmptyFrostMaterialPredicate;
import dev.dubhe.anvilcraft.recipe.frost.FrostSmithingOption;
import dev.dubhe.anvilcraft.recipe.frost.FrostSmithingRecipeInput;
import dev.dubhe.anvilcraft.recipe.frost.IFrostSmithingRecipe;
import dev.dubhe.anvilcraft.recipe.frost.PermutationRecipe;
import dev.dubhe.anvilcraft.recipe.frost.RepairMaterialFrostMaterialPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 转换默认装备组件可确定产物的浮霜锻造配方。 */
public final class FrostSmithingRecipeAdapter implements IRecipeAdapter<IFrostSmithingRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.ANVILCRAFT;
    }

    @Override
    public Class<IFrostSmithingRecipe> getRecipeClass() {
        return IFrostSmithingRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && Multiblock4DRecipeAdapter.mold("transcendence_smithing_table").test(mold);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<IFrostSmithingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || level == null) return List.of();
        IFrostSmithingRecipe source = holder.value();
        if (source instanceof PermutationRecipe permutation && permutation.input().count() != 1) return List.of();
        ItemIngredientPredicate template = source instanceof DeformationRecipe deformation ? deformation.template()
                : source instanceof PermutationRecipe permutation ? permutation.template() : null;
        if (template == null || template.items().isEmpty() || !template.components().alwaysMatches()
                || !template.subPredicates().isEmpty() || template.count() != 1 || template.getItems().length == 0) {
            return List.of();
        }
        Ingredient templateMold = template.items().get().unwrapKey().map(Ingredient::of)
                .orElseGet(() -> Ingredient.of(template.getItems()));
        Ingredient frostTable = Multiblock4DRecipeAdapter.mold("frost_smithing_table");
        Ingredient transcendenceTable = Multiblock4DRecipeAdapter.mold("transcendence_smithing_table");
        if (frostTable.isEmpty() || transcendenceTable.isEmpty()) return List.of();
        Map<ResourceLocation, AdvancedAlloyFurnaceRecipe> converted = new LinkedHashMap<>();
        for (ItemStack equipment : source.possibleInputs()) {
            if (equipment.isEmpty() || !equipment.isComponentsPatchEmpty() || !source.isInput(equipment)) continue;
            for (FrostSmithingOption option : source.options(equipment)) {
                // 按上游 IFrostSmithingRecipe.assemble 计算默认组件产物，仅接受确定性修饰。
                if (option.result().modifiers().stream().anyMatch(modifier ->
                        !(modifier instanceof RemoveData) && !(modifier instanceof RemoveAttribute)
                                && !(modifier instanceof ChangeDataType<?> change
                                && change.input().equals(RecipeInputSlot.input(0))
                                && MultipleSmithingRecipeAdapter.knownData(change.dest())))) continue;
                List<ItemStack> candidates = new ArrayList<>();
                if (option.material() instanceof EmptyFrostMaterialPredicate) {
                    candidates.add(ItemStack.EMPTY);
                } else if (option.material() instanceof CustomFrostMaterialPredicate custom) {
                    ItemIngredientPredicate predicate = custom.predicate();
                    if (predicate.items().isEmpty() || !predicate.components().alwaysMatches()
                            || !predicate.subPredicates().isEmpty() || predicate.count() <= 0) continue;
                    for (ItemStack stack : predicate.getItems()) candidates.add(stack.copyWithCount(predicate.count()));
                } else if (option.material() instanceof RepairMaterialFrostMaterialPredicate) {
                    for (Item item : BuiltInRegistries.ITEM) {
                        if (item != Items.AIR) candidates.add(new ItemStack(item, 64));
                    }
                } else {
                    continue;
                }
                for (ItemStack material : candidates) {
                    FrostSmithingRecipeInput input = new FrostSmithingRecipeInput(template.getItems()[0], equipment, material);
                    if (!option.material().test(source, input)) continue;
                    List<FrostSmithingOption> available = source.options(input);
                    int index = available.indexOf(option);
                    if (index < 0) continue;
                    int cost = source.materialCost(input, index);
                    if (!material.isEmpty() && cost <= 0) continue;
                    ItemStack result = source.assemble(index, input, level);
                    if (result.isEmpty()) continue;
                    List<CountedIngredient> inputs = new ArrayList<>();
                    inputs.add(new CountedIngredient(DataComponentIngredient.of(true, equipment.copyWithCount(1)), 1));
                    if (cost > 0) inputs.add(new CountedIngredient(Ingredient.of(material.getItem()), cost));
                    ResourceLocation base = AdapterUtils.convertedId(holder.id());
                    ResourceLocation equipmentId = BuiltInRegistries.ITEM.getKey(equipment.getItem());
                    ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
                    ResourceLocation materialId = BuiltInRegistries.ITEM.getKey(material.getItem());
                    String suffix = "/" + equipmentId.getNamespace() + "/" + equipmentId.getPath()
                            + "/" + resultId.getNamespace() + "/" + resultId.getPath()
                            + "/" + materialId.getNamespace() + "/" + materialId.getPath() + "/" + cost;
                    ResourceLocation frostId = base.withSuffix(suffix + "/frost");
                    ResourceLocation transcendenceId = base.withSuffix(suffix + "/transcendence");
                    converted.putIfAbsent(frostId, recipe(frostId, inputs, result, List.of(frostTable, templateMold)));
                    converted.putIfAbsent(transcendenceId, recipe(transcendenceId, inputs, result, List.of(transcendenceTable)));
                }
            }
        }
        return List.copyOf(converted.values());
    }

    private static AdvancedAlloyFurnaceRecipe recipe(ResourceLocation id, List<CountedIngredient> inputs,
            ItemStack result, List<Ingredient> molds) {
        return new AdvancedAlloyFurnaceRecipe(id, inputs, List.of(), List.of(), List.of(result.copy()),
                List.of(), List.of(), AdapterUtils.energyForProcessTime(AdapterUtils.DEFAULT_PROCESS_TIME),
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY, 0, molds, AlloyFurnaceMode.NORMAL);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<IFrostSmithingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<IFrostSmithingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<?> candidate : level.getRecipeManager().getRecipes()) {
            if (!(candidate.value() instanceof IFrostSmithingRecipe source)) continue;
            for (ItemStack equipment : source.possibleInputs()) {
                if (equipment.isEmpty()) continue;
                Ingredient exact = DataComponentIngredient.of(true, equipment.copyWithCount(1));
                if (AdapterUtils.matchesRequired(mergedInputs, Map.of(exact, 1L))) {
                    matches.add((RecipeHolder<IFrostSmithingRecipe>) candidate);
                    break;
                }
            }
        }
        return List.copyOf(matches);
    }
}
