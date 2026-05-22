package com.dehui.property.contracts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AdminWriteEndpointCoverageTest {

    private static final Pattern FRONTEND_WRITE_CALL = Pattern.compile(
            "request\\.(post|put|patch|delete)\\s*\\(\\s*([`'\"])(.*?)\\2",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CLASS_REQUEST_MAPPING = Pattern.compile("@RequestMapping\\(\\s*\"([^\"]*)\"");
    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(PostMapping|PutMapping|PatchMapping|DeleteMapping)\\(([^)]*)\\)"
    );
    private static final Pattern MAPPING_PATH = Pattern.compile("\"([^\"]*)\"");

    @Test
    void everyAdminWriteEndpointHasBackendMapping() throws IOException {
        Set<String> frontendRoutes = collectFrontendWriteRoutes();
        Set<String> backendRoutes = collectBackendWriteRoutes();

        Set<String> missing = new TreeSet<>(frontendRoutes);
        missing.removeAll(backendRoutes);

        assertTrue(missing.isEmpty(), "Missing backend write mappings: " + missing);
    }

    private Set<String> collectFrontendWriteRoutes() throws IOException {
        Path adminSrc = Path.of("..", "dehui-property-admin", "src").normalize();
        Set<String> routes = new TreeSet<>();
        try (Stream<Path> files = Files.walk(adminSrc)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String filename = file.getFileName().toString();
                if (!filename.endsWith(".vue") && !filename.endsWith(".js")) {
                    continue;
                }
                Matcher matcher = FRONTEND_WRITE_CALL.matcher(Files.readString(file));
                while (matcher.find()) {
                    routes.add(matcher.group(1).toUpperCase() + " " + normalizeFrontendPath(matcher.group(3)));
                }
            }
        }
        return routes;
    }

    private Set<String> collectBackendWriteRoutes() throws IOException {
        Path modulesSrc = Path.of("src", "main", "java", "com", "dehui", "property", "modules").normalize();
        Set<String> routes = new TreeSet<>();
        try (Stream<Path> files = Files.walk(modulesSrc)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("Controller.java")).toList()) {
                String source = Files.readString(file);
                String prefix = "";
                Matcher classMatcher = CLASS_REQUEST_MAPPING.matcher(source);
                if (classMatcher.find()) {
                    prefix = classMatcher.group(1);
                }
                Matcher methodMatcher = METHOD_MAPPING.matcher(source);
                while (methodMatcher.find()) {
                    Matcher pathMatcher = MAPPING_PATH.matcher(methodMatcher.group(2));
                    while (pathMatcher.find()) {
                        routes.add(methodFromAnnotation(methodMatcher.group(1)) + " " + normalizeBackendPath(prefix + "/" + pathMatcher.group(1)));
                    }
                }
            }
        }
        return routes;
    }

    private String methodFromAnnotation(String annotation) {
        return switch (annotation) {
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "PatchMapping" -> "PATCH";
            case "DeleteMapping" -> "DELETE";
            default -> throw new IllegalArgumentException(annotation);
        };
    }

    private String normalizeFrontendPath(String rawPath) {
        return normalizePath(rawPath
                .replaceAll("\\$\\{[^}]+}", "{id}")
                .replaceAll("\\?.*$", ""));
    }

    private String normalizeBackendPath(String rawPath) {
        return normalizePath(rawPath.replaceAll("\\{[^}]+}", "{id}"));
    }

    private String normalizePath(String rawPath) {
        String normalized = rawPath.replace('\\', '/')
                .replaceAll("/+", "/")
                .replaceAll("/$", "");
        return normalized.isBlank() ? "/" : normalized;
    }
}
