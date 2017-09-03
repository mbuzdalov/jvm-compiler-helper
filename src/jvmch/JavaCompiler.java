package jvmch;

import javax.tools.ToolProvider;
import java.io.*;
import java.util.*;
import java.util.jar.*;

/**
 * Created by Niyaz Nigmatullin on 11.04.17.
 * Ported to Java by Maxim Buzdalov on 03.09.17.
 */
public class JavaCompiler extends Module {
    private static final int COPY_BUFFER_SIZE = 0x10000;
    private static final Random rng = new Random(239L);

    @Override
    public boolean checkArgs(String[] args, int argumentOffset) {
        return args.length >= 3;
    }

    @Override
    public boolean run(String[] args) {
        File tempDir = new File(args[0]);
        File jarFile = new File(args[1]);
        File[] sourceFiles = new File[args.length - 2];
        for (int i = 0; i < sourceFiles.length; ++i) {
            sourceFiles[i] = new File(args[i + 2]);
        }
        int exitCode = compile(tempDir, jarFile, sourceFiles);
        return exitCode == 0;
    }

    @Override
    public String getUsage() {
        return "compiles Java source files given. Arguments:\n"
                + "        <temporary directory> <resulting jar file> <source files>";
    }

    private int compile(File tempDir, File file, File[] sources) {
        try {
            int exitCode = compile(tempDir, copyFiles(tempDir, sources));
            if (exitCode != 0) {
                return exitCode;
            }
            List<String> classes = new ArrayList<>();
            findFiles(tempDir, "", ".class", classes);
            createJar(file, tempDir, classes.toArray(new String[classes.size()]));
            return 0;
        } catch (IOException e) {
            e.printStackTrace();
            return 100;
        }
    }

    private int compile(File dir, File[] sources) throws IOException {
        String[] args = new String[sources.length + 2];
        args[0] = "-d";
        args[1] = dir.getCanonicalPath();
        for (int i = 0; i < sources.length; ++i) {
            args[i + 2] = sources[i].getCanonicalPath();
        }
        return ToolProvider.getSystemJavaCompiler().run(null, null, null, args);
    }

    private File[] copyFiles(File dir, File[] files) throws IOException {
        dir.mkdirs();
        File[] rv = new File[files.length];
        for (int i = 0; i < files.length; ++i) {
            rv[i] = new File(dir, getFilename(files[i]));
            copyFile(files[i], rv[i]);
        }
        return rv;
    }

    private String getFilename(File file) throws IOException {
        return getClassName(file).replace('.', '/') + ".java";
    }

    private void findFiles(File dir, String path, String extension, List<String> result) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findFiles(file, path + file.getName() + "/", extension, result);
                } else if (file.getName().endsWith(extension)) {
                    result.add(path + file.getName());
                }
            }
        }
    }

    private String getClassName(File file) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            StreamTokenizer tokenizer = new StreamTokenizer(reader);
            tokenizer.wordChars('_', '_');
            tokenizer.wordChars('0', '9');
            tokenizer.wordChars('$', '$');
            tokenizer.slashSlashComments(true);
            tokenizer.slashStarComments(true);
            String packageName = "";
            int blocks = 0;
            boolean publicFound = false;
            while (tokenizer.ttype != StreamTokenizer.TT_EOF) {
                if (tokenizer.ttype == '{') {
                    blocks++;
                } else if (tokenizer.ttype == '}') {
                    blocks--;
                } else if (tokenizer.ttype == StreamTokenizer.TT_WORD) {
                    if ("package".equals(tokenizer.sval)) {
                        do {
                            tokenizer.nextToken();
                            packageName += tokenizer.sval + '.';
                            tokenizer.nextToken();
                        } while (tokenizer.ttype != ';');
                    } else if ("public".equals(tokenizer.sval) && blocks == 0) {
                        publicFound = true;
                    } else if (("class".equals(tokenizer.sval) || "interface".equals(tokenizer.sval)) && publicFound) {
                        tokenizer.nextToken();
                        return packageName + tokenizer.sval;
                    }
                }
                tokenizer.nextToken();
            }
            StringBuilder randomName = new StringBuilder();
            for (int i = 0; i < 10; ++i) {
                randomName.append((char) (rng.nextInt(26) + 'a'));
            }
            return packageName + "$$__" + randomName.toString();
        }
    }

    private void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int bytes;
        while ((bytes = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, bytes);
        }
    }

    private void copyFile(File fromFile, File toFile) throws IOException {
        toFile.getAbsoluteFile().getParentFile().mkdirs();
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(fromFile));
             BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(toFile))) {
            copyStream(inputStream, outputStream);
        }
    }

    private void createJar(File jarFile, File dir, String[] files) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(jarFile);
             JarOutputStream jos = new JarOutputStream(fos)) {
            for (String file : files) {
                jos.putNextEntry(new JarEntry(file));
                try (FileInputStream fis = new FileInputStream(new File(dir, file));
                     BufferedInputStream inputStream = new BufferedInputStream(fis)) {
                    copyStream(inputStream, jos);
                }
            }
        }
    }
}
