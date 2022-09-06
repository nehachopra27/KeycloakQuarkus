#!/bin/sh
#java -jar ./target/dist/lib/quarkus-run.jar start --hostname-strict=false --cache local

#java -jar ./target/dist/lib/quarkus-run.jar build --db postgres --hostname-strict=false -https-port=8180

java -jar ./target/dist/lib/quarkus-run.jar build --cache local
