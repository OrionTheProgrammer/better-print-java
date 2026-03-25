package io.github.bpj;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Better Print for Java (BPJ) runtime API.
 *
 * <p>BPJ interpolates placeholders using braces, for example:
 * <ul>
 *   <li>{@code {name}}</li>
 *   <li>{@code {user.name}}</li>
 * </ul>
 *
 * <p>The runtime supports three context strategies:
 * <ul>
 *   <li>Explicit context per call ({@link Map}, root object, or key-value varargs).</li>
 *   <li>Thread-bound context using {@link #bind(Map)} / {@link #bind(Object)} / {@link #bind(Object...)}.</li>
 *   <li>Build-time source rewriting through the BPJ Maven plugin (separate module).</li>
 * </ul>
 *
 * <p>BPJ also supports ANSI-highlighted rendering via
 * {@link #formatHighlighted(String)},
 * {@link #printHighlighted(String)} and
 * {@link #printlnHighlighted(String)}.
 */
public final class BPJ {
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\s*([a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)*)\\s*\\}");
    private static final Object UNRESOLVED = new Object();
    private static final ThreadLocal<ContextFrame> CONTEXT_STACK = new ThreadLocal<>();
    private static final String ESCAPED_OPEN_TOKEN = "\u0001BPJ_OPEN\u0001";
    private static final String ESCAPED_CLOSE_TOKEN = "\u0001BPJ_CLOSE\u0001";
    private static final ValueDecorator IDENTITY_DECORATOR = value -> value;
    private static volatile AnsiColor highlightColor = AnsiColor.CYAN;

    private BPJ() {
    }

    /**
     * ANSI foreground colors used by highlighted BPJ output methods.
     */
    public enum AnsiColor {
        BLACK("\u001B[30m"),
        RED("\u001B[31m"),
        GREEN("\u001B[32m"),
        YELLOW("\u001B[33m"),
        BLUE("\u001B[34m"),
        MAGENTA("\u001B[35m"),
        CYAN("\u001B[36m"),
        WHITE("\u001B[37m"),
        BRIGHT_BLACK("\u001B[90m"),
        BRIGHT_RED("\u001B[91m"),
        BRIGHT_GREEN("\u001B[92m"),
        BRIGHT_YELLOW("\u001B[93m"),
        BRIGHT_BLUE("\u001B[94m"),
        BRIGHT_MAGENTA("\u001B[95m"),
        BRIGHT_CYAN("\u001B[96m"),
        BRIGHT_WHITE("\u001B[97m");

        private static final String ANSI_RESET = "\u001B[0m";

        private final String openCode;

        AnsiColor(String openCode) {
            this.openCode = openCode;
        }

        private String wrap(String value) {
            return openCode + value + ANSI_RESET;
        }
    }

    /**
     * Returns whether the current thread has at least one bound BPJ context frame.
     *
     * @return {@code true} when a context is currently bound in this thread
     */
    public static boolean hasBoundContext() {
        return CONTEXT_STACK.get() != null;
    }

    /**
     * Clears all bound context frames for the current thread.
     *
     * <p>Prefer using {@link Scope} and try-with-resources in normal flow.
     * This method is useful for defensive cleanup in custom thread management.
     */
    public static void clearBoundContext() {
        CONTEXT_STACK.remove();
    }

    /**
     * Returns the default ANSI color used by highlighted rendering methods.
     *
     * @return current highlight color
     */
    public static AnsiColor getHighlightColor() {
        return highlightColor;
    }

    /**
     * Sets the default ANSI color used by highlighted rendering methods.
     *
     * @param color new highlight color
     */
    public static void setHighlightColor(AnsiColor color) {
        highlightColor = Objects.requireNonNull(color, "color cannot be null");
    }

    /**
     * Binds a map context to the current thread.
     *
     * <p>Use with try-with-resources to guarantee automatic scope closing:
     *
     * <pre>{@code
     * try (BPJ.Scope ignored = BPJ.bind(Map.of("name", "Ana"))) {
     *     BPJ.println("Hello {name}");
     * }
     * }</pre>
     *
     * @param context map used to resolve placeholders
     * @return a closeable scope that pops the context when closed
     */
    public static Scope bind(Map<String, ?> context) {
        Objects.requireNonNull(context, "context cannot be null");
        return pushContext(copyMap(context), null);
    }

    /**
     * Binds a root object to the current thread.
     *
     * <p>If {@code context} is a {@link Map}, it is treated as map context.
     *
     * @param context root object used to resolve placeholders
     * @return a closeable scope that pops the context when closed
     */
    public static Scope bind(Object context) {
        Objects.requireNonNull(context, "context cannot be null");
        if (context instanceof Map<?, ?> mapContext) {
            return bind(castMap(mapContext));
        }
        return pushContext(Map.of(), context);
    }

    /**
     * Binds key-value pairs to the current thread.
     *
     * <p>Example:
     * <pre>{@code
     * try (BPJ.Scope ignored = BPJ.bind("name", "Ana", "total", 20)) {
     *     BPJ.println("User {name}, total {total}");
     * }
     * }</pre>
     *
     * @param keyValues alternating key/value sequence
     * @return a closeable scope that pops the context when closed
     */
    public static Scope bind(Object... keyValues) {
        return bind(toMap(keyValues));
    }

    /**
     * Formats a template with the currently bound thread context (if available).
     *
     * <p>When no context is bound, the input template is returned unchanged.
     * Double braces can be used to escape literal braces: {@code {{literal}}}.
     *
     * @param template template text
     * @return formatted text
     */
    public static String format(String template) {
        Objects.requireNonNull(template, "template cannot be null");
        ContextFrame frame = CONTEXT_STACK.get();
        if (frame == null) {
            return unescapeBraces(escapeBraces(template));
        }
        return formatFromContextStack(template, frame, false);
    }

    /**
     * Formats a template using an explicit map context.
     *
     * @param template template text
     * @param context map context
     * @return formatted text
     */
    public static String format(String template, Map<String, ?> context) {
        return formatInternal(template, context, null, false);
    }

    /**
     * Formats a template using an explicit root object context.
     *
     * <p>If {@code context} is a {@link Map}, it is treated as map context.
     *
     * @param template template text
     * @param context root object or map
     * @return formatted text
     */
    public static String format(String template, Object context) {
        if (context instanceof Map<?, ?> mapContext) {
            return format(template, castMap(mapContext));
        }
        return formatInternal(template, Map.of(), context, false);
    }

    /**
     * Formats a template using key-value pairs.
     *
     * @param template template text
     * @param keyValues alternating key/value sequence
     * @return formatted text
     */
    public static String format(String template, Object... keyValues) {
        return format(template, toMap(keyValues));
    }

    /**
     * Formats a template using bound context and highlights resolved values with ANSI color.
     *
     * @param template template text
     * @return formatted text with ANSI color for resolved placeholder values
     */
    public static String formatHighlighted(String template) {
        Objects.requireNonNull(template, "template cannot be null");
        ContextFrame frame = CONTEXT_STACK.get();
        if (frame == null) {
            return unescapeBraces(escapeBraces(template));
        }
        return formatFromContextStack(template, frame, false, highlightedDecorator());
    }

    /**
     * Formats a template using map context and highlights resolved values with ANSI color.
     *
     * @param template template text
     * @param context map context
     * @return formatted text with ANSI color for resolved placeholder values
     */
    public static String formatHighlighted(String template, Map<String, ?> context) {
        return formatInternal(template, context, null, false, highlightedDecorator());
    }

    /**
     * Formats a template using root object context and highlights resolved values with ANSI color.
     *
     * @param template template text
     * @param context root object or map
     * @return formatted text with ANSI color for resolved placeholder values
     */
    public static String formatHighlighted(String template, Object context) {
        if (context instanceof Map<?, ?> mapContext) {
            return formatHighlighted(template, castMap(mapContext));
        }
        return formatInternal(template, Map.of(), context, false, highlightedDecorator());
    }

    /**
     * Formats a template using key-value pairs and highlights resolved values with ANSI color.
     *
     * @param template template text
     * @param keyValues alternating key/value sequence
     * @return formatted text with ANSI color for resolved placeholder values
     */
    public static String formatHighlighted(String template, Object... keyValues) {
        return formatHighlighted(template, toMap(keyValues));
    }

    /**
     * Strict variant of {@link #format(String, Map)}.
     *
     * <p>Throws {@link IllegalArgumentException} when a placeholder cannot be resolved.
     *
     * @param template template text
     * @param context map context
     * @return formatted text
     */
    public static String formatStrict(String template, Map<String, ?> context) {
        return formatInternal(template, context, null, true);
    }

    /**
     * Strict variant of {@link #format(String)} using bound context frames.
     *
     * <p>Throws {@link IllegalArgumentException} when a placeholder cannot be resolved.
     *
     * @param template template text
     * @return formatted text
     */
    public static String formatStrict(String template) {
        Objects.requireNonNull(template, "template cannot be null");
        return formatFromContextStack(template, CONTEXT_STACK.get(), true);
    }

    /**
     * Strict variant of {@link #format(String, Object)}.
     *
     * @param template template text
     * @param context root object or map
     * @return formatted text
     */
    public static String formatStrict(String template, Object context) {
        if (context instanceof Map<?, ?> mapContext) {
            return formatStrict(template, castMap(mapContext));
        }
        return formatInternal(template, Map.of(), context, true);
    }

    /**
     * Strict variant of {@link #format(String, Object...)}.
     *
     * @param template template text
     * @param keyValues alternating key/value sequence
     * @return formatted text
     */
    public static String formatStrict(String template, Object... keyValues) {
        return formatStrict(template, toMap(keyValues));
    }

    /**
     * Prints formatted text to {@code System.out} without line break, using map context.
     *
     * @param template template text
     * @param context map context
     */
    public static void print(String template, Map<String, ?> context) {
        System.out.print(format(template, context));
    }

    /**
     * Prints formatted text to {@code System.out} without line break, using bound context.
     *
     * @param template template text
     */
    public static void print(String template) {
        System.out.print(format(template));
    }

    /**
     * Prints formatted text to {@code System.out} without line break, using root context.
     *
     * @param template template text
     * @param context root object or map
     */
    public static void print(String template, Object context) {
        System.out.print(format(template, context));
    }

    /**
     * Prints formatted text to {@code System.out} without line break, using key-value pairs.
     *
     * @param template template text
     * @param keyValues alternating key/value sequence
     */
    public static void print(String template, Object... keyValues) {
        System.out.print(format(template, keyValues));
    }

    /**
     * Prints highlighted formatted text to {@code System.out} without line break, using bound context.
     *
     * @param template template text
     */
    public static void printHighlighted(String template) {
        System.out.print(formatHighlighted(template));
    }

    /**
     * Prints highlighted formatted text to {@code System.out} without line break, using map context.
     *
     * @param template template text
     * @param context map context
     */
    public static void printHighlighted(String template, Map<String, ?> context) {
        System.out.print(formatHighlighted(template, context));
    }

    /**
     * Prints highlighted formatted text to {@code System.out} without line break, using root object context.
     *
     * @param template template text
     * @param context root object or map
     */
    public static void printHighlighted(String template, Object context) {
        System.out.print(formatHighlighted(template, context));
    }

    /**
     * Prints highlighted formatted text to {@code System.out} without line break, using key-value pairs.
     *
     * @param template template text
     * @param keyValues alternating key/value sequence
     */
    public static void printHighlighted(String template, Object... keyValues) {
        System.out.print(formatHighlighted(template, keyValues));
    }

    /**
     * Prints formatted text to {@code System.out} followed by line break, using map context.
     *
     * @param template template text
     * @param context map context
     */
    public static void println(String template, Map<String, ?> context) {
        System.out.println(format(template, context));
    }

    /**
     * Prints formatted text to {@code System.out} followed by line break, using bound context.
     *
     * @param template template text
     */
    public static void println(String template) {
        System.out.println(format(template));
    }

    /**
     * Prints formatted text to {@code System.out} followed by line break, using root context.
     *
     * @param template template text
     * @param context root object or map
     */
    public static void println(String template, Object context) {
        System.out.println(format(template, context));
    }

    /**
     * Prints formatted text to {@code System.out} followed by line break, using key-value pairs.
     *
     * @param template template text
     * @param keyValues alternating key/value sequence
     */
    public static void println(String template, Object... keyValues) {
        System.out.println(format(template, keyValues));
    }

    /**
     * Prints highlighted formatted text to {@code System.out} with line break, using bound context.
     *
     * @param template template text
     */
    public static void printlnHighlighted(String template) {
        System.out.println(formatHighlighted(template));
    }

    /**
     * Prints highlighted formatted text to {@code System.out} with line break, using map context.
     *
     * @param template template text
     * @param context map context
     */
    public static void printlnHighlighted(String template, Map<String, ?> context) {
        System.out.println(formatHighlighted(template, context));
    }

    /**
     * Prints highlighted formatted text to {@code System.out} with line break, using root object context.
     *
     * @param template template text
     * @param context root object or map
     */
    public static void printlnHighlighted(String template, Object context) {
        System.out.println(formatHighlighted(template, context));
    }

    /**
     * Prints highlighted formatted text to {@code System.out} with line break, using key-value pairs.
     *
     * @param template template text
     * @param keyValues alternating key/value sequence
     */
    public static void printlnHighlighted(String template, Object... keyValues) {
        System.out.println(formatHighlighted(template, keyValues));
    }

    private static String formatInternal(
            String template,
            Map<String, ?> context,
            Object rootContext,
            boolean strictMode
    ) {
        return formatInternal(template, context, rootContext, strictMode, IDENTITY_DECORATOR);
    }

    private static String formatInternal(
            String template,
            Map<String, ?> context,
            Object rootContext,
            boolean strictMode,
            ValueDecorator decorator
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(decorator, "decorator cannot be null");

        return render(
                template,
                expression -> resolveExpression(expression, context, rootContext),
                strictMode,
                decorator
        );
    }

    private static String formatFromContextStack(
            String template,
            ContextFrame frame,
            boolean strictMode
    ) {
        return formatFromContextStack(template, frame, strictMode, IDENTITY_DECORATOR);
    }

    private static String formatFromContextStack(
            String template,
            ContextFrame frame,
            boolean strictMode,
            ValueDecorator decorator
    ) {
        return render(
                template,
                expression -> resolveExpressionFromStack(expression, frame),
                strictMode,
                decorator
        );
    }

    private static String render(
            String template,
            ExpressionResolver resolver,
            boolean strictMode,
            ValueDecorator decorator
    ) {
        String escaped = escapeBraces(template);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(escaped);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String expression = matcher.group(1);
            Object resolved = resolver.resolve(expression);
            if (resolved == UNRESOLVED) {
                if (strictMode) {
                    throw new IllegalArgumentException("Unresolved placeholder: {" + expression + "}");
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            String replacement = String.valueOf(resolved);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(decorator.decorate(replacement)));
        }
        matcher.appendTail(sb);
        return unescapeBraces(sb.toString());
    }

    private static String escapeBraces(String template) {
        return template
                .replace("{{", ESCAPED_OPEN_TOKEN)
                .replace("}}", ESCAPED_CLOSE_TOKEN);
    }

    private static String unescapeBraces(String value) {
        return value
                .replace(ESCAPED_OPEN_TOKEN, "{")
                .replace(ESCAPED_CLOSE_TOKEN, "}");
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

    private static Object resolveExpressionFromStack(String expression, ContextFrame frame) {
        for (ContextFrame current = frame; current != null; current = current.previous) {
            Object resolved = resolveExpression(expression, current.context, current.rootContext);
            if (resolved != UNRESOLVED) {
                return resolved;
            }
        }
        return UNRESOLVED;
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
                // Continue with parent type.
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue with parent type.
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

    private static Map<String, Object> copyMap(Map<String, ?> map) {
        Map<String, Object> copy = new HashMap<>();
        copy.putAll(map);
        return copy;
    }

    private static Scope pushContext(Map<String, Object> context, Object rootContext) {
        ContextFrame previous = CONTEXT_STACK.get();
        ContextFrame frame = new ContextFrame(
                Collections.unmodifiableMap(context),
                rootContext,
                previous
        );
        CONTEXT_STACK.set(frame);
        return new ScopedContext(frame);
    }

    private static ValueDecorator highlightedDecorator() {
        AnsiColor currentColor = highlightColor;
        return currentColor::wrap;
    }

    @FunctionalInterface
    private interface ExpressionResolver {
        Object resolve(String expression);
    }

    @FunctionalInterface
    private interface ValueDecorator {
        String decorate(String value);
    }

    /**
     * Represents a bound context scope created by {@link #bind(Map)}, {@link #bind(Object)},
     * or {@link #bind(Object...)}.
     */
    public interface Scope extends AutoCloseable {
        /**
         * Closes the scope and restores the previous bound context frame.
         */
        @Override
        void close();
    }

    private static final class ScopedContext implements Scope {
        private final ContextFrame frame;
        private boolean closed;

        private ScopedContext(ContextFrame frame) {
            this.frame = frame;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;

            ContextFrame current = CONTEXT_STACK.get();
            if (current == null) {
                return;
            }
            if (current != frame) {
                throw new IllegalStateException(
                        "BPJ scope closed out of order. Close nested scopes before outer scopes."
                );
            }

            if (frame.previous == null) {
                CONTEXT_STACK.remove();
            } else {
                CONTEXT_STACK.set(frame.previous);
            }
        }
    }

    private static final class ContextFrame {
        private final Map<String, Object> context;
        private final Object rootContext;
        private final ContextFrame previous;

        private ContextFrame(Map<String, Object> context, Object rootContext, ContextFrame previous) {
            this.context = context;
            this.rootContext = rootContext;
            this.previous = previous;
        }
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
