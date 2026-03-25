package io.github.bpj.smoke;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TransformationReportTest {

    @Test
    void shouldGenerateTransformationReport() throws IOException {
        Path report = Path.of("target", "reports", "bpj-transform-report.txt");
        assertTrue(Files.exists(report), "Expected BPJ report to exist");

        String content = Files.readString(report, StandardCharsets.UTF_8).replace('\\', '/');

        assertTrue(content.contains("BPJ Transformation Report"));
        assertTrue(content.contains("callsTransformed: 3"));
        assertTrue(content.contains("io/github/bpj/smoke/GreetingService.java -> 1 call(s)"));
        assertTrue(content.contains("io/github/bpj/smoke/CheckoutService.java -> 1 call(s)"));
        assertTrue(content.contains("io/github/bpj/smoke/WelcomeNotifier.java -> 1 call(s)"));
    }
}
