package com.sorrowmist.useless.compat.neoecoae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemantics;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticsRegistry;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionRequirement;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerBatch;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlanner;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlans;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NeoEcoPlanningCompatTest {
    @Test
    void localPatternDispatchInstallationFollowsTheRuntimeApi() {
        boolean supportsFastPathRegistration = Stream.of(ECOParallelCraftingProviders.class.getMethods())
                .anyMatch(method -> method.getName().equals("registerFastPath"));

        NeoEcoCompat.initialize();

        assertEquals(supportsFastPathRegistration, ECOPatternSemanticsRegistry.snapshot().stream()
                .anyMatch(entry -> entry.adapter() instanceof NeoEcoPatternSemantics));
    }

    @Test
    void relaxedInputsAllowFixedOutputCyclesButExcludeDynamicOutputs() {
        var pattern = mock(DynamicComponentPattern.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{mock(IPatternDetails.IInput.class)});
        when(pattern.isItemIdInput(0)).thenReturn(true);
        var base = new ECOPatternSemantics(pattern, null, List.of(), List.of(), List.of(), List.of(),
                ECOPatternSemantics.MatchingMode.EXACT, ECOPatternSemantics.ExecutionRestriction.NONE,
                true, true, null);
        var adapter = new NeoEcoPatternSemantics();

        var fixed = adapter.analyze(pattern, base);
        assertSame(pattern, fixed.physicalPattern());
        assertEquals(ECOPatternSemantics.MatchingMode.SUBSTITUTION, fixed.matchingMode());
        assertTrue(fixed.cycleSafe());

        when(pattern.usesDynamicOutputs()).thenReturn(true);
        assertFalse(adapter.analyze(pattern, base).cycleSafe());
    }

    @Test
    void shrinkingAdmissionRejectsBeforeCommitAndThrowingCommitIsIndeterminate() {
        var target = mock(AlloyFurnaceBigIntegerTarget.class);
        var ticket = mock(AlloyFurnaceBigIntegerBatch.class);
        var pattern = mock(IPatternDetails.class);
        var prototype = new KeyCounter[]{new KeyCounter()};
        when(target.admit(pattern, prototype, BigInteger.TWO, null)).thenReturn(ticket);
        when(ticket.count()).thenReturn(BigInteger.ONE);

        assertFalse(NeoEcoAlloyFurnaceDispatch.commitWholeBatch(target, pattern, prototype, BigInteger.TWO));
        verify(ticket, never()).commit(any());

        when(ticket.count()).thenReturn(BigInteger.TWO);
        when(ticket.commit(same(prototype))).thenThrow(new IllegalStateException("commit failed"));
        assertThrows(ECOIndeterminateBatchException.class,
                () -> NeoEcoAlloyFurnaceDispatch.commitWholeBatch(target, pattern, prototype, BigInteger.TWO));
        verify(ticket).commit(same(prototype));
    }

    @Test
    void onlyTheConfirmedCompleteVectorBypassesRewriting() {
        AEKey output = mock(AEKey.class);
        IPatternDetails pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(List.of());
        CraftingPlan confirmed = plan(output, pattern, 4);
        CraftingPlan differentTasks = plan(output, pattern, 5);
        CraftingPlan differentMaterials = plan(output, pattern, 4);
        KeyCounter materials = new KeyCounter();
        materials.add(output, 1);
        when(differentMaterials.usedItems()).thenReturn(materials);
        ECOPlanningResult result = mock(ECOPlanningResult.class);
        when(result.plan()).thenReturn(confirmed);
        when(result.status()).thenReturn(PlanningStatus.SUCCESS);
        when(result.executionRequirement()).thenReturn(ECOExecutionRequirement.ORDERED);
        when(result.planningId()).thenReturn(UUID.randomUUID());
        ModList mods = mock(ModList.class);
        when(mods.isLoaded("neoecoae")).thenReturn(true);

        try (var modList = mockStatic(ModList.class); var planner = mockStatic(SmartDoublingPlanner.class)) {
            modList.when(ModList::get).thenReturn(mods);
            ECOPlanningResultRegistry.withSubmissionAlias(confirmed, result, () -> {
                assertSame(confirmed, SmartDoublingPlans.rewriteForSubmission(confirmed, ignored -> {
                    fail("A confirmed ECO plan must not reach smart doubling");
                    return List.of();
                }));
                planner.verifyNoInteractions();
                assertFalse(NeoEcoPlanningCompat.shouldPreserveSubmissionPlan(differentTasks));
                assertFalse(NeoEcoPlanningCompat.shouldPreserveSubmissionPlan(differentMaterials));
                return null;
            });
            assertFalse(NeoEcoPlanningCompat.shouldPreserveSubmissionPlan(confirmed));
        }
    }

    @Test
    void foreignPlansKeepTheExistingSmartDoublingPathWithEcoPresentOrAbsent() {
        AEKey output = mock(AEKey.class);
        IPatternDetails original = mock(IPatternDetails.class);
        IPatternDetails scaled = mock(IPatternDetails.class);
        CraftingPlan source = plan(output, original, 4);
        Function<IPatternDetails, Iterable<ICraftingProvider>> lookup = ignored -> List.of();
        ModList mods = mock(ModList.class);
        try (var modList = mockStatic(ModList.class); var planner = mockStatic(SmartDoublingPlanner.class)) {
            modList.when(ModList::get).thenReturn(mods);
            planner.when(() -> SmartDoublingPlanner.rewrite(source.patternTimes(), lookup))
                    .thenReturn(Map.of(scaled, 1L));
            for (boolean installed : new boolean[]{false, true}) {
                when(mods.isLoaded("neoecoae")).thenReturn(installed);
                ICraftingPlan rewritten = SmartDoublingPlans.rewriteForSubmission(source, lookup);
                assertNotSame(source, rewritten);
                assertEquals(Map.of(scaled, 1L), rewritten.patternTimes());
                assertEquals(source.finalOutput(), rewritten.finalOutput());
                assertSame(source.usedItems(), rewritten.usedItems());
            }
            planner.verify(() -> SmartDoublingPlanner.rewrite(source.patternTimes(), lookup), times(2));
        }
    }

    @Test
    void missingPlanningApiDoesNotMakeTheOptionalBridgeUnusable() throws Exception {
        ModList mods = mock(ModList.class);
        try (var modList = mockStatic(ModList.class)) {
            modList.when(ModList::get).thenReturn(mods);
            // Load the bridge in isolation while completely denying ECO classes.
            ClassLoader isolated = new ClassLoader(getClass().getClassLoader()) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.startsWith("cn.dancingsnow.neoecoae.")) {
                        throw new ClassNotFoundException(name);
                    }
                    if (name.startsWith(NeoEcoPlanningCompat.class.getName())) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded != null) return loaded;
                        try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (input == null) throw new ClassNotFoundException(name);
                            byte[] bytes = input.readAllBytes();
                            return defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException failure) {
                            throw new ClassNotFoundException(name, failure);
                        }
                    }
                    return super.loadClass(name, resolve);
                }
            };
            var method = isolated.loadClass(NeoEcoPlanningCompat.class.getName())
                    .getMethod("shouldPreserveSubmissionPlan", ICraftingPlan.class);
            assertEquals(false, method.invoke(null, mock(ICraftingPlan.class)));
            when(mods.isLoaded("neoecoae")).thenReturn(true);
            assertEquals(false, method.invoke(null, mock(ICraftingPlan.class)));
        }
    }

    private static CraftingPlan plan(AEKey output, IPatternDetails pattern, long tasks) {
        CraftingPlan plan = mock(CraftingPlan.class);
        when(plan.finalOutput()).thenReturn(new GenericStack(output, 1));
        when(plan.patternTimes()).thenReturn(Map.of(pattern, tasks));
        when(plan.usedItems()).thenReturn(new KeyCounter());
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        when(plan.missingItems()).thenReturn(new KeyCounter());
        return plan;
    }
}
