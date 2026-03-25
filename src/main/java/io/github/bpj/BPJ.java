package io.github.bpj;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BPJ (Better Print for Java): simple string interpolation and print helpers.
 *
 * <p>Supported placeholders:
 * <ul>
 *   <li>{@code {name}}</li>
 *   <li>{@code {product.value}}</li>
 * </ul>
 */
public final class BPJ {
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\s*([a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)*)\\s*\\}");
    private static final Object UNRESOLVED = new Object();

    private BPJ() {
    }

    public static String format(String template) {
        Objects.requireNonNull(template, "template cannot be null");
        return template;
    }

    public static String format(String template, Map<String, ?> context) {
        return formatInternal(template, context, null, false);
    }

    public static String format(String template, Object context) {
        if (context instanceof Map<?, ?> mapContext) {
            return format(template, castMap(mapContext));
        }
        return formatInternal(template, Map.of(), context, false);
    }

    public static String format(String template, Object... keyValues) {
        return format(template, toMap(keyValues));
    }

    public static String formatStrict(String template, Map<String, ?> context) {
        return formatInternal(template, context, null, true);
    }

    public static String formatStrict(String template) {
        return format(template);
    }

    public static String formatStrict(String template, Object context) {
        if (context instanceof Map<?, ?> mapContext) {
            return formatStrict(template, castMap(mapContext));
        }
        return formatInternal(template, Map.of(), context, true);
    }

    public static String formatStrict(String template, Object... keyValues) {
        return formatStrict(template, toMap(keyValues));
    }

    public static void print(String template, Map<String, ?> context) {
        System.out.print(format(template, context));
    }

    public static void print(String template) {
        System.out.print(format(template));
    }

    public static void print(String template, Object context) {
        System.out.print(format(template, context));
    }

    public static void print(String template, Object... keyValues) {
        System.out.print(format(template, keyValues));
    }

    public static void println(String template, Map<String, ?> context) {
        System.out.println(format(template, context));
    }

    public static void println(String template) {
        System.out.println(format(template));
    }

    public static void println(String template, Object context) {
        System.out.println(format(template, context));
    }

    public static void println(String template, Object... keyValues) {
        System.out.println(format(template, keyValues));
    }

    private static String formatInternal(
            String template,
            Map<String, ?> context,
            Object rootContext,
            boolean strictMode
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String expression = matcher.group(1);
            Object resolved = resolveExpression(expression, context, rootContext);
            if (resolved == UNRESOLVED) {
                if (strictMode) {
                    throw new IllegalArgumentException("Unresolved placeholder: {" + expression + "}");
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            String replacement = String.valueOf(resolved);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static Object resolveExpression(
            String expression,
            Map<String, ?> context,
            Object rootContext
    ) {
        String[] parts = expression.split("\\.");
        Object current = context.get(parts[0]);
        boolean resolved = context.containsKey(parts[0]);

        if (!resolved && rootContext != null) {
            ResolutionResult rootValue = resolveProperty(rootContext, parts[0]);
            if (rootValue.found) {
                current = rootValue.value;
                resolved = true;
            }
        }

        if (!resolved) {
            return UNRESOLVED;
        }

        for (int i = 1; i < parts.length; i++) {
            ResolutionResult next = resolveProperty(current, parts[i]);
            if (!next.found) {
                return UNRESOLVED;
            }
            current = next.value;
        }

        return current;
    }

    private static ResolutionResult resolveProperty(Object source, String property) {
        if (source == null) {
            return ResolutionResult.found(null);
        }

        if (source instanceof Map<?, ?> map) {
            if (map.containsKey(property)) {
                return ResolutionResult.found(map.get(property));
            }
            return ResolutionResult.notFound();
        }

        Class<?> type = source.getClass();

        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (component.getName().equals(property)) {
                    try {
                        return ResolutionResult.found(component.getAccessor().invoke(source));
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Cannot read record component '" + property + "'", e);
                    }
                }
            }
        }

        String getterName = "get" + capitalize(property);
        String booleanGetter = "is" + capitalize(property);
        Method method = findNoArgMethod(type, getterName);
        if (method == null) {
            method = findNoArgMethod(type, booleanGetter);
        }
        if (method == null) {
            method = findNoArgMethod(type, property);
        }

        if (method != null) {
            try {
                return ResolutionResult.found(method.invoke(source));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot invoke method '" + method.getName() + "'", e);
            }
        }

        Field field = findField(type, property);
        if (field != null) {
            try {
                field.setAccessible(true);
                return ResolutionResult.found(field.get(source));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot access field '" + property + "'", e);
            }
        }

        return ResolutionResult.notFound();
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Try parent class
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Try parent class
            }
        }
        return null;
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static Map<String, Object> toMap(Object... keyValues) {
        Objects.requireNonNull(keyValues, "keyValues cannot be null");
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must have an even number of elements");
        }

        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("keyValues keys must be String at index " + i);
            }
            map.put((String) key, keyValues[i + 1]);
        }
        return map;
    }

    private static Map<String, ?> castMap(Map<?, ?> map) {
        Map<String, Object> converted = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Map keys must be String, found: " + entry.getKey());
            }
            converted.put(key, entry.getValue());
        }
        return converted;
    }

    private static final class ResolutionResult {
        private final boolean found;
        private final Object value;

        private ResolutionResult(boolean found, Object value) {
            this.found = found;
            this.value = value;
        }

        private static ResolutionResult found(Object value) {
            return new ResolutionResult(true, value);
        }

        private static ResolutionResult notFound() {
            return new ResolutionResult(false, null);
        }
    }
}
