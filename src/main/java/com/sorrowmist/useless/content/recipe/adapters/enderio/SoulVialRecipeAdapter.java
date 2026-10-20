package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.sorrowmist.useless.compat.forge1201.enderio.NativeSoul;
import com.sorrowmist.useless.compat.forge1201.enderio.NativeSoulStorage;
import com.enderio.machines.common.blockentity.task.SpawnerMachineTask.SpawnType;
import com.enderio.machines.common.souldata.SpawnerSoul;
import com.enderio.base.common.util.EntityCaptureUtils;
import com.enderio.machines.common.tag.MachineTags;
import com.enderio.machines.common.config.MachinesConfig;
import com.enderio.base.common.init.EIOItems;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Generates Ender IO soul-vial capture recipes for the alloy furnace. */
public final class SoulVialRecipeAdapter implements IRecipeAdapter<SoulVialRecipeAdapter.SoulVialDummyRecipe> {
    private static final int INPUT_COUNT = 1;

    private final Ingredient emptySoulVial = DataComponentIngredient.of(
            true, EIOItems.EMPTY_SOUL_VIAL.get().getDefaultInstance());

    @Override
    public Class<SoulVialDummyRecipe> getRecipeClass() {
        return SoulVialDummyRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty() || !(mold.getItem() instanceof SpawnEggItem spawnEgg)) {
            return false;
        }
        return isSupportedEntity(spawnEgg.getType(mold.getTag()));
    }

    @Override
    public List<RecipeHolder<SoulVialDummyRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        List<RecipeHolder<SoulVialDummyRecipe>> result = new ArrayList<>();
        for (ResourceLocation entityId : EntityCaptureUtils.getCapturableEntities()) {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) continue;
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityId);
            if (!isSupportedEntity(entityType)) {
                continue;
            }

            SpawnEggItem spawnEgg = SpawnEggItem.byId(entityType);
            int energy = energyCost(entityType);
            if (spawnEgg == null || energy <= 0) {
                continue;
            }

            ItemStack mold = new ItemStack(spawnEgg);
            result.add(createHolder(entityType, mold, energy));
        }
        return List.copyOf(result);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SoulVialDummyRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SoulVialDummyRecipe> holder, Level level, List<ItemStack> actualInputs) {
        return convertAll(holder, level);
    }

    @Override
    public List<RecipeHolder<SoulVialDummyRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<SoulVialDummyRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold) || !matchesEmptySoulVial(mergedInputs, actualInputs)) {
            return List.of();
        }

        SpawnEggItem spawnEgg = (SpawnEggItem) mold.getItem();
        EntityType<?> entityType = spawnEgg.getType(mold.getTag());
        int energy = energyCost(entityType);
        if (energy <= 0) {
            return List.of();
        }

        return List.of(createHolder(entityType, mold.copyWithCount(1), energy));
    }

    private RecipeHolder<SoulVialDummyRecipe> createHolder(
            EntityType<?> entityType, ItemStack mold, int energy) {
        ResourceLocation recipeId = recipeId(entityType);
        ItemStack output = NativeSoulStorage.forSoul(captureSoul(entityType, mold));
        AdvancedAlloyFurnaceRecipe converted = new AdvancedAlloyFurnaceRecipe(
                recipeId,
                List.of(new CountedIngredient(emptySoulVial, INPUT_COUNT)),
                List.of(),
                List.of(output),
                List.of(),
                energy,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(mold),
                AlloyFurnaceMode.NORMAL);
        return new RecipeHolder<>(recipeId, new SoulVialDummyRecipe(converted));
    }

    private boolean matchesEmptySoulVial(
            Map<Ingredient, Long> mergedInputs, List<ItemStack> actualInputs) {
        if (actualInputs != null && !actualInputs.isEmpty()) {
            return actualInputs.stream()
                    .filter(stack -> stack != null && !stack.isEmpty())
                    .anyMatch(emptySoulVial::test);
        }
        return AdapterUtils.matchesRequired(mergedInputs, Map.of(emptySoulVial, (long) INPUT_COUNT));
    }

    static boolean isSupportedEntity(@Nullable EntityType<?> entityType) {
        return entityType != null
                && SpawnEggItem.byId(entityType) != null
                && EntityCaptureUtils.getCapturableEntities().contains(BuiltInRegistries.ENTITY_TYPE.getKey(entityType))
                && !entityType.is(MachineTags.EntityTypes.SPAWNER_BLACKLIST);
    }

    static int energyCost(EntityType<?> entityType) {
        return SpawnerSoul.SPAWNER.matches(BuiltInRegistries.ENTITY_TYPE.getKey(entityType))
                .map(SpawnerSoul.SoulData::power)
                .orElse(50_000); // Native SpawnerMachineTask fallback in Ender IO 6.2.13
    }

    private static SpawnType spawnMode(EntityType<?> entityType) {
        return SpawnerSoul.SPAWNER.matches(BuiltInRegistries.ENTITY_TYPE.getKey(entityType))
                .map(SpawnerSoul.SoulData::spawnType)
                .orElseGet(() -> MachinesConfig.COMMON.SPAWN_TYPE.get());
    }

    static NativeSoul captureSoul(EntityType<?> entityType, ItemStack mold) {
        if (spawnMode(entityType) == SpawnType.COPY) {
            try {
                NativeSoul soul = NativeSoulStorage.getBoundSoul(mold);
                if (soul != null && !soul.isEmpty()) {
                    return soul.copy();
                }
            } catch (RuntimeException ignored) {
                // Fall back to the entity type when the spawn-egg capability is unavailable.
            }
        }
        return NativeSoul.of(entityType);
    }

    private static ResourceLocation recipeId(EntityType<?> entityType) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        String path = entityId == null ? "unknown" : entityId.toString().replace(':', '_').replace('/', '_');
        return new ResourceLocation("enderio", "soul_vial_" + path);
    }

    public static final class SoulVialDummyRecipe implements Recipe<Container> {
        private final AdvancedAlloyFurnaceRecipe convertedRecipe;

        SoulVialDummyRecipe(AdvancedAlloyFurnaceRecipe convertedRecipe) {
            this.convertedRecipe = convertedRecipe;
        }

        AdvancedAlloyFurnaceRecipe convertedRecipe() {
            return convertedRecipe;
        }

        @Override
    public net.minecraft.resources.ResourceLocation getId() { return convertedRecipe.id(); }

    @Override
        public boolean matches(Container input, Level level) {
            return false;
        }

        @Override
        public ItemStack assemble(Container input, net.minecraft.core.RegistryAccess registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return false;
        }

        @Override
        public ItemStack getResultItem(net.minecraft.core.RegistryAccess registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return null;
        }

        @Override
        public RecipeType<?> getType() {
            return null;
        }
    }
}
