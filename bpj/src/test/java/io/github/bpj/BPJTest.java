package io.github.bpj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BPJTest {

    @Test
    void shouldReturnTemplateWhenNoContextIsProvided() {
        String result = BPJ.format("Plain text without placeholders");
        assertEquals("Plain text without placeholders", result);
    }

    @Test
    void shouldInterpolateUsingThreadBoundContextWithoutPassingContextPerCall() {
        try (BPJ.Scope ignored = BPJ.bind("name", "Pablo")) {
            String result = BPJ.format("Hello! my name is {name}");
            assertEquals("Hello! my name is Pablo", result);
        }
    }

    @Test
    void shouldSupportNestedBoundContexts() {
        try (BPJ.Scope ignored = BPJ.bind("name", "Ana")) {
            assertEquals("Hello Ana", BPJ.format("Hello {name}"));
            try (BPJ.Scope nested = BPJ.bind("name", "Luis")) {
                assertEquals("Hello Luis", BPJ.format("Hello {name}"));
            }
            assertEquals("Hello Ana", BPJ.format("Hello {name}"));
        }
    }

    @Test
    void shouldReportBoundContextState() {
        assertFalse(BPJ.hasBoundContext());
        try (BPJ.Scope ignored = BPJ.bind("name", "Ana")) {
            assertTrue(BPJ.hasBoundContext());
        }
        assertFalse(BPJ.hasBoundContext());
    }

    @Test
    void shouldClearBoundContext() {
        try (BPJ.Scope ignored = BPJ.bind("name", "Ana")) {
            assertTrue(BPJ.hasBoundContext());
            BPJ.clearBoundContext();
            assertFalse(BPJ.hasBoundContext());
            assertEquals("Hello {name}", BPJ.format("Hello {name}"));
        }
    }

    @Test
    void shouldInterpolateSimpleVariableFromMap() {
        String result = BPJ.format("Hello! my name is {name}", Map.of("name", "Ana"));
        assertEquals("Hello! my name is Ana", result);
    }

    @Test
    void shouldInterpolateNestedProperties() {
        Product product = new Product(1250);
        String result = BPJ.format(
                "Product value: {product.value}",
                Map.of("product", product)
        );
        assertEquals("Product value: 1250", result);
    }

    @Test
    void shouldInterpolateNoArgMethodSegments() {
        Person person = new Person("John");
        String result = BPJ.format("Hello {person.getName()}", Map.of("person", person));
        assertEquals("Hello John", result);
    }

    @Test
    void shouldInterpolateFromRootContextObject() {
        Checkout checkout = new Checkout(3000, "Carlos");
        String result = BPJ.format("Customer: {customer}. Total: {finalPrice}", checkout);
        assertEquals("Customer: Carlos. Total: 3000", result);
    }

    @Test
    void shouldInterpolateUsingVarargs() {
        String result = BPJ.format(
                "Customer: {name} | Total: {total}",
                "name", "Lucia",
                "total", 8750
        );
        assertEquals("Customer: Lucia | Total: 8750", result);
    }

    @Test
    void shouldKeepPlaceholderWhenNotResolvedInDefaultMode() {
        String result = BPJ.format("Final total: {final}", Map.of("subtotal", 100));
        assertEquals("Final total: {final}", result);
    }

    @Test
    void shouldThrowInStrictModeWhenPlaceholderIsMissing() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BPJ.formatStrict("Final total: {final}", Map.of("subtotal", 100))
        );
    }

    @Test
    void shouldKeepPlaceholderWhenMethodSegmentIsMissingInDefaultMode() {
        Person person = new Person("John");
        String result = BPJ.format("Hello {person.getMissing()}", Map.of("person", person));
        assertEquals("Hello {person.getMissing()}", result);
    }

    @Test
    void shouldThrowInStrictModeWhenMethodSegmentIsMissing() {
        Person person = new Person("John");
        assertThrows(
                IllegalArgumentException.class,
                () -> BPJ.formatStrict("Hello {person.getMissing()}", Map.of("person", person))
        );
    }

    @Test
    void shouldThrowInStrictModeWhenNoBoundContextExists() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BPJ.formatStrict("Final total: {final}")
        );
    }

    @Test
    void shouldThrowWhenVarargsAreInvalid() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BPJ.format("Hello {name}", "name", "Ana", "orphan")
        );
    }

    @Test
    void shouldSupportEscapedBraces() {
        assertEquals("{name}", BPJ.format("{{name}}"));
        try (BPJ.Scope ignored = BPJ.bind("name", "Maria")) {
            assertEquals("Value {name} and Maria", BPJ.format("Value {{name}} and {name}"));
        }
    }

    @Test
    void shouldFormatHighlightedValuesWithAnsiColor() {
        String result = BPJ.formatHighlighted("Hello {name}", Map.of("name", "Ana"));
        assertTrue(result.contains("\u001B[36mAna\u001B[0m"));
    }

    @Test
    void shouldAllowChangingHighlightColor() {
        BPJ.AnsiColor previous = BPJ.getHighlightColor();
        try {
            BPJ.setHighlightColor(BPJ.AnsiColor.BRIGHT_GREEN);
            String result = BPJ.formatHighlighted("Hello {name}", Map.of("name", "Ana"));
            assertTrue(result.contains("\u001B[92mAna\u001B[0m"));
        } finally {
            BPJ.setHighlightColor(previous);
        }
    }

    @Test
    void shouldFailWhenScopesAreClosedOutOfOrder() {
        BPJ.Scope outer = BPJ.bind("name", "outer");
        BPJ.Scope inner = BPJ.bind("name", "inner");

        assertThrows(IllegalStateException.class, outer::close);
        inner.close();
        outer.close();
    }

    private static final class Product {
        private final int value;

        private Product(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private static final class Person {
        private final String name;

        private Person(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private record Checkout(int finalPrice, String customer) {
    }
}
