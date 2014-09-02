package jetbrains.kant.dsl

import org.apache.tools.ant.Project
import org.apache.tools.ant.PropertyHelper
import java.io.File

private var propertyHelper: PropertyHelper? = null

fun initProperties(projectAO: Project) {
    propertyHelper = PropertyHelper.getPropertyHelper(projectAO)
    val basedir = File(".").getAbsoluteFile()!!.getParent()
    propertyHelper!!.setUserProperty("basedir", basedir)
}

public open class Property<T>(public val name: String? = null, public val convert: (value: String) -> T, public val defaultValue: () -> T) {
    private fun getName(prop: PropertyMetadata): String {
        if (name != null) {
            return name!!
        } else {
            return prop.name
        }
    }

    public fun get(thisRef: Any?, prop: PropertyMetadata): T {
        val propName = getName(prop)
        var value = propertyHelper?.getProperty(propName)
        if (value == null) {
            value = defaultValue()
        }
        val result = value
        return if (result is String) {
            convert(result)
        } else {
            result as T
        }
    }

    public fun set(thisRef: Any?, prop: PropertyMetadata, value: T) {
        val propName = getName(prop)
        propertyHelper!!.setUserProperty(propName, value)
    }
}

public fun propertyIsSet(name: String): Boolean {
    return propertyHelper!!.getProperty(name) != null
}

public class BooleanProperty(name: String? = null, defaultValue: () -> Boolean = { false }) : Property<Boolean>(name, { java.lang.Boolean.parseBoolean(it) }, defaultValue)
public class CharProperty(name: String? = null, defaultValue: () -> Char = { '\u0000' }) : Property<Char>(name, { it[0] }, defaultValue)
public class ByteProperty(name: String? = null, defaultValue: () -> Byte = { 0 }) : Property<Byte>(name, { java.lang.Byte.parseByte(it) }, defaultValue)
public class ShortProperty(name: String? = null, defaultValue: () -> Short = { 0 }) : Property<Short>(name, { java.lang.Short.parseShort(it) }, defaultValue)
public class IntProperty(name: String? = null, defaultValue: () -> Int = { 0 }) : Property<Int>(name, { java.lang.Integer.parseInt(it) }, defaultValue)
public class FloatProperty(name: String? = null, defaultValue: () -> Float = { 0.0f }) : Property<Float>(name, { java.lang.Float.parseFloat(it) }, defaultValue)
public class LongProperty(name: String? = null, defaultValue: () -> Long = { 0 }) : Property<Long>(name, { java.lang.Long.parseLong(it) }, defaultValue)
public class DoubleProperty(name: String? = null, defaultValue: () -> Double = { 0.0 }) : Property<Double>(name, { java.lang.Double.parseDouble(it) }, defaultValue)
public class StringProperty(name: String? = null, defaultValue: () -> String = { "" }) : Property<String>(name, { it }, defaultValue)
