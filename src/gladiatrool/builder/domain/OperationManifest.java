package gladiatrool.builder.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Manifest persistant, volontairement depourvu de secrets. */
public final class OperationManifest {
    public String operationId;
    public String operationType;
    public int spellId;
    public String repository;
    public String branch;
    public String database;
    public String serverEnvironment;
    public String migrationFile;
    public boolean clientPublication;
    public String backupDirectory;
    public String status = "PREPARED";
    public String createdAt;
    public final List<String> stagedFiles = new ArrayList<>();
    public final List<String> swfFiles = new ArrayList<>();
    public final List<String> deletedFiles = new ArrayList<>();
    public final Map<String, String> hashes = new LinkedHashMap<>();
    public final Map<String, String> steps = new LinkedHashMap<>();
}
