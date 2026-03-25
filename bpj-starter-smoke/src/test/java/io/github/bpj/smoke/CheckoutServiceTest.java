package io.github.bpj.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CheckoutServiceTest {

    @Test
    void shouldInterpolateMicroserviceStyleSummaryWithoutManualContext() {
        CheckoutService service = new CheckoutService();
        Customer customer = new Customer("Orion");
        Product product = new Product("Keyboard", 15);

        String output = service.buildOrderSummary(customer, product, 3);

        assertEquals("Pedido de Orion: 3 x Keyboard = 45. Total 45", output);
    }
}
