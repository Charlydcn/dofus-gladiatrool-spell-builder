package gladiatrool.builder.validation;

import gladiatrool.builder.domain.DamageLine;
import gladiatrool.builder.domain.ZoneSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class OperationValidator {
    private final ClientValidator client = new ClientValidator();
    public void validateJsonAndSwf(Path custom, Path patches, Path swf, int spellId, boolean clientChanged) throws IOException {
        if (clientChanged) { client.validateJson(custom); if (Files.exists(patches)) client.validateJson(patches); client.validateRecord(custom, spellId); client.validateSwf(swf); }
    }
    public void validateIds(Collection<Integer> ids, int proposed) { if (proposed < 10000 || proposed > 10999) throw new IllegalArgumentException("ID hors plage Gladiatrool : " + proposed); Set<Integer> unique=new HashSet<>(ids); if (unique.contains(proposed)) throw new IllegalStateException("Collision d'ID : " + proposed); }
    public void validateEffects(Collection<DamageLine> normal, Collection<DamageLine> critical) { if (normal == null || normal.isEmpty()) throw new IllegalStateException("Au moins un effet normal est requis."); check(normal); check(critical); }
    public void validateZone(ZoneSpec zone) { if (zone == null) throw new IllegalStateException("Zone absente."); ZoneSpec.fromCode(zone.code()); }
    private void check(Collection<DamageLine> effects) { if (effects == null) return; for (DamageLine e: effects) { if (e.min()<0 || e.max()<e.min()) throw new IllegalStateException("Jet invalide."); if (e.max()==e.min() && e.storedMax()!=-1) throw new IllegalStateException("Convention de jet fixe invalide."); } }
}
