package gladiatrool.builder.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public final class CustomSpellsJsonStore {
    private final JsonStore delegate = new JsonStore();
    public Map<String,String> read(Path file)throws IOException{return delegate.read(file);}
    public void write(Path file,Map<String,String> values)throws IOException{delegate.write(file,values);}
}
