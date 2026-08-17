package com.dehui.property.contracts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LombokConstructorInjectionGuardTest {

    private static final Pattern CLASS_NAME = Pattern.compile("(?:public\\s+)?(?:final\\s+)?class\\s+(\\w+)");
    private static final Pattern AUTOWIRED_REQUIRED_ARGS = Pattern.compile(
            "@RequiredArgsConstructor\\s*\\(\\s*onConstructor_\\s*=\\s*@Autowired\\s*\\)"
    );

    @Test
    void requiredArgsComponentsWithCompatibilityConstructorsSelectTheRuntimeConstructor() throws IOException {
        Path modules = Path.of("src", "main", "java", "com", "dehui", "property", "modules");
        List<String> failures = new ArrayList<>();

        try (Stream<Path> files = Files.walk(modules)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (!source.contains("@RequiredArgsConstructor")) {
                    continue;
                }
                Matcher classMatcher = CLASS_NAME.matcher(source);
                if (!classMatcher.find()) {
                    continue;
                }
                String className = classMatcher.group(1);
                boolean hasCompatibilityConstructor = Pattern.compile(
                        "(?m)^\\s*public\\s+" + Pattern.quote(className) + "\\s*\\("
                ).matcher(source).find();
                if (hasCompatibilityConstructor && !AUTOWIRED_REQUIRED_ARGS.matcher(source).find()) {
                    failures.add(modules.relativize(file).toString());
                }
            }
        }

        assertTrue(failures.isEmpty(),
                "@RequiredArgsConstructor components with explicit compatibility constructors must select "
                        + "the generated runtime constructor using @Autowired: " + failures);
    }
}
