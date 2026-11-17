package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.anvilcraft.lib.v2.util.predicate.BlockStatePredicate;
import dev.anvilcraft.lib.v2.util.predicate.ChanceBlockState;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.dubhe.anvilcraft.recipe.anvil.procedural.ProceduralProcessRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.procedural.ProceduralProcessStep;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.AbstractProcessRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.BlockCompressRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.BlockProcessingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.ItemInjectRecipe;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 将可静态验证的序列加工步骤合并为完整配方。 */
public final class ProceduralRecipeAdapter implements IRecipeAdapter<ProceduralProcessRecipe> {
    private static final Set<String> PROCESSORS = Set.of("heater", "burning_heater", "corrupted_beacon", "neutron_irradiator");

    @Override
    public String sourceId() {
        return RecipeSourceIds.ANVILCRAFT;
    }

    @Override
    public Class<ProceduralProcessRecipe> getRecipeClass() {
        return ProceduralProcessRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && mold.is(Items.ANVIL);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ProceduralProcessRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        ProceduralProcessRecipe source = holder.value();
        if (source.getLoop() < 1 || source.getLoop() > 1000 || source.getSteps().isEmpty()
                || source.getLoop() > 1 && source.getMultiLoopFirstStep().isEmpty()) return List.of();
        BlockStatePredicate initial = source.getInitialBlock();
        Ingredient initialItem = Multiblock4DRecipeAdapter.blockIngredient(initial);
        if (initialItem.isEmpty() || initial.getBlocks().size() != 1) return List.of();
        Block current = initial.getBlocks().stream().findFirst().orElseThrow().value();
        Map<Ingredient, Long> materials = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(materials, initialItem, 1L);
        List<Ingredient> molds = new ArrayList<>();
        molds.add(Ingredient.of(Items.ANVIL));
        Set<List<Item>> seenProcessors = new HashSet<>();
        int stepCount = 0;
        for (int loop = 0; loop < source.getLoop(); loop++) {
            for (int index = 0; index < source.getSteps().size(); index++) {
                ProceduralProcessStep wrapper = loop > 0 && index == 0
                        ? source.getMultiLoopFirstStep().orElseThrow() : source.getSteps().get(index);
                if (!(wrapper.getContent() instanceof AbstractProcessRecipe<?> step)
                        || !step.getResultItems().isEmpty() || !step.getCatalysts().isEmpty()) return List.of();
                List<BlockStatePredicate> blocks = step.getInputBlocks();
                int currentIndex = -1;
                for (int i = 0; i < blocks.size(); i++) {
                    BlockStatePredicate predicate = blocks.get(i);
                    if (predicate.getProperties().isEmpty() && !predicate.requiresBlockEntity()
                            && predicate.getBlocks().contains(current.builtInRegistryHolder())) {
                        currentIndex = i;
                        break;
                    }
                }
                if (currentIndex < 0) return List.of();
                if (step instanceof BlockProcessingRecipe) {
                    if (currentIndex != 0 || blocks.size() != 2) return List.of();
                    BlockStatePredicate processor = blocks.get(1);
                    if (processor.requiresBlockEntity() || processor.getBlocks().size() == 0) return List.of();
                    List<Item> options = new ArrayList<>();
                    for (Holder<Block> block : processor.getBlocks()) {
                        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block.value());
                        if (id == null || !id.getNamespace().equals("anvilcraft") || !PROCESSORS.contains(id.getPath())
                                || block.value().asItem() == Items.AIR) return List.of();
                        options.add(block.value().asItem());
                    }
                    List<Item> key = options.stream().distinct().sorted((a, b) -> BuiltInRegistries.ITEM.getKey(a)
                            .compareTo(BuiltInRegistries.ITEM.getKey(b))).toList();
                    if (seenProcessors.add(key)) molds.add(Ingredient.of(key.toArray(Item[]::new)));
                } else if (step instanceof BlockCompressRecipe) {
                    for (int i = 0; i < blocks.size(); i++) {
                        if (i == currentIndex) continue;
                        Ingredient material = Multiblock4DRecipeAdapter.blockIngredient(blocks.get(i));
                        if (material.isEmpty()) return List.of();
                        AdapterUtils.mergeIngredient(materials, material, 1L);
                    }
                } else if (step instanceof ItemInjectRecipe) {
                    if (blocks.size() != 1) return List.of();
                    for (ItemIngredientPredicate predicate : step.getInputItems()) {
                        if (predicate.items().isEmpty() || !predicate.components().alwaysMatches()
                                || !predicate.subPredicates().isEmpty() || predicate.count() <= 0
                                || predicate.getItems().length == 0) return List.of();
                        Ingredient material = predicate.items().get().unwrapKey().map(Ingredient::of)
                                .orElseGet(() -> Ingredient.of(predicate.getItems()));
                        AdapterUtils.mergeIngredient(materials, material, predicate.count());
                    }
                } else {
                    return List.of();
                }
                if (step.getResultBlocks().size() != 1 || !certain(step.getResultBlocks().getFirst())) return List.of();
                current = step.getResultBlocks().getFirst().state().getBlock();
                stepCount++;
            }
        }
        Block wip = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("anvilcraft", "wip_block"));
        if (current == wip) {
            ChanceBlockState result = source.getResultBlock();
            if (!certain(result)) return List.of();
            current = result.state().getBlock();
        }
        if (current.asItem() == Items.AIR) return List.of();
        try {
            int processTime = Math.multiplyExact(AdapterUtils.DEFAULT_PROCESS_TIME, stepCount);
            long energy = AdapterUtils.energyForProcessTime(processTime);
            return List.of(new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId(holder.id()),
                    materials.entrySet().stream().map(entry -> new CountedIngredient(entry.getKey(), entry.getValue())).toList(),
                    List.of(), List.of(), List.of(new ItemStack(current)), List.of(), List.of(),
                    energy, processTime, Ingredient.EMPTY, 0,
                    molds, AlloyFurnaceMode.NORMAL));
        } catch (ArithmeticException exception) {
            return List.of();
        }
    }

    private static boolean certain(ChanceBlockState result) {
        return result != null && result.nbt().isEmpty() && result.chance() instanceof ConstantValue chance
                && chance.value() == 1.0f;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<ProceduralProcessRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<ProceduralProcessRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<?> candidate : level.getRecipeManager().getRecipes()) {
            if (!(candidate.value() instanceof ProceduralProcessRecipe source)) continue;
            Ingredient initial = Multiblock4DRecipeAdapter.blockIngredient(source.getInitialBlock());
            if (!initial.isEmpty() && AdapterUtils.matchesRequired(mergedInputs, Map.of(initial, 1L))) {
                matches.add((RecipeHolder<ProceduralProcessRecipe>) candidate);
            }
        }
        return List.copyOf(matches);
    }
}
