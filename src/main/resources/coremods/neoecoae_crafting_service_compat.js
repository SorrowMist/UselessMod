var VarInsnNode = Java.type("org.objectweb.asm.tree.VarInsnNode");

var handlerName = "neoecoae$submitJobWithSimulationFlag";
var oldHandlerDescriptor = "(Lappeng/api/networking/crafting/ICraftingPlan;"
        + "Lappeng/api/networking/crafting/ICraftingRequester;"
        + "Lappeng/api/networking/crafting/ICraftingCPU;Z"
        + "Lappeng/api/networking/security/IActionSource;Z"
        + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
var newHandlerDescriptor = "(Lappeng/api/networking/crafting/ICraftingPlan;"
        + "Lappeng/api/networking/crafting/ICraftingRequester;"
        + "Lappeng/api/networking/crafting/ICraftingCPU;Z"
        + "Lappeng/api/networking/security/IActionSource;"
        + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
var oldTargetDescriptor = "submitJob(Lappeng/api/networking/crafting/ICraftingPlan;"
        + "Lappeng/api/networking/crafting/ICraftingRequester;"
        + "Lappeng/api/networking/crafting/ICraftingCPU;Z"
        + "Lappeng/api/networking/security/IActionSource;Z)"
        + "Lappeng/api/networking/crafting/ICraftingSubmitResult;";
var newTargetDescriptor = "submitJob(Lappeng/api/networking/crafting/ICraftingPlan;"
        + "Lappeng/api/networking/crafting/ICraftingRequester;"
        + "Lappeng/api/networking/crafting/ICraftingCPU;Z"
        + "Lappeng/api/networking/security/IActionSource;)"
        + "Lappeng/api/networking/crafting/ICraftingSubmitResult;";
var oldGenericSignature = "(Lappeng/api/networking/crafting/ICraftingPlan;"
        + "Lappeng/api/networking/crafting/ICraftingRequester;"
        + "Lappeng/api/networking/crafting/ICraftingCPU;Z"
        + "Lappeng/api/networking/security/IActionSource;Z"
        + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable<"
        + "Lappeng/api/networking/crafting/ICraftingSubmitResult;>;)V";
var newGenericSignature = "(Lappeng/api/networking/crafting/ICraftingPlan;"
        + "Lappeng/api/networking/crafting/ICraftingRequester;"
        + "Lappeng/api/networking/crafting/ICraftingCPU;Z"
        + "Lappeng/api/networking/security/IActionSource;"
        + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable<"
        + "Lappeng/api/networking/crafting/ICraftingSubmitResult;>;)V";

function initializeCoreMod() {
    return {
        "neoecoae_crafting_service_compat": {
            "target": {
                "type": "CLASS",
                "name": "cn.dancingsnow.neoecoae.mixins.CraftingServiceMixin"
            },
            "transformer": function(classNode) {
                for (var methodIndex = 0; methodIndex < classNode.methods.size(); methodIndex++) {
                    var method = classNode.methods.get(methodIndex);
                    if (method.name !== handlerName || method.desc !== oldHandlerDescriptor) {
                        continue;
                    }

                    method.desc = newHandlerDescriptor;
                    if (method.signature === oldGenericSignature) {
                        method.signature = newGenericSignature;
                    }

                    var instructions = method.instructions.toArray();
                    for (var instructionIndex = 0; instructionIndex < instructions.length; instructionIndex++) {
                        var instruction = instructions[instructionIndex];
                        if (instruction instanceof VarInsnNode && instruction.var >= 7) {
                            instruction.var--;
                        }
                    }

                    if (method.localVariables !== null) {
                        for (var localIndex = method.localVariables.size() - 1; localIndex >= 0; localIndex--) {
                            var local = method.localVariables.get(localIndex);
                            if (local.index === 6 && local.name === "simulate") {
                                method.localVariables.remove(localIndex);
                            } else if (local.index >= 7) {
                                local.index--;
                            }
                        }
                    }

                    if (method.parameters !== null && method.parameters.size() === 7) {
                        method.parameters.remove(5);
                    }
                    method.maxLocals--;

                    if (method.visibleAnnotations !== null) {
                        for (var annotationIndex = 0; annotationIndex < method.visibleAnnotations.size(); annotationIndex++) {
                            var annotation = method.visibleAnnotations.get(annotationIndex);
                            if (annotation.desc !== "Lorg/spongepowered/asm/mixin/injection/Inject;") {
                                continue;
                            }
                            for (var valueIndex = 0; valueIndex < annotation.values.size(); valueIndex += 2) {
                                if (annotation.values.get(valueIndex) === "method") {
                                    var targets = annotation.values.get(valueIndex + 1);
                                    for (var targetIndex = 0; targetIndex < targets.size(); targetIndex++) {
                                        if (targets.get(targetIndex) === oldTargetDescriptor) {
                                            targets.set(targetIndex, newTargetDescriptor);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    break;
                }
                return classNode;
            }
        }
    };
}
