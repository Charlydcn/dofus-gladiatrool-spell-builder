package gladiatrool.builder.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class SpellRepository {
    public boolean exists(Connection connection, int id) throws SQLException { try (PreparedStatement ps=connection.prepareStatement("SELECT 1 FROM `spells` WHERE `id`=? LIMIT 1")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next();}} }
    public int firstFree(Connection connection, int min, int max) throws SQLException { for(int id=min;id<=max;id++) if(!exists(connection,id) && !gradeExists(connection,id) && !effectExists(connection,id)) return id; throw new IllegalStateException("Aucun ID libre dans la plage."); }
    private boolean gradeExists(Connection c,int id)throws SQLException{return tableExists(c,"spells_grade","spellID",id);}
    private boolean effectExists(Connection c,int id)throws SQLException{return tableExists(c,"spells_effect","spellID",id);}
    private boolean tableExists(Connection c,String table,String column,int id)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM `"+table+"` WHERE `"+column+"`=? LIMIT 1")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next();}}}
}
