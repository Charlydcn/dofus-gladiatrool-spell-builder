package gladiatrool.builder.operations;

import gladiatrool.builder.domain.OperationManifest;
import gladiatrool.builder.git.GitService;
import gladiatrool.builder.git.WorkflowService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Livraison separee et tracee : commit, push, migration puis publication client. */
public final class OperationDeliveryService {
    private final GitService git;
    private final GitService builderGit;
    private final Path builderRepository;
    private final WorkflowService workflows;
    private final String remote;
    private final String branch;
    private final String migrationWorkflow;
    private final String publishWorkflow;
    private final OperationManager operations;
    private final Consumer<String> progress;
    public OperationDeliveryService(Path repository, Path builderRepository, String remote, String branch, String repositorySlug, String migrationWorkflow, String publishWorkflow, OperationManager operations, Consumer<String> progress) {
        this.git=new GitService(repository); this.builderRepository=builderRepository.toAbsolutePath().normalize(); this.builderGit=new GitService(this.builderRepository); this.workflows=new WorkflowService(repository,repositorySlug,progress); this.remote=remote;this.branch=branch;this.migrationWorkflow=migrationWorkflow;this.publishWorkflow=publishWorkflow;this.operations=operations;this.progress=progress == null ? message -> {} : progress;
    }
    public DeliveryResult deliver(OperationManager.PreparedOperation operation, Path repository) throws IOException {
        OperationManifest m=operation.manifest(); List<Path> files=new ArrayList<>(); for(String relative:m.stagedFiles)files.add(repository.resolve(relative)); for(String relative:m.deletedFiles)files.add(repository.resolve(relative));
        Path registryFile=builderRepository.resolve("created_spells.json").normalize();
        List<Path> rootFiles=new ArrayList<>(); boolean hasRegistry=false;
        for(Path file:files) { if(file.toAbsolutePath().normalize().equals(registryFile)) hasRegistry=true; else rootFiles.add(file); }
        if (!"OK".equals(m.steps.get("commit"))) {
            progress.accept("Étape 1/8 · contrôle et commit du dépôt principal...");
            git.requireBranch(branch); git.requireDiffCheck(); git.stageExact(rootFiles); git.commit(commitMessage(m));
            m.steps.put("commit","OK"); m.status="COMMITTED"; operations.save(operation);
            progress.accept("Étape 1/8 · commit principal terminé.");
        } else progress.accept("Étape 1/8 · commit principal déjà validé.");
        if (!"OK".equals(m.steps.get("push"))) {
            progress.accept("Étape 2/8 · push du dépôt principal...");
            git.push(remote,branch); m.steps.put("push","OK"); m.status="PUSHED"; operations.save(operation);
            progress.accept("Étape 2/8 · push principal terminé.");
        } else progress.accept("Étape 2/8 · push principal déjà validé.");
        if (hasRegistry && !"OK".equals(m.steps.get("registreCommit"))) {
            progress.accept("Étape 3/8 · commit du registre du builder...");
            builderGit.requireBranch(branch); builderGit.requireDiffCheck(); builderGit.stageExact(List.of(registryFile));
            builderGit.commit("chore(gladiatrool): register spell " + m.spellId);
            m.steps.put("registreCommit","OK"); operations.save(operation);
            progress.accept("Étape 3/8 · commit du registre terminé.");
        }
        if (hasRegistry && !"OK".equals(m.steps.get("registrePush"))) {
            progress.accept("Étape 4/8 · push du registre du builder...");
            builderGit.push(remote,branch); m.steps.put("registrePush","OK"); operations.save(operation);
            progress.accept("Étape 4/8 · push du registre terminé.");
        }
        WorkflowService.WorkflowResult migration=null;
        if (!"OK".equals(m.steps.get("migrationServeur"))) {
            progress.accept("Étape 5/8 · déclenchement de la migration serveur et redémarrage...");
            migration=workflows.runAndWait(migrationWorkflow,"operation","migrate","migration",m.migrationFile);
            m.steps.put("migrationServeur",migration.success?"OK":"ECHEC");
            m.steps.put("redemarrageServeur",migration.success?"INCLUS_DANS_WORKFLOW":"NON_CONFIRME");
            m.status=migration.success?"MIGRATED":"MIGRATION_FAILED"; operations.save(operation);
            progress.accept("Étape 5/8 · migration serveur " + (migration.success ? "réussie" : "échouée") + ".");
        } else progress.accept("Étape 5/8 · migration serveur déjà validée.");
        WorkflowService.WorkflowResult publication=null;
        if(m.clientPublication && !"OK".equals(m.steps.get("publicationClient"))){
            String version=LocalDate.now().toString().replace('-','.')+".1";
            progress.accept("Étape 6/8 · déclenchement de la publication client " + version + "...");
            publication=workflows.runAndWait(publishWorkflow,"version",version);
            progress.accept("Étape 7/8 · vérification de manifest.json, client-update.zip et Launcher.exe...");
            boolean assets=publication.success && workflows.releaseContainsAssets("client-"+version,"manifest.json","client-update.zip","Launcher.exe");
            m.steps.put("publicationClient",assets?"OK":publication.success?"ARTEFACTS_INCOMPLETS":"ECHEC");
            if(!assets)m.status="PUBLICATION_FAILED";else m.status="COMPLETED"; operations.save(operation);
            progress.accept("Étape 7/8 · publication client " + (assets ? "réussie" : "incomplète") + ".");
        } else if (!m.clientPublication && "OK".equals(m.steps.get("migrationServeur"))) {
            progress.accept("Étape 6/8 · aucune publication client prévue.");
            m.status="COMPLETED"; operations.save(operation);
        }
        progress.accept("Étape 8/8 · bilan de l'opération terminé.");
        return new DeliveryResult(migration,publication,m.status);
    }
    private String commitMessage(OperationManifest m){String verb="create".equalsIgnoreCase(m.operationType)?"create":"delete".equalsIgnoreCase(m.operationType)?"delete":"update";return ("create".equals(verb)?"feat":"delete".equals(verb)?"chore":"fix")+"(gladiatrool): "+verb+" spell "+m.spellId;}
    public static final class DeliveryResult { public final WorkflowService.WorkflowResult migration,publication; public final String status; DeliveryResult(WorkflowService.WorkflowResult m,WorkflowService.WorkflowResult p,String s){migration=m;publication=p;status=s;} }
}
