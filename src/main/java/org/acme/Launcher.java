package org.acme;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.runtime.annotations.QuarkusMain;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.util.artifact.JavaScopes;

@QuarkusMain
public class Launcher {

    public static void main(String[] args) throws Exception {
        final MavenArtifactResolver resolver = MavenArtifactResolver.builder().setWorkspaceDiscovery(false).build();
        final DependencyNode root = resolver
                .getSystem().resolveDependencies(resolver.getSession(), new DependencyRequest().setCollectRequest(
                        new CollectRequest()
                                .setRootArtifact(
                                        new DefaultArtifact(Constants.ORG_KEYCLOAK, "keycloak-build-launcher", "jar", "1.0"))
                                .addDependency(new Dependency(new DefaultArtifact(Constants.IO_QUARKUS,
                                        "quarkus-bootstrap-core", "jar", "999-SNAPSHOT"), JavaScopes.RUNTIME))
                                .addDependency(new Dependency(new DefaultArtifact(Constants.IO_QUARKUS,
                                        "quarkus-bootstrap-maven-resolver", "jar", "999-SNAPSHOT"), JavaScopes.RUNTIME))))
                .getRoot();
        final List<URL> launcherUrls = new ArrayList<>();
        collectUrls(root, launcherUrls);
        launcherUrls.add(Launcher.class.getProtectionDomain().getCodeSource().getLocation());

        final ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader launcherCl = new URLClassLoader(launcherUrls.toArray(new URL[0]), originalCl.getParent())) {
            Thread.currentThread().setContextClassLoader(launcherCl);
            Method keycloakBuilder = launcherCl.loadClass("org.acme.Keycloak").getMethod("main", Map.class);
            keycloakBuilder.invoke(null, parseCommandLineArgs(args));
        } finally {
            Thread.currentThread().setContextClassLoader(originalCl);
        }
    }

    private static void collectUrls(DependencyNode node, List<URL> urls) {
        if (node.getArtifact() != null && node.getArtifact().getFile() != null) {
            try {
                urls.add(node.getArtifact().getFile().toURI().toURL());
            } catch (MalformedURLException e) {
                throw new UncheckedIOException(e);
            }
        }
        for (DependencyNode c : node.getChildren()) {
            collectUrls(c, urls);
        }
    }

    private static Map<String, String> parseCommandLineArgs(String[] args) {
        final Map<String, String> parsed = new HashMap<>();
        int i = 0;
        while (i < args.length) {
            var arg = args[i++];
            var option = Option.forCommandLineName(arg);
            if (option == null) {
                continue;
            }
            String value = null;
            if (i < args.length) {
                value = args[i++];
            }
            parsed.put(arg, value);
        }
        System.out.println(parsed);
        return parsed;
    }
}
