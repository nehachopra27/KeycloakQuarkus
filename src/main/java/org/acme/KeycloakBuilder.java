package org.acme;

import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.AugmentResult;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.resolver.AppModelResolverException;
import io.quarkus.bootstrap.resolver.BootstrapAppModelResolver;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.bootstrap.workspace.WorkspaceModuleId;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.maven.dependency.DependencyBuilder;
import io.quarkus.paths.PathTree;
import io.quarkus.runtime.util.ClassPathUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;
import java.util.Properties;

public class KeycloakBuilder {

    static final String CREATE_QUARKUS_APPLICATION = "createQuarkusApplication";

    private static final List<String> AVAILABLE_DBS = List.of("postgresql", "mariadb", "mssql", "mysql", "oracle");

    static MavenArtifactResolver getMavenArtifactResolver() throws BootstrapMavenException {
        return MavenArtifactResolver.builder()
                .setWorkspaceDiscovery(false)
                .build();
    }

    private static ApplicationModel resolveKeycloakQuarkusModel(
            Path distDir,
            String keycloakVersion,
            String database)
            throws AppModelResolverException {
        // initialize Quarkus application model resolver
        final BootstrapAppModelResolver appModelResolver = new BootstrapAppModelResolver(getMavenArtifactResolver());
        // configure server dependencies
        final WorkspaceModule module = getServerWithoutExtraJdbc(distDir, keycloakVersion, database);
        // resolve Keycloak server Quarkus application model
        try {
            return appModelResolver.resolveModel(module);
        } catch (Throwable t) {
            // this is just to log the Quarkus bootstrap version that failed to resolve the app model
            final CodeSource cs = BootstrapAppModelResolver.class.getProtectionDomain().getCodeSource();
            final URL url = cs == null ? null : cs.getLocation();
            if (url != null) {
                ClassPathUtils.consumeAsPath(url, p -> {
                    PathTree.ofDirectoryOrArchive(p)
                            .accept("META-INF/maven/io.quarkus/quarkus-bootstrap-maven-resolver/pom.properties", visit -> {
                                if (visit != null) {
                                    try (BufferedReader reader = Files.newBufferedReader(visit.getPath())) {
                                        final Properties props = new Properties();
                                        props.load(reader);
                                        System.out.println(
                                                "ERROR: failed to resolve Keycloak application model using Quarkus bootstrap resolver "
                                                        + props.getProperty("version"));
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                });
            }
            throw t;
        }
    }

    public static void createQuarkusApplication(Path appRoot, Path distDir, String keycloakVersion, String database)
            throws Exception {

        final ApplicationModel appModel = resolveKeycloakQuarkusModel(distDir, keycloakVersion, database);

        final WorkspaceModule module = appModel.getAppArtifact().getWorkspaceModule();
        final QuarkusBootstrap.Builder builder = QuarkusBootstrap.builder()
                .setBaseClassLoader(Thread.currentThread().getContextClassLoader())
                .setExistingModel(appModel)
                .setApplicationRoot(appRoot)
                .setTargetDirectory(module.getBuildDir().toPath())
                .setIsolateDeployment(true);

        try (CuratedApplication curated = builder.build().bootstrap()) {
            AugmentAction action = curated.createAugmentor();
            AugmentResult outcome = action.createProductionApplication();
        }
    }

    private static WorkspaceModule getServerWithoutExtraJdbc(Path buildDir,
            String keycloakVersion,
            String database) {
        // customize the keycloak-quarkus-server dependency
        final DependencyBuilder quarkusServerBuilder = DependencyBuilder.newInstance()
                .setGroupId(Constants.ORG_KEYCLOAK)
                .setArtifactId(Constants.KEYCLOAK_QUARKUS_SERVER)
                .setVersion(keycloakVersion);

        for (String dbs : AVAILABLE_DBS) {
            if (!dbs.equalsIgnoreCase(database)) {
                System.out.println("Removing " + dbs);
                excludeJdbcDriver(dbs, quarkusServerBuilder);
            }
        }

        // configure the Keycloak server application
        return WorkspaceModule.builder()
                .setModuleId(WorkspaceModuleId.of("io.playground", "keycloak-app", "1"))
                .setBuildDir(buildDir)
                // enforce the Keycloak Quarkus version constraints
                //.addDependencyConstraint(Dependency.pomImport("io.quarkus", "quarkus-bom", "999-SNAPSHOT")) // to upgrade Quarkus to the current main branch
                //.addDependencyConstraint(Dependency.of("org.liquibase", "liquibase-core", "4.8.0")) to restore the original liquibase-core version used by Keycloak
                .addDependencyConstraint(
                        Dependency.of("io.quarkus", "quarkus-bootstrap-app-model", Constants.QUARKUS_BOOTSTRAP_VERSION))
                .addDependencyConstraint(
                        Dependency.of("io.quarkus", "quarkus-bootstrap-maven-resolver", Constants.QUARKUS_BOOTSTRAP_VERSION))
                .addDependencyConstraint(
                        Dependency.pomImport(Constants.ORG_KEYCLOAK, "keycloak-quarkus-parent", keycloakVersion))
                .addDependencyConstraint(quarkusServerBuilder.build())
                .addDependency(DependencyBuilder.newInstance()
                        .setGroupId(Constants.ORG_KEYCLOAK)
                        .setArtifactId(Constants.KEYCLOAK_QUARKUS_SERVER)
                        .build())
                .build();
    }

    private static void excludeJdbcDriver(String name, DependencyBuilder builder) {
        builder.addExclusion(Constants.IO_QUARKUS, Constants.QUARKUS_JDBC_PREFIX + name);
        builder.addExclusion(Constants.IO_QUARKUS, Constants.QUARKUS_JDBC_PREFIX + name + "-deployment");
    }
}
