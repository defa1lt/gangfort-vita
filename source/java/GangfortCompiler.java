import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMProblemRenderer;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMTool;
import org.teavm.vm.TeaVMOptimizationLevel;

public final class GangfortCompiler {
    public static void main(String[] args) throws Exception {
        File input = new File(args[0]);
        if (!input.isFile()) throw new IllegalArgumentException("Missing game JAR: " + input);
        TeaVMTool tool = new TeaVMTool();
        tool.setTargetType(TeaVMTargetType.C);
        tool.setMainClass(args.length > 2 ? args[2] : "GangfortLauncher");
        tool.setTargetDirectory(new File(args[1]));
        tool.setObfuscated(false);
        tool.setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE);
        tool.setMinHeapSize(64 * 1024 * 1024);
        tool.setMaxHeapSize(64 * 1024 * 1024);
        tool.getTransformers().add(GangfortClasslibShims.class.getName());
        try (URLClassLoader loader = new URLClassLoader(new URL[]{input.toURI().toURL()},
                GangfortCompiler.class.getClassLoader())) {
            tool.setClassLoader(loader);
            tool.generate();
            TeaVMProblemRenderer.describeProblems(tool.getDependencyInfo().getCallGraph(),
                    tool.getProblemProvider(), new ConsoleTeaVMToolLog(true));
            if (!tool.getProblemProvider().getSevereProblems().isEmpty()) {
                throw new IllegalStateException("Gangfort is not linkable yet; see unresolved dependencies above");
            }
        }
    }

}
