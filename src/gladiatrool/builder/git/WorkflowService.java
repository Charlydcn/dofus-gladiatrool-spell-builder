package gladiatrool.builder.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Declenche et attend GitHub Actions via gh, sans afficher de secret. */
public final class WorkflowService {
    private final Path repository;
    private final String repositorySlug;
    private final ObjectMapper mapper = new ObjectMapper();
    public WorkflowService(Path repository, String repositorySlug) { this.repository=repository; this.repositorySlug=repositorySlug; }

    public WorkflowResult runAndWait(String workflow, String... fields) throws IOException {
        List<String> dispatch = new ArrayList<>(Arrays.asList("gh", "workflow", "run", workflow, "--repo", repositorySlug));
        for (int i=0;i+1<fields.length;i+=2) { dispatch.add("-f"); dispatch.add(fields[i] + "=" + fields[i+1]); }
        execute(dispatch);
        String runId = null; String status = "queued"; String conclusion = null; String url = null;
        for (int attempts=0; attempts<180; attempts++) {
            String json = execute(List.of("gh", "run", "list", "--workflow", workflow, "--repo", repositorySlug, "--limit", "1", "--json", "databaseId,status,conclusion,url")).output;
            JsonNode rows = mapper.readTree(json);
            if (rows.isArray() && rows.size() > 0) { JsonNode row=rows.get(0); runId=row.path("databaseId").asText(); status=row.path("status").asText(); conclusion=row.path("conclusion").isNull()?null:row.path("conclusion").asText(); url=row.path("url").asText(); if ("completed".equalsIgnoreCase(status)) return new WorkflowResult(runId, status, conclusion, url, "success".equalsIgnoreCase(conclusion)); }
            sleep(1000);
        }
        return new WorkflowResult(runId, status, conclusion, url, false);
    }
    public boolean releaseContainsAssets(String tag, String... required) throws IOException {
        String json=execute(List.of("gh","release","view",tag,"--repo",repositorySlug,"--json","assets")).output;
        JsonNode root=mapper.readTree(json); java.util.Set<String> names=new java.util.HashSet<>(); for(JsonNode asset:root.path("assets")) names.add(asset.path("name").asText()); for(String name:required) if(!names.contains(name)) return false; return true;
    }
    private Result execute(List<String> command) throws IOException { Process p=new ProcessBuilder(command).directory(repository.toFile()).redirectErrorStream(true).start(); String out=new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8); try { int code=p.waitFor(); if(code!=0) throw new IOException("Workflow echoue : " + out.trim()); return new Result(out); } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Workflow interrompu",e);} }
    private static void sleep(long millis) throws IOException { try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Attente workflow interrompue",e); } }
    public static final class WorkflowResult { public final String runId,status,conclusion,url; public final boolean success; WorkflowResult(String id,String s,String c,String u,boolean ok){runId=id;status=s;conclusion=c;url=u;success=ok;} }
    private static final class Result { final String output; Result(String output){this.output=output;} }
}
