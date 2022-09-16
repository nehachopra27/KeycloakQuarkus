package org.acme;

import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.AugmentResult;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import java.nio.file.Path;

public class KeycloakBuilder {

    static final String CREATE_QUARKUS_APPLICATION = "createQuarkusApplication";

    public static void createQuarkusApplication(Path appRoot, ApplicationModel appModel) throws Exception {

        final WorkspaceModule module = appModel.getAppArtifact().getWorkspaceModule();
        final QuarkusBootstrap bootstrap = QuarkusBootstrap.builder()
                .setBaseClassLoader(Thread.currentThread().getContextClassLoader())
                .setExistingModel(appModel)
                .setApplicationRoot(module.getMainSources().getOutputTree().getRoots().iterator().next())
                .setProjectRoot(appRoot)
                .setTargetDirectory(module.getBuildDir().toPath())
                .build();

        try (CuratedApplication curated = bootstrap.bootstrap()) {
            AugmentAction action = curated.createAugmentor();
            AugmentResult outcome = action.createProductionApplication();
        }
    }
}
