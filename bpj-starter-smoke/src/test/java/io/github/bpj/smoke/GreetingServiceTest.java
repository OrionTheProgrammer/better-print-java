package io.github.bpj.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreetingServiceTest {

    @Test
    void shouldInterpolateWithoutManualPluginConfig() {
        GreetingService service = new GreetingService();
        String output = service.renderGreeting("Orion", 31);
        assertEquals("Hola Orion, tienes 31", output);
    }
}
