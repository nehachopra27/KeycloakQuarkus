package io.keycloak.builder;

import java.util.HashMap;
import java.util.Map;

public enum Option {

    APPROOT("--app-root"),
    CACHE("--cache"),
    DB("--db"),
    HOSTNAME_STRICT("--hostname-strict"),
    VERSION("--version");

    private static final Map<String, Option> options;
    static {
        options = new HashMap<>(values().length);
        for (Option o : values()) {
            options.put(o.getCommandLineName(), o);
        }
    }

    public static Option forCommandLineName(String commandLineName) {
        return options.get(commandLineName);
    }

    private final String commandLineName;

    Option(String name) {
        this.commandLineName = name;
    }

    public String getCommandLineName() {
        return commandLineName;
    }
}
