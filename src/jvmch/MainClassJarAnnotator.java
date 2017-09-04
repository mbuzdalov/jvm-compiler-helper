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
    private static final String VERBOSE = "--verbose";

    private static final Set<String> possibleExtraArgs = new HashSet<>(Arrays.asList(FORCE_OVERWRITE, USE_FIRST, VERBOSE));

    @Override
    public boolean checkArgs(String[] args, int argumentOffset) {
        for (int i = 2; i < args.length; ++i) {
            if (!possibleExtraArgs.contains(args[i])) {
                PrintStream err = System.err;
                err.println("Error: expected the arguments for the command at index "
                        + (argumentOffset + 1) + " to be:");
                err.print("    <source-jar-name> <target-jar-name>");
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
        return "annotates the given JAR file with a Main-Class attribute.\n"
                + "            The arguments are: <source-jar-name> <target-jar-name> [" + FORCE_OVERWRITE + "] [" + USE_FIRST + "] [" + VERBOSE + "], where:\n"
                + "                <source-jar-name> is the path to the JAR file to be read;\n"
                + "                <target-jar-name> is the path to the JAR file to be created;\n"
                + "                " + FORCE_OVERWRITE + " means to overwrite the existing Main-Class attribute;\n"
                + "                " + USE_FIRST + " means to use the first main class when multiple ones are found;\n"
                + "                " + VERBOSE + " enables printing non-error messages to the standard output.";
    }

    private String getName(JarEntry entry) {
        String name = entry.getName().replace('/', '.');
        if (name.endsWith(".class")) {
            return name.substring(0, name.length() - ".class".length());
        } else {
            throw new AssertionError("The class file name does not end with .class: '" + name + "'");
        }
    }

    private boolean isMainClass(InputStream stream) {
        try {
            DataInputStream ds = new DataInputStream(stream);
            if (ds.readInt() != 0xCAFEBABE) {
                return false;
            }
            ds.readUnsignedShort(); // minor version
            ds.readUnsignedShort(); // major version
            int constantPoolCount = ds.readUnsignedShort(); // not very large, can continue without checking
            if (constantPoolCount <= 0) {
                return false;
            }
            String[] strings = new String[constantPoolCount];
            for (int i = 1; i < constantPoolCount; ++i) { // this starts really from 1, see JLS.
                int tag = ds.readUnsignedByte();
                switch (tag) {
                    case 1: { // "UTF-8"
                        strings[i] = ds.readUTF();
                        break;
                    }
                    case 3: // Integer
                    case 4: // Float
                        ds.readInt();
                        break;
                    case 5: // Long
                    case 6: // Double
                        ds.readLong();
                        break;
                    case 7: // Class
                    case 8: // String
                    case 16: // Method type
                        ds.readUnsignedShort();
                        break;
                    case 9: // Field reference
                    case 10: // Method reference
                    case 11: // Interface method reference
                    case 12: // Name and type
                    case 18: // Invoke dynamic
                        ds.readUnsignedShort();
                        ds.readUnsignedShort();
                        break;
                    case 15: // Method handle
                        ds.readUnsignedByte();
                        ds.readUnsignedShort();
                        break;
                    default:
                        return false;
                }
            }
            ds.readUnsignedShort(); // access flags
            ds.readUnsignedShort(); // this class
            ds.readUnsignedShort(); // super class
            int interfaceCount = ds.readUnsignedShort();
            for (int i = 0; i < interfaceCount; ++i) {
                ds.readUnsignedShort();
            }
            int fieldsCount = ds.readUnsignedShort();
            for (int i = 0; i < fieldsCount; ++i) {
                ds.readUnsignedShort(); // access flags
                ds.readUnsignedShort(); // name index
                ds.readUnsignedShort(); // descriptor index
                int attributesCount = ds.readUnsignedShort();
                for (int a = 0; a < attributesCount; ++a) {
                    ds.readUnsignedShort(); // attribute name index
                    int length = ds.readInt(); // attribute length
                    ds.skipBytes(length);
                }
            }
            int methodsCount = ds.readUnsignedShort();
            boolean hasMainMethod = false;
            for (int i = 0; i < methodsCount; ++i) {
                int accessFlags = ds.readUnsignedShort();
                int nameIndex = ds.readUnsignedShort();
                int descriptorIndex = ds.readUnsignedShort();
                int attributesCount = ds.readUnsignedShort();
                for (int a = 0; a < attributesCount; ++a) {
                    ds.readUnsignedShort(); // attribute name index
                    int length = ds.readInt(); // attribute length
                    ds.skipBytes(length);
                }
                if (nameIndex > 0 && nameIndex < constantPoolCount
                        && descriptorIndex > 0 && descriptorIndex < constantPoolCount) {
                    String name = strings[nameIndex];
                    String desc = strings[descriptorIndex];
                    if ((accessFlags & 9) == 9
                            && name != null && name.equals("main")
                            && desc != null && desc.equals("([Ljava/lang/String;)V")) {
                        hasMainMethod = true;
                    }
                }
            }
            int attributesCount = ds.readUnsignedShort();
            for (int a = 0; a < attributesCount; ++a) {
                ds.readUnsignedShort(); // attribute name index
                int length = ds.readInt(); // attribute length
                ds.skipBytes(length);
            }
            return hasMainMethod;
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public boolean run(String[] args) {
        try {
            String sourceJarFileName = args[0];
            String targetJarFileName = args[1];
            boolean forceOverwrite = false;
            boolean useFirst = false;
            boolean verbose = false;
            for (int i = 2; i < args.length; ++i) {
                forceOverwrite |= args[i].equals(FORCE_OVERWRITE);
                useFirst |= args[i].equals(USE_FIRST);
                verbose |= args[i].equals(VERBOSE);
            }

            byte[] jarFile = Files.readAllBytes(Paths.get(sourceJarFileName));
            List<String> mainClasses = new ArrayList<>();
            Manifest manifest;
            try (JarInputStream input = new JarInputStream(new ByteArrayInputStream(jarFile))) {
                manifest = input.getManifest();
                if (manifest != null) {
                    Attributes attributes = manifest.getMainAttributes();
                    String ofMain = attributes.getValue(Attributes.Name.MAIN_CLASS);
                    if (ofMain != null && !forceOverwrite) {
                        if (verbose) {
                            System.out.println("This file already has Main-Class set to " + ofMain);
                        }
                        // No --force-overwrite is specified.
                        Files.copy(Paths.get(sourceJarFileName), Paths.get(targetJarFileName));
                        return true;
                    }
                }

                JarEntry current;
                while ((current = input.getNextJarEntry()) != null) {
                    if (isMainClass(input)) {
                        mainClasses.add(getName(current));
                    }
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
            if (verbose) {
                System.out.println("Setting the Main-Class attribute to " + mainClassName);
            }
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClassName);

            try (FileOutputStream fileOut = new FileOutputStream(targetJarFileName);
                 JarOutputStream output = new JarOutputStream(fileOut, manifest);
                 JarInputStream input = new JarInputStream(new ByteArrayInputStream(jarFile))) {
                JarEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = input.getNextJarEntry()) != null) {
                    if (entry.getName().equals("META-INF/MANIFEST.MF")) {
                        continue;
                    }
                    output.putNextEntry(entry);
                    int sz;
                    while ((sz = input.read(buffer)) > 0) {
                        output.write(buffer, 0, sz);
                    }
                }
            }
            return true;
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }
}
