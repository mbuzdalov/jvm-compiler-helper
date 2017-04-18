package jvmch

import javax.tools.ToolProvider
import java.io.*
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * Created by Niyaz Nigmatullin on 11.04.17.
 */
class JavaCompiler : Module() {
    private val COPY_BUFFER_SIZE = 0x10000
    private val rng = Random(239L)

    override fun checkArgs(args: Array<String>, argumentOffset: Int): Boolean {
        return args.size >= 3
    }

    override fun run(args: Array<String>): Boolean {
        val tempDir = File(args[0])
        val jarFile = File(args[1])
        val sourceFiles = args.takeLast(args.size - 2).map(::File).toTypedArray()
        val exitCode = compile(tempDir, jarFile, sourceFiles)
        return exitCode == 0
    }

    override fun getUsage(): String {
        return "compiles Java source files given. Arguments:\n" +
                "        <temporary directory> <resulting jar file> <source files>"
    }

    private fun compile(tempDir: File, file: File, sources: Array<File>): Int {
        try {
            val exitCode = compile(tempDir, copyFiles(tempDir, sources))
            if (exitCode != 0) {
                return exitCode
            }
            val classes = mutableListOf<String>()
            findFiles(tempDir, "", ".class", classes)
            createJar(file, tempDir, classes.toTypedArray())
            return 0
        } catch (e: IOException) {
            e.printStackTrace()
            return 100
        }
    }

    private fun compile(dir: File, sources: Array<File>): Int {
        val args = arrayOfNulls<String>(sources.size + 2)
        args[0] = "-d"
        args[1] = dir.canonicalPath
        for (i in sources.indices) {
            args[i + 2] = sources[i].canonicalPath
        }
        val compiler = ToolProvider.getSystemJavaCompiler()
        return compiler.run(null, null, null, *args)
    }

    private fun copyFiles(dir: File, files: Array<File>): Array<File> {
        dir.mkdirs()
        return files.map {
            val file = File(dir, getFilename(it))
            copyFile(it, file)
            file
        }.toTypedArray()
    }

    private fun getFilename(file: File): String = getClassName(file).replace('.', '/') + ".java"

    private fun findFiles(dir: File, path: String, extension: String, result: MutableCollection<String>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                findFiles(file, path + file.name + "/", extension, result)
            } else if (file.name.endsWith(extension)) {
                result.add(path + file.name)
            }
        }
    }

    private fun getClassName(file: File): String {
        FileReader(file).use { reader ->
            val tokenizer = StreamTokenizer(reader)
            tokenizer.wordChars('_'.toInt(), '_'.toInt())
            tokenizer.wordChars('0'.toInt(), '9'.toInt())
            tokenizer.wordChars('$'.toInt(), '$'.toInt())
            tokenizer.slashSlashComments(true)
            tokenizer.slashStarComments(true)
            var packageName = ""
            var blocks = 0
            var publicFound = false
            while (tokenizer.ttype != StreamTokenizer.TT_EOF) {
                if (tokenizer.ttype == '{'.toInt()) {
                    blocks++
                } else if (tokenizer.ttype == '}'.toInt()) {
                    blocks--
                } else if (tokenizer.ttype == StreamTokenizer.TT_WORD) {
                    if ("package" == tokenizer.sval) {
                        do {
                            tokenizer.nextToken()
                            packageName = tokenizer.sval + '.'
                            tokenizer.nextToken()
                        } while (tokenizer.ttype != ';'.toInt())
                    } else if ("public" == tokenizer.sval && blocks == 0) {
                        publicFound = true
                    } else if (("class" == tokenizer.sval || "interface" == tokenizer.sval) && publicFound) {
                        tokenizer.nextToken()
                        return packageName + tokenizer.sval
                    }
                }
                tokenizer.nextToken()
            }
            var randomName = ""
            for (i in 0..10) {
                randomName += (rng.nextInt(26) + 'a'.toInt()).toChar()
            }
            return packageName + "\$\$__" + randomName
        }
    }

    private fun copyStream(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val bytes = inputStream.read(buffer)
            if (bytes == -1) break
            outputStream.write(buffer, 0, bytes)
        }
    }

    private fun copyFile(fromFile: File, toFile: File) {
        toFile.absoluteFile.parentFile.mkdirs()
        BufferedInputStream(FileInputStream(fromFile)).use { inputStream ->
            BufferedOutputStream(FileOutputStream(toFile)).use { outputStream ->
                copyStream(inputStream, outputStream)
            }
        }
    }

    private fun createJar(jarFile: File, dir: File, files: Array<String>) {
        JarOutputStream(FileOutputStream(jarFile)).use { jos ->
            for (file in files) {
                jos.putNextEntry(JarEntry(file))
                BufferedInputStream(FileInputStream(File(dir, file))).use { inputStream ->
                    copyStream(inputStream, jos)
                }
            }
        }
    }
}
