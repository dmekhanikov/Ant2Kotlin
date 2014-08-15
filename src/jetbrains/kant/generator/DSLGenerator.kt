package jetbrains.kant.generator

import jetbrains.kant.*
import java.io.*
import java.io.File.pathSeparator
import org.kohsuke.args4j.*
import java.util.HashMap
import java.util.HashSet
import java.util.ArrayList
import java.util.regex.Pattern
import java.util.jar.JarInputStream

public val DSL_ROOT: String = "dsl/src/"
public val DSL_PACKAGE: String = "jetbrains.kant.dsl"
val BASE_DSL_FILE_NAMES = array("Base.kt", "LazyTask.kt", "Misc.kt", "Properties.kt")
public val STRUCTURE_FILE_NAME: String = "resources/structure.ser"

fun main(args: Array<String>) {
    GeneratorRunner().doMain(args)
}

class GeneratorRunner {
    [Option(name = "-cp", usage = "classpath")]
    private val classpath: String = ""

    [Option(name = "-o", usage = "output directory", required = true)]
    private val outDir: File? = null

    [Option(name = "--seek", usage = "seek alias files")]
    private val seek = false

    [Option(name = "--defaultAliases", usage = "generate functions with names similar to class names")]
    private val defAl = false

    [Option(name = "--compile", usage = "compile the library after generation")]
    private val compile = false

    [Option(name = "--jar", usage = "create jar file containing all class files of the generated library and some information about its structure")]
    private val createJar = false

    [Argument]
    private val aliasFiles: Array<String> = array()

    fun doMain(args: Array<String>) {
        val parser = CmdLineParser(this)
        try {
            parser.parseArgument(args.toArrayList())
        } catch(e: CmdLineException) {
            System.err.println(e.getMessage())
            System.err.println("java GeneratorPackage [options...] arguments...")
            parser.printUsage(System.err)
            System.err.println()
            return
        }
        val srcRoot = outDir.toString() + "/src/"
        val outRoot = outDir.toString() + "/out/"
        val binRoot = outRoot + "bin/"
        val distRoot = outRoot + "dist/"
        copyBaseFiles(File(srcRoot))
        val classpathArray = classpath.split(pathSeparator)
        val generator = DSLGenerator(srcRoot, classpathArray, aliasFiles, seek, defAl)
        generator.generate()
        if (compile || createJar) {
            compileKotlinCode(srcRoot, classpath, binRoot)
            val outFile = File(binRoot + STRUCTURE_FILE_NAME)
            outFile.getParentFile()!!.mkdirs()
            val fileOut = FileOutputStream(outFile)
            val objectOutputStream = ObjectOutputStream(fileOut)
            objectOutputStream.writeObject(generator.structure)
            objectOutputStream.close()
            fileOut.close()
        }
        if (createJar) {
            val jarFile = distRoot + "kant.jar"
            createJar(jarFile, binRoot)
        }
    }
}

private fun copyBaseFiles(dest: File) {
    val srcPrefix = "$DSL_ROOT${DSL_PACKAGE.replace('.', '/')}/"
    val destPrefix = "$dest/${DSL_PACKAGE.replace('.', '/')}/"
    for (fileName in BASE_DSL_FILE_NAMES) {
        val srcFile = File(srcPrefix + fileName)
        val destFile = File(destPrefix + fileName)
        srcFile.copyTo(destFile)
    }
}

public val DSL_TASK_CONTAINER: String = "$DSL_PACKAGE.DSLTaskContainer"
public val DSL_TASK_CONTAINER_TASK: String = "$DSL_PACKAGE.DSLTaskContainerTask"
public val DSL_PROJECT: String = "$DSL_PACKAGE.DSLProject"
public val DSL_TARGET: String = "$DSL_PACKAGE.DSLTarget"
public val DSL_TASK: String = "$DSL_PACKAGE.DSLTask"
public val DSL_REFERENCE: String = "$DSL_PACKAGE.DSLReference"
public val DSL_PATH: String = "$DSL_PACKAGE.types.DSLPath"
public val DSL_TEXT_CONTAINER: String = "$DSL_PACKAGE.DSLTextContainer"
public val DSL_CONDITION: String = "$DSL_PACKAGE.DSLCondition"

public val DSL_PROJECT_FUNCTION: String = "$DSL_PACKAGE.project"
public val DSL_TARGET_FUNCTION: String = "$DSL_PACKAGE.target"

