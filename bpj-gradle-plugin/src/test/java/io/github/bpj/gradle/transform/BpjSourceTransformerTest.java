package io.github.bpj.gradle.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BpjSourceTransformerTest {

    private final BpjSourceTransformer transformer = new BpjSourceTransformer();

    @Test
    void shouldInjectContextForSimpleBpjMemberCall() {
        String source = """
                import io.github.bpj.BPJ;
                class Demo {
                    void run(String name) {
                        BPJ.println("Hello {name}");
                    }
                }
                """;

        BpjSourceTransformer.TransformationResult result = transform(source);

        assertEquals(1, result.replacements());
        assertTrue(result.source().contains("BPJ.println(\"Hello {name}\", java.util.Map.of(\"name\", name));"));
    }

    @Test
    void shouldInjectOnlyRootVariableForNestedPlaceholder() {
        String source = """
                import io.github.bpj.BPJ;
                class Demo {
                    void run(User user) {
                        BPJ.println("Welcome {user.name} ({user.id})");
                    }
                    record User(String name, long id) {}
                }
                """;

        BpjSourceTransformer.TransformationResult result = transform(source);

        assertEquals(1, result.replacements());
        assertTrue(result.source().contains("java.util.Map.of(\"user\", user)"));
    }

    @Test
    void shouldSupportStaticImportCalls() {
        String source = """
                import static io.github.bpj.BPJ.println;
                class Demo {
                    void run(String name) {
                        println("Hello {name}");
                    }
                }
                """;

        BpjSourceTransformer.TransformationResult result = transform(source);

        assertEquals(1, result.replacements());
        assertTrue(result.source().contains("println(\"Hello {name}\", java.util.Map.of(\"name\", name));"));
    }

    @Test
    void shouldUseMapOfEntriesWhenMoreThanTenRootsArePresent() {
        String source = """
                import io.github.bpj.BPJ;
                class Demo {
                    void run(
                        int a1, int a2, int a3, int a4, int a5, int a6,
                        int a7, int a8, int a9, int a10, int a11
                    ) {
                        BPJ.println("{a1}{a2}{a3}{a4}{a5}{a6}{a7}{a8}{a9}{a10}{a11}");
                    }
                }
                """;

        BpjSourceTransformer.TransformationResult result = transform(source);

        assertEquals(1, result.replacements());
        assertTrue(result.source().contains("java.util.Map.ofEntries("));
        assertTrue(result.source().contains("java.util.Map.entry(\"a11\", a11)"));
    }

    @Test
    void shouldFailWithClearMessageForInvalidPlaceholderSyntax() {
        String source = """
                import io.github.bpj.BPJ;
                class Demo {
                    void run(String user) {
                        BPJ.println("Welcome {user-name}");
                    }
                }
                """;

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transform(source)
        );

        assertTrue(exception.getMessage().contains("Invalid BPJ placeholder(s)"));
        assertTrue(exception.getMessage().contains("{user-name}"));
        assertTrue(exception.getMessage().contains("Demo.java:4"));
    }

    @Test
    void shouldFailOnUnresolvedRootsWhenEnabled() {
        String source = """
                import io.github.bpj.BPJ;
                class Demo {
                    void run(String name) {
                        BPJ.println("Hello {missing}");
                    }
                }
                """;

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transform(source, true)
        );

        assertTrue(exception.getMessage().contains("Unresolved BPJ placeholder root(s) [missing]"));
        assertTrue(exception.getMessage().contains("Demo.java:4"));
    }

    @Test
    void shouldResolveFieldNamesWhenFailOnUnresolvedIsEnabled() {
        String source = """
                import io.github.bpj.BPJ;
                class Demo {
                    private final String name = "Ana";
                    void run() {
                        BPJ.println("Hello {name}");
                    }
                }
                """;

        BpjSourceTransformer.TransformationResult result = transform(source, true);

        assertEquals(1, result.replacements());
        assertTrue(result.source().contains("java.util.Map.of(\"name\", name)"));
    }

    private BpjSourceTransformer.TransformationResult transform(String source) {
        return transformer.transform(Path.of("Demo.java"), source);
    }

    private BpjSourceTransformer.TransformationResult transform(String source, boolean failOnUnresolved) {
        return transformer.transform(Path.of("Demo.java"), source, failOnUnresolved);
    }
}
