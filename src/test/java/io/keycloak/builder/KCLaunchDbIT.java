package io.keycloak.builder;

import static io.quarkus.test.devmode.util.DevModeTestUtils.getHttpResponse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;

@QuarkusMainIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KCLaunchDbIT extends Utils {
    Path binPath;
    Path invokablePath;

    @Test
    @Launch({ "--app-root", "target/keycloakBuild", "--db", "mysql" })
    @Order(1)
    public void testSuccessfulBuildWithoutDB(LaunchResult result) throws IOException {
        binPath = Path.of("target/keycloakBuild/keycloak").resolve("bin");
        invokablePath = Path.of(binPath + File.separator + SCRIPT_CMD_INVOKABLE).normalize().toAbsolutePath();
        final List<String> commands = new ArrayList<>();
        Process processKC = null;
        try {
            processKC = new ProcessBuilder(invokablePath.toString(), "start-dev", "--http-enabled=true",
                    "--hostname-strict=false").start();
            assertTrue(getHttpResponse().contains("Welcome to Keycloak"), "Keycloak has not started successfully without DB");
        } finally {
            if (processKC != null) {
                processKC.destroy();
            }
        }
    }

    @AfterAll
    public static void tearDown() throws IOException {
        //Delete all the dists created above
        deleteDist("target/keycloakBuild");
    }
}
