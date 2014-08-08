package jetbrains.kant.translator;

import static jetbrains.kant.KantPackage.escapeKeywords;
import static jetbrains.kant.KantPackage.toCamelCase;

public class Attribute {
    private String name;
    private String type;
    private String defaultValue;

    public Attribute(String name, String type, String defaultValue) {
        this.name = escapeKeywords(name);
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDefaultValue(PropertyManager propertyManager) {
        return StringProcessor.prepareValue(defaultValue, propertyManager, type);
    }

    public String toString(boolean includeType, PropertyManager propertyManager) {
        StringBuilder result = new StringBuilder(toCamelCase(name));
        if (includeType && type != null) {
            result.append(": ").append(type);
        }
        if (defaultValue != null) {
            result.append(" = ");
            result.append(getDefaultValue(propertyManager));
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return toString(false, null);
    }
}
