package de.mhus.lib.versioning;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

// The plugin fails if a SNAPSHOT version is included in the pom file.

@Mojo(
        name = "validate-no-snapshots",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES, 
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, 
        inheritByDefault = true,
        threadSafe = true
    )
public class ValidateNoSnapshotsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        boolean failed = false;
        String reason = "\n";

        if (Util.isSnapshot(project.getVersion())) {
            reason+="SNAPSHOT PROJECT: " + project + "\n";
            failed = true;
        }

        if (Util.isSnapshot(project.getParent().getVersion())) {
            reason+="SNAPSHOT PARENT: " + project.getParent() + "\n";
            failed = true;
        }

        for (Artifact artifact : project.getDependencyArtifacts()) {
            if (Util.isSnapshot(artifact.getVersion())) {
                    reason+= "SNAPSHOT ARTIFACT: " + artifact + "\n";
                    failed = true;
            }
        }
        for (Dependency dep : project.getDependencies()) {
            if (Util.isSnapshot(dep.getVersion())) {
                reason+="SNAPSHOT DEPENDENCY: " + dep + "\n";
                failed = true;
            }
        }

        for (Dependency dep : project.getDependencyManagement().getDependencies()) {
            if (Util.isSnapshot(dep.getVersion())) {
                reason+="SNAPSHOT DEPENDENCY MANAGEMENT: " + dep + "\n";
                failed = true;
            }
        }

       if (failed)
            throw new MojoFailureException(reason);
    }

}
