package org.acme;

import io.quarkus.bootstrap.classloading.ClassPathElement;
import io.quarkus.bootstrap.classloading.PathTreeClassPathElement;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.resolver.BootstrapAppModelResolver;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.bootstrap.workspace.ArtifactSources;
import io.quarkus.bootstrap.workspace.DefaultArtifactSources;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.bootstrap.workspace.WorkspaceModuleId;
import io.quarkus.fs.util.ZipUtils;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.maven.dependency.DependencyBuilder;
import io.quarkus.paths.DirectoryPathTree;
import io.quarkus.paths.FilteredArchivePathTree;
import io.quarkus.paths.PathFilter;
import io.quarkus.paths.PathTree;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.runtime.util.ClassPathUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResult;

@QuarkusMain
public class Keycloak {

    public static void main(String[] args) throws Exception {
        final Map<String, String> parsedArgs = parseCommandLineArgs(args);
        Path path = Paths.get(System.getProperty("user.dir"), "target", "kc");
        System.setProperty("kc.home.dir", path.toAbsolutePath().toString());
        Keycloak.newInstance().setDatabase(parsedArgs.get(Option.DB.getCommandLineName())).build();
    }

    private static Map<String, String> parseCommandLineArgs(String[] args) {
        final Map<String, String> parsed = new HashMap<>();
        int i = 0;
        while (i < args.length) {
            var arg = args[i++];
            var option = Option.forCommandLineName(arg);
            if (option == null) {
                continue;
            }
            String value = null;
            if (i < args.length) {
                value = args[i++];
            }
            parsed.put(arg, value);
        }
        return parsed;
    }

    private String database;

    public static Keycloak newInstance() {
        return new Keycloak();
    }

    public Keycloak setDatabase(String db) {
        System.out.println(
                "Possible db values are: dev-file, dev-mem, mariadb, mssql,mysql, oracle, postgres. Default: dev-file.");
        String currentDB = validateDB(db);
        System.out.println("Currently using database: " + currentDB);
        database = currentDB;
        return this;
    }

    /**
     * Builds a complete Keycloak server distribution
     *
     * @throws Exception in case of a failure
     */
    public void build() throws Exception {
        System.out.println("Building Keycloak");

        // configure paths
        Path appRoot = IoUtils.mkdirs(Path.of("").normalize().toAbsolutePath());
        Path configDir = appRoot.resolve("src").resolve("main").resolve("resources").resolve("keycloak");
        Path distDir = appRoot.resolve("target").resolve("dist");
        IoUtils.recursiveDelete(distDir);
        IoUtils.mkdirs(distDir);

        // configure server dependencies
        final WorkspaceModule module = getServerWithoutExtraJdbc(configDir, distDir);

        // initialize Maven artifact resolver
        final MavenArtifactResolver mavenResolver = MavenArtifactResolver.builder()
                .setWorkspaceDiscovery(false)
                .build();

        // initialize Quarkus application model resolver
        final BootstrapAppModelResolver appModelResolver = new BootstrapAppModelResolver(mavenResolver);

        // resolve Keycloak server Quarkus application model
        final ApplicationModel appModel = appModelResolver.resolveModel(module);

        // build the Keycloak Quarkus application
        buildKeycloakQuarkusApp(appRoot, appModel);

        // add the missing content
        Path contentDir = appRoot.resolve("src").resolve("main").resolve("resources").resolve("content");
        IoUtils.copy(contentDir, distDir);

        ArtifactResult result = mavenResolver.resolve(new DefaultArtifact(Constants.ORG_KEYCLOAK,
                Constants.KEYCLOAK_CLIENT_CLI_DIST, "", "zip", Constants.KEYCLOAK_VERSION));
        File keycloakServerAppCliJar = result.getArtifact().getFile();
        ZipUtils.unzip(keycloakServerAppCliJar.toPath(), appRoot.resolve("target").resolve("zip"));
        IoUtils.copy(appRoot.resolve("target").resolve("zip").resolve("keycloak-client-tools"), distDir);
        IoUtils.recursiveDelete(appRoot.resolve("target").resolve("zip"));

        System.out.println("Done!");
    }

