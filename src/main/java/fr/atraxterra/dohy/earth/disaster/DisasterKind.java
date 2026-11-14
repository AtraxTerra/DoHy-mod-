package fr.atraxterra.dohy.earth.disaster;

/** Les quatre familles de catastrophes de la Terre. */
public enum DisasterKind {
    QUAKE("seisme"),
    ERUPTION("eruption"),
    HURRICANE("ouragan"),
    MONSOON("mousson");

    private final String key;

    DisasterKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
