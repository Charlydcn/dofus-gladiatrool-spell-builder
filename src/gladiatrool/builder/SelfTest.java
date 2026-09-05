package gladiatrool.builder;

import gladiatrool.builder.client.IconAssetManager;
import gladiatrool.builder.database.MigrationGenerator;
import gladiatrool.builder.domain.DamageLine;
import gladiatrool.builder.domain.ZoneSpec;
import gladiatrool.builder.validation.OperationValidator;
import gladiatrool.builder.operations.OperationManager;
import gladiatrool.builder.backup.BackupService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SelfTest {
    private SelfTest() {}
    public static void run() throws Exception {
        DamageLine fixed = DamageLine.fixed(DamageLine.Element.FIRE, false, 60, 0);
        check(fixed.storedMax() == -1 && "0d0+60".equals(fixed.jet()) && "60".equals(fixed.displayValue()), "jet fixe");
        DamageLine variable = DamageLine.variable(DamageLine.Element.WATER, true, 2, 4, 1);
        check(variable.effectId() == 91 && variable.storedMax() == 4 && "1d3+1".equals(variable.jet()), "jet variable");
        check("Pa".equals(ZoneSpec.single().code()) && "Cb".equals(ZoneSpec.circle(1).code()) && "Ce".equals(ZoneSpec.circle(4).code()) && "Xb".equals(ZoneSpec.cross(1).code()) && "Xe".equals(ZoneSpec.cross(4).code()), "zones natives");
        check(ZoneSpec.cross(2).equals(ZoneSpec.fromCode("Xc")), "conversion zone");
        try { new OperationValidator().validateIds(List.of(), 9999); throw new IllegalStateException("ID hors plage accepte"); } catch (IllegalArgumentException expected) { }
        try { new OperationValidator().validateEffects(List.of(), List.of()); throw new IllegalStateException("effets vides acceptes"); } catch (IllegalStateException expected) { }
        String migration = new MigrationGenerator().createSpell(10025, "Test", 103, "info", 3, 1, 6, 50, 100, false, true, true, 0, 0, 2, false, 1, "Pa", "Pa", List.of(fixed), List.of(DamageLine.fixed(DamageLine.Element.FIRE, false, 80, 1)), "", "");
        check(migration.contains("10025,6,99,60,-1") && migration.contains("'0d0+60'"), "migration fixe");
        String deletion = new MigrationGenerator().deleteSpell(10025, "", List.of(108));
        check(deletion.contains("UPDATE `gladiatrool_spells`") && deletion.contains("REGEXP_REPLACE") && deletion.contains("`fullMorphId`=108"), "nettoyage suppression disposition");
        Path temp = Files.createTempFile("builder-self-test", ".swf"); Files.writeString(temp, "swf"); check(IconAssetManager.sha256(temp).equals(IconAssetManager.sha256(temp)), "hash SHA-256"); Files.deleteIfExists(temp);
        Path operationRoot = Files.createTempDirectory("builder-operations"); OperationManager manager = new OperationManager(operationRoot); OperationManager.PreparedOperation operation = manager.create("self-test", 10025); operation.manifest().steps.put("validation", "OK"); manager.save(operation); check("OK".equals(manager.load(operation.directory().resolve("operation.json")).manifest().steps.get("validation")), "manifest operation");
        Path backupRoot = Files.createTempDirectory("builder-backups"); Path before = Files.createTempFile("builder-before", ".json"); Files.writeString(before, "{}"); Path backup = new BackupService().create(backupRoot, "self-test", java.util.Map.of("custom_spells.json.before", before), "-- restore\n"); new BackupService().validate(backup); check(Files.isRegularFile(backup.resolve("hashes.json")), "backup hash");
        Files.deleteIfExists(before); Files.deleteIfExists(temp);
        System.out.println("Self-test OK : domaines, jets, zones, migration, validation et hash.");
    }
    private static void check(boolean condition, String name) { if (!condition) throw new IllegalStateException("Echec auto-test : " + name); }
}
