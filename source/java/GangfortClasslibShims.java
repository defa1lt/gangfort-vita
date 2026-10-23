import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodHolder;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;

/** Replaces TeaVM's unsupported reflective constructor intrinsic. */
public final class GangfortClasslibShims implements ClassHolderTransformer {
    @Override
    public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (cls.getName().equals("wl")) {
            for (MethodHolder method : cls.getMethods()) {
                if (method.getName().equals("render")) {
                    for (int i = 0; i < method.getProgram().basicBlockCount(); i++) {
                        for (org.teavm.model.Instruction instruction : method.getProgram().basicBlockAt(i)) {
                            if (!(instruction instanceof org.teavm.model.instructions.InvokeInstruction)) continue;
                            org.teavm.model.instructions.InvokeInstruction call =
                                    (org.teavm.model.instructions.InvokeInstruction) instruction;
                            if (!call.getMethod().getClassName().equals("com.badlogic.gdx.Game")
                                    || !call.getMethod().getName().equals("render")) continue;
                            call.setArguments(call.getInstance());
                            call.setInstance(null);
                            call.setMethod(new org.teavm.model.MethodReference("GangfortScreenTransitions",
                                    "render", ValueType.object("com.badlogic.gdx.Game"), ValueType.VOID));
                            call.setType(org.teavm.model.instructions.InvocationType.SPECIAL);
                        }
                    }
                }
                if (!method.getDescriptor().toString().equals("setScreen(Lcom/badlogic/gdx/Screen;)V")) continue;
                int disposals = 0;
                for (int i = 0; i < method.getProgram().basicBlockCount(); i++) {
                    for (org.teavm.model.Instruction instruction : method.getProgram().basicBlockAt(i)) {
                        if (instruction instanceof org.teavm.model.instructions.InvokeInstruction) {
                            org.teavm.model.instructions.InvokeInstruction call =
                                    (org.teavm.model.instructions.InvokeInstruction) instruction;
                            if (call.getMethod().getClassName().equals("com.badlogic.gdx.Screen")
                                    && call.getMethod().getName().equals("dispose")) {
                                // The current screen is already disposed by wl.h's history loop.
                                if (++disposals == 2) call.delete();
                            }
                        }
                    }
                }
                if (disposals != 2) throw new IllegalStateException("Unexpected Gangfort screen ownership");
                deferScreenChange(method);
            }
        }
        if (cls.getName().equals("aig")) {
            for (MethodHolder method : cls.getMethods()) {
                if (!method.getName().equals("<init>")) continue;
                org.teavm.model.Program program = method.getProgram();
                for (int i = 0; i < program.basicBlockCount(); i++) {
                    for (org.teavm.model.Instruction instruction : program.basicBlockAt(i)) {
                        if (!(instruction instanceof org.teavm.model.instructions.ExitInstruction)) continue;
                        org.teavm.model.instructions.GetFieldInstruction group =
                                new org.teavm.model.instructions.GetFieldInstruction();
                        group.setInstance(program.variableAt(0));
                        group.setField(new org.teavm.model.FieldReference("aig", "a"));
                        group.setFieldType(ValueType.object("com.badlogic.gdx.scenes.scene2d.Group"));
                        group.setReceiver(program.createVariable());
                        org.teavm.model.instructions.InvokeInstruction arrange =
                                new org.teavm.model.instructions.InvokeInstruction();
                        arrange.setType(org.teavm.model.instructions.InvocationType.SPECIAL);
                        arrange.setMethod(new org.teavm.model.MethodReference("VitaControlHelp",
                                "arrange", ValueType.object("com.badlogic.gdx.scenes.scene2d.Group"), ValueType.VOID));
                        arrange.setArguments(group.getReceiver());
                        instruction.insertPrevious(group);
                        instruction.insertPrevious(arrange);
                    }
                }
            }
        }
        if (cls.getName().equals("aeh")) {
            for (MethodHolder method : cls.getMethods()) {
                if (method.getProgram() == null) continue;
                for (int i = 0; i < method.getProgram().basicBlockCount(); i++) {
                    for (org.teavm.model.Instruction instruction : method.getProgram().basicBlockAt(i)) {
                        if (instruction instanceof org.teavm.model.instructions.StringConstantInstruction) {
                            org.teavm.model.instructions.StringConstantInstruction text =
                                    (org.teavm.model.instructions.StringConstantInstruction) instruction;
                            if (text.getConstant().equals("gamepad_info_showed")) {
                                text.setConstant("vita_controls_lines_showed");
                            }
                        }
                    }
                }
            }
        }
        for (MethodHolder method : cls.getMethods()) {
            String label = cls.getName() + "." + method.getName();
            if ((cls.getName().equals("akz") && (method.getName().equals("c")
                    || method.getName().equals("d") || method.getName().equals("f")))
                    || label.equals("ale.a") || label.equals("wl.e")
                    || (cls.getName().equals("aeb") && method.getName().equals("<init>"))) {
                checkpoints(method, label);
                if (cls.getName().equals("aeb") && method.getName().equals("<init>")) {
                    traceScreenSetup(method);
                }
            } else if (cls.getName().equals("com.badlogic.gdx.graphics.glutils.ShaderProgram")
                    && (method.getName().equals("<init>") || method.getName().equals("compileShaders")
                    || method.getName().equals("fetchAttributes") || method.getName().equals("fetchUniforms")
                    || method.getName().equals("addManagedShader"))) {
                checkpoints(method, label);
            }
        }
        if (cls.getName().equals("adv")) {
            for (MethodHolder method : cls.getMethods()) {
                if (method.getName().equals("clicked")) guardExternalLink(method);
            }
        } else if (cls.getName().equals("aga")) {
            for (MethodHolder method : cls.getMethods()) {
                String signature = method.getDescriptor().toString();
                boolean refresh = signature.equals("a(Z)V");
                if (!refresh && !signature.equals("a(Ljava/lang/String;Lcom/gangfort/game/network/ServerConfig;)V")) continue;
                ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                emitter.var(0, ValueType.object("aga")).invokeSpecial("a", void.class,
                        emitter.constant("Online multiplayer is unavailable on PS Vita."),
                        emitter.constant(4));
                emitter.exit();
            }
        } else if (cls.getName().equals("com.badlogic.gdx.utils.reflect.ClassReflection")) {
            for (MethodHolder method : cls.getMethods()) {
                if (!method.getName().equals("newInstance")) continue;
                ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                emitter.invoke(com.gangfort.vita.GangfortObjectFactory.class, "newInstance",
                        Object.class, emitter.var(1, Class.class))
                        .returnValue();
            }
        } else if (cls.getName().equals("javax.xml.parsers.DocumentBuilderFactory")) {
            for (MethodHolder method : cls.getMethods()) {
                if (!method.getName().equals("newInstance")
                        || method.getDescriptor().parameterCount() != 0) continue;
                ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                emitter.construct(com.stsvita.backend.VitaXmlDocumentBuilderFactory.class).returnValue();
            }
        } else if (cls.getName().equals("java.lang.Thread")) {
            for (MethodHolder method : cls.getMethods()) {
                if (!method.getName().equals("getStackTrace")
                        || method.getDescriptor().parameterCount() != 0) continue;
                org.teavm.model.TextLocation location = null;
                for (int i = 0; i < method.getProgram().basicBlockCount(); i++) {
                    for (org.teavm.model.Instruction instruction : method.getProgram().basicBlockAt(i)) {
                        if (instruction.getLocation() != null) location = instruction.getLocation();
                    }
                }
                ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                emitter.setCurrentLocation(location);
                org.teavm.model.emit.ValueEmitter current =
                        emitter.invoke(Thread.class, "currentThread", Thread.class);
                emitter.when(emitter.var(0, Thread.class).isSame(current))
                        .thenDo(() -> emitter.invoke(org.teavm.runtime.ExceptionHandling.class,
                                "fillStackTrace", StackTraceElement[].class).returnValue())
                        .elseDo(() -> emitter.constructArray(StackTraceElement.class, 0).returnValue());
            }
        } else if (cls.getName().equals("java.lang.Class")) {
            for (MethodHolder method : cls.getMethods()) {
                if (method.getName().equals("newInstance")) {
                    ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                    emitter.invoke(com.gangfort.vita.GangfortObjectFactory.class, "newInstance",
                            Object.class, emitter.var(0, Class.class)).returnValue();
                } else if (method.getName().equals("getDeclaredConstructors")) {
                    ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                    emitter.constructArray(java.lang.reflect.Constructor.class, 0).returnValue();
                } else if (method.getName().equals("getDeclaredMethods")) {
                    ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                    emitter.constructArray(java.lang.reflect.Method.class, 0).returnValue();
                } else if (method.getName().equals("getTypeParameters")) {
                    ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                    emitter.constructArray(java.lang.reflect.TypeVariable.class, 0).returnValue();
                } else if (method.getName().equals("getGenericInterfaces")) {
                    ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                    emitter.constructArray(java.lang.reflect.Type.class, 0).returnValue();
                } else if (method.getName().equals("getGenericSuperclass")) {
                    ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                    emitter.constantNull(java.lang.reflect.Type.class).returnValue();
                } else if (method.getName().equals("fillNameMap")) {
                    ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                    emitter.setField("java.lang.Class", "nameMap",
                            emitter.construct(java.util.HashMap.class));
                    emitter.exit();
                }
            }
        } else if (cls.getName().equals("java.lang.reflect.Constructor")) {
            for (MethodHolder method : cls.getMethods()) {
                if (!method.getName().equals("newInstance")) continue;
                ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                emitter.invoke(com.gangfort.vita.GangfortObjectFactory.class, "newInstance",
                        Object.class, emitter.var(0, java.lang.reflect.Constructor.class),
                        emitter.var(1, Object[].class)).returnValue();
            }
        } else if (cls.getName().equals("java.lang.reflect.Method")) {
            for (MethodHolder method : cls.getMethods()) {
                if (!method.getName().equals("invoke")) continue;
                ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                emitter.construct(UnsupportedOperationException.class,
                        emitter.constant("Method.invoke is unavailable in the Vita AOT runtime"))
                        .raise();
            }
        } else if (cls.getName().equals("com.badlogic.gdx.utils.reflect.Field")) {
            for (MethodHolder method : cls.getMethods()) {
                if (!method.getName().equals("getElementType")) continue;
                org.teavm.model.Program program = method.getProgram();
                for (int i = 0; i < program.basicBlockCount(); i++) {
                    for (org.teavm.model.Instruction instruction : program.basicBlockAt(i)) {
                        if (!(instruction instanceof org.teavm.model.instructions.InvokeInstruction)) continue;
                        org.teavm.model.instructions.InvokeInstruction call =
                                (org.teavm.model.instructions.InvokeInstruction) instruction;
                        if (call.getMethod().getClassName().equals("java.lang.reflect.Field")
                                && call.getMethod().getName().equals("getGenericType")) {
                            // Skin fields use concrete classes; game collection types are explicit in GangfortTypes.
                            call.setMethod(new org.teavm.model.MethodReference(
                                    java.lang.reflect.Field.class, "getType", Class.class));
                        }
                    }
                }
            }
        } else if (cls.getName().equals("com.badlogic.gdx.utils.reflect.Constructor")) {
            for (MethodHolder method : cls.getMethods()) {
                if (!method.getName().equals("newInstance")) continue;
                ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                org.teavm.model.emit.ValueEmitter type = emitter
                        .var(0, ValueType.object("com.badlogic.gdx.utils.reflect.Constructor"))
                        .invokeVirtual("getDeclaringClass", Class.class);
                emitter.invoke(com.gangfort.vita.GangfortObjectFactory.class, "newInstance",
                        Object.class, type, emitter.var(1, Object[].class)).returnValue();
            }
        } else if (cls.getName().equals("com.badlogic.gdx.graphics.glutils.ETC1")) {
            for (MethodHolder method : cls.getMethods()) {
                if (method.getName().equals("getWidthPKM")) {
                    method.getModifiers().remove(ElementModifier.NATIVE);
                    ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                    emitter.invoke(com.gangfort.vita.VitaEtc1.class, "getWidthPKM", int.class,
                            emitter.var(1, java.nio.ByteBuffer.class), emitter.var(2, int.class))
                            .returnValue();
                } else if (method.getName().equals("getHeightPKM")) {
                    method.getModifiers().remove(ElementModifier.NATIVE);
                    ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                    emitter.invoke(com.gangfort.vita.VitaEtc1.class, "getHeightPKM", int.class,
                            emitter.var(1, java.nio.ByteBuffer.class), emitter.var(2, int.class))
                            .returnValue();
                } else if (method.getName().equals("decodeImage")
                        && method.getDescriptor().parameterCount() == 7) {
                    method.getModifiers().remove(ElementModifier.NATIVE);
                    ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
                    emitter.construct(UnsupportedOperationException.class,
                            emitter.constant("VitaGL ETC1 compressed upload is required"))
                            .raise();
                }
            }
        }
    }

    private static void deferScreenChange(MethodHolder method) {
        org.teavm.model.Program program = method.getProgram();
        org.teavm.model.BasicBlock entry = program.basicBlockAt(0);
        org.teavm.model.BasicBlock immediate = program.createBasicBlock();
        while (entry.getFirstInstruction() != null) {
            org.teavm.model.Instruction instruction = entry.getFirstInstruction();
            instruction.delete();
            immediate.add(instruction);
        }
        org.teavm.model.instructions.InvokeInstruction defer =
                new org.teavm.model.instructions.InvokeInstruction();
        defer.setType(org.teavm.model.instructions.InvocationType.SPECIAL);
        defer.setMethod(new org.teavm.model.MethodReference("GangfortScreenTransitions",
                "defer", ValueType.object("com.badlogic.gdx.Game"),
                ValueType.object("com.badlogic.gdx.Screen"), ValueType.BOOLEAN));
        defer.setArguments(program.variableAt(0), program.variableAt(1));
        defer.setReceiver(program.createVariable());
        entry.add(defer);
        org.teavm.model.BasicBlock queued = program.createBasicBlock();
        queued.add(new org.teavm.model.instructions.ExitInstruction());
        org.teavm.model.instructions.BranchingInstruction branch =
                new org.teavm.model.instructions.BranchingInstruction(
                        org.teavm.model.instructions.BranchingCondition.NOT_EQUAL);
        branch.setOperand(defer.getReceiver());
        branch.setConsequent(queued);
        branch.setAlternative(immediate);
        entry.add(branch);
    }

    private static void guardExternalLink(MethodHolder method) {
        org.teavm.model.Program program = method.getProgram();
        for (int i = 0; i < program.basicBlockCount(); i++) {
            for (org.teavm.model.Instruction instruction : program.basicBlockAt(i)) {
                if (!(instruction instanceof org.teavm.model.instructions.InvokeInstruction)) continue;
                org.teavm.model.instructions.InvokeInstruction call =
                        (org.teavm.model.instructions.InvokeInstruction) instruction;
                if (!call.getMethod().getClassName().equals("com.badlogic.gdx.Net")
                        || !call.getMethod().getName().equals("openURI")) continue;
                if (call.getReceiver() == null) call.setReceiver(program.createVariable());
                org.teavm.model.BasicBlock continuation = program.createBasicBlock();
                while (call.getNext() != null) {
                    org.teavm.model.Instruction next = call.getNext();
                    next.delete();
                    continuation.add(next);
                }
                org.teavm.model.BasicBlock unavailable = program.createBasicBlock();
                unavailable.add(new org.teavm.model.instructions.ExitInstruction());
                org.teavm.model.instructions.BranchingInstruction branch =
                        new org.teavm.model.instructions.BranchingInstruction(
                                org.teavm.model.instructions.BranchingCondition.NOT_EQUAL);
                branch.setOperand(call.getReceiver());
                branch.setConsequent(continuation);
                branch.setAlternative(unavailable);
                call.insertNext(branch);
                return;
            }
        }
        throw new IllegalStateException("Like-page handler no longer contains Net.openURI");
    }

    private static void checkpoints(MethodHolder method, String label) {
        org.teavm.model.Program program = method.getProgram();
        if (program == null || program.basicBlockCount() == 0) return;
        java.util.List<org.teavm.model.Instruction> exits = new java.util.ArrayList<>();
        for (int i = 0; i < program.basicBlockCount(); i++) {
            for (org.teavm.model.Instruction instruction : program.basicBlockAt(i)) {
                if (instruction instanceof org.teavm.model.instructions.ExitInstruction) exits.add(instruction);
            }
        }
        checkpointBefore(program, program.basicBlockAt(0).getFirstInstruction(), label + " begin");
        for (org.teavm.model.Instruction exit : exits) checkpointBefore(program, exit, label + " end");
    }

    private static void traceScreenSetup(MethodHolder method) {
        org.teavm.model.Program program = method.getProgram();
        java.util.List<org.teavm.model.instructions.InvokeInstruction> calls = new java.util.ArrayList<>();
        for (int i = 0; i < program.basicBlockCount(); i++) {
            for (org.teavm.model.Instruction instruction : program.basicBlockAt(i)) {
                if (!(instruction instanceof org.teavm.model.instructions.InvokeInstruction)) continue;
                org.teavm.model.instructions.InvokeInstruction call =
                        (org.teavm.model.instructions.InvokeInstruction) instruction;
                String owner = call.getMethod().getClassName();
                String name = call.getMethod().getName();
                boolean shader = owner.equals("com.badlogic.gdx.graphics.glutils.ShaderProgram")
                        && (name.equals("<init>") || name.equals("isCompiled") || name.equals("begin")
                        || name.equals("setUniformi") || name.equals("setUniformf") || name.equals("end"));
                boolean texture = owner.equals("com.badlogic.gdx.graphics.Texture") && name.equals("bind");
                boolean activeTexture = owner.equals("com.badlogic.gdx.graphics.GL20") && name.equals("glActiveTexture");
                boolean camera = owner.equals("com.badlogic.gdx.graphics.OrthographicCamera") && name.equals("<init>");
                boolean profiler = owner.equals("com.badlogic.gdx.graphics.profiling.GLProfiler") && name.equals("enable");
                if (shader || texture || activeTexture || camera || profiler) calls.add(call);
            }
        }
        for (org.teavm.model.instructions.InvokeInstruction call : calls) {
            org.teavm.model.Instruction after = call.getNext();
            if (after != null) checkpointBefore(program, after, "aeb setup " + call.getMethod().getName() + " end");
            checkpointBefore(program, call, "aeb setup " + call.getMethod().getName() + " begin");
        }
    }

    private static void checkpointBefore(org.teavm.model.Program program,
            org.teavm.model.Instruction anchor, String label) {
        org.teavm.model.instructions.StringConstantInstruction message =
                new org.teavm.model.instructions.StringConstantInstruction();
        message.setConstant(label);
        message.setReceiver(program.createVariable());
        org.teavm.model.instructions.InvokeInstruction call = new org.teavm.model.instructions.InvokeInstruction();
        call.setType(org.teavm.model.instructions.InvocationType.SPECIAL);
        call.setMethod(new org.teavm.model.MethodReference(com.stsvita.backend.VitaNatives.class,
                "diag", String.class, void.class));
        call.setArguments(message.getReceiver());
        anchor.insertPrevious(message);
        anchor.insertPrevious(call);
    }
}
