# Custom Keycloak Builder

## Overview

This project helps to build a keycloak application with only required dependancies.
Currently, it supports to create a distribution with the database dependency that you specify and the default h2, removing all other unnecessary db dependencies from the distribution.

## Getting started

### Download a released version 
You can download the releases from [here](https://github.com/nehachopra27/KeycloakQuarkus/releases)

### Building Keycloak builder jar From Source

Ensure you have JDK 11 (or newer) and  Maven 3.5.4 (or newer) installed

   
To build Keycloak builder jar  run:

    mvn clean install
    
Once completed you will find 'keycloak-builder-1.0.0-SNAPSHOT-builder.jar' in `target`.



## How to Use Keycloak Builder Jar to build Customised Keycloak Distribution

Pass the required db with parameter --db to keycloak-builder.jar and run the jar as follow:


**NOTE**

Here is the list of keycloak Quarkus [supported database](https://www.keycloak.org/server/db#_supported_databases)

  
For postgres db you can specify

     java -jar keycloak-builder.jar --db postgres 
 


This will build a keycloak distribution in the current/target directory based on your jar's location. You can also specify --app-root parameter to specify the location where you want your dist to be build.

    java -jar keycloak-builder.jar --db postgres --app-root target/dist

**NOTE**

To start keycloak from the distribution created, you can use the official keycloak guide to [start a server](https://www.keycloak.org/server/configuration#_starting_keycloak) and also [configure](https://www.keycloak.org/server/db#_configuring_a_database) the database you buit the distribution with.


## How to run tests

To run the existing tests, run the following command:

    mvn clean verify


