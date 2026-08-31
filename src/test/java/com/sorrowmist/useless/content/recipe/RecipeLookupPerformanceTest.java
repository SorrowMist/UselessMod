package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Small repeatable benchmark for comparing recipe lookup revisions.
 *
 * <p>This is intentionally a JUnit test instead of a correctness test: run it on the old and
 * new revisions with the same JVM arguments and compare the reported median ns/op values. It
 * uses a fixed synthetic data set so the result is not affected by the installed mod pack.</p>
 */
@Tag("performance")
class RecipeLookupPerformanceTest {
    private static final int CANDIDATE_COUNT = 128;
    private static final int SAMPLE_COUNT = 5;
    private static volatile long BLACK_HOLE;
    private static Unsafe unsafe;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
    }

    @Test
    void benchmarkRecipeLookupHotPaths() {
        BenchmarkConfig config = BenchmarkConfig.fromSystemProperties();
        BenchmarkFixture fixture = BenchmarkFixture.create();
        LevelFixture levelFixture = LevelFixture.create(fixture);

        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.invalidateIndex();
        manager.buildIndex(levelFixture.level());

        BenchmarkResult itemResult = benchmark(
                "item-allocator-exact",
                config,
                () -> ItemIngredientAllocator.matches(
                        fixture.itemRequirements(), fixture.itemInputs(), 1L) ? 1L : 0L);
        BenchmarkResult fluidResult = benchmark(
                "fluid-allocator-single",
                config,
                () -> FluidIngredientAllocator.matchesLong(
                        fixture.fluidRequirements(), fixture.fluidInputs(), 1L) ? 1L : 0L);
        BenchmarkResult lookupResult = benchmark(
                "recipe-selection-128-candidates",
                config,
                () -> {
                    AdvancedAlloyFurnaceRecipe selected = AlloyFurnaceRecipeManager.selectBestCandidate(
                            fixture.candidates(), fixture.itemInputs(), fixture.fluidInputs(),
                            List.of(), ItemStack.EMPTY, List.of(), 1L);
                    return selected == fixture.expectedRecipe() ? 1L : 0L;
                });
        manager.clearCache();
        List<LookupQuery> cacheMissQueries = createCacheMissQueries(config);
        int[] cacheMissIndex = {0};
        BenchmarkResult indexedLookupMissResult = benchmark(
                "manager-lookup-indexed-cache-miss",
                config,
                () -> {
                    LookupQuery query = cacheMissQueries.get(cacheMissIndex[0]++);
                    AdvancedAlloyFurnaceRecipe selected = manager.findRecipe(
                            levelFixture.level(), query.itemInputs(), query.fluidInputs(), ItemStack.EMPTY);
                    return selected == fixture.expectedRecipe() ? 1L : 0L;
                });
        AdvancedAlloyFurnaceRecipe warm = manager.findRecipe(
                levelFixture.level(), fixture.itemInputs(), fixture.fluidInputs(), ItemStack.EMPTY);
        assertEquals(fixture.expectedRecipe(), warm);
        BenchmarkResult cachedLookupResult = benchmark(
                "manager-lookup-cache-hit",
                config,
                () -> manager.findRecipe(
                                levelFixture.level(), fixture.itemInputs(), fixture.fluidInputs(), ItemStack.EMPTY)
                        == fixture.expectedRecipe() ? 1L : 0L);

        assertEquals(config.iterations(), itemResult.successes());
        assertEquals(config.iterations(), fluidResult.successes());
        assertEquals(config.iterations(), lookupResult.successes());
        assertEquals(config.iterations(), indexedLookupMissResult.successes());
        assertEquals(config.iterations(), cachedLookupResult.successes());
        assertTrue(BLACK_HOLE > 0L);
    }

    private static List<LookupQuery> createCacheMissQueries(BenchmarkConfig config) {
        int queryCount = Math.addExact(
                config.warmupIterations(), Math.multiplyExact(config.iterations(), config.samples()));
        List<LookupQuery> queries = new ArrayList<>(queryCount);
        for (int index = 0; index < queryCount; index++) {
            int itemCount = index + 1;
            int fluidAmount = 1_000 + index;
            queries.add(new LookupQuery(
                    List.of(new ItemStack(Items.OAK_LOG, itemCount),
                            new ItemStack(Items.COBBLESTONE, itemCount)),
                    List.of(new FluidStack(Fluids.WATER, fluidAmount))));
        }
        return List.copyOf(queries);
    }

    private static BenchmarkResult benchmark(String name, BenchmarkConfig config, LongSupplier operation) {
        for (int iteration = 0; iteration < config.warmupIterations(); iteration++) {
            BLACK_HOLE += operation.getAsLong();
        }

        long[] elapsedNanos = new long[config.samples()];
        long measuredSuccesses = 0L;
        for (int sample = 0; sample < elapsedNanos.length; sample++) {
            long successes = 0L;
            long startedAt = System.nanoTime();
            for (int iteration = 0; iteration < config.iterations(); iteration++) {
                successes += operation.getAsLong();
            }
            elapsedNanos[sample] = System.nanoTime() - startedAt;
            measuredSuccesses = successes;
            BLACK_HOLE += successes;
        }

        Arrays.sort(elapsedNanos);
        long medianNanos = elapsedNanos[elapsedNanos.length / 2];
        double nanosPerOperation = (double) medianNanos / config.iterations();
        double operationsPerSecond = nanosPerOperation <= 0.0
                ? Double.POSITIVE_INFINITY
                : 1_000_000_000.0 / nanosPerOperation;
        System.out.printf(Locale.ROOT,
                "RECIPE_PERF scenario=%s samples=%d iterations=%d median_ns_per_op=%.1f throughput_ops_per_sec=%.1f successes=%d%n",
                name, config.samples(), config.iterations(), nanosPerOperation,
                operationsPerSecond, measuredSuccesses);
        return new BenchmarkResult(measuredSuccesses, medianNanos);
    }

    private record BenchmarkResult(long successes, long medianNanos) {
    }

    private record LookupQuery(List<ItemStack> itemInputs, List<FluidStack> fluidInputs) {
    }

    private record BenchmarkConfig(int warmupIterations, int iterations, int samples) {
        private static BenchmarkConfig fromSystemProperties() {
            int warmup = positiveProperty("recipePerf.warmup", 2_000);
            int iterations = positiveProperty("recipePerf.iterations", 5_000);
            int samples = positiveProperty("recipePerf.samples", SAMPLE_COUNT);
            return new BenchmarkConfig(warmup, iterations, samples);
        }

        private static int positiveProperty(String name, int defaultValue) {
            int value = Integer.getInteger(name, defaultValue);
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }

    private record BenchmarkFixture(
            List<AdvancedAlloyFurnaceRecipe> candidates,
            AdvancedAlloyFurnaceRecipe expectedRecipe,
            List<CountedIngredient> itemRequirements,
            List<LongSizedFluidIngredient> fluidRequirements,
            List<ItemStack> itemInputs,
            List<FluidStack> fluidInputs
    ) {
        private static BenchmarkFixture create() {
            List<CountedIngredient> itemRequirements = List.of(
                    new CountedIngredient(Ingredient.of(Items.OAK_LOG), 1L),
                    new CountedIngredient(Ingredient.of(Items.COBBLESTONE), 1L));
            List<LongSizedFluidIngredient> fluidRequirements = List.of(
                    new LongSizedFluidIngredient(FluidIngredient.of(Fluids.WATER), 1_000L));
            List<ItemStack> itemInputs = List.of(
                    new ItemStack(Items.OAK_LOG, 1),
                    new ItemStack(Items.COBBLESTONE, 1));
            List<FluidStack> fluidInputs = List.of(new FluidStack(Fluids.WATER, 1_000));

            List<AdvancedAlloyFurnaceRecipe> candidates = new ArrayList<>(CANDIDATE_COUNT);
            for (int index = 0; index < CANDIDATE_COUNT; index++) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                        "useless_mod_perf", String.format(Locale.ROOT, "candidate_%03d", index));
                candidates.add(new AdvancedAlloyFurnaceRecipe(
                        id,
                        itemRequirements,
                        fluidRequirements,
                        List.of(),
                        List.of(new ItemStack(Items.GOLD_INGOT)),
                        List.of(),
                        List.of(),
                        2_000L,
                        200,
                        Ingredient.EMPTY,
                        0,
                        Ingredient.EMPTY,
                        AlloyFurnaceMode.NORMAL));
            }
            return new BenchmarkFixture(
                    List.copyOf(candidates), candidates.getFirst(), itemRequirements,
                    fluidRequirements, itemInputs, fluidInputs);
        }
    }

    private record LevelFixture(Level level) {
        private static LevelFixture create(BenchmarkFixture fixture) {
            RecipeManager recipeManager = new RecipeManager(null);
            List<RecipeHolder<?>> holders = new ArrayList<>(fixture.candidates().size());
            for (AdvancedAlloyFurnaceRecipe recipe : fixture.candidates()) {
                holders.add(new RecipeHolder<>(recipe.id(), recipe));
            }
            recipeManager.replaceRecipes(new ArrayList<>(holders));
            TestServerLevel.recipeManager = recipeManager;
            try {
                return new LevelFixture((Level) unsafe.allocateInstance(TestServerLevel.class));
            } catch (InstantiationException exception) {
                throw new IllegalStateException("Unable to create benchmark level", exception);
            }
        }
    }

    private static final class TestServerLevel extends ServerLevel {
        private static RecipeManager recipeManager;

        private TestServerLevel() {
            super(null, null, null, null, null, null, null, false, 0L, List.of(), false, null);
        }

        @Override
        public RecipeManager getRecipeManager() {
            return recipeManager;
        }

        @Override
        public RegistryAccess registryAccess() {
            return RegistryAccess.EMPTY;
        }
    }
}
