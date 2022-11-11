package io.keycloak.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.junit.jupiter.api.*;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;

@QuarkusMainIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KCBuildIT extends Utils {
    @Test
    @Launch({"--app-root", "target/keycloakBuild"})
    @Order(1)
    public void distDefaultSuccess(LaunchResult result) throws IOException {
        assertTrue(result.getOutput().contains("For database dev-file"));
        Path dist = Path.of("target/launch").resolve("keycloak").normalize().toAbsolutePath();
        deleteDist("target/keycloak");
    }

    @Test
    @Launch({"--app-root", "target/keycloakOracle", "--db", "oracle"})
    @Order(2)
    public void distOracleBuilds(LaunchResult result) {
        assertTrue(result.getOutput().contains("For database oracle"));
    }

    @Test
    @Order(3)
    public void distOracleJarExists() throws IOException {
        Path libDirPath = Path.of("target/keycloakOracle/keycloak/lib/lib/main").normalize().toAbsolutePath();
        String oracleJar = "io.quarkus.quarkus-jdbc-oracle-*.Final.jar";
        File file = new File(libDirPath.toString());
        FileFilter fileFilter = new WildcardFileFilter(oracleJar);
        File[] fileArr = file.listFiles(fileFilter);
        assertTrue(fileArr.length > 0);
    }

    //Validates only 2 db exists, one h2(default) and the other specified
    @Test
    @Order(4)
    public void onlySpecifiedJarExists() throws IOException {
        Path libDirPath = Path.of("target/keycloakOracle/keycloak/lib/lib/main").normalize().toAbsolutePath();
        String dbJarRegex = "io.quarkus.quarkus-jdbc-*.Final.jar";
        File libJar = new File(libDirPath.toString());
        FileFilter fileFilter = new WildcardFileFilter(dbJarRegex);
        File[] fileArr = libJar.listFiles(fileFilter);
        assertTrue(Arrays.stream(fileArr).anyMatch(n -> n.toString().contains("io.quarkus.quarkus-jdbc-h2")));
        assertTrue(Arrays.stream(fileArr).anyMatch(n -> n.toString().contains("io.quarkus.quarkus-jdbc-oracle")));
        assertEquals(2, fileArr.length);
    }

    @Test
    @Order(5)
    public void noUnSpecifiedJarExists() throws IOException {
        Path libDirPath = Path.of("target/keycloakOracle/keycloak/lib/lib/main").normalize().toAbsolutePath();
        String dbJarRegex = "io.quarkus.quarkus-jdbc-*.Final.jar";
        File libJar = new File(libDirPath.toString());
        FileFilter fileFilter = new WildcardFileFilter(dbJarRegex);
        File[] fileArr = libJar.listFiles(fileFilter);
        assertTrue(Arrays.stream(fileArr).noneMatch(n -> n.toString().contains("io.quarkus.quarkus-jdbc-mssql")));
        assertTrue(Arrays.stream(fileArr).noneMatch(n -> n.toString().contains("io.quarkus.quarkus-jdbc-mysql")));
        assertTrue(Arrays.stream(fileArr).noneMatch(n -> n.toString().contains("io.quarkus.quarkus-jdbc-mariadb")));
        assertTrue(Arrays.stream(fileArr).noneMatch(n -> n.toString().contains("io.quarkus.quarkus-jdbc-postgresql")));
    }

    @AfterAll
    public static void tearDown() throws IOException {
        //Delete all the dists created above
        deleteDist("target/keycloakOracle");
        deleteDist("target/keycloak");
    }

}
