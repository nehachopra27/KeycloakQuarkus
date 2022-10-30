package org.acme;

import io.quarkus.bootstrap.classloading.PathTreeClassPathElement;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.paths.DirectoryPathTree;
import io.quarkus.paths.FilteredArchivePathTree;
import io.quarkus.paths.PathFilter;
import io.quarkus.paths.PathTree;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.runtime.util.ClassPathUtils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResult;

@QuarkusMain
public class Keycloak {

    public static void main(String[] args) throws Exception {
        final Map<String, String> parsedArgs = parseCommandLineArgs(args);
        Path path = Paths.get(System.getProperty("user.dir"), "target", "kc");
        System.setProperty("kc.home.dir", path.toAbsolutePath().toString());
        Keycloak.newInstance()
                .setDatabase(parsedArgs.get(Option.DB.getCommandLineName()))
                .setVersion(parsedArgs.get(Option.VERSION.getCommandLineName()))
                .build();
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

    private String keycloakVersion;
    private String database;

    public static Keycloak newInstance() {
        return new Keycloak();
    }

    public Keycloak setDatabase(String db) {
        System.out.println(
                "Possible db values are: dev-file, dev-mem, mariadb, mssql,mysql, oracle, postgres. Default: dev-file.");
        database = validateDB(db);
        return this;
    }

    public Keycloak setVersion(String version) {
        if (version == null || version.isBlank()) {
            version = Constants.KEYCLOAK_VERSION;
        }
        this.keycloakVersion = version;
        return this;
    }

    /**
     * Builds a complete Keycloak server distribution
     *
     * @throws Exception in case of a failure
     */
    public void build() throws Exception {
        System.out.println("Building Keycloak " + keycloakVersion);
        System.out.println("For database " + database);

        // configure paths
        Path appRoot = IoUtils.mkdirs(Path.of("").normalize().toAbsolutePath());
        Path distDir = appRoot.resolve("keycloak");
        IoUtils.recursiveDelete(distDir);
        IoUtils.mkdirs(distDir);

        // initialize Maven artifact resolver
        final MavenArtifactResolver mavenResolver = KeycloakBuilder.getMavenArtifactResolver();

        // build the Keycloak Quarkus application
        buildKeycloakQuarkusApp(appRoot, distDir, mavenResolver);

        // add resources from the content directory
        ClassPathUtils.consumeAsPaths("content", p -> {
            try {
                IoUtils.copy(p, distDir);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to copy " + p + " to " + distDir, e);
            }
        });

        ArtifactResult result = mavenResolver.resolve(new DefaultArtifact(Constants.ORG_KEYCLOAK,
                Constants.KEYCLOAK_CLIENT_CLI_DIST, "", "zip", keycloakVersion));
        final File keycloakServerAppCliZip = result.getArtifact().getFile();
        PathTree.ofArchive(keycloakServerAppCliZip.toPath()).accept(Constants.KEYCLOAK_CLIENT_TOOLS, visit -> {
            if (visit == null) {
                throw new IllegalStateException(
                        "Failed to locate " + Constants.KEYCLOAK_CLIENT_TOOLS + " in " + keycloakServerAppCliZip);
            }
            try {
                IoUtils.copy(visit.getPath(), distDir);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to copy Keycloak CLI scripts", e);
            }
        });

        setExecutePermissions(distDir);

        System.out.println("Done!");
    }

    private void setExecutePermissions(Path distDir) throws IOException {
        final Path binDir = distDir.resolve("bin");
        if (!Files.isDirectory(binDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(binDir)) {
            stream.forEach(p -> {
                if (p.getFileName().toString().endsWith(".sh")) {
                    try {
                        Files.setPosixFilePermissions(p, Set.of(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE,
                                PosixFilePermission.OWNER_EXECUTE,
                                PosixFilePermission.GROUP_READ,
                                PosixFilePermission.GROUP_WRITE,
                                PosixFilePermission.GROUP_EXECUTE));
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to set execute permissions of " + p, e);
                    }
                }
            });
        }
    }

    /**
     * Sets up proper classloading environment to build the target Keycloak server version
     * and invokes {@link #KeycloakBuilder}.
     *
     * @param workingDir working directory
     * @param appModel resolved Keycloak Quarkus application model
     */
    private void buildKeycloakQuarkusApp(Path workingDir, Path distDir, MavenArtifactResolver mavenResolver) {

        final Path builderClassesLocation = ClassPathUtils
                .toLocalPath(KeycloakBuilder.class.getProtectionDomain().getCodeSource().getLocation());
        final PathTree keycloakBuilderPathTree;
        if (Files.isDirectory(builderClassesLocation)) {
            keycloakBuilderPathTree = new DirectoryPathTree(builderClassesLocation, getClassFilter());
        } else {
            keycloakBuilderPathTree = new FilteredArchivePathTree(builderClassesLocation, getClassFilter());
        }

        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final QuarkusClassLoader.Builder keycloakClBuilder = QuarkusClassLoader
                .builder("Keycloak Builder Class Loader", cl, false)
                .addElement(new PathTreeClassPathElement(keycloakBuilderPathTree, true));

        final URL keycloakBuilderResources = Thread.currentThread().getContextClassLoader().getResource("keycloak-builder");
        ClassPathUtils.consumeAsPath(keycloakBuilderResources, keycloakBuilderPath -> {
            try (QuarkusClassLoader keycloakCl = keycloakClBuilder.build()) {
                Thread.currentThread().setContextClassLoader(keycloakCl);
                keycloakCl.loadClass(KeycloakBuilder.class.getName())
                        .getMethod(KeycloakBuilder.CREATE_QUARKUS_APPLICATION,
                                Path.class, // working dir
                                Path.class, // dist dir
                                String.class, // keycloak version
                                String.class // database
                )
                        .invoke(null, keycloakBuilderPath, distDir, keycloakVersion, database);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create Keycloak server", e);
            } finally {
                Thread.currentThread().setContextClassLoader(cl);
            }
        });
    }

    private static PathFilter getClassFilter() {
        return PathFilter.forIncludes(List.of(
                KeycloakBuilder.class.getName().replace('.', '/') + ".class"));
    }

    public static String validateDB(String dbVendor) {
        if (!validate(dbVendor)) {
            System.out.println("Entered selection doesn't matches the possible types. Using default");
            dbVendor = "dev-file";
        }
        return dbVendor;
    }

    public static Boolean validate(String dbVendor) {
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
