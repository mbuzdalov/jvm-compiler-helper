package jvmch;

import java.io.*;
import java.util.jar.*;

/**
 * Created by Niyaz Nigmatullin on 11.04.17.
 * Ported to Java by Maxim Buzdalov on 03.09.17.
 */
public class JarFilesMerger extends Module {
    @Override
    public boolean run(String[] args) {
        File resultFile = new File(args[0]);
        File[] files = new File[args.length - 1];
        for (int i = 0; i < files.length; ++i) {
            files[i] = new File(args[i + 1]);
        }
        try (FileOutputStream inputStream = new FileOutputStream(resultFile);
             JarOutputStream jos = new JarOutputStream(inputStream)) {
            for (File file : files) {
                try (FileInputStream outputStream = new FileInputStream(file);
                     JarInputStream jis = new JarInputStream(outputStream)) {
                    byte[] buffer = new byte[8192];
                    JarEntry entry;
                    while ((entry = jis.getNextJarEntry()) != null) {
                        jos.putNextEntry(entry);
                        int sz;
                        while ((sz = jis.read(buffer)) > 0) {
                            jos.write(buffer, 0, sz);
                        }
                    }
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String getUsage() {
        return "merges several JAR files, manifest file isn't copied.\n"
                + "            The arguments are: <target-jar-name> <source-jar-name-1> [<source-jar-name-2> [...]]";
    }

    @Override
    public boolean checkArgs(String[] args, int argumentOffset) {
        return args.length >= 3;
    }
}
