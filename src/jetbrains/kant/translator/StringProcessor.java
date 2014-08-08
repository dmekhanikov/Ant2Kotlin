package jetbrains.kant.translator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jetbrains.kant.KantPackage.toCamelCase;
import static jetbrains.kant.generator.GeneratorPackage.*;

public class StringProcessor {
    public static String getType(String value) {
        if (value != null && (value.equals("true") || value.equals("false")
                || value.equals("yes") || value.equals("no"))) {
            return "Boolean";
        } else {
            return "String";
        }
    }

    public static boolean isStartOfTemplate(String string, int i) {
        return (string.charAt(i) == '$' || string.charAt(i) == '@') &&
                (i == 0 || string.charAt(i - 1) != string.charAt(i)) &&
                i + 1 < string.length() && string.charAt(i + 1) == '{';
    }

    public static String processProperties(String value, PropertyManager propertyManager) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (isStartOfTemplate(value, i)) {
                boolean isAttribute = value.charAt(i) == '@';
                StringBuilder propertyName = new StringBuilder();
                for (i += 2; i < value.length() && value.charAt(i) != '}'; i++) {
                    propertyName.append(value.charAt(i));
                }
                String name = propertyName.toString();
                if (name.equals("\"$\"")) {
                    result.append("${\"$\"}");
                } else {
                    if (propertyManager != null && !isAttribute) {
                        propertyManager.readAccess(propertyName.toString());
                    }
                    name = toCamelCase(name);
                    if (i + 1 < value.length() && Character.isJavaIdentifierPart(value.charAt(i + 1))) {
                        name = "${" + name + "}";
                    } else {
                        name = "$" + name;
                    }
                    result.append(name);
                }
            } else {
                result.append(value.charAt(i));
            }
        }
        return result.toString();
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    public static String escapeTemplates(String value) { //don't forget about @@ inside macrodefs
        StringBuilder result = new StringBuilder(value);
        for (int i = 0; i < result.length() - 1; i++) {
            if (result.charAt(i) == '$') {
                if (isIdentifierStart(result.charAt(i + 1))) {
                    result.insert(i + 1, "{\"$\"}");
                    i += 5;
                } else if (result.charAt(i + 1) == '$') {
                    if (i + 2 < result.length() &&
                            (isIdentifierStart(result.charAt(i + 2)) || result.charAt(i + 2) == '{')) {
                        result.replace(i + 1, i + 2, "{\"$\"}");
                    } else {
                        result.deleteCharAt(i);
                    }
                }
            }
        }
        return result.toString();
    }

    public static String prepareValue(String value, PropertyManager propertyManager, String type) {
        if (value == null) {
            return null;
        }
        if (type.startsWith(getDSL_REFERENCE())) {
            return toCamelCase(value);
        }
        try {
            switch (type) {
                case "Boolean":
                    if (value.equals("true") || value.equals("yes")) {
                        return "true";
                    } else if (value.equals("false") || value.equals("no")) {
                        return "false";
                    }
                    break;
                case "Char":
                    if (value.length() == 1) {
                        return "'" + value + "'";
                    }
                    break;
                case "Byte":
                    return String.valueOf(Byte.parseByte(value));
                case "Short":
                    return String.valueOf(Short.parseShort(value));
                case "Int":
                    return String.valueOf(Integer.parseInt(value));
                case "Float":
                    return String.valueOf(Float.parseFloat(value));
                case "Long":
                    return String.valueOf(Long.parseLong(value));
                case "Double":
                    return String.valueOf(Double.parseDouble(value));
            }
        } catch (NumberFormatException ignore) {}
        String pattern = "[$@]\\{([^\\{]+)\\}"; //wrong
        Matcher matcher = Pattern.compile(pattern).matcher(value);
        if (matcher.matches()) {
            String propName = matcher.group(1);
            String propCCName = processProperties(value, propertyManager).substring(1);
            String propType = propertyManager.getPropType(propName);
            if (type.equals(propType)) {
                return propCCName;
            } else if (type.equals("Char") && propType.equals("String")) {
                return propCCName + "[0]";
            } else if (type.equals("String")) {
                return propCCName + ".toString()";
            } else {
                return propCCName + ".to" + type + "()";
            }
        }


        StringBuilder result = new StringBuilder("\"");
        result.append(processProperties(escapeTemplates(value), propertyManager));
        result.append("\"");
        for (int i = result.indexOf("\\"); i != -1; i = result.indexOf("\\", i)) {
            result.replace(i, i + 1, "\\\\");
            i += 2;
        }
        return result.toString();
    }
}
