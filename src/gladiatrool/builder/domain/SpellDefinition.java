package gladiatrool.builder.domain;

import java.util.ArrayList;
import java.util.List;

/** Modele neutre d'un sort grade 6, independant de l'interface console. */
public final class SpellDefinition {
    public int id;
    public String name = "";
    public String description = "";
    public int paCost, poMin, poMax, criticalRate, failureRate, cooldown, maxPerTurn, maxPerTarget;
    public boolean poModifiable, lineOnly, lineOfSight, emptyCell, failureEndsTurn;
    public ZoneSpec normalZone = ZoneSpec.single();
    public ZoneSpec criticalZone = ZoneSpec.single();
    public final List<Integer> morphIds = new ArrayList<>();
    public final List<DamageLine> normalEffects = new ArrayList<>();
    public final List<DamageLine> criticalEffects = new ArrayList<>();
}
