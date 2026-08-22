package gladiatrool.builder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.fusesource.jansi.internal.Kernel32;
import org.fusesource.jansi.internal.WindowsSupport;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public final class SpellBuilderApp {
    private static final int CUSTOM_ID_MIN = 10_000;
    private static final int CUSTOM_ID_MAX = 10_999;
    private static final int CUSTOM_GRADE = 6;
    private static final int[] GLADIATROOL_MORPHS = {101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112};
    private static final String[] GLADIATROOL_NAMES = {
            "Féca", "Osamodas", "Enutrof", "Sram", "Xélor", "Écaflip",
            "Eniripsa", "Iop", "Crâ", "Sadida", "Sacrieur", "Pandawa"
    };

    private final Terminal ui = new Terminal();
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private Path builderDirectory;
    private Path gameConfig;
    private Path clientDataFile;
    private Path clientPatchesFile;
    private Path clientIconDirectory;
    private Path backupRoot;
    private Path registryFile;
    private int iconTemplateSpellId;
    private int animationTemplateSpellId;

    public static void main(String[] args) {
        try {
            SpellBuilderApp app = new SpellBuilderApp();
            if (args.length > 0 && "--integration-test".equals(args[0])) app.runIntegrationTest();
            else app.run();
        } catch (UserCancelledException ignored) {
            System.out.println("\nOpération annulée. Aucune modification n'a été effectuée.");
        } catch (Exception e) {
            System.err.println("\nERREUR : " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void runIntegrationTest() throws Exception {
        loadBuilderPaths();
        clientDataFile = builderDirectory.resolve("build/integration-test/custom_spells.json");
        clientPatchesFile = builderDirectory.resolve("build/integration-test/spell_patches.json");
        backupRoot = builderDirectory.resolve("build/integration-test/backups");
        registryFile = builderDirectory.resolve("build/integration-test/created_spells.json");
        Files.deleteIfExists(clientDataFile);
        Files.deleteIfExists(clientPatchesFile);
        Files.deleteIfExists(registryFile);
        DbSettings db = DbSettings.from(loadProperties(gameConfig));

        try (Connection c = DriverManager.getConnection(db.url(), db.user, db.password)) {
            verifySchema(c);
            AnimationTemplate animation = loadAnimationTemplate(c);
            List<String> testTables = List.of("spells", "spells_grade", "spells_effect", "full_morphs", "gladiatrool_spells");
            Map<String, String> createStatements = new LinkedHashMap<>();
            for (String table : testTables) {
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
                    if (!rs.next()) throw new IllegalStateException("DDL introuvable : " + table);
                    createStatements.put(table, rs.getString(2));
                }
            }
            for (String table : testTables) {
                String ddl = createStatements.get(table).replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE");
                try (Statement st = c.createStatement()) { st.execute(ddl); }
            }
            for (int templateId : new LinkedHashSet<>(List.of(iconTemplateSpellId, animationTemplateSpellId))) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO `spells` (`id`,`name`,`sprite`,`spriteinfo`,`type`,`duration`) VALUES (?,?,?,?,?,?)")) {
                    ps.setInt(1, templateId); ps.setString(2, "Sort modèle " + templateId); ps.setInt(3, animation.sprite);
                    ps.setString(4, animation.spriteInfo); ps.setInt(5, -1); ps.setInt(6, 0); ps.executeUpdate();
                }
            }
            for (int i = 0; i < GLADIATROOL_MORPHS.length; i++) {
                int morphId = GLADIATROOL_MORPHS[i];
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO `full_morphs` (`id`,`name`,`gfxId`,`spells`,`args`) VALUES (?,?,?,?,?)")) {
                    ps.setInt(1, morphId); ps.setString(2, GLADIATROOL_NAMES[i]); ps.setInt(3, (i + 1) * 10);
                    ps.setString(4, "176;6;1,103;6;2"); ps.setString(5, "0"); ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO `gladiatrool_spells` (`id`,`playerId`,`fullMorphId`,`spells`) VALUES (?,?,?,?)")) {
                ps.setInt(1, 1); ps.setInt(2, 1); ps.setInt(3, 109); ps.setString(4, "176;6;1,103;6;2"); ps.executeUpdate();
            }

            SpellDraft d = new SpellDraft();
            d.id = 10_000; d.name = "Test intégration"; d.description = "Test"; d.global = true;
            d.iconTemplateSpellId = iconTemplateSpellId;
            d.animationTemplateSpellId = animationTemplateSpellId;
            for (int morph : GLADIATROOL_MORPHS) d.morphIds.add(morph);
            d.paCost = 3; d.poMin = 1; d.poMax = 6; d.ratioCc = 40; d.ratioEc = 100;
            d.poModifiable = true; d.needLos = true; d.targetMask = 1;
            DamageLine normalFire = new DamageLine(); normalFire.element = Element.FIRE; normalFire.min = 5; normalFire.max = 10;
            DamageLine normalWaterSteal = new DamageLine(); normalWaterSteal.element = Element.WATER; normalWaterSteal.lifeSteal = true; normalWaterSteal.min = 2; normalWaterSteal.max = 4;
            DamageLine criticalAir = new DamageLine(); criticalAir.element = Element.AIR; criticalAir.min = 12; criticalAir.max = 18;
            d.normalEffects.add(normalFire); d.normalEffects.add(normalWaterSteal); d.criticalEffects.add(criticalAir);

            CreationSnapshot snapshot = captureSnapshot(c, d);
            createSpell(c, d, snapshot);
            updateClientData(d);
            updateRegistry(d, snapshot);

            assertCount(c, "SELECT COUNT(*) FROM `spells` WHERE `id`=10000", 1);
            assertCount(c, "SELECT COUNT(*) FROM `spells_grade` WHERE `spellID`=10000 AND `gradeID`=6", 1);
            assertCount(c, "SELECT COUNT(*) FROM `spells_effect` WHERE `spellID`=10000", 3);
            assertCount(c, "SELECT COUNT(*) FROM `full_morphs` WHERE `spells` LIKE '%10000;6;_%'", 12);
            assertCount(c, "SELECT COUNT(*) FROM `gladiatrool_spells` WHERE `spells` LIKE '%10000;6;_%'", 1);
            Map<String, String> records = json.readValue(clientDataFile.toFile(), new TypeReference<Map<String, String>>() {});
            if (!records.containsKey("10000")) throw new IllegalStateException("Donnée client 10000 absente.");
            if (!records.get("10000").startsWith("Test%20int%u00E9gration|")) {
                throw new IllegalStateException("Encodage Unicode du nom client incorrect.");
            }

            GradeSettings originalGrade = loadGradeSettings(c, 10_000);
            GradeSettings modifiedGrade = originalGrade.copy();
            modifiedGrade.poMin = 2; modifiedGrade.poMax = 8; modifiedGrade.paCost = 4;
            updateGradeRow(c, modifiedGrade);
            updateClientPatch(modifiedGrade);
            assertCount(c, "SELECT COUNT(*) FROM `spells_grade` WHERE `spellID`=10000 AND `gradeID`=6 AND `paCost`=4 AND `poMin`=2 AND `poMax`=8", 1);
            Map<String, String> patches = json.readValue(clientPatchesFile.toFile(), new TypeReference<Map<String, String>>() {});
            if (!patches.containsKey("10000")) throw new IllegalStateException("Patch client 10000 absent.");

            CreatedSpellRecord created = discoverCreatedSpells(c).get("10000");
            if (created == null || !created.global) throw new IllegalStateException("Registre de suppression incomplet.");
            SpellDraft deletionDraft = new SpellDraft();
            deletionDraft.id = created.id;
            deletionDraft.morphIds.addAll(created.morphIds);
            CreationSnapshot deletionSnapshot = captureSnapshot(c, deletionDraft);
            Path deletionBackup = writeDeletionBackup(c, deletionSnapshot, created);
            deleteSpell(c, created);
            removeClientRecord(created.id);
            removeClientPatch(created.id);
            Map<String, CreatedSpellRecord> registry = loadRegistry();
            registry.remove("10000");
            writeRegistry(registry);
            assertCount(c, "SELECT COUNT(*) FROM `spells` WHERE `id`=10000", 0);
            assertCount(c, "SELECT COUNT(*) FROM `spells_grade` WHERE `spellID`=10000", 0);
            assertCount(c, "SELECT COUNT(*) FROM `spells_effect` WHERE `spellID`=10000", 0);
            assertCount(c, "SELECT COUNT(*) FROM `full_morphs` WHERE `spells` LIKE '%10000;6;_%'", 0);
            Map<String, String> afterDelete = json.readValue(clientDataFile.toFile(), new TypeReference<Map<String, String>>() {});
            if (afterDelete.containsKey("10000")) throw new IllegalStateException("Donnée client 10000 non supprimée.");
            Map<String, String> patchesAfterDelete = json.readValue(clientPatchesFile.toFile(), new TypeReference<Map<String, String>>() {});
            if (patchesAfterDelete.containsKey("10000")) throw new IllegalStateException("Patch client 10000 non supprimé.");

            executeRestoreSql(c, deletionBackup.resolve("restore.sql"));
            restoreClientBackup(deletionSnapshot);
            restoreClientPatchesBackup(deletionSnapshot);
            restoreRegistryBackup(deletionSnapshot);
            assertCount(c, "SELECT COUNT(*) FROM `spells` WHERE `id`=10000", 1);
            CreatedSpellRecord restored = discoverCreatedSpells(c).get("10000");
            if (restored == null) throw new IllegalStateException("Restauration de suppression incomplète.");
            deleteSpell(c, restored);
            removeClientRecord(restored.id);
            removeClientPatch(restored.id);
            Map<String, CreatedSpellRecord> finalRegistry = loadRegistry();
            finalRegistry.remove("10000");
            writeRegistry(finalRegistry);
        }
        System.out.println("Integration test OK (tables temporaires uniquement).");
    }

    private void assertCount(Connection c, String sql, int expected) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next() || rs.getInt(1) != expected) throw new IllegalStateException("Assertion échouée : " + sql + " attendu=" + expected);
        }
    }

    private void run() throws Exception {
        loadBuilderPaths();
        backupRoot = builderDirectory.resolve("backups");
        registryFile = builderDirectory.resolve("created_spells.json");

        ui.title("Générateur de sorts Gladiatrool");
        ui.info("Grade 6 uniquement · IDs 10000–10999 · dégâts directs et vol de vie");
        int mode = ui.select("Action", List.of(
                "Créer un sort",
                "Modifier un sort Gladiatrool existant",
                "Supprimer un sort personnalisé"
        ));

        Properties config = loadProperties(gameConfig);
        DbSettings db = DbSettings.from(config);
        ui.info("Base détectée : " + db.database + " sur " + db.host + ":" + db.port);
        if (!ui.confirm("Utiliser cette connexion ?", true)) {
            throw new UserCancelledException();
        }

        try (Connection connection = DriverManager.getConnection(db.url(), db.user, db.password)) {
            verifySchema(connection);
            ui.success("Connexion et schéma validés.");

            if (mode == 1) {
                updateSpellFlow(connection);
                return;
            }
            if (mode == 2) {
                deleteSpellFlow(connection);
                return;
            }

            SpellDraft draft = askDraft(connection);
            int spellId = findNextCustomId(connection);
            draft.id = spellId;

            reviewDraft(connection, draft);

            CreationSnapshot snapshot = captureSnapshot(connection, draft);
            Path backupDir = writeBackup(snapshot, draft);
            try {
                createSpell(connection, draft, snapshot);
                updateClientData(draft);
                updateRegistry(draft, snapshot);
            } catch (Exception failure) {
                try {
                    rollback(connection, snapshot, draft.id);
                    restoreClientBackup(snapshot);
                    restoreRegistryBackup(snapshot);
                } catch (Exception rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }

            ui.title("Sort créé");
            ui.success("ID : " + draft.id + " · " + draft.name);
            ui.info("Sauvegarde : " + backupDir);
            ui.info("Données client : " + clientDataFile);
            System.out.println();
            System.out.println("Étapes restantes :");
            System.out.println("  1. Vérifier que l'override AS2 Custom Spells est publié dans core.swf.");
            System.out.println("  2. Redémarrer le serveur Game.");
            System.out.println("  3. Redémarrer complètement le client.");
            System.out.println("  4. Entrer dans le Gladiatrool et tester le sort.");
        }
    }

    private SpellDraft askDraft(Connection connection) throws SQLException {
        SpellDraft d = new SpellDraft();
        askIdentity(d);
        askTemplates(connection, d);
        askScope(d);
        askParameters(d);
        askAllEffects(d);
        askPlacement(connection, d);
        return d;
    }

    private void askIdentity(SpellDraft d) {
        d.name = ui.askText("Nom du sort", 1, 80);
        d.description = ui.askText("Description du sort (Entrée pour aucune description)", 0, 500);
    }

    private void askTemplates(Connection connection, SpellDraft d) throws SQLException {
        int iconMode = ui.select("Source de l'icône", List.of(
                "Icône d'un sort existant (par ID de sort)",
                "Icône par ID dans clips/spells/icons/up"
        ));
        if (iconMode == 0) {
            d.iconTemplateSpellId = askTemplateSpellId(connection,
                    "ID du sort modèle pour l'icône", iconTemplateSpellId, "Icône");
            d.directIconId = null;
        } else {
            d.iconTemplateSpellId = iconTemplateSpellId;
            d.directIconId = askDirectIconId();
        }
        d.animationTemplateSpellId = askTemplateSpellId(connection,
                "ID du sort modèle pour l'animation", animationTemplateSpellId, "Animation");
    }

    private int askDirectIconId() {
        while (true) {
            int iconId = ui.askInt("ID de l'icône (fichier <ID>.swf)", 1, 1_000_000, 673);
            Path iconFile = clientIconDirectory.resolve(iconId + ".swf");
            if (!Files.isRegularFile(iconFile)) {
                ui.info("Icône introuvable : " + iconFile + ". Saisissez un ID valide.");
                continue;
            }
            ui.success("Icône trouvée : " + iconFile.getFileName());
            if (ui.confirm("Utiliser l'icône ID " + iconId + " ?", true)) return iconId;
        }
    }

    private int askTemplateSpellId(Connection connection, String question, int defaultSpellId, String label) throws SQLException {
        while (true) {
            int spellId = ui.askInt(question, 1, 1_000_000, defaultSpellId);
            String spellName = null;
            try (PreparedStatement ps = connection.prepareStatement("SELECT `name` FROM `spells` WHERE `id`=? LIMIT 1")) {
                ps.setInt(1, spellId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) spellName = rs.getString("name");
                }
            }
            if (spellName == null || spellName.isBlank()) {
                ui.info("ID incorrect : aucun sort trouvé dans spells pour l'ID " + spellId + ". Saisissez un ID valide.");
                continue;
            }
            ui.success(label + " trouvée : " + label.toLowerCase(Locale.ROOT) + " du sort " + spellName + " (ID " + spellId + ")");
            if (ui.confirm("Utiliser " + label.toLowerCase(Locale.ROOT) + " du sort " + spellName + " ?", true)) return spellId;
        }
    }

    private void askScope(SpellDraft d) {
        int scope = ui.select("Portée Gladiatrool", List.of(
                "Une classe particulière",
                "Global : les 12 classes Gladiatrool"
        ));
        d.global = scope == 1;
        d.morphIds.clear();
        if (d.global) {
            for (int morph : GLADIATROOL_MORPHS) d.morphIds.add(morph);
        } else {
            int classIndex = ui.select("Classe Gladiatrool", Arrays.asList(GLADIATROOL_NAMES));
            d.morphIds.add(GLADIATROOL_MORPHS[classIndex]);
        }
    }

    private void askParameters(SpellDraft d) {
        d.paCost = ui.askInt("Coût en PA", 0, 20, 3);
        d.poMin = ui.askInt("Portée minimale", 0, 63, 1);
        d.poMax = ui.askInt("Portée maximale", d.poMin, 63, Math.max(d.poMin, 6));
        d.poModifiable = ui.confirm("Portée modifiable ?", true);
        d.lineOnly = ui.confirm("Lancer uniquement en ligne ?", false);
        d.needLos = ui.confirm("Ligne de vue nécessaire ?", true);
        d.ratioCc = ui.askInt("Taux de critique : saisir X pour 1/X, 0 pour aucun CC", 0, 1000, 50);
        d.ratioEc = ui.askInt("Taux d'échec : saisir X pour 1/X, 0 pour aucun EC", 0, 1000, 100);
        d.ecEndsTurn = d.ratioEc > 0 && ui.confirm("L'échec critique termine-t-il le tour ?", false);
        d.cooldown = ui.askInt("Délai de relance en tours, 0 pour aucun", 0, 100, 0);
        d.maxPerTurn = ui.askInt("Maximum par tour, 0 pour illimité", 0, 100, 0);
        d.maxPerTarget = ui.askInt("Maximum par cible, 0 pour illimité", 0, 100, 0);

        int target = ui.select("Cibles affectées", List.of(
                "Tout le monde",
                "Ennemis uniquement",
                "Lanceur uniquement"
        ));
        d.targetMask = target == 0 ? 0 : target == 1 ? 1 : 32;
    }

    private void askAllEffects(SpellDraft d) {
        if (!d.normalEffects.isEmpty() || !d.criticalEffects.isEmpty()) {
            ui.title("Effets actuels");
            printEffectLines("normale", d.normalEffects);
            printEffectLines("critique", d.criticalEffects);
            System.out.println("Ces lignes vont maintenant être reconstruites.");
        }
        d.normalEffects.clear();
        d.criticalEffects.clear();
        ui.title("Effets normaux");
        askEffects(d.normalEffects, false);
        if (d.ratioCc > 0) {
            ui.title("Effets critiques");
            askEffects(d.criticalEffects, true);
        }
    }

    private void printEffectLines(String type, List<DamageLine> effects) {
        if (effects.isEmpty()) {
            System.out.println("Aucune ligne " + type + ".");
            return;
        }
        for (int i = 0; i < effects.size(); i++) {
            DamageLine effect = effects.get(i);
            System.out.println("Ligne " + type + " " + (i + 1) + " : " + effect.min + " à " + effect.max
                    + " (" + effect.element.label + ", " + (effect.lifeSteal ? "vol de vie" : "dégâts directs") + ")");
        }
    }

    private void askPlacement(Connection connection, SpellDraft d) throws SQLException {
        d.replace = false;
        d.replacePosition = 0;
        d.replacements.clear();
        if (d.global) {
            int placement = ui.select("Affectation aux 12 morphs", List.of(
                    "Ajouter au livre de sorts sans raccourci",
                    "Remplacer le sort occupant la même position dans chaque classe"
            ));
            d.replace = placement == 1;
            if (d.replace) {
                d.replacePosition = ui.askInt("Position commune à remplacer (1 à 31)", 1, 31, 30);
                Map<Integer, List<MorphSpell>> byMorph = loadMorphSpells(connection, d.morphIds);
                System.out.println("Sorts qui seront déliés :");
                for (int i = 0; i < GLADIATROOL_MORPHS.length; i++) {
                    int morphId = GLADIATROOL_MORPHS[i];
                    MorphSpell current = findByPosition(byMorph.get(morphId), d.replacePosition);
                    System.out.println("  " + GLADIATROOL_NAMES[i] + " : " + (current == null ? "emplacement vide" : current.name + " (ID " + current.id + ")"));
                }
                if (!ui.confirm("Confirmer ces remplacements ?", false)) throw new UserCancelledException();
            }
        } else {
            int placement = ui.select("Affectation au morph", List.of(
                    "Ajouter au livre de sorts sans raccourci",
                    "Remplacer un sort existant"
            ));
            d.replace = placement == 1;
            if (d.replace) {
                int morphId = d.morphIds.get(0);
                List<MorphSpell> spells = loadMorphSpells(connection, d.morphIds).get(morphId);
                List<MorphSpell> positioned = spells.stream()
                        .filter(s -> s.position >= 1 && s.position <= 31)
                        .sorted(Comparator.comparingInt(s -> s.position))
                        .collect(Collectors.toList());
                int selected = ui.select("Sort à remplacer", positioned.stream()
                        .map(s -> "Position " + s.position + " · " + s.name + " (ID " + s.id + ")")
                        .collect(Collectors.toList()));
                d.replacements.put(morphId, positioned.get(selected));
            }
        }
    }

    private void reviewDraft(Connection connection, SpellDraft d) throws SQLException {
        while (true) {
            ui.title("Récapitulatif");
            printSummary(d);
            int action = ui.select("Que voulez-vous faire ?", List.of(
                    "Créer définitivement ce sort",
                    "Modifier le nom ou la description",
                    "Modifier l'icône ou l'animation",
                    "Modifier la classe / portée Gladiatrool",
                    "Modifier les paramètres du sort",
                    "Modifier les effets normaux et critiques",
                    "Modifier l'affectation au livre de sorts",
                    "Annuler"
            ));
            switch (action) {
                case 0: return;
                case 1: askIdentity(d); break;
                case 2: askTemplates(connection, d); break;
                case 3:
                    askScope(d);
                    askPlacement(connection, d);
                    break;
                case 4:
                    askParameters(d);
                    if (d.ratioCc == 0) d.criticalEffects.clear();
                    else if (d.criticalEffects.isEmpty()) {
                        ui.title("Effets critiques");
                        askEffects(d.criticalEffects, true);
                    }
                    break;
                case 5: askAllEffects(d); break;
                case 6: askPlacement(connection, d); break;
                default: throw new UserCancelledException();
            }
        }
    }

    private void askEffects(List<DamageLine> target, boolean critical) {
        do {
            DamageLine line = new DamageLine();
            int element = ui.select("Élément de la ligne", List.of("Neutre", "Terre", "Feu", "Eau", "Air"));
            line.element = Element.values()[element];
            line.lifeSteal = ui.select("Type de la ligne", List.of("Dégâts directs", "Vol de vie")) == 1;
            line.min = ui.askInt("Dégâts minimum" + (critical ? " critiques" : ""), 0, 100_000, 1);
            line.max = ui.askInt("Dégâts maximum" + (critical ? " critiques" : ""), line.min, 100_000, Math.max(line.min, 5));
            target.add(line);
        } while (ui.confirm("Ajouter une autre ligne " + (critical ? "critique" : "normale") + " ?", false));
    }

    private void createSpell(Connection c, SpellDraft d, CreationSnapshot snapshot) throws SQLException {
        AnimationTemplate animation = loadAnimationTemplate(c, d.animationTemplateSpellId);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO `spells` (`id`,`name`,`sprite`,`spriteinfo`,`type`,`duration`) VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, d.id);
            ps.setString(2, d.name);
            ps.setInt(3, animation.sprite);
            ps.setString(4, animation.spriteInfo);
            ps.setInt(5, -1);
            ps.setInt(6, 0);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO `spells_grade` (`spellID`,`gradeID`,`paCost`,`poMin`,`poMax`,`ratioCC`,`ratioEC`,`isLine`,`needLOS`,`needEmptyC`,`isPoModif`,`maxByTurn`,`maxByTarget`,`CD`,`lvlLearn`,`endTurn`,`statesForbidden`,`stateNeed`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int i = 1;
            ps.setInt(i++, d.id); ps.setInt(i++, CUSTOM_GRADE); ps.setInt(i++, d.paCost);
            ps.setInt(i++, d.poMin); ps.setInt(i++, d.poMax); ps.setInt(i++, d.ratioCc);
            ps.setInt(i++, d.ratioEc); ps.setBoolean(i++, d.lineOnly); ps.setBoolean(i++, d.needLos);
            ps.setBoolean(i++, false); ps.setBoolean(i++, d.poModifiable); ps.setInt(i++, d.maxPerTurn);
            ps.setInt(i++, d.maxPerTarget); ps.setInt(i++, d.cooldown); ps.setInt(i++, 1);
            ps.setBoolean(i++, d.ecEndsTurn); ps.setString(i++, "0"); ps.setInt(i, -1);
            ps.executeUpdate();
        }

        insertEffects(c, d, d.normalEffects, false);
        insertEffects(c, d, d.criticalEffects, true);
        updateMorphLinks(c, d, snapshot);
    }

    private void insertEffects(Connection c, SpellDraft d, List<DamageLine> effects, boolean critical) throws SQLException {
        if (effects.isEmpty()) return;
        String sql = "INSERT INTO `spells_effect` (`spellID`,`gradeID`,`effectID`,`min`,`max`,`args`,`area`,`chance`,`turn`,`isCCeffect`,`jet`,`effectTarget`,`trigger`,`onHitTrigger`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (DamageLine effect : effects) {
                ps.setInt(1, d.id);
                ps.setInt(2, CUSTOM_GRADE);
                ps.setInt(3, effect.effectId());
                ps.setInt(4, effect.min);
                ps.setInt(5, effect.max);
                ps.setInt(6, -1);
                ps.setString(7, "Pa");
                ps.setInt(8, 0);
                ps.setInt(9, 0);
                ps.setBoolean(10, critical);
                ps.setString(11, diceJet(effect.min, effect.max));
                ps.setInt(12, d.targetMask);
                ps.setInt(13, -1);
                ps.setInt(14, -1);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void updateMorphLinks(Connection c, SpellDraft d, CreationSnapshot snapshot) throws SQLException {
        Map<Integer, String> names = loadSpellNames(c);
        for (int morphId : d.morphIds) {
            List<MorphSpell> spells = parseMorphSpells(snapshot.fullMorphValues.get(morphId), names);
            MorphSpell replacement = d.replacements.get(morphId);
            if (d.replace && replacement == null && d.global) {
                replacement = findByPosition(spells, d.replacePosition);
            }
            String newValue = applyLink(spells, d.id, replacement);
            try (PreparedStatement ps = c.prepareStatement("UPDATE `full_morphs` SET `spells`=? WHERE `id`=?")) {
                ps.setString(1, newValue);
                ps.setInt(2, morphId);
                ps.executeUpdate();
            }

            List<SavedLayout> saved = snapshot.savedLayouts.stream().filter(s -> s.fullMorphId == morphId).collect(Collectors.toList());
            for (SavedLayout layout : saved) {
                List<MorphSpell> personalized = parseMorphSpells(layout.spells, names);
                final MorphSpell replacementForLayout = replacement;
                MorphSpell personalizedReplacement = replacementForLayout == null ? null : personalized.stream()
                        .filter(s -> s.id == replacementForLayout.id || (s.position == replacementForLayout.position && replacementForLayout.position > 0))
                        .findFirst().orElse(null);
                String updated = applyLink(personalized, d.id, personalizedReplacement);
                try (PreparedStatement ps = c.prepareStatement("UPDATE `gladiatrool_spells` SET `spells`=? WHERE `id`=?")) {
                    ps.setString(1, updated);
                    ps.setInt(2, layout.id);
                    ps.executeUpdate();
                }
            }
        }
    }

    private String applyLink(List<MorphSpell> spells, int newSpellId, MorphSpell replacement) {
        if (replacement == null) {
            spells.add(new MorphSpell(newSpellId, CUSTOM_GRADE, -1, "_", "Nouveau sort"));
        } else {
            int index = spells.indexOf(replacement);
            spells.set(index, new MorphSpell(newSpellId, CUSTOM_GRADE, replacement.position, replacement.rawPosition, "Nouveau sort"));
        }
        return spells.stream().map(MorphSpell::serialize).collect(Collectors.joining(","));
    }

    private CreationSnapshot captureSnapshot(Connection c, SpellDraft draft) throws Exception {
        CreationSnapshot snapshot = new CreationSnapshot();
        for (int morphId : draft.morphIds) {
            try (PreparedStatement ps = c.prepareStatement("SELECT `spells` FROM `full_morphs` WHERE `id`=?")) {
                ps.setInt(1, morphId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("Morph " + morphId + " introuvable.");
                    snapshot.fullMorphValues.put(morphId, rs.getString(1));
                }
            }
        }
        String placeholders = draft.morphIds.stream().map(x -> "?").collect(Collectors.joining(","));
        try (PreparedStatement ps = c.prepareStatement("SELECT `id`,`playerId`,`fullMorphId`,`spells` FROM `gladiatrool_spells` WHERE `fullMorphId` IN (" + placeholders + ")")) {
            for (int i = 0; i < draft.morphIds.size(); i++) ps.setInt(i + 1, draft.morphIds.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) snapshot.savedLayouts.add(new SavedLayout(rs.getInt("id"), rs.getInt("playerId"), rs.getInt("fullMorphId"), rs.getString("spells")));
            }
        }
        snapshot.clientFileExisted = Files.exists(clientDataFile);
        if (snapshot.clientFileExisted) snapshot.clientFileBytes = Files.readAllBytes(clientDataFile);
        snapshot.clientPatchesFileExisted = clientPatchesFile != null && Files.exists(clientPatchesFile);
        if (snapshot.clientPatchesFileExisted) snapshot.clientPatchesFileBytes = Files.readAllBytes(clientPatchesFile);
        snapshot.registryFileExisted = registryFile != null && Files.exists(registryFile);
        if (snapshot.registryFileExisted) snapshot.registryFileBytes = Files.readAllBytes(registryFile);
        return snapshot;
    }

    private Path writeBackup(CreationSnapshot snapshot, SpellDraft draft) throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = backupRoot.resolve(stamp + "-spell-" + draft.id);
        Files.createDirectories(dir);
        StringBuilder sql = new StringBuilder();
        sql.append("-- Restauration avant création du sort ").append(draft.id).append("\n");
        sql.append("DELETE FROM `spells_effect` WHERE `spellID`=").append(draft.id).append(";\n");
        sql.append("DELETE FROM `spells_grade` WHERE `spellID`=").append(draft.id).append(";\n");
        sql.append("DELETE FROM `spells` WHERE `id`=").append(draft.id).append(";\n");
        for (Map.Entry<Integer, String> entry : snapshot.fullMorphValues.entrySet()) {
            sql.append("UPDATE `full_morphs` SET `spells`='").append(sqlEscape(entry.getValue())).append("' WHERE `id`=").append(entry.getKey()).append(";\n");
        }
        for (SavedLayout layout : snapshot.savedLayouts) {
            sql.append("UPDATE `gladiatrool_spells` SET `spells`='").append(sqlEscape(layout.spells)).append("' WHERE `id`=").append(layout.id).append(";\n");
        }
        Files.writeString(dir.resolve("restore.sql"), sql.toString(), StandardCharsets.UTF_8);
        if (snapshot.clientFileExisted) Files.write(dir.resolve("custom_spells.json.before"), snapshot.clientFileBytes);
        else Files.writeString(dir.resolve("custom_spells.json.before"), "{}\n", StandardCharsets.UTF_8);
        if (snapshot.registryFileExisted) Files.write(dir.resolve("created_spells.json.before"), snapshot.registryFileBytes);
        else Files.writeString(dir.resolve("created_spells.json.before"), "{}\n", StandardCharsets.UTF_8);
        return dir;
    }

    private Path writeDeletionBackup(Connection connection, CreationSnapshot snapshot, CreatedSpellRecord record) throws Exception {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = backupRoot.resolve(stamp + "-delete-spell-" + record.id);
        Files.createDirectories(dir);
        StringBuilder sql = new StringBuilder();
        sql.append("-- Restauration avant suppression du sort ").append(record.id).append("\n");
        sql.append("DELETE FROM `spells_effect` WHERE `spellID`=").append(record.id).append(";\n");
        sql.append("DELETE FROM `spells_grade` WHERE `spellID`=").append(record.id).append(";\n");
        sql.append("DELETE FROM `spells` WHERE `id`=").append(record.id).append(";\n");
        appendInsertRows(connection, sql, "spells", "id", record.id);
        appendInsertRows(connection, sql, "spells_grade", "spellID", record.id);
        appendInsertRows(connection, sql, "spells_effect", "spellID", record.id);
        for (Map.Entry<Integer, String> entry : snapshot.fullMorphValues.entrySet()) {
            sql.append("UPDATE `full_morphs` SET `spells`='").append(sqlEscape(entry.getValue())).append("' WHERE `id`=").append(entry.getKey()).append(";\n");
        }
        for (SavedLayout layout : snapshot.savedLayouts) {
            sql.append("UPDATE `gladiatrool_spells` SET `spells`='").append(sqlEscape(layout.spells)).append("' WHERE `id`=").append(layout.id).append(";\n");
        }
        Files.writeString(dir.resolve("restore.sql"), sql.toString(), StandardCharsets.UTF_8);
        if (snapshot.clientFileExisted) Files.write(dir.resolve("custom_spells.json.before"), snapshot.clientFileBytes);
        else Files.writeString(dir.resolve("custom_spells.json.before"), "{}\n", StandardCharsets.UTF_8);
        if (snapshot.registryFileExisted) Files.write(dir.resolve("created_spells.json.before"), snapshot.registryFileBytes);
        else Files.writeString(dir.resolve("created_spells.json.before"), "{}\n", StandardCharsets.UTF_8);
        if (snapshot.clientPatchesFileExisted) Files.write(dir.resolve("spell_patches.json.before"), snapshot.clientPatchesFileBytes);
        else Files.writeString(dir.resolve("spell_patches.json.before"), "{}\n", StandardCharsets.UTF_8);
        return dir;
    }

    private void appendInsertRows(Connection connection, StringBuilder sql, String table, String idColumn, int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM `" + table + "` WHERE `" + idColumn + "`=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    sql.append("INSERT INTO `").append(table).append("` (");
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        if (i > 1) sql.append(',');
                        sql.append('`').append(md.getColumnLabel(i)).append('`');
                    }
                    sql.append(") VALUES (");
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        if (i > 1) sql.append(',');
                        sql.append(sqlValue(rs.getObject(i)));
                    }
                    sql.append(");\n");
                }
            }
        }
    }

    private String sqlValue(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return (Boolean) value ? "1" : "0";
        if (value instanceof byte[]) {
            StringBuilder hex = new StringBuilder("X'");
            for (byte b : (byte[]) value) hex.append(String.format("%02X", b));
            return hex.append('\'').toString();
        }
        return "'" + sqlEscape(value.toString()) + "'";
    }

    private void executeRestoreSql(Connection connection, Path restoreFile) throws IOException, SQLException {
        for (String line : Files.readAllLines(restoreFile, StandardCharsets.UTF_8)) {
            String statement = line.trim();
            if (statement.isEmpty() || statement.startsWith("--")) continue;
            if (statement.endsWith(";")) statement = statement.substring(0, statement.length() - 1);
            try (Statement st = connection.createStatement()) { st.executeUpdate(statement); }
        }
    }

    private void rollback(Connection c, CreationSnapshot snapshot, int spellId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM `spells_effect` WHERE `spellID`=?")) { ps.setInt(1, spellId); ps.executeUpdate(); }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM `spells_grade` WHERE `spellID`=?")) { ps.setInt(1, spellId); ps.executeUpdate(); }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM `spells` WHERE `id`=?")) { ps.setInt(1, spellId); ps.executeUpdate(); }
        for (Map.Entry<Integer, String> e : snapshot.fullMorphValues.entrySet()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE `full_morphs` SET `spells`=? WHERE `id`=?")) { ps.setString(1, e.getValue()); ps.setInt(2, e.getKey()); ps.executeUpdate(); }
        }
        for (SavedLayout s : snapshot.savedLayouts) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE `gladiatrool_spells` SET `spells`=? WHERE `id`=?")) { ps.setString(1, s.spells); ps.setInt(2, s.id); ps.executeUpdate(); }
        }
    }

    private void restoreClientBackup(CreationSnapshot snapshot) throws IOException {
        if (snapshot.clientFileExisted) Files.write(clientDataFile, snapshot.clientFileBytes);
        else Files.deleteIfExists(clientDataFile);
    }

    private void restoreRegistryBackup(CreationSnapshot snapshot) throws IOException {
        if (registryFile == null) return;
        if (snapshot.registryFileExisted) Files.write(registryFile, snapshot.registryFileBytes);
        else Files.deleteIfExists(registryFile);
    }

    private void restoreClientPatchesBackup(CreationSnapshot snapshot) throws IOException {
        if (clientPatchesFile == null) return;
        if (snapshot.clientPatchesFileExisted) Files.write(clientPatchesFile, snapshot.clientPatchesFileBytes);
        else Files.deleteIfExists(clientPatchesFile);
    }

    private void updateClientData(SpellDraft d) throws IOException {
        Files.createDirectories(clientDataFile.getParent());
        Map<String, String> records = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
        if (Files.exists(clientDataFile) && Files.size(clientDataFile) > 0) {
            records.putAll(json.readValue(clientDataFile.toFile(), new TypeReference<Map<String, String>>() {}));
        }
        records.put(String.valueOf(d.id), ClientRecord.encode(d));
        Path temp = clientDataFile.resolveSibling(clientDataFile.getFileName() + ".tmp");
        json.writeValue(temp.toFile(), records);
        try {
            Files.move(temp, clientDataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicNotSupported) {
            Files.move(temp, clientDataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void updateRegistry(SpellDraft draft, CreationSnapshot snapshot) throws IOException {
        Map<String, CreatedSpellRecord> registry = loadRegistry();
        CreatedSpellRecord record = new CreatedSpellRecord();
        record.id = draft.id;
        record.name = draft.name;
        record.global = draft.global;
        record.morphIds.addAll(draft.morphIds);
        record.replacementMetadataAvailable = true;

        for (int morphId : draft.morphIds) {
            MorphSpell expected = draft.replacements.get(morphId);
            if (expected == null && draft.global && draft.replace) {
                expected = findRawByPosition(snapshot.fullMorphValues.get(morphId), draft.replacePosition);
            }
            if (expected != null) record.morphReplacements.put(morphId, expected.serialize());

            final MorphSpell expectedForLayout = expected;
            if (expectedForLayout != null) {
                snapshot.savedLayouts.stream().filter(s -> s.fullMorphId == morphId).forEach(layout -> {
                    MorphSpell previous = findRawMatch(layout.spells, expectedForLayout.id, expectedForLayout.position);
                    if (previous != null) record.layoutReplacements.put(layout.id, previous.serialize());
                });
            }
        }
        registry.put(String.valueOf(draft.id), record);
        writeRegistry(registry);
    }

    private Map<String, CreatedSpellRecord> loadRegistry() throws IOException {
        if (registryFile == null || !Files.exists(registryFile) || Files.size(registryFile) == 0) return new TreeMap<>();
        return json.readValue(registryFile.toFile(), new TypeReference<Map<String, CreatedSpellRecord>>() {});
    }

    private void writeRegistry(Map<String, CreatedSpellRecord> registry) throws IOException {
        Files.createDirectories(registryFile.getParent());
        Path temp = registryFile.resolveSibling(registryFile.getFileName() + ".tmp");
        json.writeValue(temp.toFile(), new TreeMap<>(registry));
        try {
            Files.move(temp, registryFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicNotSupported) {
            Files.move(temp, registryFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void updateSpellFlow(Connection connection) throws Exception {
        int scope = ui.select("Type de sort à modifier", List.of(
                "Sort d'une classe",
                "Sort global aux 12 classes"
        ));
        Integer selectedMorph = null;
        if (scope == 0) {
            int classIndex = ui.select("Classe Gladiatrool", Arrays.asList(GLADIATROOL_NAMES));
            selectedMorph = GLADIATROOL_MORPHS[classIndex];
        }

        List<EditableSpell> allSpells = loadEditableSpells(connection);
        final Integer morphFilter = selectedMorph;
        List<EditableSpell> candidates = allSpells.stream()
                .filter(s -> scope == 1 ? s.global : s.morphIds.contains(morphFilter))
                .sorted(Comparator.comparing((EditableSpell s) -> s.name).thenComparingInt(s -> s.id))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            ui.info("Aucun sort de grade 6 ne correspond à ce filtre.");
            return;
        }

        int selected = ui.select("Sort à modifier", candidates.stream()
                .map(s -> s.name + " (ID " + s.id + ")")
                .collect(Collectors.toList()));
        EditableSpell spell = candidates.get(selected);
        GradeSettings original = loadGradeSettings(connection, spell.id);
        loadExistingVisualPatch(original);
        GradeSettings edited = original.copy();

        ui.info("Classes utilisant cet ID : " + spell.morphIds.stream().map(this::morphName).collect(Collectors.joining(", ")));
        ui.info("La modification s'appliquera à cet ID partout où il est utilisé. Les effets ne seront pas modifiés.");

        while (true) {
            ui.title("Paramètres actuels · " + spell.name + " (ID " + spell.id + ")");
            printGradeSettings(edited);
            int property = ui.select("Propriété à modifier", List.of(
                    "Coût en PA",
                    "Portée minimale et maximale",
                    "Portée modifiable",
                    "Lancer uniquement en ligne",
                    "Ligne de vue nécessaire",
                    "Taux de critique",
                    "Taux d'échec critique",
                    "Délai de relance",
                    "Maximum par tour",
                    "Maximum par cible",
                    "Icône",
                    "Animation de lancement",
                    "Terminer et enregistrer",
                    "Annuler"
            ));
            switch (property) {
                case 0: edited.paCost = ui.askInt("Coût en PA actuel : " + edited.paCost, 0, 20, edited.paCost); break;
                case 1:
                    edited.poMin = ui.askInt("Portée minimale actuelle : " + edited.poMin, 0, 63, edited.poMin);
                    edited.poMax = ui.askInt("Portée maximale actuelle : " + edited.poMax, edited.poMin, 63, Math.max(edited.poMin, edited.poMax));
                    break;
                case 2: edited.poModifiable = ui.confirm("Portée modifiable ? Valeur actuelle : " + yesNo(edited.poModifiable), edited.poModifiable); break;
                case 3: edited.lineOnly = ui.confirm("Lancer uniquement en ligne ? Valeur actuelle : " + yesNo(edited.lineOnly), edited.lineOnly); break;
                case 4: edited.needLos = ui.confirm("Ligne de vue nécessaire ? Valeur actuelle : " + yesNo(edited.needLos), edited.needLos); break;
                case 5: edited.ratioCc = ui.askInt("Taux de critique actuel : " + ratio(edited.ratioCc) + " · saisir X pour 1/X", 0, 1000, edited.ratioCc); break;
                case 6:
                    edited.ratioEc = ui.askInt("Taux d'échec actuel : " + ratio(edited.ratioEc) + " · saisir X pour 1/X", 0, 1000, edited.ratioEc);
                    edited.ecEndsTurn = edited.ratioEc > 0 && ui.confirm("L'échec termine-t-il le tour ? Valeur actuelle : " + yesNo(edited.ecEndsTurn), edited.ecEndsTurn);
                    break;
                case 7: edited.cooldown = ui.askInt("Délai actuel : " + edited.cooldown, 0, 100, edited.cooldown); break;
                case 8: edited.maxPerTurn = ui.askInt("Maximum par tour actuel : " + edited.maxPerTurn, 0, 100, edited.maxPerTurn); break;
                case 9: edited.maxPerTarget = ui.askInt("Maximum par cible actuel : " + edited.maxPerTarget, 0, 100, edited.maxPerTarget); break;
                case 10:
                    int iconMode = ui.select("Source de l'icône", List.of(
                            "Icône d'un sort existant (par ID de sort)",
                            "Icône par ID dans clips/spells/icons/up"
                    ));
                    if (iconMode == 0) {
                        edited.iconTemplateSpellId = askTemplateSpellId(connection,
                                "ID du sort modèle pour l'icône", iconTemplateSpellId, "Icône");
                        edited.directIconId = null;
                    } else {
                        edited.iconTemplateSpellId = iconTemplateSpellId;
                        edited.directIconId = askDirectIconId();
                    }
                    break;
                case 11:
                    int animationSpellId = askTemplateSpellId(connection,
                            "ID du sort modèle pour l'animation", animationTemplateSpellId, "Animation");
                    AnimationTemplate animation = loadAnimationTemplate(connection, animationSpellId);
                    edited.sprite = animation.sprite;
                    edited.spriteInfo = animation.spriteInfo;
                    edited.animationTemplateSpellId = animationSpellId;
                    break;
                case 12:
                    if (!ui.confirm("Enregistrer ces modifications ?", false)) break;
                    applyGradeUpdate(connection, spell, original, edited);
                    return;
                default: throw new UserCancelledException();
            }
        }
    }

    private List<EditableSpell> loadEditableSpells(Connection connection) throws SQLException {
        List<Integer> morphIds = Arrays.stream(GLADIATROOL_MORPHS).boxed().collect(Collectors.toList());
        Map<Integer, List<MorphSpell>> byMorph = loadMorphSpells(connection, morphIds);
        Map<Integer, EditableSpell> spells = new LinkedHashMap<>();
        for (int morphId : GLADIATROOL_MORPHS) {
            for (MorphSpell linked : byMorph.getOrDefault(morphId, List.of())) {
                EditableSpell spell = spells.computeIfAbsent(linked.id, id -> new EditableSpell(id, linked.name));
                if (!spell.morphIds.contains(morphId)) spell.morphIds.add(morphId);
            }
        }
        Set<Integer> gradeSix = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT `spellID` FROM `spells_grade` WHERE `gradeID`=?")) {
            ps.setInt(1, CUSTOM_GRADE);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) gradeSix.add(rs.getInt(1)); }
        }
        List<Integer> allMorphs = Arrays.stream(GLADIATROOL_MORPHS).boxed().collect(Collectors.toList());
        return spells.values().stream().filter(s -> gradeSix.contains(s.id)).peek(s -> s.global = s.morphIds.containsAll(allMorphs)).collect(Collectors.toList());
    }

    private GradeSettings loadGradeSettings(Connection connection, int spellId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT g.`paCost`,g.`poMin`,g.`poMax`,g.`ratioCC`,g.`ratioEC`,g.`isLine`,g.`needLOS`,g.`isPoModif`,g.`maxByTurn`,g.`maxByTarget`,g.`CD`,g.`endTurn`,s.`sprite`,s.`spriteinfo` FROM `spells_grade` g JOIN `spells` s ON s.`id`=g.`spellID` WHERE g.`spellID`=? AND g.`gradeID`=?")) {
            ps.setInt(1, spellId); ps.setInt(2, CUSTOM_GRADE);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Grade 6 introuvable pour le sort " + spellId + ".");
                GradeSettings g = new GradeSettings();
                g.spellId = spellId; g.paCost = rs.getInt("paCost"); g.poMin = rs.getInt("poMin"); g.poMax = rs.getInt("poMax");
                g.ratioCc = rs.getInt("ratioCC"); g.ratioEc = rs.getInt("ratioEC"); g.lineOnly = rs.getBoolean("isLine");
                g.needLos = rs.getBoolean("needLOS"); g.poModifiable = rs.getBoolean("isPoModif"); g.maxPerTurn = rs.getInt("maxByTurn");
                g.maxPerTarget = rs.getInt("maxByTarget"); g.cooldown = rs.getInt("CD"); g.ecEndsTurn = rs.getBoolean("endTurn");
                g.sprite = rs.getInt("sprite"); g.spriteInfo = rs.getString("spriteinfo");
                return g;
            }
        }
    }

    private void printGradeSettings(GradeSettings g) {
        System.out.println("PA / PO          : " + g.paCost + " PA · " + g.poMin + "–" + g.poMax + " PO");
        System.out.println("CC / EC          : " + ratio(g.ratioCc) + " / " + ratio(g.ratioEc));
        System.out.println("Contraintes      : ligne=" + yesNo(g.lineOnly) + ", LDV=" + yesNo(g.needLos) + ", PO modifiable=" + yesNo(g.poModifiable));
        System.out.println("Limites          : tour=" + g.maxPerTurn + ", cible=" + g.maxPerTarget + ", relance=" + g.cooldown);
        System.out.println("EC termine tour  : " + yesNo(g.ecEndsTurn));
        String icon = g.directIconId != null ? "fichier ID " + g.directIconId
                : g.iconTemplateSpellId != null ? "sort modèle ID " + g.iconTemplateSpellId : "inchangée";
        System.out.println("Icône            : " + icon);
        System.out.println("Animation        : " + (g.animationTemplateSpellId == null ? "actuelle" : "sort modèle ID " + g.animationTemplateSpellId));
    }

    private void loadExistingVisualPatch(GradeSettings settings) throws IOException {
        if (!Files.exists(clientPatchesFile) || Files.size(clientPatchesFile) == 0) return;
        Map<String, String> patches = json.readValue(clientPatchesFile.toFile(), new TypeReference<Map<String, String>>() {});
        String encoded = patches.get(String.valueOf(settings.spellId));
        if (encoded == null) return;
        String[] parts = encoded.split("\\|", -1);
        if (parts.length >= 13 && !parts[12].isBlank()) settings.iconTemplateSpellId = Integer.parseInt(parts[12]);
        if (parts.length >= 14 && !parts[13].isBlank()) settings.directIconId = Integer.parseInt(parts[13]);
    }

    private void applyGradeUpdate(Connection connection, EditableSpell spell, GradeSettings original, GradeSettings edited) throws Exception {
        boolean patchExisted = Files.exists(clientPatchesFile);
        byte[] patchBefore = patchExisted ? Files.readAllBytes(clientPatchesFile) : null;
        Path backupDir = writeUpdateBackup(original, patchExisted, patchBefore);
        try {
            updateGradeRow(connection, edited);
            updateSpellVisuals(connection, edited);
            updateClientPatch(edited);
        } catch (Exception failure) {
            try {
                updateGradeRow(connection, original);
                updateSpellVisuals(connection, original);
                if (patchExisted) Files.write(clientPatchesFile, patchBefore); else Files.deleteIfExists(clientPatchesFile);
            } catch (Exception rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw failure;
        }
        ui.title("Sort modifié");
        ui.success(spell.name + " (ID " + spell.id + ")");
        ui.info("Effets conservés sans modification.");
        ui.info("Sauvegarde : " + backupDir);
        ui.info("Redémarrer le serveur Game et le client.");
    }

    private void updateSpellVisuals(Connection connection, GradeSettings g) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE `spells` SET `sprite`=?,`spriteinfo`=? WHERE `id`=?")) {
            ps.setInt(1, g.sprite); ps.setString(2, g.spriteInfo); ps.setInt(3, g.spellId);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Animation impossible à mettre à jour pour le sort " + g.spellId + ".");
        }
    }

    private void updateGradeRow(Connection connection, GradeSettings g) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE `spells_grade` SET `paCost`=?,`poMin`=?,`poMax`=?,`ratioCC`=?,`ratioEC`=?,`isLine`=?,`needLOS`=?,`isPoModif`=?,`maxByTurn`=?,`maxByTarget`=?,`CD`=?,`endTurn`=? WHERE `spellID`=? AND `gradeID`=?")) {
            int i = 1;
            ps.setInt(i++, g.paCost); ps.setInt(i++, g.poMin); ps.setInt(i++, g.poMax); ps.setInt(i++, g.ratioCc); ps.setInt(i++, g.ratioEc);
            ps.setBoolean(i++, g.lineOnly); ps.setBoolean(i++, g.needLos); ps.setBoolean(i++, g.poModifiable); ps.setInt(i++, g.maxPerTurn);
            ps.setInt(i++, g.maxPerTarget); ps.setInt(i++, g.cooldown); ps.setBoolean(i++, g.ecEndsTurn); ps.setInt(i++, g.spellId); ps.setInt(i, CUSTOM_GRADE);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Mise à jour impossible pour le sort " + g.spellId + ".");
        }
    }

    private void updateClientPatch(GradeSettings g) throws IOException {
        Files.createDirectories(clientPatchesFile.getParent());
        Map<String, String> patches = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
        if (Files.exists(clientPatchesFile) && Files.size(clientPatchesFile) > 0) {
            patches.putAll(json.readValue(clientPatchesFile.toFile(), new TypeReference<Map<String, String>>() {}));
        }
        patches.put(String.valueOf(g.spellId), g.encode());
        Path temp = clientPatchesFile.resolveSibling(clientPatchesFile.getFileName() + ".tmp");
        json.writeValue(temp.toFile(), patches);
        Files.move(temp, clientPatchesFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path writeUpdateBackup(GradeSettings original, boolean patchExisted, byte[] patchBefore) throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = backupRoot.resolve(stamp + "-update-spell-" + original.spellId);
        Files.createDirectories(dir);
        String sql = "-- Restauration avant modification du sort " + original.spellId + "\n" + original.restoreSql() + "\n";
        Files.writeString(dir.resolve("restore.sql"), sql, StandardCharsets.UTF_8);
        if (patchExisted) Files.write(dir.resolve("spell_patches.json.before"), patchBefore);
        else Files.writeString(dir.resolve("spell_patches.json.before"), "{}\n", StandardCharsets.UTF_8);
        return dir;
    }

    private void deleteSpellFlow(Connection connection) throws Exception {
        int scope = ui.select("Type de sort à supprimer", List.of(
                "Sort global",
                "Sort d'une classe"
        ));
        Integer selectedMorph = null;
        if (scope == 1) {
            int classIndex = ui.select("Classe Gladiatrool", Arrays.asList(GLADIATROOL_NAMES));
            selectedMorph = GLADIATROOL_MORPHS[classIndex];
        }

        Map<String, CreatedSpellRecord> registry = discoverCreatedSpells(connection);
        final Integer morphFilter = selectedMorph;
        List<CreatedSpellRecord> candidates = registry.values().stream()
                .filter(r -> scope == 0 ? r.global : !r.global && r.morphIds.contains(morphFilter))
                .sorted(Comparator.comparingInt(r -> r.id))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            ui.info("Aucun sort créé par le builder ne correspond à ce filtre.");
            return;
        }

        int selected = ui.select("Sort personnalisé à supprimer", candidates.stream()
                .map(r -> r.name + " (ID " + r.id + ")")
                .collect(Collectors.toList()));
        CreatedSpellRecord record = candidates.get(selected);
        System.out.println("Sort sélectionné : " + record.name + " (ID " + record.id + ")");
        System.out.println("Portée : " + (record.global ? "12 classes" : record.morphIds.stream().map(this::morphName).collect(Collectors.joining(", "))));
        if (!record.replacementMetadataAvailable && isPositionedInMorph(connection, record)) {
            ui.info("Attention : ce sort est antérieur au registre de suppression. Le sort qu'il remplaçait ne pourra pas être rétabli automatiquement.");
        }
        if (!ui.confirm("Êtes-vous sûr de vouloir supprimer ce sort ?", false)) throw new UserCancelledException();
        if (!ui.confirm("Dernière confirmation : supprimer définitivement " + record.name + " (ID " + record.id + ") ?", false)) throw new UserCancelledException();

        SpellDraft snapshotDraft = new SpellDraft();
        snapshotDraft.id = record.id;
        snapshotDraft.morphIds.addAll(record.morphIds);
        CreationSnapshot snapshot = captureSnapshot(connection, snapshotDraft);
        Path backupDir = writeDeletionBackup(connection, snapshot, record);
        try {
            deleteSpell(connection, record);
            removeClientRecord(record.id);
            removeClientPatch(record.id);
            registry.remove(String.valueOf(record.id));
            writeRegistry(registry);
        } catch (Exception failure) {
            try {
                executeRestoreSql(connection, backupDir.resolve("restore.sql"));
                restoreClientBackup(snapshot);
                restoreClientPatchesBackup(snapshot);
                restoreRegistryBackup(snapshot);
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }

        ui.title("Sort supprimé");
        ui.success(record.name + " (ID " + record.id + ")");
        ui.info("Sauvegarde de suppression : " + backupDir);
        ui.info("Redémarrer le serveur Game et le client.");
    }

    private Map<String, CreatedSpellRecord> discoverCreatedSpells(Connection connection) throws Exception {
        Map<String, CreatedSpellRecord> records = loadRegistry();
        Set<Integer> ids = new TreeSet<>();
        for (String key : records.keySet()) {
            try { ids.add(Integer.parseInt(key)); } catch (NumberFormatException ignored) {}
        }
        if (Files.exists(clientDataFile) && Files.size(clientDataFile) > 0) {
            Map<String, String> clientRecords = json.readValue(clientDataFile.toFile(), new TypeReference<Map<String, String>>() {});
            for (String key : clientRecords.keySet()) {
                try {
                    int id = Integer.parseInt(key);
                    if (id >= CUSTOM_ID_MIN && id <= CUSTOM_ID_MAX) ids.add(id);
                } catch (NumberFormatException ignored) {}
            }
        }
        Map<Integer, String> names = loadSpellNames(connection);
        Map<Integer, List<Integer>> memberships = new HashMap<>();
        for (int id : ids) memberships.put(id, new ArrayList<>());
        Map<Integer, List<MorphSpell>> morphSpells = loadMorphSpells(connection, Arrays.stream(GLADIATROOL_MORPHS).boxed().collect(Collectors.toList()));
        for (int morphId : GLADIATROOL_MORPHS) {
            for (MorphSpell spell : morphSpells.getOrDefault(morphId, List.of())) {
                if (memberships.containsKey(spell.id)) memberships.get(spell.id).add(morphId);
            }
        }
        for (int id : ids) {
            CreatedSpellRecord record = records.computeIfAbsent(String.valueOf(id), key -> new CreatedSpellRecord());
            record.id = id;
            if (record.name == null || record.name.isBlank()) record.name = names.getOrDefault(id, "Sort inconnu");
            List<Integer> liveMemberships = memberships.getOrDefault(id, List.of());
            if (!liveMemberships.isEmpty()) {
                record.morphIds.clear();
                record.morphIds.addAll(liveMemberships);
                record.global = liveMemberships.containsAll(Arrays.stream(GLADIATROOL_MORPHS).boxed().collect(Collectors.toList()));
            }
        }
        return records;
    }

    private boolean isPositionedInMorph(Connection connection, CreatedSpellRecord record) throws SQLException {
        Map<Integer, List<MorphSpell>> byMorph = loadMorphSpells(connection, record.morphIds);
        return byMorph.values().stream().flatMap(Collection::stream).anyMatch(s -> s.id == record.id && s.position > 0);
    }

    private void deleteSpell(Connection connection, CreatedSpellRecord record) throws SQLException {
        for (int morphId : record.morphIds) {
            try (PreparedStatement select = connection.prepareStatement("SELECT `spells` FROM `full_morphs` WHERE `id`=?")) {
                select.setInt(1, morphId);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        String updated = removeAndRestoreLink(rs.getString(1), record.id, record.morphReplacements.get(morphId));
                        try (PreparedStatement update = connection.prepareStatement("UPDATE `full_morphs` SET `spells`=? WHERE `id`=?")) {
                            update.setString(1, updated); update.setInt(2, morphId); update.executeUpdate();
                        }
                    }
                }
            }
        }
        if (!record.morphIds.isEmpty()) {
            String placeholders = record.morphIds.stream().map(x -> "?").collect(Collectors.joining(","));
            try (PreparedStatement select = connection.prepareStatement("SELECT `id`,`spells` FROM `gladiatrool_spells` WHERE `fullMorphId` IN (" + placeholders + ")")) {
                for (int i = 0; i < record.morphIds.size(); i++) select.setInt(i + 1, record.morphIds.get(i));
                List<SavedLayout> updates = new ArrayList<>();
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) updates.add(new SavedLayout(rs.getInt("id"), 0, 0,
                            removeAndRestoreLink(rs.getString("spells"), record.id, record.layoutReplacements.get(rs.getInt("id")))));
                }
                for (SavedLayout update : updates) {
                    try (PreparedStatement ps = connection.prepareStatement("UPDATE `gladiatrool_spells` SET `spells`=? WHERE `id`=?")) {
                        ps.setString(1, update.spells); ps.setInt(2, update.id); ps.executeUpdate();
                    }
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM `spells_effect` WHERE `spellID`=?")) { ps.setInt(1, record.id); ps.executeUpdate(); }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM `spells_grade` WHERE `spellID`=?")) { ps.setInt(1, record.id); ps.executeUpdate(); }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM `spells` WHERE `id`=?")) { ps.setInt(1, record.id); ps.executeUpdate(); }
    }

    private String removeAndRestoreLink(String serialized, int spellId, String replacement) {
        List<String> entries = serialized == null || serialized.isBlank() ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(serialized.split(",")));
        entries.removeIf(entry -> serializedSpellId(entry) == spellId);
        if (replacement != null && entries.stream().noneMatch(entry -> serializedSpellId(entry) == serializedSpellId(replacement))) entries.add(replacement);
        return String.join(",", entries);
    }

    private int serializedSpellId(String entry) {
        try { return Integer.parseInt(entry.trim().split(";", -1)[0]); }
        catch (Exception ignored) { return Integer.MIN_VALUE; }
    }

    private MorphSpell findRawByPosition(String serialized, int position) {
        return parseMorphSpells(serialized, Map.of()).stream().filter(s -> s.position == position).findFirst().orElse(null);
    }

    private MorphSpell findRawMatch(String serialized, int id, int position) {
        return parseMorphSpells(serialized, Map.of()).stream()
                .filter(s -> s.id == id || (position > 0 && s.position == position)).findFirst().orElse(null);
    }

    private void removeClientRecord(int spellId) throws IOException {
        if (!Files.exists(clientDataFile)) return;
        Map<String, String> records = json.readValue(clientDataFile.toFile(), new TypeReference<Map<String, String>>() {});
        records.remove(String.valueOf(spellId));
        Path temp = clientDataFile.resolveSibling(clientDataFile.getFileName() + ".tmp");
        json.writeValue(temp.toFile(), new TreeMap<>(records));
        Files.move(temp, clientDataFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private void removeClientPatch(int spellId) throws IOException {
        if (!Files.exists(clientPatchesFile)) return;
        Map<String, String> patches = json.readValue(clientPatchesFile.toFile(), new TypeReference<Map<String, String>>() {});
        patches.remove(String.valueOf(spellId));
        Path temp = clientPatchesFile.resolveSibling(clientPatchesFile.getFileName() + ".tmp");
        json.writeValue(temp.toFile(), new TreeMap<>(patches));
        Files.move(temp, clientPatchesFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private void verifySchema(Connection c) throws SQLException {
        Map<String, Set<String>> required = new LinkedHashMap<>();
        required.put("spells", Set.of("id", "name", "sprite", "spriteinfo", "type", "duration"));
        required.put("spells_grade", Set.of("spellID", "gradeID", "paCost", "poMin", "poMax", "ratioCC", "ratioEC", "isLine", "needLOS", "needEmptyC", "isPoModif", "maxByTurn", "maxByTarget", "CD", "lvlLearn", "endTurn", "statesForbidden", "stateNeed"));
        required.put("spells_effect", Set.of("spellID", "gradeID", "effectID", "min", "max", "args", "area", "chance", "turn", "isCCeffect", "jet", "effectTarget", "trigger", "onHitTrigger"));
        required.put("full_morphs", Set.of("id", "spells"));
        required.put("gladiatrool_spells", Set.of("id", "playerId", "fullMorphId", "spells"));
        DatabaseMetaData md = c.getMetaData();
        for (Map.Entry<String, Set<String>> table : required.entrySet()) {
            Set<String> actual = new HashSet<>();
            try (ResultSet rs = md.getColumns(c.getCatalog(), null, table.getKey(), null)) {
                while (rs.next()) actual.add(rs.getString("COLUMN_NAME"));
            }
            if (actual.isEmpty()) throw new IllegalStateException("Table absente : " + table.getKey());
            Set<String> missing = new LinkedHashSet<>(table.getValue());
            missing.removeAll(actual);
            if (!missing.isEmpty()) throw new IllegalStateException("Colonnes absentes dans " + table.getKey() + " : " + missing);
        }
    }

    private int findNextCustomId(Connection c) throws SQLException {
        for (int id = CUSTOM_ID_MIN; id <= CUSTOM_ID_MAX; id++) {
            if (isIdFree(c, id)) return id;
        }
        throw new IllegalStateException("La plage 10000–10999 est complète.");
    }

    private boolean isIdFree(Connection c, int id) throws SQLException {
        for (String query : List.of(
                "SELECT 1 FROM `spells` WHERE `id`=? LIMIT 1",
                "SELECT 1 FROM `spells_grade` WHERE `spellID`=? LIMIT 1",
                "SELECT 1 FROM `spells_effect` WHERE `spellID`=? LIMIT 1")) {
            try (PreparedStatement ps = c.prepareStatement(query)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return false; }
            }
        }
        return true;
    }

    private AnimationTemplate loadAnimationTemplate(Connection c) throws SQLException {
        return loadAnimationTemplate(c, animationTemplateSpellId);
    }

    private AnimationTemplate loadAnimationTemplate(Connection c, int templateSpellId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT `sprite`,`spriteinfo` FROM `spells` WHERE `id`=?")) {
            ps.setInt(1, templateSpellId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Sort modèle d'animation introuvable : ID " + templateSpellId + ".");
                return new AnimationTemplate(rs.getInt(1), rs.getString(2));
            }
        }
    }

    private Map<Integer, List<MorphSpell>> loadMorphSpells(Connection c, List<Integer> morphIds) throws SQLException {
        Map<Integer, String> names = loadSpellNames(c);
        Map<Integer, List<MorphSpell>> result = new LinkedHashMap<>();
        for (int morphId : morphIds) {
            try (PreparedStatement ps = c.prepareStatement("SELECT `spells` FROM `full_morphs` WHERE `id`=?")) {
                ps.setInt(1, morphId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("Morph " + morphId + " introuvable.");
                    result.put(morphId, parseMorphSpells(rs.getString(1), names));
                }
            }
        }
        return result;
    }

    private Map<Integer, String> loadSpellNames(Connection c) throws SQLException {
        Map<Integer, String> names = new HashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT `id`,`name` FROM `spells`")) {
            while (rs.next()) names.put(rs.getInt(1), rs.getString(2));
        }
        return names;
    }

    private List<MorphSpell> parseMorphSpells(String serialized, Map<Integer, String> names) {
        List<MorphSpell> result = new ArrayList<>();
        if (serialized == null || serialized.isBlank()) return result;
        for (String item : serialized.split(",")) {
            if (item.isBlank()) continue;
            String[] parts = item.trim().split(";", -1);
            if (parts.length < 2) continue;
            try {
                int id = Integer.parseInt(parts[0].trim());
                int grade = Integer.parseInt(parts[1].trim());
                String raw = parts.length >= 3 && !parts[2].isBlank() ? parts[2].trim() : "_";
                int position;
                try { position = Integer.parseInt(raw, 16); } catch (NumberFormatException e) { position = -1; }
                result.add(new MorphSpell(id, grade, position, raw, names.getOrDefault(id, "Sort inconnu")));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private MorphSpell findByPosition(List<MorphSpell> spells, int position) {
        if (spells == null) return null;
        return spells.stream().filter(s -> s.position == position).findFirst().orElse(null);
    }

    private void printSummary(SpellDraft d) {
        System.out.println("Nom              : " + d.name);
        System.out.println("Description      : " + (d.description.isBlank() ? "aucune" : d.description));
        System.out.println("ID prévu         : " + d.id);
        System.out.println("Portée Gladiatrool: " + (d.global ? "12 classes" : morphName(d.morphIds.get(0))));
        System.out.println("PA / PO          : " + d.paCost + " PA · " + d.poMin + "–" + d.poMax + " PO");
        System.out.println("CC / EC          : " + ratio(d.ratioCc) + " / " + ratio(d.ratioEc));
        System.out.println("Contraintes      : ligne=" + yesNo(d.lineOnly) + ", LDV=" + yesNo(d.needLos) + ", PO modifiable=" + yesNo(d.poModifiable));
        System.out.println("Limites          : tour=" + d.maxPerTurn + ", cible=" + d.maxPerTarget + ", relance=" + d.cooldown);
        System.out.println("Cibles           : " + (d.targetMask == 0 ? "tout le monde" : d.targetMask == 1 ? "ennemis" : "lanceur"));
        System.out.println("Effets normaux   : " + describeEffects(d.normalEffects));
        System.out.println("Effets critiques : " + (d.ratioCc == 0 ? "aucun" : describeEffects(d.criticalEffects)));
        System.out.println("Affectation      : " + (d.replace ? "remplacement réversible" : "ajout sans raccourci"));
        String icon = d.directIconId == null ? "sort modèle ID " + d.iconTemplateSpellId
                : "fichier ID " + d.directIconId + " dans clips/spells/icons/up";
        System.out.println("Icône / animation: " + icon + " / sort modèle ID " + d.animationTemplateSpellId);
    }

    private String describeEffects(List<DamageLine> effects) {
        return effects.stream().map(e -> (e.lifeSteal ? "vol " : "") + e.element.label + " " + e.min + "–" + e.max).collect(Collectors.joining(" + "));
    }

    private void loadBuilderPaths() throws IOException {
        builderDirectory = Path.of("").toAbsolutePath().normalize();
        Path builderConfig = builderDirectory.resolve("builder.properties");
        Properties paths = loadProperties(builderConfig);
        gameConfig = resolveConfiguredPath(builderDirectory, requiredProperty(paths, "server.config.path"));
        clientDataFile = resolveConfiguredPath(builderDirectory, requiredProperty(paths, "client.customSpells.path"));
        clientPatchesFile = resolveConfiguredPath(builderDirectory, requiredProperty(paths, "client.spellPatches.path"));
        clientIconDirectory = clientDataFile.getParent().resolve("clips/spells/icons/up");
        iconTemplateSpellId = requiredPositiveInt(paths, "template.iconSpellId");
        animationTemplateSpellId = requiredPositiveInt(paths, "template.animationSpellId");
    }

    private Path resolveConfiguredPath(Path base, String configured) {
        Path path = Path.of(configured);
        return (path.isAbsolute() ? path : base.resolve(path)).normalize();
    }

    private String requiredProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Clé absente dans builder.properties : " + key);
        }
        return value.trim();
    }

    private int requiredPositiveInt(Properties properties, String key) {
        String value = requiredProperty(properties, key);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) {}
        throw new IllegalStateException("Valeur invalide dans builder.properties pour " + key + " : " + value);
    }

    private Properties loadProperties(Path path) throws IOException {
        if (!Files.exists(path)) throw new IllegalStateException("Configuration introuvable : " + path);
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(reader); }
        return p;
    }

    private String morphName(int id) {
        for (int i = 0; i < GLADIATROOL_MORPHS.length; i++) if (GLADIATROOL_MORPHS[i] == id) return GLADIATROOL_NAMES[i];
        return String.valueOf(id);
    }

    private static String ratio(int value) { return value == 0 ? "aucun" : "1/" + value; }
    private static String yesNo(boolean value) { return value ? "oui" : "non"; }
    private static String sqlEscape(String value) { return value == null ? "" : value.replace("'", "''"); }

    private static String diceJet(int min, int max) {
        if (max <= min) return "0d0+" + min;
        int faces = max - min + 1;
        return "1d" + faces + "+" + (min - 1);
    }

    private enum Element {
        NEUTRAL("Neutre", 100, 95, 0),
        EARTH("Terre", 97, 92, 3),
        FIRE("Feu", 99, 94, 2),
        WATER("Eau", 96, 91, 1),
        AIR("Air", 98, 93, 4);
        final String label;
        final int damageEffect;
        final int lifeStealEffect;
        final int clientClassId;
        Element(String label, int damageEffect, int lifeStealEffect, int clientClassId) {
            this.label = label; this.damageEffect = damageEffect; this.lifeStealEffect = lifeStealEffect; this.clientClassId = clientClassId;
        }
    }

    private static final class DamageLine {
        Element element;
        boolean lifeSteal;
        int min;
        int max;
        int effectId() { return lifeSteal ? element.lifeStealEffect : element.damageEffect; }
    }

    private static final class SpellDraft {
        int id;
        String name;
        String description;
        int iconTemplateSpellId;
        Integer directIconId;
        int animationTemplateSpellId;
        boolean global;
        final List<Integer> morphIds = new ArrayList<>();
        int paCost, poMin, poMax, ratioCc, ratioEc, cooldown, maxPerTurn, maxPerTarget, targetMask;
        boolean poModifiable, lineOnly, needLos, ecEndsTurn, replace;
        int replacePosition;
        final Map<Integer, MorphSpell> replacements = new HashMap<>();
        final List<DamageLine> normalEffects = new ArrayList<>();
        final List<DamageLine> criticalEffects = new ArrayList<>();
    }

    private static final class MorphSpell {
        final int id, grade, position;
        final String rawPosition, name;
        MorphSpell(int id, int grade, int position, String rawPosition, String name) {
            this.id = id; this.grade = grade; this.position = position; this.rawPosition = rawPosition; this.name = name;
        }
        String serialize() { return id + ";" + grade + ";" + (rawPosition == null || rawPosition.isBlank() ? "_" : rawPosition); }
    }

    private static final class SavedLayout {
        final int id, playerId, fullMorphId;
        final String spells;
        SavedLayout(int id, int playerId, int fullMorphId, String spells) { this.id = id; this.playerId = playerId; this.fullMorphId = fullMorphId; this.spells = spells; }
    }

    private static final class CreationSnapshot {
        final Map<Integer, String> fullMorphValues = new LinkedHashMap<>();
        final List<SavedLayout> savedLayouts = new ArrayList<>();
        boolean clientFileExisted;
        byte[] clientFileBytes;
        boolean clientPatchesFileExisted;
        byte[] clientPatchesFileBytes;
        boolean registryFileExisted;
        byte[] registryFileBytes;
    }

    private static final class CreatedSpellRecord {
        public int id;
        public String name;
        public boolean global;
        public boolean replacementMetadataAvailable;
        public List<Integer> morphIds = new ArrayList<>();
        public Map<Integer, String> morphReplacements = new HashMap<>();
        public Map<Integer, String> layoutReplacements = new HashMap<>();
        public CreatedSpellRecord() {}
    }

    private static final class EditableSpell {
        final int id;
        final String name;
        final List<Integer> morphIds = new ArrayList<>();
        boolean global;
        EditableSpell(int id, String name) { this.id = id; this.name = name; }
    }

    private static final class GradeSettings {
        int spellId;
        int paCost, poMin, poMax, ratioCc, ratioEc, maxPerTurn, maxPerTarget, cooldown;
        int sprite;
        Integer iconTemplateSpellId, directIconId, animationTemplateSpellId;
        String spriteInfo;
        boolean lineOnly, needLos, poModifiable, ecEndsTurn;

        GradeSettings copy() {
            GradeSettings copy = new GradeSettings();
            copy.spellId = spellId; copy.paCost = paCost; copy.poMin = poMin; copy.poMax = poMax;
            copy.ratioCc = ratioCc; copy.ratioEc = ratioEc; copy.maxPerTurn = maxPerTurn; copy.maxPerTarget = maxPerTarget;
            copy.cooldown = cooldown; copy.lineOnly = lineOnly; copy.needLos = needLos;
            copy.poModifiable = poModifiable; copy.ecEndsTurn = ecEndsTurn;
            copy.sprite = sprite; copy.spriteInfo = spriteInfo; copy.iconTemplateSpellId = iconTemplateSpellId;
            copy.directIconId = directIconId; copy.animationTemplateSpellId = animationTemplateSpellId;
            return copy;
        }

        String encode() {
            return String.join("|", String.valueOf(paCost), String.valueOf(poMin), String.valueOf(poMax),
                    String.valueOf(ratioCc), String.valueOf(ratioEc), lineOnly ? "1" : "0", needLos ? "1" : "0",
                    poModifiable ? "1" : "0", String.valueOf(maxPerTurn), String.valueOf(maxPerTarget),
                    String.valueOf(cooldown), ecEndsTurn ? "1" : "0",
                    iconTemplateSpellId == null ? "" : String.valueOf(iconTemplateSpellId),
                    directIconId == null ? "" : String.valueOf(directIconId));
        }

        String restoreSql() {
            return "UPDATE `spells_grade` SET `paCost`=" + paCost + ",`poMin`=" + poMin + ",`poMax`=" + poMax
                    + ",`ratioCC`=" + ratioCc + ",`ratioEC`=" + ratioEc + ",`isLine`=" + (lineOnly ? 1 : 0)
                    + ",`needLOS`=" + (needLos ? 1 : 0) + ",`isPoModif`=" + (poModifiable ? 1 : 0)
                    + ",`maxByTurn`=" + maxPerTurn + ",`maxByTarget`=" + maxPerTarget + ",`CD`=" + cooldown
                    + ",`endTurn`=" + (ecEndsTurn ? 1 : 0) + " WHERE `spellID`=" + spellId + " AND `gradeID`=" + CUSTOM_GRADE + ";\n"
                    + "UPDATE `spells` SET `sprite`=" + sprite + ",`spriteinfo`=" + sqlString(spriteInfo) + " WHERE `id`=" + spellId + ";";
        }

        private static String sqlString(String value) { return value == null ? "NULL" : "'" + value.replace("'", "''") + "'"; }
    }

    private static final class AnimationTemplate {
        final int sprite;
        final String spriteInfo;
        AnimationTemplate(int sprite, String spriteInfo) { this.sprite = sprite; this.spriteInfo = spriteInfo; }
    }

    private static final class DbSettings {
        final String host, port, user, password, database;
        DbSettings(String host, String port, String user, String password, String database) { this.host = host; this.port = port; this.user = user; this.password = password; this.database = database; }
        static DbSettings from(Properties p) {
            return new DbSettings(required(p, "database.game.host"), required(p, "database.game.port"), required(p, "database.game.user"), required(p, "database.game.pass"), required(p, "database.game.name"));
        }
        String url() { return "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=UTF-8"; }
        static String required(Properties p, String key) {
            String value = p.getProperty(key);
            if (value == null || value.trim().isEmpty()) throw new IllegalStateException("Clé absente dans config.properties : " + key);
            return value.trim();
        }
    }

    private static final class ClientRecord {
        static String encode(SpellDraft d) {
            int classId = d.normalEffects.isEmpty() ? 0 : d.normalEffects.get(0).element.clientClassId;
            return join(
                    encodeText(d.name), encodeText(d.description), String.valueOf(d.paCost), String.valueOf(d.poMin), String.valueOf(d.poMax),
                    String.valueOf(d.ratioCc), String.valueOf(d.ratioEc), bool(d.lineOnly), bool(d.needLos), bool(d.poModifiable),
                    String.valueOf(classId), String.valueOf(d.maxPerTurn), String.valueOf(d.maxPerTarget), String.valueOf(d.cooldown),
                    bool(d.ecEndsTurn), encodeEffects(d.normalEffects), encodeEffects(d.criticalEffects), String.valueOf(d.iconTemplateSpellId),
                    "", d.directIconId == null ? "" : String.valueOf(d.directIconId)
            );
        }
        private static String encodeEffects(List<DamageLine> effects) {
            return effects.stream().map(e -> e.effectId() + "," + e.min + "," + e.max + "," + diceJet(e.min, e.max)).collect(Collectors.joining(";"));
        }
        private static String encodeText(String text) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                int value = text.charAt(i);
                if ((value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z') || (value >= '0' && value <= '9') || value == '-' || value == '_' || value == '.' || value == '~') out.append((char) value);
                else if (value <= 0x7f) out.append('%').append(String.format("%02X", value));
                else out.append("%u").append(String.format("%04X", value));
            }
            return out.toString();
        }
        private static String bool(boolean value) { return value ? "1" : "0"; }
        private static String join(String... values) { return String.join("|", values); }
    }

    private static final class Terminal {
        private final java.io.BufferedReader fallbackReader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8));

        void title(String value) { System.out.println("\n=== " + value + " ==="); }
        void info(String value) { System.out.println(value); }
        void success(String value) { System.out.println("OK · " + value); }

        int select(String question, List<String> options) {
            if (options == null || options.isEmpty()) throw new IllegalArgumentException("Liste vide : " + question);
            if (!isInteractiveWindowsConsole()) return selectFallback(question, options);
            int selected = 0;
            int lines = 0;
            try {
                while (true) {
                    if (lines > 0) System.out.print("\033[" + lines + "A");
                    System.out.println(question + " (↑/↓ puis Entrée)");
                    for (int i = 0; i < options.size(); i++) {
                        System.out.print("\033[2K");
                        System.out.println((i == selected ? "  > " : "    ") + options.get(i));
                    }
                    lines = options.size() + 1;
                    int key = readWindowsKey();
                    if (key == 38) selected = (selected - 1 + options.size()) % options.size();
                    else if (key == 40) selected = (selected + 1) % options.size();
                    else if (key == 13) return selected;
                    else if (key == 27) throw new UserCancelledException();
                }
            } catch (IOException | UnsatisfiedLinkError e) {
                return selectFallback(question, options);
            }
        }

        boolean confirm(String question, boolean defaultValue) {
            return select(question, defaultValue ? List.of("Oui", "Non") : List.of("Non", "Oui")) == (defaultValue ? 0 : 1);
        }

        String askText(String question, int minLength, int maxLength) {
            while (true) {
                System.out.print(question + " : ");
                String value = readLine(false);
                if (value == null) throw new UserCancelledException();
                value = value.trim();
                if (value.length() >= minLength && value.length() <= maxLength && !value.contains("|")) return value;
                System.out.println("Valeur invalide : " + minLength + " à " + maxLength + " caractères, sans le caractère |.");
            }
        }

        int askInt(String question, int min, int max, int defaultValue) {
            while (true) {
                System.out.print(question + " [" + min + "–" + max + ", défaut " + defaultValue + "] : ");
                String value = readLine(false);
                if (value == null) throw new UserCancelledException();
                value = value.trim();
                if (value.isEmpty()) return defaultValue;
                try {
                    int parsed = Integer.parseInt(value);
                    if (parsed >= min && parsed <= max) return parsed;
                } catch (NumberFormatException ignored) {
                }
                System.out.println("Saisie invalide. Valeur attendue entre " + min + " et " + max + ".");
            }
        }

        private int selectFallback(String question, List<String> options) {
            while (true) {
                System.out.println(question);
                for (int i = 0; i < options.size(); i++) System.out.println("  " + (i + 1) + ". " + options.get(i));
                System.out.print("Choix [1–" + options.size() + "] : ");
                String input = readLine(false);
                if (input == null) throw new UserCancelledException();
                try {
                    int selected = Integer.parseInt(input.trim()) - 1;
                    if (selected >= 0 && selected < options.size()) return selected;
                } catch (NumberFormatException ignored) {
                }
                System.out.println("Choix invalide.");
            }
        }

        private String readLine(boolean secret) {
            Console console = System.console();
            if (console != null) {
                if (secret) {
                    char[] chars = console.readPassword();
                    return chars == null ? null : new String(chars);
                }
                return console.readLine();
            }
            try {
                return fallbackReader.readLine();
            } catch (IOException e) {
                throw new IllegalStateException("Lecture console impossible.", e);
            }
        }

        private boolean isInteractiveWindowsConsole() {
            return System.console() != null && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        }

        private int readWindowsKey() throws IOException {
            while (true) {
                Kernel32.INPUT_RECORD[] records = WindowsSupport.readConsoleInput(1);
                if (records == null || records.length == 0) continue;
                Kernel32.INPUT_RECORD record = records[0];
                if (record.eventType == Kernel32.INPUT_RECORD.KEY_EVENT && record.keyEvent != null && record.keyEvent.keyDown) return record.keyEvent.keyCode;
            }
        }
    }

    private static final class UserCancelledException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
