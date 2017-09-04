package jvmch;

import java.io.PrintStream;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * This is the entry point to the JVM Compiler Helper facility.
 *
 * @author Maxim Buzdalov
 */
public class Main {
    private static void printUsageAndExit(Map<String, Module> modules) {
        PrintStream err = System.err;

        err.println("Usage: " + Main.class.getCanonicalName() + " <command> [command-arguments] ['--then' <command> [command-arguments]]*");
        err.println("    where <command> is one of:");
        for (Map.Entry<String, Module> entry : modules.entrySet()) {
            err.println("        " + entry.getKey() + ": " + entry.getValue().getUsage());
        }
        System.exit(1);
    }

    private static int indexOfSharpOrLength(String[] array, int from) {
        for (int i = from; i < array.length; ++i) {
            if (array[i].equals("--then")) {
                return i;
            }
        }
        return array.length;
    }

    public static void main(String[] args) {
        Map<String, Module> modules = new HashMap<>();
        modules.put("annotate-jar-with-main-class-attribute", new MainClassJarAnnotator());
        modules.put("compile-java-files", new JavaCompiler());
        modules.put("merge-jar-files", new JarFilesMerger());

        if (args.length == 0) {
            printUsageAndExit(modules);
            return;
        }

        List<BooleanSupplier> commandsToRun = new ArrayList<>();
        for (int cmd = 0; cmd < args.length; ++cmd) {
            Module current = modules.get(args[cmd]);
            if (current == null) {
                System.err.println("Error: command line argument no. " + (cmd + 1)
                        + ", which is '" + args[cmd] + "' does not name a module.");
                printUsageAndExit(modules);
                return;
            }
            int first = ++cmd;
            int last = indexOfSharpOrLength(args, first + 1);
            String[] localArgs = Arrays.copyOfRange(args, first, last);
            if (!current.checkArgs(localArgs, first)) {
                printUsageAndExit(modules);
                return;
            }
            commandsToRun.add(() -> current.run(localArgs));
            cmd = last;
        }

        for (BooleanSupplier r : commandsToRun) {
            if (!r.getAsBoolean()) {
                System.exit(1);
                return;
            }
        }
    }
}
