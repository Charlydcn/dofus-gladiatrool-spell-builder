package gladiatrool.builder.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gladiatrool.builder.domain.OperationManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class OperationManager {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;
    public OperationManager(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public PreparedOperation create(String type, int spellId) throws IOException {
        String stamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssXXX")).replace(":", "").replace("+", "p");
        Path dir = root.resolve(stamp + "-" + type + "-" + spellId).normalize();
        Files.createDirectories(dir.resolve("staged"));
        OperationManifest manifest = new OperationManifest(); manifest.operationId = dir.getFileName().toString(); manifest.operationType = type; manifest.spellId = spellId; manifest.createdAt = OffsetDateTime.now().toString();
        save(dir, manifest);
        return new PreparedOperation(dir, manifest);
    }

    public void save(PreparedOperation operation) throws IOException { save(operation.directory, operation.manifest); }
    private void save(Path dir, OperationManifest manifest) throws IOException { mapper.writeValue(dir.resolve("operation.json").toFile(), manifest); }

    public List<Path> resumable() throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (java.util.stream.Stream<Path> stream = Files.list(root)) {
            return stream.filter(Files::isDirectory).map(p -> p.resolve("operation.json")).filter(Files::isRegularFile).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        }
    }
    public OperationManifest read(Path manifest) throws IOException { return mapper.readValue(manifest.toFile(), OperationManifest.class); }
    public PreparedOperation load(Path manifest) throws IOException { return new PreparedOperation(manifest.toAbsolutePath().normalize().getParent(), read(manifest)); }

    public static final class PreparedOperation {
        private final Path directory; private final OperationManifest manifest;
        private PreparedOperation(Path directory, OperationManifest manifest) { this.directory=directory; this.manifest=manifest; }
        public Path directory() { return directory; } public Path stagingDirectory() { return directory.resolve("staged"); } public OperationManifest manifest() { return manifest; }
    }
}
