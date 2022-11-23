package io.keycloak.builder;

import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresTestBase {

    static String postgresPassword = "password";
    static String postgresUsername = "keycloak";
    static String postgresDbName = "keycloak";
    static PostgreSQLContainer POSTGRES_CONTAINER;
    static String postgresJDBCUrl;

    public static void startPostgresContainer() {
        final String POSTGRES_DOCKER_IMAGE_NAME = "postgres:latest";
        POSTGRES_CONTAINER = new PostgreSQLContainer(POSTGRES_DOCKER_IMAGE_NAME);
        POSTGRES_CONTAINER
                .withDatabaseName(postgresDbName)
                .withUsername(postgresUsername)
                .withPassword(postgresPassword)
                .start();
        postgresJDBCUrl = POSTGRES_CONTAINER.getJdbcUrl().replace("jdbc:postgresql://", "");
    }

    public static void stopPostgresContainer() {
        POSTGRES_CONTAINER.stop();
    }

}
