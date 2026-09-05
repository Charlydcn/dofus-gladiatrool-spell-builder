package gladiatrool.builder.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class MorphRepository {
    public String spells(Connection connection, int morphId) throws SQLException { try(PreparedStatement ps=connection.prepareStatement("SELECT `spells` FROM `full_morphs` WHERE `id`=?")){ps.setInt(1,morphId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalStateException("Morph introuvable : "+morphId);return rs.getString(1);}} }
}
