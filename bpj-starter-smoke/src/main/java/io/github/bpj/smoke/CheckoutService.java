package io.github.bpj.smoke;

import io.github.bpj.BPJ;

public final class CheckoutService {

    public String buildOrderSummary(Customer customer, Product product, int quantity) {
        int subtotal = product.unitPrice() * quantity;
        int total = subtotal;
        return BPJ.format("Pedido de {customer.name}: {quantity} x {product.name} = {subtotal}. Total {total}");
    }
}
