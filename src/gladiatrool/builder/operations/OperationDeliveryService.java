package gladiatrool.builder.operations;

import gladiatrool.builder.domain.OperationManifest;
import gladiatrool.builder.git.GitService;
import gladiatrool.builder.git.WorkflowService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
    public OperationDeliveryService(Path repository, Path builderRepository, String remote, String branch, String repositorySlug, String migrationWorkflow, String publishWorkflow, OperationManager operations) {
        this.git=new GitService(repository); this.builderRepository=builderRepository.toAbsolutePath().normalize(); this.builderGit=new GitService(this.builderRepository); this.workflows=new WorkflowService(repository,repositorySlug); this.remote=remote;this.branch=branch;this.migrationWorkflow=migrationWorkflow;this.publishWorkflow=publishWorkflow;this.operations=operations;
    }
    public DeliveryResult deliver(OperationManager.PreparedOperation operation, Path repository) throws IOException {
        OperationManifest m=operation.manifest(); List<Path> files=new ArrayList<>(); for(String relative:m.stagedFiles)files.add(repository.resolve(relative)); for(String relative:m.deletedFiles)files.add(repository.resolve(relative));
        Path registryFile=builderRepository.resolve("created_spells.json").normalize();
        List<Path> rootFiles=new ArrayList<>(); boolean hasRegistry=false;
        for(Path file:files) { if(file.toAbsolutePath().normalize().equals(registryFile)) hasRegistry=true; else rootFiles.add(file); }
        if (!"OK".equals(m.steps.get("commit"))) { git.requireBranch(branch); git.requireDiffCheck(); git.stageExact(rootFiles); git.commit(commitMessage(m)); m.steps.put("commit","OK"); m.status="COMMITTED"; operations.save(operation); }
        if (!"OK".equals(m.steps.get("push"))) { git.push(remote,branch); m.steps.put("push","OK"); m.status="PUSHED"; operations.save(operation); }
        if (hasRegistry && !"OK".equals(m.steps.get("registreCommit"))) { builderGit.requireBranch(branch); builderGit.requireDiffCheck(); builderGit.stageExact(List.of(registryFile)); builderGit.commit("chore(gladiatrool): register spell " + m.spellId); m.steps.put("registreCommit","OK"); operations.save(operation); }
        if (hasRegistry && !"OK".equals(m.steps.get("registrePush"))) { builderGit.push(remote,branch); m.steps.put("registrePush","OK"); operations.save(operation); }
        WorkflowService.WorkflowResult migration=null; if (!"OK".equals(m.steps.get("migrationServeur"))) { migration=workflows.runAndWait(migrationWorkflow,"operation","migrate","migration",m.migrationFile); m.steps.put("migrationServeur",migration.success?"OK":"ECHEC"); m.steps.put("redemarrageServeur",migration.success?"INCLUS_DANS_WORKFLOW":"NON_CONFIRME"); m.status=migration.success?"MIGRATED":"MIGRATION_FAILED"; operations.save(operation); }
        WorkflowService.WorkflowResult publication=null; if(m.clientPublication && !"OK".equals(m.steps.get("publicationClient"))){String version=LocalDate.now().toString().replace('-','.')+".1"; publication=workflows.runAndWait(publishWorkflow,"version",version);boolean assets=publication.success && workflows.releaseContainsAssets("client-"+version,"manifest.json","client-update.zip","Launcher.exe");m.steps.put("publicationClient",assets?"OK":publication.success?"ARTEFACTS_INCOMPLETS":"ECHEC");if(!assets)m.status="PUBLICATION_FAILED";else m.status="COMPLETED"; operations.save(operation); } else if (!m.clientPublication && "OK".equals(m.steps.get("migrationServeur"))) { m.status="COMPLETED"; operations.save(operation); } return new DeliveryResult(migration,publication,m.status);
    }
    private String commitMessage(OperationManifest m){String verb="create".equalsIgnoreCase(m.operationType)?"create":"delete".equalsIgnoreCase(m.operationType)?"delete":"update";return ("create".equals(verb)?"feat":"delete".equals(verb)?"chore":"fix")+"(gladiatrool): "+verb+" spell "+m.spellId;}
    public static final class DeliveryResult { public final WorkflowService.WorkflowResult migration,publication; public final String status; DeliveryResult(WorkflowService.WorkflowResult m,WorkflowService.WorkflowResult p,String s){migration=m;publication=p;status=s;} }
}
