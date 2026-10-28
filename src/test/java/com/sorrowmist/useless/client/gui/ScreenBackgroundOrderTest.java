package com.sorrowmist.useless.client.gui;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Container screens must dim the world before any container/JEI foreground is painted. */
class ScreenBackgroundOrderTest {
    @Test
    void sharedAppleContainerDimsOnceBeforeContainerAndJeiRendering() throws IOException {
        assertBackgroundOrder("com/sorrowmist/useless/client/gui/apple/AppleContainerScreen", 1);
    }

    @Test
    void wirelessDimsOnceBeforeEitherNormalOrFilterContainerBranch() throws IOException {
        assertBackgroundOrder("com/sorrowmist/useless/client/gui/StaffLinkScreen", 2);
    }

    private static void assertBackgroundOrder(String type, long containerCalls) throws IOException {
        List<String> calls = new ArrayList<>();
        try (var bytes = ScreenBackgroundOrderTest.class.getResourceAsStream("/" + type + ".class")) {
            assertNotNull(bytes, type);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals("render") || !descriptor.equals("(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
                        return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method,
                                                    String desc, boolean isInterface) {
                            if (method.equals("renderBackground")) calls.add("background");
                            else if (owner.equals("net/minecraft/client/gui/screens/inventory/AbstractContainerScreen")
                                    && method.equals("render")) calls.add("container");
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(1, calls.stream().filter("background"::equals).count(), type);
        assertEquals(containerCalls, calls.stream().filter("container"::equals).count(), type);
        assertEquals("background", calls.get(0), "World dimming must not cover the UI or JEI previews");
    }
}
