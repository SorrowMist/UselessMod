package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.anvilcraft.lib.v2.util.predicate.BlockStatePredicate;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTypes;
import dev.dubhe.anvilcraft.recipe.anvil.collision.AnvilCollisionCraftRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按上游碰撞事件的铁砧消耗、撞击目标和速度优先级转换静态配方。 */
public final class AnvilCollisionRecipeAdapter implements IRecipeAdapter<AnvilCollisionCraftRecipe> {
    private static final int BASE_COLLISION_SPEED = 32;
    private static final List<Integer> SPEEDS = List.of(BASE_COLLISION_SPEED, 128, 256);

    @Override
    public String sourceId() {
        return RecipeSourceIds.ANVILCRAFT;
    }

    @Override
    public Class<AnvilCollisionCraftRecipe> getRecipeClass() {
        return AnvilCollisionCraftRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<AnvilCollisionCraftRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || level == null) return List.of();
        AnvilCollisionCraftRecipe source = holder.value();
        if (!isStatic(source.anvil()) || !isStatic(source.hitBlock()) || source.speed() <= 0
                || source.transformBlocks().size() > 1) return List.of();
        List<ExpectedOutputScaler.WeightedItemOutput> weighted = new ArrayList<>();
        for (var output : source.outputItems()) {
            if (!AnvilItemRecipeAdapter.addOutput(weighted, output)) return List.of();
        }
        Map<Ingredient, Long> surrounding = new LinkedHashMap<>();
        if (!source.transformBlocks().isEmpty()) {
            var transform = source.transformBlocks().getFirst();
            var output = transform.outputBlock();
            Ingredient input = Multiblock4DRecipeAdapter.blockIngredient(transform.inputBlock());
            // 过热持续时间等方块实体数据不能通过普通方块物品重建。
            if (!isStatic(transform.inputBlock()) || input.isEmpty() || transform.maxCount() <= 0
                    || !output.nbt().isEmpty() || !(output.chance() instanceof ConstantValue chance)
                    || chance.value() != 1.0F || output.state().getBlock().asItem() == Items.AIR
                    || !output.state().equals(output.state().getBlock().defaultBlockState())) return List.of();
            surrounding.put(input, (long) transform.maxCount());
            weighted.add(new ExpectedOutputScaler.WeightedItemOutput(new ItemStack(output.state().getBlock()),
                    transform.maxCount(), transform.maxCount(), 1.0));
        }
        var scaled = ExpectedOutputScaler.scale(weighted);
        if (scaled.isEmpty() || scaled.get().outputs().isEmpty()) return List.of();
        List<RecipeHolder<AnvilCollisionCraftRecipe>> peers =
                level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.ANVIL_COLLISION_CRAFT.get());
        var hitBlocks = source.hitBlock().getBlocks();
        Map<ResourceLocation, AdvancedAlloyFurnaceRecipe> recipes = new LinkedHashMap<>();
        for (int speed : SPEEDS) {
            if (speed < source.speed()) continue;
            for (var hitHolder : hitBlocks) {
                Block hit = hitHolder.value();
                if (hit.asItem() == Items.AIR) continue;
                List<ItemStack> anvils = source.anvil().getBlocks().stream()
                        .map(entry -> entry.value()).filter(block -> block.asItem() != Items.AIR)
                        .filter(block -> selectedAtSpeed(holder, peers, block, hit, speed))
                        .map(block -> new ItemStack(block)).toList();
                if (anvils.isEmpty()) continue;
                Map<Ingredient, Long> materials = new LinkedHashMap<>(surrounding);
                List<Ingredient> molds = new ArrayList<>();
                Ingredient anvil = Ingredient.of(anvils.toArray(ItemStack[]::new));
                if (source.consume()) AdapterUtils.mergeIngredient(materials, anvil, 1);
                else molds.add(anvil);
                // 依据 FallingBlockCollisionEventListener.removeBlock 保留碰撞免疫目标。
                if (hit.defaultBlockState().is(ModBlockTags.COLLISION_IMMUNE)) molds.add(Ingredient.of(hit));
                else AdapterUtils.mergeIngredient(materials, Ingredient.of(hit), 1);
                molds.add(Ingredient.of(ModBlocks.ACCELERATION_RING.asItem()));
                molds.add(Ingredient.of(ModBlocks.DEFLECTION_RING.asItem()));
                List<CountedIngredient> inputs = new ArrayList<>();
                try {
                    int operations = scaled.get().operations();
                    for (var entry : materials.entrySet()) {
                        inputs.add(new CountedIngredient(entry.getKey(), Math.multiplyExact(entry.getValue(), operations)));
                    }
                    if (inputs.isEmpty()) continue;
                    ResourceLocation base = AdapterUtils.convertedId(holder.id()).withSuffix("/speed/" + speed);
                    // 以 32 档为基准，能耗随配方要求的碰撞速度线性缩放。
                    int processTime = Math.multiplyExact(AdapterUtils.DEFAULT_PROCESS_TIME, operations);
                    long energy = Math.multiplyExact(
                            AdapterUtils.energyForProcessTime(processTime),
                            speed / (long) BASE_COLLISION_SPEED);
                    AdvancedAlloyFurnaceRecipe preview = new AdvancedAlloyFurnaceRecipe(base, inputs,
                            List.of(), List.of(), scaled.get().outputs(), List.of(), List.of(),
                            energy, processTime, Ingredient.EMPTY, 0,
                            molds, AlloyFurnaceMode.NORMAL);
                    ResourceLocation id = base;
                    if (hitBlocks.size() > 1) {
                        ResourceLocation hitId = BuiltInRegistries.BLOCK.getKey(hit);
                        if (hitId == null) continue;
                        id = base.withSuffix("/hit/" + hitId.getNamespace() + "/" + hitId.getPath());
                    }
                    recipes.putIfAbsent(id, new AdvancedAlloyFurnaceRecipe(id, preview.inputs(), preview.inputFluids(),
                            preview.keyInputs(), preview.outputs(), preview.outputFluids(), preview.keyOutputs(),
                            preview.energy(), preview.processTime(), preview.catalyst(), preview.catalystUses(),
                            preview.molds(), preview.mode()));
                } catch (ArithmeticException exception) {
                    return List.of();
                }
            }
        }
        return List.copyOf(recipes.values());
    }

    private static boolean isStatic(BlockStatePredicate predicate) {
        return predicate != null && !predicate.requiresBlockEntity() && predicate.getProperties().isEmpty()
                && predicate.getBlocks().size() > 0;
    }

    private static boolean selectedAtSpeed(RecipeHolder<AnvilCollisionCraftRecipe> source,
            List<RecipeHolder<AnvilCollisionCraftRecipe>> peers, Block anvil, Block hit, int speed) {
        for (var peer : peers) {
            if (peer.id().equals(source.id())) continue;
            var recipe = peer.value();
            if (recipe.speed() < source.value().speed() || recipe.speed() > speed) continue;
            if (recipe.anvil().getBlocks().stream().noneMatch(entry -> entry.value() == anvil)
                    || recipe.hitBlock().getBlocks().stream().noneMatch(entry -> entry.value() == hit)) continue;
            // 原版同速冲突依赖遍历顺序；存在冲突时隔离，避免生成不稳定的配方身份。
            return false;
        }
        return true;
    }

    @Override
    public List<RecipeHolder<AnvilCollisionCraftRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null) return List.of();
        List<RecipeHolder<AnvilCollisionCraftRecipe>> matches = new ArrayList<>();
        for (var holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.ANVIL_COLLISION_CRAFT.get())) {
            for (var recipe : convertAll(holder, level)) {
                if (recipe.molds().size() > 1 || !AdapterUtils.matchesMold(recipe.mold(), mold)) continue;
                Map<Ingredient, Long> required = new LinkedHashMap<>();
                recipe.inputs().forEach(input -> AdapterUtils.mergeIngredient(required, input.ingredient(), input.count()));
                if (AdapterUtils.matchesRequired(mergedInputs, required)) {
                    matches.add(holder);
                    break;
                }
            }
        }
        return List.copyOf(matches);
    }
}
