package gladiatrool.builder.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

public final class SchemaValidator {
    public void validate(Connection connection) throws SQLException {
        Map<String,Set<String>> required=Map.of(
                "spells",Set.of("id","name","sprite","spriteinfo"),
                "spells_grade",Set.of("spellID","gradeID","paCost","poMin","poMax","ratioCC","ratioEC","isLine","needLOS","needEmptyC","isPoModif","maxByTurn","maxByTarget","CD","endTurn"),
                "spells_effect",Set.of("spellID","gradeID","effectID","min","max","args","area","chance","turn","isCCeffect","jet","effectTarget","trigger","onHitTrigger"),
                "full_morphs",Set.of("id","spells"), "gladiatrool_spells",Set.of("id","playerId","fullMorphId","spells"));
        DatabaseMetaData meta=connection.getMetaData();
        for(Map.Entry<String,Set<String>> entry:required.entrySet()){Set<String> actual=new java.util.HashSet<>();try(ResultSet rs=meta.getColumns(connection.getCatalog(),null,entry.getKey(),null)){while(rs.next())actual.add(rs.getString("COLUMN_NAME"));}Set<String> missing=new java.util.HashSet<>(entry.getValue());missing.removeAll(actual);if(!missing.isEmpty())throw new IllegalStateException("Colonnes absentes dans "+entry.getKey()+" : "+missing);}
    }
}
