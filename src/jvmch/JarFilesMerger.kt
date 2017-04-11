package jvmch

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream

/**
 * Created by Niyaz Nigmatullin on 11.04.17.
 */

class JarFilesMerger: Module() {
    override fun run(args: Array<String>): Boolean {
        val resultFile = File(args[0])
        val files = args.takeLast(args.size - 1).map(::File)
        try {
            FileOutputStream(resultFile).use { inputStream ->
                JarOutputStream(inputStream).use { jos ->
                    for (file in files) {
                        FileInputStream(file).use { outputStream ->
                            JarInputStream(outputStream).use { jis ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val entry = jis.nextJarEntry ?: break
                                    jos.putNextEntry(entry)
                                    while (true) {
                                        val sz = jis.read(buffer)
                                        if (sz == -1) break
                                        jos.write(buffer, 0, sz)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
        return true
    }

    override fun getUsage(): String {
        return "merges several JAR files, manifest file isn't copied. The arguments are:\n" +
                "        <target-jar-name> <source-jar-name-1> [<source-jar-name-2> [...]]"
    }

    override fun checkArgs(args: Array<String>, argumentOffset: Int): Boolean {
        return args.size >= 3
    }
}