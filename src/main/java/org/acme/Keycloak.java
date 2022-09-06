package org.acme;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import io.quarkus.bootstrap.BootstrapException;
import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.AugmentResult;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.resolver.AppModelResolverException;
import io.quarkus.bootstrap.resolver.BootstrapAppModelResolver;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.bootstrap.workspace.ArtifactSources;
import io.quarkus.bootstrap.workspace.DefaultArtifactSources;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.bootstrap.workspace.WorkspaceModuleId;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.maven.dependency.DependencyBuilder;

public class Keycloak {

    private static final String KEYCLOAK_VERSION = "19.0.1"; // 999-SNAPSHOT to test the current main branch
    private static final String ORG_KEYCLOAK = "org.keycloak";
    private static final String KEYCLOAK_QUARKUS_SERVER = "keycloak-quarkus-parent";

    private static final String KEYCLOAK_QUARKUS_DIST = "keycloak-quarkus-dist";
    private static final String IO_QUARKUS = "io.quarkus";
    private static final String QUARKUS_JDBC_PREFIX = "quarkus-jdbc-";
    private static String DATABASE="dev-file";

    public static Keycloak newInstance(){
        return new Keycloak();
    }
    public Keycloak setDatabase(String db){
        System.out.println("Possible db values are: dev-file, dev-mem, mariadb, mssql,mysql, oracle, postgres. Default: dev-file.");
        String currentDB=validateDB(db);
        System.out.println( "Currently using database: "+currentDB);
        DATABASE=currentDB;
        return this;
    }

    public static void main(String[] args) throws Exception {
        Path path = Paths.get(System.getProperty("user.dir"), "target", "kc");
        System.setProperty("kc.home.dir", path.toAbsolutePath().toString());
        Keycloak.newInstance().setDatabase("oracle").build();

    }
    public void build() throws AppModelResolverException, BootstrapException {
        System.out.println("Building Keycloak");

        final MavenArtifactResolver mavenResolver = MavenArtifactResolver.builder()
                .setWorkspaceDiscovery(false)
                .build();
        final BootstrapAppModelResolver appModelResolver = new BootstrapAppModelResolver(mavenResolver);

        Path appRoot = IoUtils.mkdirs(Path.of("").normalize().toAbsolutePath());
        Path configDir = appRoot.resolve("src").resolve("main").resolve("resources");
        Path distDir = appRoot.resolve("target").resolve("dist");
        IoUtils.recursiveDelete(distDir);
        IoUtils.mkdirs(distDir);

        //final WorkspaceModule module = getDefaultServer(distDir);
        final WorkspaceModule module = getServerWithoutExtraJdbc(configDir, distDir);

        final ApplicationModel appModel = appModelResolver.resolveModel(module);

        final QuarkusBootstrap bootstrap = QuarkusBootstrap.builder()
                .setExistingModel(appModel)
                .setApplicationRoot(module.getMainSources().getOutputTree().getRoots().iterator().next())
                .setProjectRoot(appRoot)
                .setTargetDirectory(appModel.getAppArtifact().getWorkspaceModule().getBuildDir().toPath())
                .setAppModelResolver(appModelResolver)
                .build();

        try (CuratedApplication curated = bootstrap.bootstrap()) {
            AugmentAction action = curated.createAugmentor();
            AugmentResult outcome = action.createProductionApplication();
        }

        System.out.println("Done!");
    }

    private static WorkspaceModule getDefaultServer(Path target) {
        final WorkspaceModule module = WorkspaceModule.builder()
                .setModuleId(WorkspaceModuleId.of("io.playground", "keycloak-app", "1"))
                .setBuildDir(target)
                .addDependencyConstraint(Dependency.pomImport(ORG_KEYCLOAK, "keycloak-quarkus-parent", KEYCLOAK_VERSION))
                .addDependency(Dependency.of(ORG_KEYCLOAK, KEYCLOAK_QUARKUS_SERVER, KEYCLOAK_VERSION))
                //.addDependency(Dependency.of(ORG_KEYCLOAK, KEYCLOAK_QUARKUS_DIST, KEYCLOAK_VERSION))
                .build();
        return module;
    }

    private static WorkspaceModule getServerWithoutExtraJdbc(Path configDir, Path buildDir) {
        // customize the keycloak-quarkus-server dependency
        final DependencyBuilder quarkusServerBuilder = DependencyBuilder.newInstance()
                .setGroupId(ORG_KEYCLOAK)
                .setArtifactId(KEYCLOAK_QUARKUS_SERVER)
                .setVersion(KEYCLOAK_VERSION);
        List<String> AvailableDBs = Arrays.asList("postgresql", "mariadb","mssql","mysql","oracle");
        for (String dbs : AvailableDBs) {
            if(!dbs.equalsIgnoreCase(DATABASE)){
                System.out.println("Removing "+dbs);
                excludeJdbcDriver(dbs, quarkusServerBuilder);
            }
        }
        /*excludeJdbcDriver("h2", quarkusServerBuilder); required for some reason
        excludeJdbcDriver("postgresql", quarkusServerBuilder);
        excludeJdbcDriver("mariadb", quarkusServerBuilder);
        excludeJdbcDriver("mssql", quarkusServerBuilder);
        excludeJdbcDriver("mysql", quarkusServerBuilder);
        excludeJdbcDriver("oracle", quarkusServerBuilder);*/

        // configure the Keycloak server application
        final WorkspaceModule module = WorkspaceModule.builder()
                .setModuleId(WorkspaceModuleId.of("io.playground", "keycloak-app", "1"))
                .setBuildDir(buildDir)
                // enforce the Keycloak Quarkus version constraints
                // .addDependencyConstraint(Dependency.pomImport(IO_QUARKUS, "quarkus-bom", "999-SNAPSHOT")) to upgrade Quarkus to the current main branch
                // .addDependencyConstraint(Dependency.of("org.liquibase", "liquibase-core", "4.8.0")) to restore the original liquibase-core version used by Keycloak
                .addDependencyConstraint(Dependency.pomImport(ORG_KEYCLOAK, "keycloak-quarkus-parent", KEYCLOAK_VERSION))
                .addDependencyConstraint(quarkusServerBuilder.build())
                .addDependency(DependencyBuilder.newInstance()
                        .setGroupId(ORG_KEYCLOAK)
                        .setArtifactId(KEYCLOAK_QUARKUS_SERVER)
                        .build())
                .addArtifactSources(new DefaultArtifactSources(ArtifactSources.MAIN,
                        List.of(), List.of(SourceDir.of(configDir, configDir))))
                .build();
        return module;
    }

    private static void excludeJdbcDriver(String name, DependencyBuilder builder) {
        builder.addExclusion(ArtifactKey.ga(IO_QUARKUS, QUARKUS_JDBC_PREFIX + name));
        builder.addExclusion(ArtifactKey.ga(IO_QUARKUS, QUARKUS_JDBC_PREFIX + name + "-deployment"));
    }

    public static String validateDB(String dbVendor){
        if(!Validate(dbVendor)) {
            System.out.println("Entered selection doesn't matches the possible types. Using default");
            dbVendor="dev-file";
        }
        return dbVendor;
    }
    public static Boolean Validate(String dbVendor){
        String[] validDBs={"dev-file","dev-mem","mariadb","mssql","mysql","oracle","postgres"};
        boolean isValidDB=false;
        for (String db: validDBs){
            if(db.equals(dbVendor)) {
                isValidDB = true;
                System.out.println("Entered selection is a valid type");
                break;
            }
        }
        return isValidDB;
    }


}