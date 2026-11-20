package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.anvilcraft.lib.v2.multiblock.dynamic.definition.DefinitionSerialization;
import dev.anvilcraft.lib.v2.util.predicate.BlockStatePredicate;
import dev.dubhe.anvilcraft.block.item.SimpleMultiPartBlockItem;
import dev.dubhe.anvilcraft.block.item.FlexibleMultiPartBlockItem;
import dev.dubhe.anvilcraft.block.item.LargeCakeBlockItem;
import dev.dubhe.anvilcraft.block.LargeCakeBlock;
import dev.dubhe.anvilcraft.block.DeflectionRingBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.block.multipart.FlexibleMultiPartBlock;
import dev.dubhe.anvilcraft.block.state.IFlexibleMultiPartBlockState;
import dev.dubhe.anvilcraft.block.multipart.SimpleMultiPartBlock;
import dev.dubhe.anvilcraft.block.state.ISimpleMultiPartBlockState;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockConversionRecipe;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 转换不包含方块实体数据的多方块转换配方。 */
public final class MultiblockConversionRecipeAdapter implements IRecipeAdapter<MultiblockConversionRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.ANVILCRAFT;
    }

    @Override
    public Class<MultiblockConversionRecipe> getRecipeClass() {
        return MultiblockConversionRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<MultiblockConversionRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().getModifySpawnerAction().isPresent()) {
            return List.of();
        }
        DefinitionSerialization input = DefinitionSerialization.fromDefinition(holder.value().getInputPattern());
        DefinitionSerialization output = DefinitionSerialization.fromDefinition(holder.value().getOutputPattern());
        Map<Ingredient, Long> materials = new LinkedHashMap<>();
        for (String[] layer : input.grid()) {
            for (String row : layer) {
                for (int i = 0; i < row.length(); i++) {
                    char symbol = row.charAt(i);
                    if (symbol == ' ') continue;
                    if (!Multiblock4DRecipeAdapter.addBlockMaterial(materials, input.mapping().get(symbol), level)) {
                        return List.of();
                    }
                }
            }
        }
        Item multipart = singleMultipartOutput(output);
        Map<Item, Integer> results = new LinkedHashMap<>();
        if (multipart != Items.AIR) {
            results.put(multipart, 1);
        } else {
            for (String[] layer : output.grid()) {
                for (String row : layer) {
                    for (int i = 0; i < row.length(); i++) {
                        char symbol = row.charAt(i);
                        if (symbol == ' ') continue;
                        BlockStatePredicate predicate = output.mapping().get(symbol);
                        if (predicate == null || predicate.requiresBlockEntity() || !predicate.getProperties().isEmpty()
                                || predicate.getBlocks().size() != 1) return List.of();
                        Block block = predicate.getBlocks().stream().findFirst().orElseThrow().value();
                        if (block instanceof SimpleMultiPartBlock<?> || block instanceof FlexibleMultiPartBlock<?, ?, ?>
                                || block instanceof LargeCakeBlock) return List.of();
                        Item item = block.asItem();
                        if (item == Items.AIR) return List.of();
                        results.merge(item, 1, Math::addExact);
                    }
                }
            }
        }
        List<Ingredient> molds = Multiblock4DRecipeAdapter.multiblockMolds();
        if (materials.isEmpty() || results.isEmpty() || molds.stream().anyMatch(Ingredient::isEmpty)) return List.of();
        return List.of(new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId(holder.id()),
                materials.entrySet().stream().map(entry -> new CountedIngredient(entry.getKey(), entry.getValue())).toList(),
                List.of(), List.of(), results.entrySet().stream()
                        .map(entry -> new ItemStack(entry.getKey(), entry.getValue())).toList(), List.of(), List.of(),
                AdapterUtils.energyForProcessTime(AdapterUtils.DEFAULT_PROCESS_TIME),
                AdapterUtils.DEFAULT_PROCESS_TIME, Ingredient.EMPTY, 0,
                molds, AlloyFurnaceMode.NORMAL));
    }

    private static Item singleMultipartOutput(DefinitionSerialization output) {
        String[][] grid = output.grid();
        Block multipart = null;
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (int y = 0; y < grid.length; y++) {
            for (int z = 0; z < grid[y].length; z++) {
                String row = grid[y][z];
                for (int x = 0; x < row.length(); x++) {
                    if (row.charAt(x) == ' ') continue;
                    BlockStatePredicate predicate = output.mapping().get(row.charAt(x));
                    if (predicate == null || predicate.requiresBlockEntity() || predicate.getBlocks().size() != 1) {
                        return Items.AIR;
                    }
                    BlockState state = MultiblockUtil.getDefaultState(predicate);
                    Block block = state.getBlock();
                    if (!(block instanceof SimpleMultiPartBlock<?> && block.asItem() instanceof SimpleMultiPartBlockItem<?>)
                            && !(block instanceof FlexibleMultiPartBlock<?, ?, ?> && block.asItem() instanceof FlexibleMultiPartBlockItem<?, ?, ?>)
                            && !(block instanceof LargeCakeBlock && block.asItem() instanceof LargeCakeBlockItem)) return Items.AIR;
                    if (multipart != null && multipart != block) return Items.AIR;
                    multipart = block;
                    states.put(new BlockPos(x, y, z), state);
                }
            }
        }
        if (multipart instanceof SimpleMultiPartBlock<?> simple && isCompleteMultipart(simple, states)) return multipart.asItem();
        if (multipart instanceof FlexibleMultiPartBlock<?, ?, ?> flexible && isCompleteFlexible(flexible, states)) return multipart.asItem();
        if (multipart instanceof LargeCakeBlock && isCompleteCake(states)) return multipart.asItem();
        return Items.AIR;
    }

    private static <P extends Enum<P> & IFlexibleMultiPartBlockState<P, E>, T extends Property<E>, E extends Comparable<E>>
            boolean isCompleteFlexible(FlexibleMultiPartBlock<P, T, E> block, Map<BlockPos, BlockState> states) {
        if (states.size() != block.getParts().length) return false;
        Set<P> parts = new HashSet<>();
        BlockPos origin = null;
        BlockState base = null;
        for (Map.Entry<BlockPos, BlockState> entry : states.entrySet()) {
            BlockState state = entry.getValue();
            P part = state.getValue(block.getPart());
            if (!parts.add(part)) return false;
            BlockState normalized = block.placedState(block.getParts()[0], state);
            BlockState placement = block.defaultBlockState().setValue(block.getAdditionalProperty(),
                    state.getValue(block.getAdditionalProperty()));
            // 上游偏转环配方显式指定未过载状态，该状态由电力网络维护，不属于物品数据。
            if (block instanceof DeflectionRingBlock) placement = placement.setValue(DeflectionRingBlock.OVERLOAD, false);
            BlockState expected = block.placedState(block.getParts()[0], placement);
            if (!normalized.equals(expected)) return false;
            if (base != null && !base.equals(normalized)) return false;
            base = normalized;
            BlockPos main = block.getMainPartPos(entry.getKey(), state);
            if (origin != null && !origin.equals(main)) return false;
            origin = main;
        }
        return parts.size() == block.getParts().length;
    }

    private static boolean isCompleteCake(Map<BlockPos, BlockState> states) {
        if (states.size() != Cube3x3PartHalf.values().length) return false;
        Set<Cube3x3PartHalf> parts = new HashSet<>();
        BlockPos origin = null;
        for (Map.Entry<BlockPos, BlockState> entry : states.entrySet()) {
            BlockState state = entry.getValue();
            if (!state.hasProperty(LargeCakeBlock.HALF) || !parts.add(state.getValue(LargeCakeBlock.HALF))) return false;
            BlockPos current = LargeCakeBlockItem.origin(entry.getKey(), state);
            if (origin != null && !origin.equals(current)) return false;
            origin = current;
        }
        return parts.size() == Cube3x3PartHalf.values().length;
    }

    private static <P extends Enum<P> & ISimpleMultiPartBlockState<P>> boolean isCompleteMultipart(
            SimpleMultiPartBlock<P> block, Map<BlockPos, BlockState> states) {
        if (states.size() != block.getParts().length) return false;
        Set<P> parts = new HashSet<>();
        BlockPos origin = null;
        for (Map.Entry<BlockPos, BlockState> entry : states.entrySet()) {
            BlockState state = entry.getValue();
            if (!state.hasProperty(block.getPart())) return false;
            P part = state.getValue(block.getPart());
            if (!parts.add(part) || !block.placedState(part, block.defaultBlockState()).equals(state)) return false;
            BlockPos main = block.getMainPartPos(entry.getKey(), state);
            if (origin != null && !origin.equals(main)) return false;
            origin = main;
        }
        return parts.size() == block.getParts().length;
    }
}
