package io.github.bpj.smoke;

import io.github.bpj.BPJ;

public final class GreetingService {

    public String renderGreeting(String name, int age) {
        return BPJ.format("Hola {name}, tienes {age}");
    }
}
