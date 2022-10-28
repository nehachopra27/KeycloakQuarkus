package org.acme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;
import util.KeycloakDistribution;

@QuarkusMainTest
public class KeycloakIT {

    @Test
    @Launch({ "start-dev", "--hostname-strict=false" })
    void resetConfig(LaunchResult result) {
        System.out.println(result.getOutput());
        assertTrue(
                result.getOutput()
                        .contains("Updating the configuration and installing your custom providers, if any. Please wait."),
                () -> "The Output:\n" + result.getOutput() + "doesn't contains the expected string.");
        assertTrue(result.getOutput().contains("Quarkus augmentation completed"),
                () -> "The Output:\n" + result.getOutput() + "doesn't contains the expected string.");
        assertTrue(
                result.getOutput().contains(
                        "Server configuration updated and persisted. Run the following command to review the configuration:"),
                () -> "The Output:\n" + result.getOutput() + "doesn't contains the expected string.");
        assertTrue(result.getOutput().contains(KeycloakDistribution.SCRIPT_CMD + " show-config"),
                () -> "The Output:\n" + result.getOutput() + "doesn't contains the expected string.");
    }
}
