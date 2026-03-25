package io.github.bpj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BPJTest {

    @Test
    void shouldReturnTemplateWhenNoContextIsProvided() {
        String result = BPJ.format("Texto sin placeholders");
        assertEquals("Texto sin placeholders", result);
    }

    @Test
    void shouldInterpolateSimpleVariableFromMap() {
        String result = BPJ.format("Hola! mi nombre es {name}", Map.of("name", "Ana"));
        assertEquals("Hola! mi nombre es Ana", result);
    }

    @Test
    void shouldInterpolateNestedProperties() {
        Product product = new Product(1250);
        String result = BPJ.format(
                "Valor del producto: {product.value}",
                Map.of("product", product)
        );
        assertEquals("Valor del producto: 1250", result);
    }

    @Test
    void shouldInterpolateFromRootContextObject() {
        Checkout checkout = new Checkout(3000, "Carlos");
        String result = BPJ.format("Cliente: {customer}. Total: {finalPrice}", checkout);
        assertEquals("Cliente: Carlos. Total: 3000", result);
    }

    @Test
    void shouldInterpolateUsingVarargs() {
        String result = BPJ.format(
                "Cliente: {name} | Total: {total}",
                "name", "Lucia",
                "total", 8750
        );
        assertEquals("Cliente: Lucia | Total: 8750", result);
    }

    @Test
    void shouldKeepPlaceholderWhenNotResolvedInDefaultMode() {
        String result = BPJ.format("Total final: {final}", Map.of("subtotal", 100));
        assertEquals("Total final: {final}", result);
    }

    @Test
    void shouldThrowInStrictModeWhenPlaceholderIsMissing() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BPJ.formatStrict("Total final: {final}", Map.of("subtotal", 100))
        );
    }

    @Test
    void shouldThrowWhenVarargsAreInvalid() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BPJ.format("Hola {name}", "name")
        );
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

    private record Checkout(int finalPrice, String customer) {
    }
}
