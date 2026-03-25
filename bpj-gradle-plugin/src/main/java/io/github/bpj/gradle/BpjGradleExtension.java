package io.github.bpj.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * Configuration extension for BPJ Gradle plugin.
 */
public class BpjGradleExtension {
    private final Property<Boolean> failOnError;
    private final Property<Boolean> failOnUnresolved;
    private final Property<Boolean> verbose;
    private final Property<Boolean> writeReport;
    private final DirectoryProperty outputDirectory;
    private final DirectoryProperty reportDirectory;

    /**
     * Creates extension with default values.
     *
     * @param objects Gradle object factory
     * @param layout project layout
     */
    public BpjGradleExtension(ObjectFactory objects, ProjectLayout layout) {
        this.failOnError = objects.property(Boolean.class).convention(true);
        this.failOnUnresolved = objects.property(Boolean.class).convention(false);
        this.verbose = objects.property(Boolean.class).convention(false);
        this.writeReport = objects.property(Boolean.class).convention(false);
        this.outputDirectory = objects.directoryProperty()
                .convention(layout.getBuildDirectory().dir("generated/sources/bpj"));
        this.reportDirectory = objects.directoryProperty()
                .convention(layout.getBuildDirectory().dir("reports/bpj"));
    }

    /**
     * If true, transformation failures fail the build.
     *
     * @return failOnError property
     */
    public Property<Boolean> getFailOnError() {
        return failOnError;
    }

    /**
     * If true, unresolved placeholder roots fail the build.
     *
     * @return failOnUnresolved property
     */
    public Property<Boolean> getFailOnUnresolved() {
        return failOnUnresolved;
    }

    /**
     * If true, transformed files are logged.
     *
     * @return verbose property
     */
    public Property<Boolean> getVerbose() {
        return verbose;
    }

    /**
     * If true, transformation report files are generated.
     *
     * @return writeReport property
     */
    public Property<Boolean> getWriteReport() {
        return writeReport;
    }

    /**
     * Base output directory for transformed sources.
     *
     * @return output directory property
     */
    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Base output directory for report files.
     *
     * @return report directory property
     */
    public DirectoryProperty getReportDirectory() {
        return reportDirectory;
    }
}
