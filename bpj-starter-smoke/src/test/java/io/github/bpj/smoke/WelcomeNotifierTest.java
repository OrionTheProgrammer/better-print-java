package io.github.bpj.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WelcomeNotifierTest {

    @Test
    void shouldPrintResolvedPlaceholderWithoutManualContext() throws Exception {
        WelcomeNotifier notifier = new WelcomeNotifier();
        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (PrintStream capture = new PrintStream(out, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            notifier.printWelcome(new Customer("Pablo"));
        } finally {
            System.setOut(originalOut);
        }

        assertEquals("Bienvenido Pablo" + System.lineSeparator(), out.toString(StandardCharsets.UTF_8));
    }
}
