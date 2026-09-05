package gladiatrool.builder.database;

import gladiatrool.builder.domain.DamageLine;

import java.util.LinkedHashSet;
import java.util.List;

/** Generateur SQL deterministe pour les tables Game utilisees par Gladiatrool. */
public final class MigrationGenerator {
    public String createSpell(int id, String name, int sprite, String spriteInfo, int pa, int poMin, int poMax, int cc, int ec,
                              boolean line, boolean los, boolean poModif, int maxTurn, int maxTarget, int cooldown,
                              boolean endTurn, int targetMask, String normalZone, String criticalZone, List<DamageLine> normal, List<DamageLine> critical,
                              String fullMorphUpdates, String layoutUpdates) {
        StringBuilder sql = new StringBuilder("-- Migration Gladiatrool: creation ").append(id).append("\n");
        sql.append("INSERT INTO `spells` (`id`,`name`,`sprite`,`spriteinfo`,`type`,`duration`) VALUES (")
                .append(id).append(",").append(q(name)).append(",").append(sprite).append(",").append(q(spriteInfo)).append(",-1,0);\n");
        sql.append("INSERT INTO `spells_grade` (`spellID`,`gradeID`,`paCost`,`poMin`,`poMax`,`ratioCC`,`ratioEC`,`isLine`,`needLOS`,`needEmptyC`,`isPoModif`,`maxByTurn`,`maxByTarget`,`CD`,`lvlLearn`,`endTurn`,`statesForbidden`,`stateNeed`) VALUES (")
                .append(id).append(",6,").append(pa).append(",").append(poMin).append(",").append(poMax).append(",").append(cc).append(",").append(ec).append(",")
                .append(b(line)).append(",").append(b(los)).append(",0,").append(b(poModif)).append(",").append(maxTurn).append(",").append(maxTarget).append(",").append(cooldown).append(",1,").append(b(endTurn)).append(",'0',-1);\n");
        for (DamageLine lineDef : normal) effect(sql, id, lineDef, false, targetMask, normalZone);
        for (DamageLine lineDef : critical) effect(sql, id, lineDef, true, targetMask, criticalZone);
        sql.append(fullMorphUpdates == null ? "" : fullMorphUpdates);
        sql.append(layoutUpdates == null ? "" : layoutUpdates);
        return sql.toString();
    }

    public String updateGrade(int id, int pa, int poMin, int poMax, int cc, int ec, boolean line, boolean los, boolean poModif,
                              int maxTurn, int maxTarget, int cooldown, boolean endTurn, String name, int sprite, String spriteInfo,
                              String normalZone, String criticalZone, List<DamageLine> normal, List<DamageLine> critical, boolean effectsChanged) {
        StringBuilder sql = new StringBuilder("-- Migration Gladiatrool: modification ").append(id).append("\n");
        sql.append("UPDATE `spells_grade` SET `paCost`=").append(pa).append(",`poMin`=").append(poMin).append(",`poMax`=").append(poMax)
                .append(",`ratioCC`=").append(cc).append(",`ratioEC`=").append(ec).append(",`isLine`=").append(b(line)).append(",`needLOS`=").append(b(los))
                .append(",`isPoModif`=").append(b(poModif)).append(",`maxByTurn`=").append(maxTurn).append(",`maxByTarget`=").append(maxTarget)
                .append(",`CD`=").append(cooldown).append(",`endTurn`=").append(b(endTurn)).append(" WHERE `spellID`=").append(id).append(" AND `gradeID`=6;\n");
        if (name != null) sql.append("UPDATE `spells` SET `name`=").append(q(name)).append(",`sprite`=").append(sprite).append(",`spriteinfo`=").append(q(spriteInfo)).append(" WHERE `id`=").append(id).append(";\n");
        if (effectsChanged) { sql.append("DELETE FROM `spells_effect` WHERE `spellID`=").append(id).append(" AND `gradeID`=6;\n"); for (DamageLine e : normal) effect(sql,id,e,false,e.effectTarget(),normalZone); for (DamageLine e : critical) effect(sql,id,e,true,e.effectTarget(),criticalZone); }
        return sql.toString();
    }

    public String deleteSpell(int id, String restoreSql, List<Integer> morphIds) {
        StringBuilder sql = new StringBuilder("-- Migration Gladiatrool: suppression ").append(id).append("\n");
        sql.append("DELETE FROM `spells_effect` WHERE `spellID`=").append(id).append(";\n");
        sql.append("DELETE FROM `spells_grade` WHERE `spellID`=").append(id).append(";\n");
        sql.append("DELETE FROM `spells` WHERE `id`=").append(id).append(";\n");
        sql.append(restoreSql == null ? "" : restoreSql);
        for (Integer morphId : new LinkedHashSet<>(morphIds == null ? List.of() : morphIds)) {
            sql.append("UPDATE `gladiatrool_spells` SET `spells`=TRIM(BOTH ',' FROM REGEXP_REPLACE(COALESCE(`spells`,''), '(^|,)")
                    .append(id).append(";[0-9]+;[^,]+', '')) WHERE `fullMorphId`=").append(morphId)
                    .append(" AND `spells` REGEXP '(^|,)").append(id).append(";[0-9]+;[^,]+';\n");
        }
        return sql.toString();
    }

    private void effect(StringBuilder sql, int id, DamageLine e, boolean critical, int target, String area) {
        sql.append("INSERT INTO `spells_effect` (`spellID`,`gradeID`,`effectID`,`min`,`max`,`args`,`area`,`chance`,`turn`,`isCCeffect`,`jet`,`effectTarget`,`trigger`,`onHitTrigger`) VALUES (")
                .append(id).append(",6,").append(e.effectId()).append(",").append(e.min()).append(",").append(e.storedMax()).append(",-1,").append(q(area)).append(",0,0,").append(b(critical)).append(",").append(q(e.jet())).append(",").append(target).append(",-1,-1);\n");
    }
    private static int b(boolean value) { return value ? 1 : 0; }
    private static String q(String value) { return value == null ? "NULL" : "'" + value.replace("'", "''") + "'"; }
}
