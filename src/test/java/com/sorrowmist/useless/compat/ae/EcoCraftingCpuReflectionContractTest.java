package com.sorrowmist.useless.compat.ae;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EcoCraftingCpuReflectionContractTest {
    @Test
    void ecoUsesItsOwnExecutingJobWithTheRequiredDynamicOutputFields() {
        Class<?> logic = DynamicReflectionSupport.findClassSafe(
                "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic");
        Class<?> job = DynamicReflectionSupport.findClassSafe(
                "cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob");
        Class<?> aeJob = DynamicReflectionSupport.findClassSafe(
                "appeng.crafting.execution.ExecutingCraftingJob");
        Class<?> tracker = DynamicReflectionSupport.findClassSafe(
                "appeng.crafting.execution.ElapsedTimeTracker");

        assertNotNull(logic);
        assertNotNull(job);
        assertNotNull(aeJob);
        assertNotNull(tracker);
        assertNotEquals(aeJob, job);
        var jobField = DynamicReflectionSupport.findFieldSafe(logic, "job");
        assertNotNull(jobField);
        assertEquals(job, jobField.getType());

        assertNotNull(DynamicReflectionSupport.findFieldSafe(job, "waitingFor"));
        assertNotNull(DynamicReflectionSupport.findFieldSafe(job, "timeTracker"));
        assertNotNull(DynamicReflectionSupport.findFieldSafe(job, "finalOutput"));
        assertNotNull(DynamicReflectionSupport.findFieldSafe(job, "remainingAmount"));
        assertNotNull(DynamicReflectionSupport.findFieldSafe(job, "link"));
        assertNotNull(DynamicReflectionSupport.findMethodSafe(logic, "finishJob", boolean.class));
        assertNotNull(DynamicReflectionSupport.findMethodSafe(logic, "postChange", AEKey.class));
        assertNotNull(DynamicReflectionSupport.findMethodSafe(
                tracker, "decrementItems", long.class, AEKeyType.class));
    }
}
