/*
 * Copyright 2015 Reto Gmür
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package aQute.bnd.maven.export.plugin;

import aQute.bnd.osgi.Jar;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

@Mojo(name = "create-launcher", requiresProject = true, defaultPhase = LifecyclePhase.INSTALL, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class CreateLauncherMavenPlugin extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File buildDirectory;

    @Parameter(defaultValue = "${project.build.finalName}", readonly = true)
    private String finalName;
    
    
    
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * The entry point to Aether, i.e. the component doing all the work.
     *
     */
    @Component
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     *
     */
    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution.
     *
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepos;

    private final List<String> includeScopes = Arrays.asList("runtime", "compile");

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Collection<File> jarsToInclude = getDependecyFiles();
            JarsLauncher jarsLauncher;
            try {
                jarsLauncher = new JarsLauncher();
            } catch (Exception ex) {
                throw new MojoExecutionException("Exception in execution", ex);
            }
            addFrameworkJars(jarsLauncher);
            //projectLauncher.getRunpath().add(ROLE)
            for (File jarToInclude : jarsToInclude) {
                jarsLauncher.addRunBundle(jarToInclude);
            }
            if ("jar".equals(project.getArtifact().getType())) {
                final File thisArtifactJar = project.getArtifact().getFile();
                if (thisArtifactJar.exists()) {
                    jarsLauncher.addRunBundle(thisArtifactJar);
                } else {
                    getLog().warn(thisArtifactJar + " does not exist, nota dding to bundle.");
                }
            }
            OutputStream outStream;
            try (
                    Jar jar = jarsLauncher.executable()) {
                buildDirectory.mkdirs();
                outStream = new FileOutputStream(new File(buildDirectory, finalName+"-launcher.jar"));
                jar.write(outStream);
            } catch (Exception ex) {
                throw new MojoExecutionException("Exception in execution", ex);
            }
            outStream.close();

        } catch (IOException ex) {
            throw new MojoExecutionException("Exception in execution", ex);
        }

    }

    private Collection<File> getDependecyFiles() throws MojoFailureException, MojoExecutionException {
        Log log = getLog();

        Collection<File> resultList = new HashSet<>();
        Set<org.apache.maven.artifact.Artifact> artifacts = project.getArtifacts();
        for (org.apache.maven.artifact.Artifact artifact : artifacts) {
            if (!includeScopes.contains(artifact.getScope())) {
                log.info("skipping (wrong scope): " + artifact);
                continue;
            }
            if (!"jar".equals(artifact.getType())) {
                log.info("skipping (not a jar): " + artifact);
                continue;
            }
            resultList.add(artifact.getFile());
        }
        return resultList;
    }

    private void addFrameworkJars(JarsLauncher jarsLauncher) throws MojoExecutionException {
        addFrameworkJar(jarsLauncher, "org.apache.felix", "org.apache.felix.framework", "5.4.0");
        addFrameworkJar(jarsLauncher, "org.wymiwyg.simple-osgi-launcher", "simple-osgi-launcher", "1.0.0-SNAPSHOT");

    }

    private void addFrameworkJar(JarsLauncher jarsLauncher, String groupId, String artifactId, String version) throws MojoExecutionException {
        Artifact external = new DefaultArtifact(groupId, artifactId, "jar", version);
        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(external);
        request.setRepositories(remoteRepos);

        getLog().info("Resolving artifact " + external + " from " + remoteRepos);

        ArtifactResult result;
        try {
            result = repoSystem.resolveArtifact(repoSession, request);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        File jar = result.getArtifact().getFile();
        jarsLauncher.addFrameworkJar(jar);
    }

}
