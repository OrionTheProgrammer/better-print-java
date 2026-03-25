package io.github.bpj.smoke;

import io.github.bpj.BPJ;

public final class WelcomeNotifier {

    public void printWelcome(Customer customer) {
        BPJ.println("Bienvenido {customer.name}");
    }
}