    /**
     * Sets up proper classloading environment to build the target Keycloak server version
     * and invokes {@link #KeycloakBuilder}.
     *
     * @param workingDir working directory
     * @param appModel resolved Keycloak Quarkus application model
     */
    private void buildKeycloakQuarkusApp(Path workingDir, final ApplicationModel appModel) {
        final Path builderClassesLocation = ClassPathUtils
                .toLocalPath(KeycloakBuilder.class.getProtectionDomain().getCodeSource().getLocation());
        final PathTree keycloakBuilderPathTree;
        if (Files.isDirectory(builderClassesLocation)) {
            keycloakBuilderPathTree = new DirectoryPathTree(builderClassesLocation,
                    PathFilter.forIncludes(List.of(KeycloakBuilder.class.getName().replace('.', '/') + ".class")));
        } else {
            keycloakBuilderPathTree = new FilteredArchivePathTree(builderClassesLocation,
                    PathFilter.forIncludes(List.of(KeycloakBuilder.class.getName().replace('.', '/') + ".class")));
        }

        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final QuarkusClassLoader.Builder keycloakClBuilder = QuarkusClassLoader
                .builder("Keycloak Builder Class Loader", cl, false)
                .addElement(new PathTreeClassPathElement(keycloakBuilderPathTree, true));
        appModel.getDependencies().forEach(d -> {
            if (!d.getArtifactId().equals("quarkus-bootstrap-app-model")) {
                keycloakClBuilder.addElement(ClassPathElement.fromDependency(d));
            }
        });

        try (QuarkusClassLoader keycloakCl = keycloakClBuilder.build()) {
            Thread.currentThread().setContextClassLoader(keycloakCl);
            keycloakCl.loadClass(KeycloakBuilder.class.getName())
                    .getMethod(KeycloakBuilder.CREATE_QUARKUS_APPLICATION, Path.class, ApplicationModel.class)
                    .invoke(null, workingDir, appModel);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Keycloak server", e);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    private static WorkspaceModule getDefaultServer(Path target) {
        final WorkspaceModule module = WorkspaceModule.builder()
                .setModuleId(WorkspaceModuleId.of("io.playground", "keycloak-app", "1"))
                .setBuildDir(target)
                .addDependencyConstraint(
                        Dependency.pomImport(Constants.ORG_KEYCLOAK, "keycloak-quarkus-parent", Constants.KEYCLOAK_VERSION))
                .addDependency(
                        Dependency.of(Constants.ORG_KEYCLOAK, Constants.KEYCLOAK_QUARKUS_SERVER, Constants.KEYCLOAK_VERSION))
                .addDependency(Dependency.of(Constants.ORG_KEYCLOAK, Constants.KEYCLOAK_QUARKUS_SERVER_APP,
                        Constants.KEYCLOAK_VERSION))
                .build();
        return module;
    }

    private WorkspaceModule getServerWithoutExtraJdbc(Path configDir, Path buildDir) {
        // customize the keycloak-quarkus-server dependency
        final DependencyBuilder quarkusServerBuilder = DependencyBuilder.newInstance()
                .setGroupId(Constants.ORG_KEYCLOAK)
                .setArtifactId(Constants.KEYCLOAK_QUARKUS_SERVER)
                .setVersion(Constants.KEYCLOAK_VERSION)
                .addExclusion(Constants.IO_QUARKUS, "quarkus-bootstrap-core")
                .addExclusion(Constants.IO_QUARKUS, "quarkus-bootstrap-maven-resolver")
                .addExclusion(Constants.IO_QUARKUS, "quarkus-bootstrap-gradle-resolver");
        List<String> AvailableDBs = Arrays.asList("postgresql", "mariadb", "mssql", "mysql", "oracle");
        for (String dbs : AvailableDBs) {
            if (!dbs.equalsIgnoreCase(database)) {
                System.out.println("Removing " + dbs);
                excludeJdbcDriver(dbs, quarkusServerBuilder);
            }
        }
        /*
         * excludeJdbcDriver("h2", quarkusServerBuilder); required for some reason
         * excludeJdbcDriver("postgresql", quarkusServerBuilder);
         * excludeJdbcDriver("mariadb", quarkusServerBuilder);
         * excludeJdbcDriver("mssql", quarkusServerBuilder);
         * excludeJdbcDriver("mysql", quarkusServerBuilder);
         * excludeJdbcDriver("oracle", quarkusServerBuilder);
         */

        // configure the Keycloak server application
        final WorkspaceModule module = WorkspaceModule.builder()
                .setModuleId(WorkspaceModuleId.of("io.playground", "keycloak-app", "1"))
                .setBuildDir(buildDir)
                // enforce the Keycloak Quarkus version constraints
                // .addDependencyConstraint(Dependency.pomImport(IO_QUARKUS, "quarkus-bom", "999-SNAPSHOT")) to upgrade Quarkus to the current main branch
                // .addDependencyConstraint(Dependency.of("org.liquibase", "liquibase-core", "4.8.0")) to restore the original liquibase-core version used by Keycloak
                .addDependencyConstraint(
                        Dependency.pomImport(Constants.ORG_KEYCLOAK, "keycloak-quarkus-parent", Constants.KEYCLOAK_VERSION))
                .addDependencyConstraint(quarkusServerBuilder.build())
                .addDependency(DependencyBuilder.newInstance()
                        .setGroupId(Constants.ORG_KEYCLOAK)
                        .setArtifactId(Constants.KEYCLOAK_QUARKUS_SERVER)
                        .build())
                .addArtifactSources(new DefaultArtifactSources(ArtifactSources.MAIN,
                        List.of(), List.of(SourceDir.of(configDir, configDir))))
                .build();
        return module;
    }

    private static void excludeJdbcDriver(String name, DependencyBuilder builder) {
        builder.addExclusion(Constants.IO_QUARKUS, Constants.QUARKUS_JDBC_PREFIX + name);
        builder.addExclusion(Constants.IO_QUARKUS, Constants.QUARKUS_JDBC_PREFIX + name + "-deployment");
    }

    public static String validateDB(String dbVendor) {
        if (!Validate(dbVendor)) {
            System.out.println("Entered selection doesn't matches the possible types. Using default");
            dbVendor = "dev-file";
        }
        return dbVendor;
    }

    public static Boolean Validate(String dbVendor) {
        String[] validDBs = { "dev-file", "dev-mem", "mariadb", "mssql", "mysql", "oracle", "postgres" };
        boolean isValidDB = false;
        for (String db : validDBs) {
            if (db.equals(dbVendor)) {
                isValidDB = true;
                System.out.println("Entered selection is a valid type");
                break;
            }
        }
        return isValidDB;
    }

}