package io.keycloak.builder;

import static io.quarkus.bootstrap.util.PropertyUtils.isWindows;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;

public class Utils {

    String SCRIPT_CMD = isWindows() ? "kc.bat" : "kc.sh";
    String SCRIPT_CMD_INVOKABLE = isWindows() ? SCRIPT_CMD : "./" + SCRIPT_CMD;
    static final String EXPECTED_OUTPUT_FROM_SUCCESSFULLY_STARTED = "Installed features";
    static final String EXPECTED_OUTPUT_FROM_FAILURE_STARTED = "For more details";

    public boolean ensureApplicationStartupOrFailure(Process process) throws IOException {
        boolean isApplicationFinished = false;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                //System.out.println(line);
                if (line.contains(EXPECTED_OUTPUT_FROM_SUCCESSFULLY_STARTED)
                        || line.contains(EXPECTED_OUTPUT_FROM_FAILURE_STARTED)) {
                    System.out.println("test");
                    isApplicationFinished = true;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isApplicationFinished;
    }

    public boolean verifyLogStringExists(Process process,String string) throws IOException {
        boolean logExists = false;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                //System.out.println(line);
                if (line.contains(string)) {
                    logExists = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return logExists;
    }

    public static void deleteDist(String path) throws IOException {
        FileUtils.deleteDirectory(new File(path));
    }

}
