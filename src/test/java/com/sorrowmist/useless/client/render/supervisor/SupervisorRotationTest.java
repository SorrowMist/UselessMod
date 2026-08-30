package com.sorrowmist.useless.client.render.supervisor;

import com.google.gson.JsonParser;
import net.minecraft.client.resources.model.BlockModelRotation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupervisorRotationTest {
    @Test
    void compositeRotationUsesZyxAroundTheSourceOrigin() {
        var rotation = JsonParser.parseString("""
                {
                  "x": 20,
                  "y": 35,
                  "z": -15,
                  "origin": [4, -2, 3]
                }
                """).getAsJsonObject();

        Matrix4f actual = SupervisorGeometry.parseRotation(rotation);
        Matrix4f expected = new Matrix4f()
                .translate(4.0F / 16.0F, -2.0F / 16.0F, 3.0F / 16.0F)
                .rotateZ((float) Math.toRadians(-15.0F))
                .rotateY((float) Math.toRadians(35.0F))
                .rotateX((float) Math.toRadians(20.0F))
                .translate(-4.0F / 16.0F, 2.0F / 16.0F, -3.0F / 16.0F);

        Vector3f actualPoint = actual.transformPosition(new Vector3f(5.0F / 16.0F, 1.0F / 16.0F, 7.0F / 16.0F));
        Vector3f expectedPoint = expected.transformPosition(new Vector3f(5.0F / 16.0F, 1.0F / 16.0F, 7.0F / 16.0F));

        assertEquals(expectedPoint.x(), actualPoint.x(), 0.00001F);
        assertEquals(expectedPoint.y(), actualPoint.y(), 0.00001F);
        assertEquals(expectedPoint.z(), actualPoint.z(), 0.00001F);

        Vector3f pivot = actual.transformPosition(new Vector3f(4.0F / 16.0F, -2.0F / 16.0F, 3.0F / 16.0F));
        assertEquals(4.0F / 16.0F, pivot.x(), 0.00001F);
        assertEquals(-2.0F / 16.0F, pivot.y(), 0.00001F);
        assertEquals(3.0F / 16.0F, pivot.z(), 0.00001F);
    }

    @Test
    void blockStateRotationIsAppliedAfterElementRotationAroundTheBlockCenter() {
        var rotation = JsonParser.parseString("""
                {
                  "angle": 30,
                  "axis": "z",
                  "origin": [2, 4, 6]
                }
                """).getAsJsonObject();
        Matrix4f elementTransform = SupervisorGeometry.parseRotation(rotation);

        Matrix4f actual = SupervisorGeometry.composeTransforms(
                BlockModelRotation.X0_Y90, elementTransform);
        Matrix4f expected = new Matrix4f()
                .translate(0.5F, 0.5F, 0.5F)
                .mul(BlockModelRotation.X0_Y90.getRotation().getMatrix())
                .translate(-0.5F, -0.5F, -0.5F)
                .mul(elementTransform);

        Vector3f actualPoint = actual.transformPosition(new Vector3f(7.0F / 16.0F, 5.0F / 16.0F, 3.0F / 16.0F));
        Vector3f expectedPoint = expected.transformPosition(new Vector3f(7.0F / 16.0F, 5.0F / 16.0F, 3.0F / 16.0F));

        assertEquals(expectedPoint.x(), actualPoint.x(), 0.00001F);
        assertEquals(expectedPoint.y(), actualPoint.y(), 0.00001F);
        assertEquals(expectedPoint.z(), actualPoint.z(), 0.00001F);
    }
}
