package io.github.bpj.maven;

import io.github.bpj.maven.transform.BpjSourceTransformer;
import io.github.bpj.maven.transform.BpjSourceTransformer.TransformationResult;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Rewrites one-argument BPJ calls in source code into explicit context calls.
 */
@Mojo(name = "prepare", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class BpjPrepareMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.sourceDirectory}", required = true)
    private File inputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/bpj", required = true)
    private File outputDirectory;

    @Parameter(defaultValue = "true")
    private boolean replaceCompileSourceRoot;

    @Parameter(defaultValue = "true")
    private boolean failOnError;

    @Parameter(defaultValue = "false")
    private boolean verbose;

    @Override
    public void execute() throws MojoExecutionException {
        if (inputDirectory == null || !inputDirectory.exists()) {
            getLog().info("BPJ prepare skipped: input directory not found.");
            return;
        }

        Path inputRoot = inputDirectory.toPath();
        Path outputRoot = outputDirectory.toPath();
        BpjSourceTransformer transformer = new BpjSourceTransformer();

        int changedCalls = 0;
        int filesProcessed = 0;

        try {
            List<Path> sources;
            try (Stream<Path> sourceStream = Files.walk(inputRoot)) {
                sources = sourceStream
                        .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                        .toList();
            }

            if (sources.isEmpty()) {
                getLog().info("BPJ prepare skipped: no Java source files found.");
                return;
            }

            for (Path source : sources) {
                filesProcessed++;
                Path relative = inputRoot.relativize(source);
                Path target = outputRoot.resolve(relative);
                Files.createDirectories(target.getParent());

                String original = Files.readString(source, StandardCharsets.UTF_8);
                TransformationResult result = transformer.transform(source, original);
                changedCalls += result.replacements();

                Files.writeString(target, result.source(), StandardCharsets.UTF_8);
                if (verbose && result.replacements() > 0) {
                    getLog().info("BPJ transformed " + relative + " (" + result.replacements() + " call(s))");
                }
            }

            configureCompileRoots();
            getLog().info(
                    "BPJ prepare completed. Processed " + filesProcessed
                            + " file(s), transformed " + changedCalls + " call(s)."
            );
        } catch (Exception e) {
            if (failOnError) {
                throw new MojoExecutionException("BPJ source preparation failed", e);
            }
            getLog().warn("BPJ prepare failed but build will continue: " + e.getMessage());
            if (verbose) {
                getLog().debug(stackTrace(e));
            }
        }
    }

    private void configureCompileRoots() throws IOException {
        String inputRoot = inputDirectory.getCanonicalPath();
        String outputRoot = outputDirectory.getCanonicalPath();

        if (replaceCompileSourceRoot) {
            removeCompileSourceRootCompat(inputRoot);
        }

        if (!project.getCompileSourceRoots().contains(outputRoot)) {
            project.addCompileSourceRoot(outputRoot);
        }
    }

    private void removeCompileSourceRootCompat(String inputRoot) {
        try {
            Method removeMethod = MavenProject.class.getMethod("removeCompileSourceRoot", String.class);
            removeMethod.invoke(project, inputRoot);
            return;
        } catch (ReflectiveOperationException ignored) {
            // Fallback for Maven versions where this API is not available.
        }
        project.getCompileSourceRoots().remove(inputRoot);
    }

    private String stackTrace(Exception exception) {
        StringBuilder sb = new StringBuilder(exception.toString()).append(System.lineSeparator());
        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("    at ").append(element).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
