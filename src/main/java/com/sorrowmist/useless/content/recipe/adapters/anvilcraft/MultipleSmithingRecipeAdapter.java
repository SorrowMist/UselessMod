package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeFingerprint;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.dubhe.anvilcraft.api.recipe.number.ConstantValue;
import dev.dubhe.anvilcraft.api.recipe.data.ICustomDataComponent;
import dev.dubhe.anvilcraft.api.recipe.data.ItemEnchantmentsData;
import dev.dubhe.anvilcraft.api.recipe.data.MultiphaseData;
import dev.dubhe.anvilcraft.api.recipe.data.NormalDataComponent;
import dev.dubhe.anvilcraft.api.recipe.result.ResultContext;
import dev.dubhe.anvilcraft.api.recipe.result.modifier.ApplyData;
import dev.dubhe.anvilcraft.api.recipe.result.modifier.CopyData;
import dev.dubhe.anvilcraft.api.recipe.result.modifier.IResultModifier;
import dev.dubhe.anvilcraft.api.recipe.result.modifier.MergeData;
import dev.dubhe.anvilcraft.api.recipe.result.modifier.ModifyCount;
import dev.dubhe.anvilcraft.recipe.multiple.BaseMultipleToOneSmithingRecipe;
import dev.dubhe.anvilcraft.recipe.multiple.MultipleToOneSmithingRecipeInput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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

