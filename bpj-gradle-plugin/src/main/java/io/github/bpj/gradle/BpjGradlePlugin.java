package io.github.bpj.gradle;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Gradle plugin that integrates BPJ source transformation before Java compilation.
 */
public final class BpjGradlePlugin implements Plugin<Project> {
    /**
     * Public plugin id.
     */
    public static final String PLUGIN_ID = "io.github.oriontheprogrammer.bpj";

    @Override
    public void apply(Project project) {
        BpjGradleExtension extension = project.getExtensions().create(
                "bpj",
                BpjGradleExtension.class,
                project.getObjects(),
                project.getLayout()
        );

        project.getPlugins().withType(JavaPlugin.class, ignored -> configureJavaProject(project, extension));
    }

    private void configureJavaProject(Project project, BpjGradleExtension extension) {
        project.afterEvaluate(ignored -> {
            SourceSetContainer sourceSets = project.getExtensions()
                    .getByType(JavaPluginExtension.class)
                    .getSourceSets();

            sourceSets.all(sourceSet -> configureSourceSet(project, sourceSet, extension));
        });
    }

    private void configureSourceSet(Project project, SourceSet sourceSet, BpjGradleExtension extension) {
        Set<File> originalSourceRoots = new LinkedHashSet<>(sourceSet.getJava().getSrcDirs());
        if (originalSourceRoots.isEmpty()) {
            return;
        }

        File outputRoot = extension.getOutputDirectory().dir(sourceSet.getName()).get().getAsFile();
        String taskName = taskName(sourceSet.getName());

        TaskProvider<BpjPrepareTask> prepareTask = project.getTasks().register(taskName, BpjPrepareTask.class, task -> {
            task.setGroup("build");
            task.setDescription("Transforms BPJ placeholders for source set '" + sourceSet.getName() + "'.");

            task.getInputDirectories().from(originalSourceRoots);
            task.getOutputDirectory().set(outputRoot);
            task.getFailOnError().set(extension.getFailOnError());
            task.getFailOnUnresolved().set(extension.getFailOnUnresolved());
            task.getVerbose().set(extension.getVerbose());
            task.getWriteReport().set(extension.getWriteReport());
            task.getReportFile().set(
                    extension.getReportDirectory().file("bpj-transform-" + sourceSet.getName() + ".txt")
            );
        });

        sourceSet.getJava().setSrcDirs(List.of(outputRoot));

        project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class).configure(task -> {
            task.dependsOn(prepareTask);
        });
    }

    private String taskName(String sourceSetName) {
        if ("main".equals(sourceSetName)) {
            return "bpjPrepare";
        }
        if (sourceSetName.isEmpty()) {
            return "bpjPrepare";
        }
        return "bpjPrepare" + Character.toUpperCase(sourceSetName.charAt(0)) + sourceSetName.substring(1);
    }
}
