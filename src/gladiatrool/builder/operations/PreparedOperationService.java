package gladiatrool.builder.operations;

import gladiatrool.builder.backup.BackupService;
import gladiatrool.builder.domain.OperationManifest;
import gladiatrool.builder.client.IconAssetManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Construit une operation dans staged/ puis l'applique au depot apres confirmation. */
public final class PreparedOperationService {
    private final OperationManager operations;
    private final BackupService backups;
    public PreparedOperationService(OperationManager operations) { this.operations=operations; this.backups=new BackupService(); }

    public OperationManager.PreparedOperation prepare(String type, int spellId, Path repository, Path backupRoot,
                                                       String database, String environment, String migrationFileName,
                                                       String migration, Map<Path, byte[]> files, Map<String, Path> backupFiles, String restoreSql,
                                                       boolean publishClient, List<Path> deletions) throws IOException {
        OperationManager.PreparedOperation operation=operations.create(type,spellId); OperationManifest manifest=operation.manifest();
        manifest.repository=repository.toString(); manifest.branch="main"; manifest.database=database; manifest.serverEnvironment=environment; manifest.migrationFile=migrationFileName; manifest.clientPublication=publishClient;
        Path definitiveMigration=repository.resolve("serveur/game/sql").resolve(migrationFileName).normalize(); if(Files.exists(definitiveMigration)) throw new IllegalStateException("Migration deja existante : " + definitiveMigration);
        Path migrationPath=operation.stagingDirectory().resolve("serveur/game/sql").resolve(migrationFileName); Files.createDirectories(migrationPath.getParent()); Files.writeString(migrationPath,migration,StandardCharsets.UTF_8);
        manifest.stagedFiles.add(repository.relativize(repository.resolve("serveur/game/sql").resolve(migrationFileName)).toString());
        manifest.hashes.put("serveur/game/sql/" + migrationFileName, IconAssetManager.sha256(migrationPath));
        for(Map.Entry<Path,byte[]> entry:files.entrySet()){Path target=entry.getKey().toAbsolutePath().normalize();Path relative=repository.relativize(target);Path staged=operation.stagingDirectory().resolve(relative);Files.createDirectories(staged.getParent());Files.write(staged,entry.getValue());manifest.stagedFiles.add(relative.toString());manifest.hashes.put(relative.toString().replace('\\','/'),IconAssetManager.sha256(staged));if(relative.toString().toLowerCase().endsWith(".swf"))manifest.swfFiles.add(relative.toString());}
        for(Path deletion:deletions){Path relative=repository.relativize(deletion.toAbsolutePath().normalize());manifest.deletedFiles.add(relative.toString());}
        Path backup=backups.create(backupRoot, operation.directory().getFileName().toString(), backupFiles, restoreSql); manifest.backupDirectory=backup.toString(); manifest.steps.put("preparation","OK"); manifest.steps.put("validation","A_FAIRE"); manifest.status="READY_FOR_CONFIRMATION"; operations.save(operation); Files.copy(operation.directory().resolve("operation.json"), backup.resolve("operation.json"), StandardCopyOption.REPLACE_EXISTING); return operation;
    }

    public void apply(OperationManager.PreparedOperation operation, Path repository) throws IOException {
        for(Path staged:listFiles(operation.stagingDirectory())){Path relative=operation.stagingDirectory().relativize(staged); if(relative.toString().replace('\\','/').equals("operation.json"))continue; Path target=repository.resolve(relative).normalize(); if(!target.startsWith(repository.toAbsolutePath().normalize()))throw new IllegalStateException("Cible hors depot : "+target); Files.createDirectories(target.getParent()); Files.copy(staged,target,StandardCopyOption.REPLACE_EXISTING);}
        for(String deleted:operation.manifest().deletedFiles){Path target=repository.resolve(deleted).normalize();if(!target.startsWith(repository.toAbsolutePath().normalize()))throw new IllegalStateException("Suppression hors depot : "+target);Files.deleteIfExists(target);}
        operation.manifest().steps.put("sourceLocale","OK"); operation.manifest().status="APPLIED_LOCALLY"; operations.save(operation);
    }
    private List<Path> listFiles(Path root)throws IOException{try(java.util.stream.Stream<Path> s=Files.walk(root)){List<Path> files=new ArrayList<>();s.filter(Files::isRegularFile).forEach(files::add);return files;}}
}
