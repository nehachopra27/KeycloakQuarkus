package io.keycloak.builder;

import static io.quarkus.bootstrap.util.PropertyUtils.isWindows;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;

public class Utils {

    public static void deleteDist(String path) throws IOException {
        FileUtils.deleteDirectory(new File(path));
    }

    String SCRIPT_CMD = isWindows() ? "kc.bat" : "kc.sh";
    String SCRIPT_CMD_INVOKABLE = isWindows() ? SCRIPT_CMD : "./" + SCRIPT_CMD;

}
