package gladiatrool.builder.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GitService {
    private final Path repository;
    public GitService(Path repository) { this.repository = repository.toAbsolutePath().normalize(); }

    public String branch() throws IOException { return run("branch", "--show-current").stdout.trim(); }
    public String status() throws IOException { return run("status", "--porcelain").stdout; }
    public void requireBranch(String expected) throws IOException { if (!expected.equals(branch())) throw new IllegalStateException("Branche active inattendue : " + branch() + " (attendu : " + expected + ")"); }
    public void requireDiffCheck() throws IOException { run("diff", "--check"); }
    public void stageExact(List<Path> files) throws IOException {
        if (files.isEmpty()) throw new IllegalArgumentException("Aucun fichier a versionner.");
        List<String> args = new ArrayList<>(Arrays.asList("add", "--"));
        for (Path file : files) { Path normalized = file.toAbsolutePath().normalize(); if (!normalized.startsWith(repository)) throw new IllegalArgumentException("Fichier hors depot : " + file); String relative=repository.relativize(normalized).toString().replace('\\','/'); String lower=relative.toLowerCase(java.util.Locale.ROOT); if (lower.endsWith("config.properties") || lower.contains("/.env") || lower.contains("/secrets/") || lower.endsWith("builder.properties")) throw new IllegalStateException("Fichier sensible refuse : " + relative); args.add(relative); }
        run(args.toArray(new String[0]));
    }
    public void commit(String message) throws IOException { if (message == null || !message.matches("(feat|fix|chore)\\(gladiatrool\\): .+")) throw new IllegalArgumentException("Message de commit invalide."); run("diff", "--cached", "--check"); run("commit", "-m", message); }
    public void push(String remote, String branch) throws IOException { run("push", remote, branch); }

    private Result run(String... args) throws IOException {
        List<String> command = new ArrayList<>(); command.add("git"); command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command).directory(repository.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try { int code = process.waitFor(); if (code != 0) throw new IOException("Git echoue (" + String.join(" ", args) + ") : " + output.trim()); return new Result(code, output); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Git interrompu", e); }
    }
    private static final class Result { final int code; final String stdout; Result(int code,String stdout){this.code=code;this.stdout=stdout;} }
}
