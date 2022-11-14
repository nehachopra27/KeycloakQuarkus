package io.keycloak.builder;

import static io.keycloak.builder.PostgresTestBase.*;
import static io.quarkus.test.devmode.util.DevModeTestUtils.isCode;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@QuarkusMainIntegrationTest
public class KCLaunchDBBuildIT extends Utils {
    Path binPath;
    Path invokablePath;

    @BeforeAll()
    public static void init() {
        startPostgresContainer();
    }

    @Test
    @Launch({ "--app-root", "target/keycloakBuild", "--db", "postgres" })
    public void testSuccessfulBuildWithDB() {
        binPath = Path.of("target/keycloakBuild/keycloak").resolve("bin");
        invokablePath = Path.of(binPath + File.separator + SCRIPT_CMD_INVOKABLE).normalize().toAbsolutePath();
        Process processKC = null;
        try {
            ProcessBuilder processBuilderKC = new ProcessBuilder(invokablePath.toString(), "start-dev", "--db", "postgres",
                    "--db-url-host", postgresJDBCUrl, "--db-username", postgresUsername, "--db-password", postgresPassword,
                    "--http-enabled=true", "--hostname-strict=false");
            processBuilderKC.redirectErrorStream(true);
            processKC = processBuilderKC.start();
            if (ensureApplicationStartupOrFailure(processKC)) {
                assertTrue(isCode("", 200), "Keycloak has not started with DB Postgres");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (processKC != null) {
                processKC.destroy();

            }
        }
    }

    @Test
    @Launch({ "--app-root", "target/keycloakBuild", "--db", "postgres" })
    public void testWrongUsername() throws IOException {
        binPath = Path.of("target/keycloakBuild/keycloak").resolve("bin");
        invokablePath = Path.of(binPath + File.separator + SCRIPT_CMD_INVOKABLE).normalize().toAbsolutePath();
        Process processKC = null;
        String incorrectPassword = "incorrect";
        try {
            ProcessBuilder processBuilderKC = new ProcessBuilder(invokablePath.toString(), "start-dev", "--db", "postgres",
                    "--db-url-host", postgresJDBCUrl, "--db-username", postgresUsername, "--db-password", incorrectPassword,
                    "--hostname-strict=false");
            processBuilderKC.redirectErrorStream(true);
            processKC = processBuilderKC.start();
            assertTrue(verifyLogStringExists(processKC, "password authentication failed"));
            assertFalse(isCode("", 200), "Keycloak has started even with incorrect password. Something needs to be fixed");
        } finally {
            if (processKC != null) {
                processKC.destroy();
            }
        }
    }

    @AfterAll
    public static void tearDown() throws IOException {
        stopPostgresContainer();
        deleteDist("target/keycloakBuild");
    }
}
