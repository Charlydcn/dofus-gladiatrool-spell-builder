package gladiatrool.builder.domain;

import java.util.Objects;

/** Zone standard partagee par la preview client et la definition serveur. */
public final class ZoneSpec {
    public enum Shape { SINGLE, CIRCLE, CROSS }

    private final Shape shape;
    private final int radius;

    private ZoneSpec(Shape shape, int radius) {
        this.shape = Objects.requireNonNull(shape, "shape");
        if (shape == Shape.SINGLE && radius != 0) throw new IllegalArgumentException("Une monocase a un rayon nul.");
        if (shape != Shape.SINGLE && (radius < 1 || radius > 4)) throw new IllegalArgumentException("Rayon attendu entre 1 et 4.");
        this.radius = radius;
    }

    public static ZoneSpec single() { return new ZoneSpec(Shape.SINGLE, 0); }
    public static ZoneSpec circle(int radius) { return new ZoneSpec(Shape.CIRCLE, radius); }
    public static ZoneSpec cross(int radius) { return new ZoneSpec(Shape.CROSS, radius); }

    public static ZoneSpec fromCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Zone absente.");
        if ("Pa".equals(code)) return single();
        if (code.length() != 2) throw new IllegalArgumentException("Zone native inconnue : " + code);
        int radius = code.charAt(1) - 'a';
        if (radius < 1 || radius > 4) throw new IllegalArgumentException("Rayon hors plage : " + code);
        switch (code.charAt(0)) {
            case 'C': return circle(radius);
            case 'X': return cross(radius);
            default: throw new IllegalArgumentException("Forme native non geree : " + code);
        }
    }

    public Shape shape() { return shape; }
    public int radius() { return radius; }

    public String code() {
        if (shape == Shape.SINGLE) return "Pa";
        return (shape == Shape.CIRCLE ? "C" : "X") + (char) ('a' + radius);
    }

    public String summary() {
        if (shape == Shape.SINGLE) return "Monocase";
        return (shape == Shape.CIRCLE ? "Cercle" : "Croix") + " rayon " + radius
                + (shape == Shape.CIRCLE ? " — centre et cellules dans le rayon " : " — centre et branches de longueur ") + radius;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof ZoneSpec)) return false;
        ZoneSpec that = (ZoneSpec) other;
        return shape == that.shape && radius == that.radius;
    }
    @Override public int hashCode() { return Objects.hash(shape, radius); }
    @Override public String toString() { return code(); }
}
