package gladiatrool.builder.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Configuration locale du builder, sans mot de passe ni token. */
public final class BuilderConfig {
    private final Path builderDirectory;
    private final Path repository;
    private final String remote;
    private final String branch;
    private final Properties properties;

    private BuilderConfig(Path builderDirectory, Path repository, String remote, String branch, Properties properties) {
        this.builderDirectory = builderDirectory; this.repository = repository; this.remote = remote; this.branch = branch; this.properties = properties;
    }

    public static BuilderConfig load(Path builderDirectory) throws IOException {
        Path configFile = builderDirectory.resolve("builder.properties");
        if (!Files.isRegularFile(configFile)) throw new IllegalStateException("Configuration introuvable : " + configFile);
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) { p.load(reader); }
        Path repo = resolve(builderDirectory, value(p, "repository.path", "../.."));
        return new BuilderConfig(builderDirectory.toAbsolutePath().normalize(), repo, value(p, "repository.remote", "origin"), value(p, "repository.branch", "main"), p);
    }

    public Path builderDirectory() { return builderDirectory; }
    public Path repository() { return repository; }
    public String remote() { return remote; }
    public String branch() { return branch; }
    public Path path(String key, String defaultValue) { return resolve(repository, value(properties, key, defaultValue)); }
    public String value(String key, String defaultValue) { return value(properties, key, defaultValue); }
    public String required(String key) { return value(properties, key, null); }
    public int intValue(String key, int defaultValue) { try { return Integer.parseInt(value(properties, key, String.valueOf(defaultValue))); } catch (NumberFormatException e) { throw new IllegalStateException("Valeur invalide : " + key); } }

    private static String value(Properties p, String key, String fallback) {
        String v = p.getProperty(key, fallback);
        if (v == null || v.trim().isEmpty()) throw new IllegalStateException("Clé absente dans builder.properties : " + key);
        return v.trim();
    }
    private static Path resolve(Path base, String configured) { Path p = Path.of(configured); return (p.isAbsolute() ? p : base.resolve(p)).normalize(); }
}