public val DSL_PROPERTIES_PACKAGE: String = DSL_PACKAGE

class DSLGenerator(resultRoot: String, val classpath: Array<String>, aliasFiles: Array<String>,
                   val seekForAliasFiles: Boolean = false, val defAl: Boolean = false) {
    private val resolved = HashMap<String, Target>()
    public val structure: HashMap<String, DSLClass> = HashMap()
    private val containerGenerator = ContainerGenerator()
    private var classLoader: ClassLoader = createClassLoader(classpath)
    private val resultRoot = resultRoot + if (resultRoot.endsWith('/')) {""} else {'/'}
    private val aliasFiles = ArrayList<String>(); {
        this.aliasFiles.addAll(aliasFiles)
        structure.put(DSL_TASK_CONTAINER, DSLClass(DSL_TASK_CONTAINER, ArrayList<String>()))
        structure.put(DSL_PROJECT, DSLClass(DSL_PROJECT, array(DSL_TASK_CONTAINER).toList()))
        structure.put(DSL_TARGET, DSLClass(DSL_TARGET, array(DSL_TASK_CONTAINER).toList()))
    }

    private var aliasReader: BufferedReader? = null

    private val PRIMITIVE_TYPES = array("Boolean", "Char", "Byte", "Short", "Int", "Float", "Long", "Double").toSet()

    private fun createAntClass(className: String): AntClass? {
        try {
            return AntClass(classLoader, className)
        } catch (e: ClassNotFoundException) {
            return null
        } catch(e: NoClassDefFoundError) {
            return null
        } catch(e: IllegalAccessError) {
            return null
        }
    }

    private fun addTarget(antClass: AntClass) {
        val className = antClass.className
        val target = Target(className)
        target.antClass = antClass
        resolved[className] = target
        val dslClassName = resultClassName(antClass.className)
        structure.put(dslClassName,
                DSLClass(dslClassName, antClass.nestedTypes.map { containerGenerator.containerName(it) }))
        val resultClassName = resultClassName(className)
        val pkg = resultClassName.substring(0, resultClassName.lastIndexOf("."))
        target.kotlinSrcFile = antClass.toKotlin(pkg)
        containerGenerator.resolveContainers(antClass.implementedInterfaces)
        containerGenerator.resolveContainers(antClass.nestedTypes)
    }

    public fun resolveClass(className: String) {
        val names = explodeTypeName(className)
        for (name in names) {
            if (!resolved.containsKey(name)) {
                val antClass = createAntClass(className)
                if (antClass != null) {
                    addTarget(antClass)
                }
            }
        }
    }

    public fun generate() {
        val generatedRoot = resultRoot + DSL_PACKAGE.replace('.', '/') + "/generated"
        File(generatedRoot).mkdirs()
        if (seekForAliasFiles) {
            for (jar in classpath) {
                if (jar.endsWith(".jar")) {
                    val jis = JarInputStream(FileInputStream(jar))
                    aliasFiles.addAll(jis.getAliasFiles())
                    jis.close()
                }
            }
        }
        val aliases = ArrayList<Alias>()
        for (aliasFileName in aliasFiles) {
            var stream = classLoader.getResourceAsStream(aliasFileName)
            if (stream == null) {
                val aliasFile = File(aliasFileName)
                if (!aliasFile.exists()) {
                    System.err.println("File $aliasFile was not found")
                    continue
                }
                stream = FileInputStream(aliasFile)
            }
            if (aliasFileName.toLowerCase().endsWith(".properties")) {
                aliases.addAll(AliasParser(stream!!).parseProperties())
            } else if (aliasFileName.toLowerCase().endsWith(".xml")) {
                aliases.addAll(AliasParser(stream!!).parseXML())
            }
        }
        for (alias in aliases) {
            resolveAlias(alias)
        }
        if (defAl) {
            for (jar in classpath) {
                if (jar.endsWith(".jar")) {
                    val jis = JarInputStream(FileInputStream(jar))
                    val antTasks = jis.getAntTasks()
                    for (antTask in antTasks) {
                        val className = antTask.className
                        val taskName = defaultConstructorName(className)
                        val dslClassName = resultClassName(className)
                        if (!resolved.contains(className)) {
                            addTarget(antTask)
                        }
                        if (!constructorIsGenerated(dslClassName, taskName)) {
                            resolved[antTask.className]?.generateConstructors(taskName, true)
                        }
                    }
                    jis.close()
                }
            }
        }
        for (target in resolved.values()) {
            target.dumpFile()
        }
        containerGenerator.dump(File(generatedRoot + "/Containers.kt"))
    }

    private fun resolveAlias(alias: Alias) {
        val tag = alias.tag
        val className = alias.className
        val topLevel = alias.topLevel
        resolveClass(className)
        resolved[className]?.generateConstructors(tag, topLevel)
    }

    private fun resultClassName(srcClassName: String): String {
        val className = srcClassName.replace("$", "")
        val result = StringBuilder()
        result.append(DSL_PACKAGE + ".")
        if (srcClassName.startsWith(ANT_CLASS_PREFIX)) {
            result.append(className.substring(ANT_CLASS_PREFIX.length))
        } else {
            result.append("other." + className)
        }
        result.insert(result.lastIndexOf(".") + 1, "DSL")
        return result.toString()
    }

    private fun resultFile(srcClassName: String): File {
        return File(resultRoot + resultClassName(srcClassName)
                .replace(DSL_PACKAGE, DSL_PACKAGE + ".generated").replace('.', '/') + ".kt")
    }

    private fun JarInputStream.getAliasFiles(): List<String> {
        val result = ArrayList<String>()
        var jarEntry = getNextJarEntry()
        while (jarEntry != null) {
            val entryName = jarEntry!!.getName()
            if (entryName.toLowerCase().endsWith(".properties") || entryName.toLowerCase().endsWith(".xml")) {
                result.add(entryName)
            }
            jarEntry = getNextJarEntry()
        }
        return result
    }

    private fun JarInputStream.getAntTasks(): List<AntClass> {
        val result = ArrayList<AntClass>()
        var jarEntry = getNextJarEntry()
        while (jarEntry != null) {
            val entryName = jarEntry!!.getName()
            if (entryName.endsWith(".class") && !entryName.contains("$")) {
                val className = entryName.substring(0, entryName.lastIndexOf('.')).replace('/', '.')
                val antClass = createAntClass(className)
                if (antClass != null && (antClass.isTask || antClass.isCondition || antClass.hasRefId)) {
                    result.add(antClass)
                }
            }
            jarEntry = getNextJarEntry()
        }
        return result
    }

    private fun dslAttribute(attr: AntAttribute, parentName: String): AntAttribute {
        val name = attr.name
        val typeName =
                if (PRIMITIVE_TYPES.contains(attr.typeName)) {
                    attr.typeName
                } else if (attr.typeName == ANT_CLASS_PREFIX + "types.Reference") {
                    if (attr.name == "refid") {
                        "$DSL_REFERENCE<${resultClassName(parentName)}>"
                    } else if (attr.name.toLowerCase().endsWith("pathref")) {
                        "$DSL_REFERENCE<$DSL_PATH>"
                    } else {
                        "String"
                    }
                } else {
                    "String"
                }
        return AntAttribute(name, typeName)
    }

    private fun AntClass.toKotlin(pkg: String?): KotlinSourceFile {
        val res = KotlinSourceFile(pkg)
        val dslTypeName = "$pkg.DSL" + cutShortName(className)
        val dslTypeNameShorten = res.importManager.shorten(dslTypeName)
        val projectShorten = res.importManager.shorten(ANT_CLASS_PREFIX + "Project")
        val targetShorten = res.importManager.shorten(ANT_CLASS_PREFIX + "Target")
        val rcShorten = res.importManager.shorten(ANT_CLASS_PREFIX + "RuntimeConfigurable")
        val dslTaskShorten = res.importManager.shorten(DSL_TASK)
        val dslTextContainerShorten = res.importManager.shorten(DSL_TEXT_CONTAINER)
        val dslTaskContainerTaskShorten = res.importManager.shorten(DSL_TASK_CONTAINER_TASK)
        val dslConditionShorten = res.importManager.shorten(DSL_CONDITION)
        res.append("public class $dslTypeNameShorten(projectAO: $projectShorten, targetAO: $targetShorten, parentWrapperAO: $rcShorten?, elementTag: String, nearestExecutable: $dslTaskShorten?)")
        res.append(": ${if (isTaskContainer) {dslTaskContainerTaskShorten} else {dslTaskShorten}}(projectAO, targetAO, parentWrapperAO, elementTag, nearestExecutable)")
        if (isTextContainer) {
            res.append(",\n        $dslTextContainerShorten")
        }
        if (isCondition) {
            res.append(",\n        $dslConditionShorten")
        }
        for (nestedType in nestedTypes) {
            val containerName = res.importManager.shorten(containerGenerator.containerName(nestedType))
            res.append(",\n        $containerName")
        }
        res.append(" {\n")
        for (attr in attributes) {
            val dslAttr = dslAttribute(attr, className)
            res.append("    var ${escapeKeywords(dslAttr.name)}: ${res.importManager.shorten(dslAttr.typeName.replace('$', '.'))} " +
                    "by ${res.importManager.shorten("kotlin.properties.Delegates")}.mapVar(attributes)\n")
        }
        res.append("}\n")
        for (element in nestedElements) {
            renderNestedElement(res, dslTypeName, element)
        }
        return res
    }

    private fun AntClass.constructorReturnType(): String? {
        val typeName = resultClassName(className)
        if (isCondition) {
            return "Boolean"
        } else if (hasRefId) {
            return "$DSL_REFERENCE<$typeName>"
        } else {
            return null
        }
    }

    private fun AntClass.renderNestedElement(out: KotlinSourceFile,
                                             parentName: String, element: AntAttribute) {
        if (constructorIsGenerated(parentName, element.name)) {
            return
        }
        resolveClass(element.typeName)
        val elementClass = resolved[explodeTypeName(element.typeName)[0]]!!.antClass!!
        elementClass.renderConstructor(out, parentName, element.name, true)
        elementClass.renderConstructor(out, parentName, element.name, false)
        addConstructor(parentName, element.name, out.pkg, elementClass)
    }

    private fun AntClass.renderConstructorParameters(out: KotlinSourceFile) {
        var first = true
        for (attr in attributes) {
            val dslAttr = dslAttribute(attr, className)
            if (first) {
                first = false
            } else {
                out.append(",\n")
            }
            out.append("        ${escapeKeywords(dslAttr.name)}: ${out.importManager.shorten(dslAttr.typeName.replace('$', '.'))}? = null")
        }
    }

    private fun AntClass.renderConstructor(out: KotlinSourceFile,
                                           parentName: String, tag: String, withInit: Boolean) {
        val dslTypeName = out.importManager.shorten(resultClassName(className))
        val dslTaskContainerShorten = out.importManager.shorten(DSL_TASK_CONTAINER)
        val dslReferenceShorten = out.importManager.shorten(DSL_REFERENCE)
        val initReceiverTypeName = if (isTaskContainer) {
            dslTaskContainerShorten
        } else {
            dslTypeName
        }
        out.append("\n")
        out.append("public fun ${out.importManager.shorten(parentName)}.${escapeKeywords(toCamelCase(tag))}(\n")
        renderConstructorParameters(out)
        if (withInit) {
            if (!attributes.empty) {
                out.append(",\n")
            }
            out.append("        init: $initReceiverTypeName.() -> ${out.importManager.shorten("kotlin.Unit")}")
        }
        out.append(")")
        val returnType = constructorReturnType()
        if (returnType != null) {
            out.append(": " + out.importManager.shorten(returnType))
        }
        out.append(" {\n")
        if (withInit) {
            val mustBeExecuted = (parentName == DSL_TASK_CONTAINER || parentName == DSL_PROJECT)
            out.append("    val dslObject = $dslTypeName(this.projectAO, this.targetAO, ${if (mustBeExecuted) {"null"} else {"this.wrapperAO"}}, \"$tag\", ")
            out.append(if (mustBeExecuted) {"null"} else {"this"})
            out.append(")\n")
            for (attr in attributes) {
                val dslAttr = dslAttribute(attr, className)
                out.append("    if (${escapeKeywords(dslAttr.name)} != null) { dslObject.${escapeKeywords(dslAttr.name)} = ${escapeKeywords(dslAttr.name)} }\n")
            }
            if (!isTaskContainer) {
                out.append("    dslObject.init()\n")
            }
            if (!isCondition && returnType != null) {
                out.append("    val reference = $dslReferenceShorten(dslObject)\n")
            }
            out.append("    dslObject.configure()\n")
            if (isTaskContainer) {
                out.append("    dslObject.addTaskContainer(dslObject, init)\n")
            }
            if (mustBeExecuted && !isCondition) {
                out.append("    dslObject.execute()\n")
            }
            if (returnType != null) {
                out.append("    return ")
                out.append(if (isCondition) {
                    "dslObject.eval()"
                } else {
                    "reference"
                }).append("\n")
            }
        } else {
            out.append("    return ${toCamelCase(tag)}(\n")
            for (attr in attributes) {
                out.append("        ${escapeKeywords(attr.name)},\n")
            }
            out.append("        {$initReceiverTypeName.() -> })\n")
        }
        out.append("}\n")
    }

    private fun cutShortName(name: String): String {
        return name.substring(name.lastIndexOf('.') + 1).replace("$", "")
    }

    private fun constructorIsGenerated(parent: String, function: String): Boolean {
        val dslClass = structure[parent]!!
        return dslClass.containsFunction(function)
    }

    private fun defaultConstructorName(className: String): String {
        val shortName = cutShortName(className)
        return Character.toLowerCase(shortName.charAt(0)) + shortName.substring(1)
    }

    private fun addConstructor(parent: String, function: String, pkg: String?, antClass: AntClass) {
        val dslClass = structure[parent]!!
        dslClass.addFunction(function, pkg,
                antClass.attributes.map { dslAttribute(it, antClass.className) },
                resultClassName(antClass.className))
    }

    private inner class Target(val className: String) {
        var antClass: AntClass? = null
        var kotlinSrcFile: KotlinSourceFile? = null

        fun generateConstructors(tag: String, topLevel: Boolean) {
            val invAntClass = antClass!!
            val invKotlinSrcFile = kotlinSrcFile!!
            if (topLevel) {
                if (invAntClass.isTask || invAntClass.isCondition || invAntClass.hasRefId) {
                    invAntClass.renderNestedElement(invKotlinSrcFile, DSL_TASK_CONTAINER, AntAttribute(tag, className))
                }
            }
            for (interface in invAntClass.implementedInterfaces) {
                if (containerGenerator.typeNeedsContainer(interface)) {
                    val containerName = containerGenerator.containerName(interface)
                    invAntClass.renderNestedElement(invKotlinSrcFile, containerName, AntAttribute(tag, className))
                }
            }
        }

        fun dumpFile() {
            kotlinSrcFile!!.dump(resultFile(className))
        }
    }

    private inner class ContainerGenerator {
        private val kotlinSrcFile = KotlinSourceFile("$DSL_PACKAGE.containers")
        private val resolved = HashSet<String>()

        public fun resolveContainers(names: List<String>) {
            for (name in names) {
                resolveContainer(name)
            }
        }

        public fun containerName(name: String): String {
            val shortName = name.substring(name.lastIndexOf('.') + 1).replace("$", "")
            return "$DSL_PACKAGE.containers._DSL" + shortName + "Container"
        }

        public fun typeNeedsContainer(name: String): Boolean {
            return !name.startsWith("java.")
        }

        public fun resolveContainer(name: String) {
            if (!resolved.contains(name) && typeNeedsContainer(name)) {
                val containerName = containerName(name)
                val containerNameShorten = kotlinSrcFile.importManager.shorten(containerName)
                val dslTaskShorten =  kotlinSrcFile.importManager.shorten(DSL_TASK)
                kotlinSrcFile.append("\ntrait $containerNameShorten : $dslTaskShorten\n")
                resolved.add(name)
                structure.put(containerName, DSLClass(containerName, ArrayList<String>()))
            }
        }

        public fun dump(file: File) {
            kotlinSrcFile.dump(file)
        }
    }
}

class DSLClass(val name: String, val traits: List<String>): Serializable {
    private val functions = HashMap<String, DSLFunction>()

    fun addFunction(name: String, pkg: String?, attributes: List<AntAttribute>, receiver: String) {
        functions.put(name.toLowerCase(), DSLFunction(toCamelCase(name), pkg, attributes.valuesToMap {it.name.toLowerCase()}, receiver))
    }

    fun containsFunction(name: String): Boolean {
        return functions.containsKey(name.toLowerCase())
    }

    fun getFunction(name: String): DSLFunction? {
        return functions[name.toLowerCase()]
    }
}

class DSLFunction(val name: String, val pkg: String?, val attributes: Map<String, AntAttribute>, val initReceiver: String): Serializable {
    public fun getAttribute(attributeName: String): AntAttribute? {
        return attributes[attributeName.toLowerCase()]
    }

    public fun getAttributeName(attributeName: String): String? {
        return getAttribute(attributeName)?.name;
    }

    public fun getAttributeType(attributeName: String): String? {
        return getAttribute(attributeName)?.typeName
    }
}