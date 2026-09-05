package gladiatrool.builder.domain;

import java.util.Objects;

/** Ligne d'effet geree par le builder, avec convention explicite des jets fixes. */
public final class DamageLine {
    public enum Element {
        NEUTRAL("Neutre", 100, 95), EARTH("Terre", 97, 92), FIRE("Feu", 99, 94),
        WATER("Eau", 96, 91), AIR("Air", 98, 93);
        private final String label; private final int damageId; private final int stealId;
        Element(String label, int damageId, int stealId) { this.label = label; this.damageId = damageId; this.stealId = stealId; }
        public String label() { return label; }
        public int effectId(boolean lifeSteal) { return lifeSteal ? stealId : damageId; }
    }

    private final Element element;
    private final boolean lifeSteal;
    private final int min;
    private final int max;
    private final int effectTarget;

    public DamageLine(Element element, boolean lifeSteal, int min, int max, int effectTarget) {
        this.element = Objects.requireNonNull(element, "element");
        if (min < 0 || max < min) throw new IllegalArgumentException("Jet invalide.");
        this.lifeSteal = lifeSteal; this.min = min; this.max = max; this.effectTarget = effectTarget;
    }
    public static DamageLine fixed(Element element, boolean lifeSteal, int value, int target) { return new DamageLine(element, lifeSteal, value, value, target); }
    public static DamageLine variable(Element element, boolean lifeSteal, int min, int max, int target) { return new DamageLine(element, lifeSteal, min, max, target); }
    public Element element() { return element; }
    public boolean lifeSteal() { return lifeSteal; }
    public int min() { return min; }
    public int max() { return max; }
    public int effectTarget() { return effectTarget; }
    public int effectId() { return element.effectId(lifeSteal); }
    public int storedMax() { return max == min ? -1 : max; }
    public String jet() { return max == min ? "0d0+" + min : "1d" + (max - min + 1) + "+" + (min - 1); }
    public String displayValue() { return max == min ? String.valueOf(min) : min + " à " + max; }
}
