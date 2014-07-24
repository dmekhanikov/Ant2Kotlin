package ru.ifmo.rain.mekhanikov.ant2kotlin

import java.io.File
import java.io.FileWriter

class KotlinSourceFile(public val pkg: String?) {
    public val importManager: ImportManager = ImportManager(pkg)
    private val body = StringBuilder("")

    public fun append(code: String) {
        body.append(code)
    }

    override public fun toString(): String {
        val result = StringBuilder()
        if (pkg != null) {
            result.append("package " + pkg + "\n\n")
        }
        if (!importManager.imports.empty) {
            result.append(importManager.toString()).append("\n")
        }
        result.append(body.toString())
        return result.toString()
    }

    public fun dump(file: File) {
        file.getParentFile()!!.mkdirs()
        file.createNewFile()
        val writer = FileWriter(file)
        writer.write(toString())
        writer.close()
    }
}
