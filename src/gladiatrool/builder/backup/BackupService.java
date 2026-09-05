package gladiatrool.builder.backup;

import gladiatrool.builder.client.IconAssetManager;
import gladiatrool.builder.domain.OperationManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BackupService {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public Path create(Path backupRoot, String operationName, Map<String, Path> files, String restoreSql) throws IOException {
        Path dir = backupRoot.resolve(operationName).normalize();
        Files.createDirectories(dir);
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            Path source = entry.getValue();
            Path target = dir.resolve(entry.getKey()).normalize();
            if (!target.startsWith(dir)) throw new IllegalArgumentException("Fichier de sauvegarde hors dossier.");
            if (Files.isRegularFile(source)) { Files.createDirectories(target.getParent()); Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING); hashes.put(entry.getKey(), IconAssetManager.sha256(source)); }
            else { Files.createDirectories(target.getParent()); Files.writeString(target, "", StandardCharsets.UTF_8); hashes.put(entry.getKey(), IconAssetManager.sha256(target)); }
        }
        Files.writeString(dir.resolve("restore.sql"), restoreSql == null ? "-- aucune restauration SQL\n" : restoreSql, StandardCharsets.UTF_8);
        mapper.writeValue(dir.resolve("hashes.json").toFile(), hashes);
        return dir;
    }

    public void validate(Path backupDirectory) throws IOException {
        Path hashesFile = backupDirectory.resolve("hashes.json");
        Map<String, String> hashes = mapper.readValue(hashesFile.toFile(), Map.class);
        for (Map.Entry<String, String> e : hashes.entrySet()) {
            Path file = backupDirectory.resolve(e.getKey()).normalize();
            if (!Files.isRegularFile(file) || !e.getValue().equals(IconAssetManager.sha256(file))) throw new IllegalStateException("Sauvegarde alteree : " + file);
        }
    }

    public void restoreFiles(Path backupDirectory, Path targetRoot, Map<String, Path> mappings) throws IOException {
        validate(backupDirectory);
        for (Map.Entry<String, Path> entry : mappings.entrySet()) {
            Path source = backupDirectory.resolve(entry.getKey()).normalize();
            Path target = entry.getValue().toAbsolutePath().normalize();
            if (!source.startsWith(backupDirectory.toAbsolutePath().normalize())) throw new IllegalArgumentException("Source de restauration invalide.");
            if (!target.startsWith(targetRoot.toAbsolutePath().normalize())) throw new IllegalArgumentException("Cible de restauration hors périmètre.");
            Files.createDirectories(target.getParent()); Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
