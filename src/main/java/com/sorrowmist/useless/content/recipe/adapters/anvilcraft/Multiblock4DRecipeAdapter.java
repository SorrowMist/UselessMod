package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.anvilcraft.lib.v2.multiblock.dynamic.definition.DefinitionSerialization;
import dev.anvilcraft.lib.v2.multiblock.dynamic.definition.MultiblockDefinition;
import dev.anvilcraft.lib.v2.util.predicate.BlockStatePredicate;
import dev.dubhe.anvilcraft.recipe.multiblock.Multiblock4DRecipe;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockUtil;
import dev.dubhe.anvilcraft.api.block.BlockPlacementRules;
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
import net.minecraft.world.level.block.SlabBlock;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将四维多方块合成的各阶段材料合并为一次加工。 */
public final class Multiblock4DRecipeAdapter implements IRecipeAdapter<Multiblock4DRecipe> {
    static final int TRANSPARENT_CRAFTING_TABLE_MOLD_COUNT = 9;

    @Override
    public String sourceId() {
        return RecipeSourceIds.ANVILCRAFT;
    }

    @Override
    public Class<Multiblock4DRecipe> getRecipeClass() {
        return Multiblock4DRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<Multiblock4DRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        Multiblock4DRecipe source = holder.value();
        if (source.getDefinitions().isEmpty() || source.getResult().isEmpty()) return List.of();

        Map<Ingredient, Long> materials = new LinkedHashMap<>();
        for (MultiblockDefinition definition : source.getDefinitions()) {
            DefinitionSerialization serialization = DefinitionSerialization.fromDefinition(definition);
            for (String[] layer : serialization.grid()) {
                for (String row : layer) {
                    for (int i = 0; i < row.length(); i++) {
                        char symbol = row.charAt(i);
                        if (symbol == ' ') continue;
                        BlockStatePredicate predicate = serialization.mapping().get(symbol);
                        if (!addBlockMaterial(materials, predicate, level)) return List.of();
                    }
                }
            }
        }
        List<Ingredient> molds = multiblockMolds("spacetime_supercomputer");
        if (materials.isEmpty() || molds.stream().anyMatch(Ingredient::isEmpty)) return List.of();
        List<CountedIngredient> inputs = materials.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), inputs, List.of(), List.of(),
                List.of(source.getResult().copy()), List.of(), List.of(),
                AdapterUtils.energyForProcessTime(AdapterUtils.DEFAULT_PROCESS_TIME),
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY, 0, molds, AlloyFurnaceMode.NORMAL));
    }

    static Ingredient mold(String path) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("anvilcraft", path));
        return item == Items.AIR ? Ingredient.EMPTY : Ingredient.of(item);
    }

    static List<Ingredient> multiblockMolds(String... additionalMolds) {
        List<Ingredient> molds = new ArrayList<>(List.of(mold("giant_anvil")));
        Ingredient craftingTable = mold("transparent_crafting_table");
        for (int count = 0; count < TRANSPARENT_CRAFTING_TABLE_MOLD_COUNT; count++) {
            molds.add(craftingTable);
        }
        for (String path : additionalMolds) molds.add(mold(path));
        return List.copyOf(molds);
    }

    static Ingredient blockIngredient(@Nullable BlockStatePredicate predicate) {
        if (predicate == null || predicate.requiresBlockEntity() || !predicate.getProperties().isEmpty()) {
            return Ingredient.EMPTY;
        }
        List<ItemStack> items = new ArrayList<>();
        for (Holder<Block> block : predicate.getBlocks()) {
            Item item = block.value().asItem();
            if (item == Items.AIR) return Ingredient.EMPTY;
            items.add(new ItemStack(item));
        }
        return items.isEmpty() ? Ingredient.EMPTY : Ingredient.of(items.toArray(ItemStack[]::new));
    }

    static boolean addBlockMaterial(Map<Ingredient, Long> materials, @Nullable BlockStatePredicate predicate, Level level) {
        if (predicate == null || predicate.requiresBlockEntity()) return false;
        if (predicate.getProperties().isEmpty()) {
            Ingredient ingredient = blockIngredient(predicate);
            if (ingredient.isEmpty()) return false;
            AdapterUtils.mergeIngredient(materials, ingredient, 1);
            return true;
        }
        if (predicate.getBlocks().size() != 1 || predicate.getProperties().size() != 1) return false;
        Block block = predicate.getBlocks().stream().findFirst().orElseThrow().value();
        for (var matcher : predicate.getProperties().getFirst()) {
            if (!(matcher.valueMatcher() instanceof BlockStatePredicate.ExactMatcher exact)) return false;
            if (!List.of("facing", "axis", "half", "shape", "waterlogged", "type").contains(matcher.name())) return false;
            if (matcher.name().equals("waterlogged") && !exact.value().equals("false")) return false;
            if (matcher.name().equals("type") && !(block instanceof SlabBlock)) return false;
        }
        // 按铁砧工艺 MultiblockUtil 的放置规则计算材料，保留双台阶等状态的数量要求。
        var state = MultiblockUtil.getDefaultState(predicate);
        var stacks = BlockPlacementRules.getPlacementIngredients(level == null ? null : level.registryAccess(), state);
        if (stacks.isEmpty()) return false;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) return false;
            AdapterUtils.mergeIngredient(materials, Ingredient.of(stack.copyWithCount(1)), stack.getCount());
        }
        return true;
    }
}
