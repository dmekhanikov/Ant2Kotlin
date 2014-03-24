package testData.DSLGenerator.copy

import ru.ifmo.rain.mekhanikov.antdsl.AntProperty
import ru.ifmo.rain.mekhanikov.antdsl.project
import ru.ifmo.rain.mekhanikov.antdsl.target
import ru.ifmo.rain.mekhanikov.antdsl.generated.taskdefs.copy
import ru.ifmo.rain.mekhanikov.antdsl.generated.taskdefs.fileset
import ru.ifmo.rain.mekhanikov.antdsl.generated.taskdefs.retry
import java.io.File

val srcDir : String by AntProperty<String>()
val destDir : String by AntProperty<String>()

fun main(args : Array<String>) {
    project(args) {
        default = target("copy test") {
            retry(retrycount = 3) {
                copy(todir = destDir) {
                    fileset(dir = srcDir)
                }
            }
        }
    }
}