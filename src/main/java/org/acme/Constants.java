package org.acme;

public interface Constants {

    String KEYCLOAK_VERSION = "19.0.3"; // 999-SNAPSHOT to test the current main branch
    String ORG_KEYCLOAK = "org.keycloak";
    String KEYCLOAK_CLIENT_TOOLS = "keycloak-client-tools";
    String KEYCLOAK_QUARKUS_SERVER = "keycloak-quarkus-server";
    String KEYCLOAK_QUARKUS_SERVER_APP = "keycloak-quarkus-server-app";

    String KEYCLOAK_CLIENT_CLI_DIST = "keycloak-client-cli-dist";

    String KEYCLOAK_QUARKUS_DIST = "keycloak-quarkus-dist";
    String IO_QUARKUS = "io.quarkus";
    String QUARKUS_BOOTSTRAP_VERSION = "2.13.3.Final"; // this should be removed once Keycloak moves to 2.13
    String QUARKUS_JDBC_PREFIX = "quarkus-jdbc-";
    String DATABASE = "dev-file";

    String OPTION_CACHE = "--cache";
    String OPTION_DB = "--db";
    String OPTION_HOST_NAME_STRICT = "--hostname-strict";

    String KEYCLOAK_DIST_CONFIG = "keycloak-dist-quarkus.properties";
}
