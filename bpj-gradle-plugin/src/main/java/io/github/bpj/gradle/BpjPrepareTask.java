package io.github.bpj.gradle;

import io.github.bpj.gradle.transform.BpjSourceTransformer;
import io.github.bpj.gradle.transform.BpjSourceTransformer.TransformationResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

/**
 * Gradle task that rewrites one-argument BPJ calls into explicit context calls.
 */
public abstract class BpjPrepareTask extends DefaultTask {
    private final ConfigurableFileCollection inputDirectories = getProject().files();
    private final DirectoryProperty outputDirectory = getProject().getObjects().directoryProperty();
    private final Property<Boolean> failOnError = getProject().getObjects().property(Boolean.class).convention(true);
    private final Property<Boolean> failOnUnresolved = getProject().getObjects().property(Boolean.class).convention(false);
    private final Property<Boolean> verbose = getProject().getObjects().property(Boolean.class).convention(false);
    private final Property<Boolean> writeReport = getProject().getObjects().property(Boolean.class).convention(false);
    private final RegularFileProperty reportFile = getProject().getObjects().fileProperty();

    /**
     * Source directories to transform.
     *
     * @return source directory collection
     */
    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getInputDirectories() {
        return inputDirectories;
    }

    /**
     * Output directory where transformed sources are written.
     *
     * @return output directory property
     */
    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * If true, transformation errors fail the task.
     *
     * @return failOnError property
     */
    @Input
    public Property<Boolean> getFailOnError() {
        return failOnError;
    }

    /**
     * If true, unresolved placeholder roots fail the task.
     *
     * @return failOnUnresolved property
     */
    @Input
    public Property<Boolean> getFailOnUnresolved() {
        return failOnUnresolved;
    }

    /**
     * If true, logs transformed files.
     *
     * @return verbose property
     */
    @Input
    public Property<Boolean> getVerbose() {
        return verbose;
    }

    /**
     * If true, writes a transformation report file.
     *
     * @return writeReport property
     */
    @Input
    public Property<Boolean> getWriteReport() {
        return writeReport;
    }

    /**
     * Output report file.
     *
     * @return report file property
     */
    @Optional
    @OutputFile
    public RegularFileProperty getReportFile() {
        return reportFile;
    }

    /**
     * Executes source transformation.
     */
    @TaskAction
    public void transformSources() {
        BpjSourceTransformer transformer = new BpjSourceTransformer();
        Path outputRoot = outputDirectory.get().getAsFile().toPath();

        int filesProcessed = 0;
        int transformedCalls = 0;
        List<FileTransformResult> transformedFiles = new ArrayList<>();

        try {
            resetDirectory(outputRoot);
            List<Path> inputRoots = inputDirectories.getFiles().stream()
                    .map(java.io.File::toPath)
                    .filter(Files::exists)
                    .toList();

            for (Path inputRoot : inputRoots) {
                List<Path> sources;
                try (Stream<Path> walk = Files.walk(inputRoot)) {
                    sources = walk
                            .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                            .toList();
                }

                for (Path source : sources) {
                    filesProcessed++;
                    Path relative = inputRoot.relativize(source);
                    Path target = outputRoot.resolve(relative);
                    Files.createDirectories(target.getParent());

                    String original = Files.readString(source, StandardCharsets.UTF_8);
                    TransformationResult result = transformer.transform(source, original, failOnUnresolved.get());
                    transformedCalls += result.replacements();
                    transformedFiles.add(new FileTransformResult(relative.toString(), result.replacements()));

                    Files.writeString(target, result.source(), StandardCharsets.UTF_8);
                    if (Boolean.TRUE.equals(verbose.get()) && result.replacements() > 0) {
                        getLogger().lifecycle("BPJ transformed {} ({} call(s))", relative, result.replacements());
                    }
                }
            }

            if (Boolean.TRUE.equals(writeReport.get()) && reportFile.isPresent()) {
                writeReport(filesProcessed, transformedCalls, transformedFiles);
            }

            getLogger().lifecycle(
                    "BPJ prepare completed. Processed {} file(s), transformed {} call(s).",
                    filesProcessed,
                    transformedCalls
            );
        } catch (Exception exception) {
            if (Boolean.TRUE.equals(failOnError.get())) {
                throw new RuntimeException("BPJ Gradle transformation failed", exception);
            }
            getLogger().warn("BPJ transformation failed but build will continue: {}", exception.getMessage());
            if (Boolean.TRUE.equals(verbose.get())) {
                getLogger().debug("BPJ transformation stacktrace", exception);
            }
        }
    }

    private void resetDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> walk = Files.walk(directory)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (RuntimeException exception) {
                if (exception.getCause() instanceof IOException ioException) {
                    throw ioException;
                }
                throw exception;
            }
        }
        Files.createDirectories(directory);
    }

    private void writeReport(
            int filesProcessed,
            int transformedCalls,
            List<FileTransformResult> transformedFiles
    ) throws IOException {
        Path reportPath = reportFile.get().getAsFile().toPath();
        Path parent = reportPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> lines = new ArrayList<>();
        lines.add("BPJ Transformation Report");
        lines.add("generatedAt: " + OffsetDateTime.now());
        lines.add("project: " + getProject().getPath());
        lines.add("filesProcessed: " + filesProcessed);
        lines.add("callsTransformed: " + transformedCalls);
        lines.add("");
        lines.add("files:");
        for (FileTransformResult result : transformedFiles) {
            lines.add(result.path() + " -> " + result.replacements() + " call(s)");
        }

        Files.write(reportPath, lines, StandardCharsets.UTF_8);
        getLogger().lifecycle("BPJ report written to {}", reportPath);
    }

    private record FileTransformResult(String path, int replacements) {
    }
}
