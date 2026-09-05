package gladiatrool.builder.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public final class JsonStore {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public Map<String, String> read(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) == 0) return new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
        return new TreeMap<>(mapper.readValue(file.toFile(), new TypeReference<Map<String, String>>() {}));
    }
    public void write(Path file, Map<String, String> values) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temp.toFile(), new TreeMap<>(values));
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException unsupportedAtomicMove) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
    public void validate(Path file) throws IOException { mapper.readTree(file.toFile()); }
}
