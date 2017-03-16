package jvmch;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.*;

/**
 * This is the module for annotating JAR files with the main class.
 *
 * @author Maxim Buzdalov
 */
public class MainClassJarAnnotator extends Module {
    private static final String FORCE_OVERWRITE = "--force-overwrite";
    private static final String USE_FIRST = "--use-first";

    private static final Set<String> possibleExtraArgs = new HashSet<>(Arrays.asList(FORCE_OVERWRITE, USE_FIRST));

    @Override
    public boolean checkArgs(String[] args, int argumentOffset) {
        for (int i = 1; i < args.length; ++i) {
            if (!possibleExtraArgs.contains(args[i])) {
                PrintStream err = System.err;
                err.println("Error: expected the arguments for the command at index "
                        + (argumentOffset + 1) + " to be:");
                err.print("    <jar-name>");
                for (String s : possibleExtraArgs) {
                    err.print(" [");
                    err.print(s);
                    err.print("]");
                }
                err.println();
                err.print("Found:");
                for (String a : args) {
                    err.print(" '");
                    err.print(a);
                    err.print("'");
                }
                err.println();
                return false;
            }
        }
        return true;
    }

    @Override
    public String getUsage() {
        return "annotates the given JAR file with a Main-Class attribute.";
    }

    private String getName(JarEntry entry) {
        return entry.getName().replace('/', '.');
    }

    private boolean isMainClass(InputStream stream) {
        return false;
    }

    @Override
    public boolean run(String[] args) {
        try {
            String jarFileName = args[0];
            boolean forceOverwrite = false;
            boolean useFirst = false;
            for (int i = 1; i < args.length; ++i) {
                forceOverwrite |= args[i].equals(FORCE_OVERWRITE);
                useFirst |= args[i].equals(USE_FIRST);
            }

            byte[] jarFile = Files.readAllBytes(Paths.get(jarFileName));
            List<String> mainClasses = new ArrayList<>();
            Manifest manifest;
            try (JarInputStream input = new JarInputStream(new ByteArrayInputStream(jarFile))) {
                manifest = input.getManifest();
                if (manifest != null) {
                    Attributes attributes = manifest.getMainAttributes();
                    String ofMain = attributes.getValue(Attributes.Name.MAIN_CLASS);
                    if (ofMain != null && !forceOverwrite) {
                        // No --force-overwrite is specified.
                        return true;
                    }
                }

                JarEntry current;
                while ((current = input.getNextJarEntry()) != null) {
                    if (isMainClass(input)) {
                        mainClasses.add(getName(current));
                    }
                    input.closeEntry();
                }
            }
            if (mainClasses.size() == 0) {
                throw new IOException("The JAR file contains no classes with 'public static void main(String[])' " +
                        "or an equivalent construction.");
            }
            if (mainClasses.size() > 1 && !useFirst) {
                StringBuilder sb = new StringBuilder();
                sb.append("The JAR file contains two or more classes with 'public static void main(String[])' ");
                sb.append("or equivalent constructions:");
                for (String e : mainClasses) {
                    sb.append(" ");
                    sb.append(e);
                }
                throw new IOException(sb.toString());
            }
            String mainClassName = mainClasses.get(0);
            if (manifest == null) {
                manifest = new Manifest();
            }
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClassName);

            try (FileOutputStream fileOut = new FileOutputStream(jarFileName);
                 JarOutputStream output = new JarOutputStream(fileOut, manifest);
                 JarInputStream input = new JarInputStream(new ByteArrayInputStream(jarFile))) {
                JarEntry entry;
                while ((entry = input.getNextJarEntry()) != null) {
                    output.putNextEntry(entry);
                    input.closeEntry();
                }
            }
            return true;
        } catch (IOException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
