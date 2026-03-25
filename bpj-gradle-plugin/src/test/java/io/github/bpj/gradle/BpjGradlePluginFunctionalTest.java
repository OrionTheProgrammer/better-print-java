package io.github.bpj.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bpj.BPJ;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BpjGradlePluginFunctionalTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldTransformAndCompileJavaProject() throws Exception {
        Assumptions.assumeTrue(
                Runtime.version().feature() <= 23,
                "Gradle 8.x functional test is skipped on Java " + Runtime.version().feature()
        );

        Path projectDir = tempDir.resolve("sample");
        Files.createDirectories(projectDir);

        Path pluginLocation = Path.of(BpjGradlePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path bpjLocation = Path.of(BPJ.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String pluginClasspath = toGradleFileLiteral(pluginLocation.toString());

        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'bpj-gradle-smoke'");
        writeFile(projectDir.resolve("build.gradle"), """
                buildscript {
                  dependencies {
                    classpath files(
                            %s
                    )
                  }
                }

                apply plugin: "java"
                apply plugin: "io.github.oriontheprogrammer.bpj"

                repositories {
                  mavenCentral()
                }

                dependencies {
                  implementation files(%s)
                  testImplementation "org.junit.jupiter:junit-jupiter:5.11.4"
                }

                test {
                  useJUnitPlatform()
                }
                """.formatted(pluginClasspath, toGradleFileLiteral(bpjLocation.toString())));

        Path mainJava = projectDir.resolve("src/main/java/demo/GreetingService.java");
        writeFile(mainJava, """
                package demo;

                import io.github.bpj.BPJ;

                public class GreetingService {
                    public String greet(String name) {
                        return BPJ.format("Hello {name}");
                    }
                }
                """);

        Path testJava = projectDir.resolve("src/test/java/demo/GreetingServiceTest.java");
        writeFile(testJava, """
                package demo;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                class GreetingServiceTest {
                    @Test
                    void shouldInterpolateWithoutManualContext() {
                        GreetingService service = new GreetingService();
                        assertEquals("Hello Orion", service.greet("Orion"));
                    }
                }
                """);

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("test", "--stacktrace")
                .withGradleVersion("8.11.1")
                .build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));

        Path transformedSource =
                projectDir.resolve("build/generated/sources/bpj/main/demo/GreetingService.java");
        assertTrue(Files.exists(transformedSource));
        String transformed = Files.readString(transformedSource, StandardCharsets.UTF_8);
        assertTrue(transformed.contains("java.util.Map.of(\"name\", name)"));
    }

    private static String toGradleFileLiteral(String path) {
        return "'" + path.replace("\\", "/").replace("'", "\\'") + "'";
    }

    private static void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
