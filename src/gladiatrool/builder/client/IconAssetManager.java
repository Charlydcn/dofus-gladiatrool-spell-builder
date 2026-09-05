package gladiatrool.builder.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

/** Prepare les icones dans un dossier d'operation, sans modifier le template source. */
public final class IconAssetManager {
    public PreparedIcon prepare(Path iconDirectory, Path source, int spellId, Path stagingDirectory, boolean ownedTarget, boolean explicitReplacement) throws IOException {
        if (!Files.isRegularFile(source)) throw new IllegalStateException("Source SWF introuvable : " + source);
        Path target = iconDirectory.resolve(spellId + ".swf");
        if (Files.exists(target) && !(ownedTarget && explicitReplacement)) {
            throw new IllegalStateException("Le fichier SWF cible existe et n'est pas remplacable automatiquement : " + target);
        }
        Files.createDirectories(stagingDirectory);
        Path staged = stagingDirectory.resolve(target.getFileName());
        Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
        return new PreparedIcon(source, target, staged, sha256(source), sha256(staged), Files.exists(target) ? sha256(target) : null);
    }

    public void apply(PreparedIcon icon) throws IOException {
        Files.createDirectories(icon.target.getParent());
        if (Files.exists(icon.target) && icon.beforeHash != null && !icon.beforeHash.equals(sha256(icon.target))) {
            throw new IllegalStateException("Le SWF cible a change depuis la preparation : " + icon.target);
        }
        Files.copy(icon.staged, icon.target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static String sha256(Path file) throws IOException {
        try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)); StringBuilder out = new StringBuilder(); for (byte b : digest) out.append(String.format("%02x", b)); return out.toString(); }
        catch (Exception e) { throw new IOException("SHA-256 indisponible", e); }
    }

    public static final class PreparedIcon {
        private final Path source, target, staged; private final String sourceHash, stagedHash, beforeHash;
        private PreparedIcon(Path source, Path target, Path staged, String sourceHash, String stagedHash, String beforeHash) { this.source=source;this.target=target;this.staged=staged;this.sourceHash=sourceHash;this.stagedHash=stagedHash;this.beforeHash=beforeHash; }
        public Path source() { return source; } public Path target() { return target; } public Path staged() { return staged; }
        public String sourceHash() { return sourceHash; } public String stagedHash() { return stagedHash; } public String beforeHash() { return beforeHash; }
    }
}
