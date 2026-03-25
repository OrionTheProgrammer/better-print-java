package io.github.bpj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BPJPublicApiContractTest {

    @Test
    void shouldKeepExpectedPublicApiForBpjClass() {
        assertTrue(Modifier.isFinal(BPJ.class.getModifiers()), "BPJ must remain final");

        Constructor<?>[] constructors = BPJ.class.getDeclaredConstructors();
        assertEquals(1, constructors.length, "BPJ must keep a single private constructor");
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()), "BPJ constructor must stay private");

        Set<String> expected = Set.of(
                "bind(java.lang.Object):io.github.bpj.BPJ$Scope",
                "bind(java.lang.Object[]):io.github.bpj.BPJ$Scope",
                "bind(java.util.Map):io.github.bpj.BPJ$Scope",
                "clearBoundContext():void",
                "format(java.lang.String):java.lang.String",
                "format(java.lang.String,java.lang.Object):java.lang.String",
                "format(java.lang.String,java.lang.Object[]):java.lang.String",
                "format(java.lang.String,java.util.Map):java.lang.String",
                "formatHighlighted(java.lang.String):java.lang.String",
                "formatHighlighted(java.lang.String,java.lang.Object):java.lang.String",
                "formatHighlighted(java.lang.String,java.lang.Object[]):java.lang.String",
                "formatHighlighted(java.lang.String,java.util.Map):java.lang.String",
                "formatStrict(java.lang.String):java.lang.String",
                "formatStrict(java.lang.String,java.lang.Object):java.lang.String",
                "formatStrict(java.lang.String,java.lang.Object[]):java.lang.String",
                "formatStrict(java.lang.String,java.util.Map):java.lang.String",
                "getHighlightColor():io.github.bpj.BPJ$AnsiColor",
                "hasBoundContext():boolean",
                "print(java.lang.String):void",
                "print(java.lang.String,java.lang.Object):void",
                "print(java.lang.String,java.lang.Object[]):void",
                "print(java.lang.String,java.util.Map):void",
                "printHighlighted(java.lang.String):void",
                "printHighlighted(java.lang.String,java.lang.Object):void",
                "printHighlighted(java.lang.String,java.lang.Object[]):void",
                "printHighlighted(java.lang.String,java.util.Map):void",
                "println(java.lang.String):void",
                "println(java.lang.String,java.lang.Object):void",
                "println(java.lang.String,java.lang.Object[]):void",
                "println(java.lang.String,java.util.Map):void",
                "printlnHighlighted(java.lang.String):void",
                "printlnHighlighted(java.lang.String,java.lang.Object):void",
                "printlnHighlighted(java.lang.String,java.lang.Object[]):void",
                "printlnHighlighted(java.lang.String,java.util.Map):void",
                "setHighlightColor(io.github.bpj.BPJ$AnsiColor):void"
        );

        Set<String> actual = Arrays.stream(BPJ.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(BPJPublicApiContractTest::signature)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(new TreeSet<>(expected), actual, () -> mismatch("BPJ", expected, actual));
    }

    @Test
    void shouldKeepExpectedScopeContract() {
        assertTrue(BPJ.Scope.class.isInterface(), "BPJ.Scope must remain an interface");

        Set<String> expected = Set.of("close():void");

        Set<String> actual = Arrays.stream(BPJ.Scope.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(BPJPublicApiContractTest::signature)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(new TreeSet<>(expected), actual, () -> mismatch("BPJ.Scope", expected, actual));
    }

    private static String signature(Method method) {
        String params = Arrays.stream(method.getParameterTypes())
                .map(BPJPublicApiContractTest::typeName)
                .collect(Collectors.joining(","));
        return method.getName() + "(" + params + "):" + typeName(method.getReturnType());
    }

    private static String typeName(Class<?> type) {
        if (type.isArray()) {
            return typeName(type.getComponentType()) + "[]";
        }
        return type.getName();
    }

    private static String mismatch(String owner, Set<String> expected, Set<String> actual) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);

        Set<String> added = new TreeSet<>(actual);
        added.removeAll(expected);

        return owner + " API changed. Missing: " + missing + " | Added: " + added;
    }
}
