package gladiatrool.builder.validation;

import gladiatrool.builder.client.JsonStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ClientValidator {
    private final JsonStore json = new JsonStore();
    public void validateJson(Path file) throws IOException { if (!Files.isRegularFile(file)) throw new IllegalStateException("JSON client absent : " + file); json.validate(file); }
    public void validateRecord(Path file, int spellId) throws IOException { Map<String,String> values=json.read(file); if (!values.containsKey(String.valueOf(spellId))) throw new IllegalStateException("Sort absent du JSON client : " + spellId); }
    public void validateDirectIcon(Path file, int spellId) throws IOException { Map<String,String> values=json.read(file); String value=values.get(String.valueOf(spellId)); if(value==null)throw new IllegalStateException("Sort absent du JSON client : "+spellId); String[] fields=value.split("\\|",-1); if(fields.length<=19 || !String.valueOf(spellId).equals(fields[19]))throw new IllegalStateException("Icône directe non dédiée au sort "+spellId); }
    public void validateSwf(Path file) throws IOException { if (!Files.isRegularFile(file) || Files.size(file)==0) throw new IllegalStateException("SWF absent ou vide : " + file); }
}
