package com.sorrowmist.useless.compat.neoecoae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionRequirement;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlanner;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlans;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NeoEcoForgePlanningBridgeTest {
    @Test void onlyTheConfirmedCompleteVectorBypassesTheExistingSmartDoublingHook() {
        AEKey output = mock(AEKey.class);
        IPatternDetails pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(new GenericStack[0]); // AE2 15 returns arrays, not 1.21 lists.
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
                assertTrue(NeoEcoPlanningCompat.shouldPreserveSubmissionPlan(confirmed));
                assertSame(confirmed, SmartDoublingPlans.rewriteForSubmission(confirmed, ignored -> {
                    fail("A confirmed ECO vector must not be rebuilt");
                    return List.of();
                }));
                planner.verifyNoInteractions();
                assertFalse(NeoEcoPlanningCompat.shouldPreserveSubmissionPlan(differentTasks));
                assertFalse(NeoEcoPlanningCompat.shouldPreserveSubmissionPlan(differentMaterials));
                return null;
            });
            assertFalse(NeoEcoPlanningCompat.shouldPreserveSubmissionPlan(confirmed));
            assertFalse(NeoEcoPlanningCompat.shouldPreserveSubmissionPlan(null));
        }
    }

    @Test void foreignPlansKeepTheOrdinaryRewriteWithEcoPresentOrAbsent() {
        AEKey output = mock(AEKey.class);
        IPatternDetails pattern = mock(IPatternDetails.class);
        IPatternDetails scaled = mock(IPatternDetails.class);
        CraftingPlan source = plan(output, pattern, 4);
        ModList mods = mock(ModList.class);
        try (var modList = mockStatic(ModList.class); var planner = mockStatic(SmartDoublingPlanner.class)) {
            modList.when(ModList::get).thenReturn(mods);
            planner.when(() -> SmartDoublingPlanner.rewrite(eq(source.patternTimes()), any()))
                    .thenReturn(Map.of(scaled, 1L));
            for (boolean installed : new boolean[]{false, true}) {
                when(mods.isLoaded("neoecoae")).thenReturn(installed);
                assertFalse(NeoEcoPlanningCompat.shouldPreserveSubmissionPlan(source));
                ICraftingPlan rewritten = SmartDoublingPlans.rewriteForSubmission(source, ignored -> List.of());
                assertNotSame(source, rewritten);
                assertEquals(Map.of(scaled, 1L), rewritten.patternTimes());
            }
            planner.verify(() -> SmartDoublingPlanner.rewrite(eq(source.patternTimes()), any()), times(2));
        }
    }

    @Test void missingPlanningApiStillLoadsTheOptionalBridge() throws Exception {
        ModList mods = mock(ModList.class);
        when(mods.isLoaded("neoecoae")).thenReturn(true);
        try (var modList = mockStatic(ModList.class)) {
            modList.when(ModList::get).thenReturn(mods);
            String bridge = NeoEcoPlanningCompat.class.getName();
            ClassLoader isolated = new ClassLoader(getClass().getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.startsWith("cn.dancingsnow.neoecoae.")) throw new ClassNotFoundException(name);
                    if (name.startsWith(bridge)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded != null) return loaded;
                        try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (input == null) throw new ClassNotFoundException(name);
                            byte[] bytes = input.readAllBytes();
                            return defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException failure) { throw new ClassNotFoundException(name, failure); }
                    }
                    return super.loadClass(name, resolve);
                }
            };
            assertEquals(false, isolated.loadClass(bridge).getMethod("shouldPreserveSubmissionPlan", ICraftingPlan.class)
                    .invoke(null, mock(ICraftingPlan.class)));
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