/** 转换不依赖输入物品组件的多合一锻造配方。 */
public final class MultipleSmithingRecipeAdapter implements IRecipeAdapter<BaseMultipleToOneSmithingRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.ANVILCRAFT;
    }

    @Override
    public Class<BaseMultipleToOneSmithingRecipe> getRecipeClass() {
        return BaseMultipleToOneSmithingRecipe.class;
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
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<BaseMultipleToOneSmithingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || level == null) return List.of();
        BaseMultipleToOneSmithingRecipe source = holder.value();
        Ingredient template = ingredient(source.getTemplate());
        Ingredient emberTable = Multiblock4DRecipeAdapter.mold("ember_smithing_table");
        Ingredient transcendenceTable = Multiblock4DRecipeAdapter.mold("transcendence_smithing_table");
        if (source.getTemplate().count() != 1 || template.isEmpty()
                || emberTable.isEmpty() || transcendenceTable.isEmpty()) return List.of();
        if (source.getResult().modifiers().stream().anyMatch(modifier ->
                !(modifier instanceof ModifyCount count && count.count() instanceof ConstantValue))) {
            return convertDefaultComponents(holder, level, emberTable, transcendenceTable);
        }
        Map<Ingredient, Long> materials = new LinkedHashMap<>();
        List<ItemIngredientPredicate> predicates = new ArrayList<>(source.getInputs());
        predicates.add(source.getMaterial());
        for (ItemIngredientPredicate predicate : predicates) {
            Ingredient converted = ingredient(predicate);
            if (converted.isEmpty() || predicate.count() <= 0) return List.of();
            AdapterUtils.mergeIngredient(materials, converted, predicate.count());
        }
        ItemStack result = source.getResult().getResult(ResultContext.builder(level.registryAccess(), level.getRandom(),
                source.getResult().result().getDefaultInstance()).build());
        if (result.isEmpty() || materials.isEmpty()) return List.of();
        List<CountedIngredient> inputs = materials.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue())).toList();
        ResourceLocation base = AdapterUtils.convertedId(holder.id());
        return List.of(recipe(base.withSuffix("/ember"), inputs, result, List.of(emberTable, template)),
                recipe(base.withSuffix("/transcendence"), inputs, result, List.of(transcendenceTable)));
    }

    private static Ingredient ingredient(ItemIngredientPredicate predicate) {
        if (predicate == null || predicate.items().isEmpty() || !predicate.components().alwaysMatches()
                || !predicate.subPredicates().isEmpty() || predicate.getItems().length == 0) return Ingredient.EMPTY;
        return predicate.items().get().unwrapKey().map(Ingredient::of)
                .orElseGet(() -> Ingredient.of(predicate.getItems()));
    }

    private static List<AdvancedAlloyFurnaceRecipe> convertDefaultComponents(
            RecipeHolder<BaseMultipleToOneSmithingRecipe> holder, Level level,
            Ingredient emberTable, Ingredient transcendenceTable) {
        BaseMultipleToOneSmithingRecipe source = holder.value();
        if (source.getTemplate().count() != 1 || source.getResult().modifiers().stream()
                .anyMatch(modifier -> !deterministicModifier(modifier))) return List.of();
        ItemStack template = singleDefault(source.getTemplate());
        ItemStack material = singleDefault(source.getMaterial());
        if (template.isEmpty() || material.isEmpty()) return List.of();
        List<List<ItemStack>> choices = new ArrayList<>();
        int combinations = 1;
        for (ItemIngredientPredicate predicate : source.getInputs()) {
            if (ingredient(predicate).isEmpty() || predicate.count() <= 0) return List.of();
            List<ItemStack> options = new ArrayList<>();
            for (ItemStack candidate : predicate.getItems()) {
                ItemStack stack = candidate.copyWithCount(predicate.count());
                if (stack.isComponentsPatchEmpty() && predicate.test(stack)) options.add(stack);
            }
            if (options.isEmpty() || options.size() > 256 / combinations) return List.of();
            combinations *= options.size();
            choices.add(options);
        }
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        appendDefaultVariants(holder, level, emberTable, transcendenceTable, template, material,
                choices, combinations > 1, 0, new ArrayList<>(), recipes);
        return List.copyOf(recipes);
    }

    private static void appendDefaultVariants(RecipeHolder<BaseMultipleToOneSmithingRecipe> holder, Level level,
            Ingredient emberTable, Ingredient transcendenceTable, ItemStack template, ItemStack material,
            List<List<ItemStack>> choices, boolean multiple, int index, List<ItemStack> items,
            List<AdvancedAlloyFurnaceRecipe> recipes) {
        if (index < choices.size()) {
            for (ItemStack stack : choices.get(index)) {
                // 合金炉输入无槽位顺序，候选物品不可同时满足其他输入槽的条件。
                boolean ambiguous = false;
                for (int i = 0; i < holder.value().getInputs().size(); i++) {
                    if (i != index && holder.value().getInputs().get(i).test(stack)) ambiguous = true;
                }
                if (ambiguous) continue;
                items.add(stack);
                appendDefaultVariants(holder, level, emberTable, transcendenceTable, template, material,
                        choices, multiple, index + 1, items, recipes);
                items.removeLast();
            }
            return;
        }
        BaseMultipleToOneSmithingRecipe source = holder.value();
        MultipleToOneSmithingRecipeInput input = new MultipleToOneSmithingRecipeInput(template, material, items);
        if (!source.matches(input, level)) return;
        ItemStack result = source.assemble(input, level);
        if (result.isEmpty()) return;
        Map<Ingredient, Long> materials = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(materials, DataComponentIngredient.of(true, material.copyWithCount(1)), material.getCount());
        for (ItemStack item : items) {
            AdapterUtils.mergeIngredient(materials, DataComponentIngredient.of(true, item.copyWithCount(1)), item.getCount());
        }
        List<CountedIngredient> inputs = materials.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue())).toList();
        ResourceLocation base = AdapterUtils.convertedId(holder.id());
        AdvancedAlloyFurnaceRecipe preview = recipe(base, inputs, result,
                List.of(emberTable, DataComponentIngredient.of(true, template.copyWithCount(1))));
        String variant = multiple ? "/" + AlloyFurnaceRecipeFingerprint.create(preview, level.registryAccess()) : "";
        recipes.add(recipe(base.withSuffix(variant + "/ember"), inputs, result, preview.molds()));
        ItemStack withoutTemplate = source.assemble(new MultipleToOneSmithingRecipeInput(ItemStack.EMPTY, material, items), level);
        if (ItemStack.matches(result, withoutTemplate)) {
            recipes.add(recipe(base.withSuffix(variant + "/transcendence"), inputs, result, List.of(transcendenceTable)));
        }
    }

    private static ItemStack singleDefault(ItemIngredientPredicate predicate) {
        if (ingredient(predicate).isEmpty() || predicate.count() <= 0 || predicate.getItems().length != 1) {
            return ItemStack.EMPTY;
        }
        ItemStack candidate = predicate.getItems()[0].copyWithCount(predicate.count());
        return candidate.isComponentsPatchEmpty() && predicate.test(candidate) ? candidate : ItemStack.EMPTY;
    }

    private static boolean deterministicModifier(IResultModifier modifier) {
        if (modifier instanceof ModifyCount count) return count.count() instanceof ConstantValue;
        if (modifier instanceof ApplyData) return true;
        if (modifier instanceof CopyData copy) return copy.types().stream().allMatch(MultipleSmithingRecipeAdapter::knownData);
        if (modifier instanceof MergeData merge) return merge.types().stream().allMatch(MultipleSmithingRecipeAdapter::knownData);
        return false;
    }

    static boolean knownData(ICustomDataComponent<?> data) {
        return data instanceof NormalDataComponent<?> || data instanceof ItemEnchantmentsData || data instanceof MultiphaseData;
    }

    private static AdvancedAlloyFurnaceRecipe recipe(ResourceLocation id, List<CountedIngredient> inputs,
            ItemStack output, List<Ingredient> molds) {
        return new AdvancedAlloyFurnaceRecipe(id, inputs, List.of(), List.of(), List.of(output.copy()),
                List.of(), List.of(), AdapterUtils.energyForProcessTime(AdapterUtils.DEFAULT_PROCESS_TIME),
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY, 0, molds, AlloyFurnaceMode.NORMAL);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<BaseMultipleToOneSmithingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mold == null
                || !Multiblock4DRecipeAdapter.mold("transcendence_smithing_table").test(mold)) return List.of();
        List<RecipeHolder<BaseMultipleToOneSmithingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<?> candidate : level.getRecipeManager().getRecipes()) {
            if (!(candidate.value() instanceof BaseMultipleToOneSmithingRecipe)) continue;
            RecipeHolder<BaseMultipleToOneSmithingRecipe> typed = (RecipeHolder<BaseMultipleToOneSmithingRecipe>) candidate;
            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(typed, level);
            if (converted.isEmpty()) continue;
            for (AdvancedAlloyFurnaceRecipe recipe : converted) {
                Map<Ingredient, Long> required = new LinkedHashMap<>();
                for (CountedIngredient input : recipe.inputs()) {
                    AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
                }
                if (AdapterUtils.matchesRequired(mergedInputs, required)) {
                    matches.add(typed);
                    break;
                }
            }
        }
        return List.copyOf(matches);
    }
}
