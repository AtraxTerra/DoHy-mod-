package fr.atraxterra.dohy.agri;

/**
 * Les cereales de DoHy. Le ble de Minecraft en est la neuvieme : il n'est pas
 * redeclare ici, mais il sert de parent au kamut et nourrit la mangeoire.
 *
 * <p>Les trois OGM ne poussent pas dans la nature et ne tombent pas de
 * l'herbe : leurs graines sortent de la machine a OGM, qui croise deux
 * semences naturelles.
 *
 * @see GmoMachineMenu
 */
public enum Cereal {
    CORN("corn", "Mais", false, 1.0f, null, null),
    OAT("oat", "Avoine", false, 1.0f, null, null),
    RYE("rye", "Seigle", false, 1.0f, null, null),
    BARLEY("barley", "Orge", false, 1.0f, null, null),
    SOY("soy", "Soja", false, 1.0f, null, null),
    FONIO("fonio", "Fonio", true, 0.6f, "oat", "soy"),
    SORGHUM("sorghum", "Sorgho", true, 0.6f, "corn", "rye"),
    KAMUT("kamut", "Kamut", true, 0.6f, "barley", "wheat");

    private final String key;
    private final String label;
    private final boolean gmo;
    private final float growth;
    private final String parentA;
    private final String parentB;

    Cereal(String key, String label, boolean gmo, float growth, String parentA, String parentB) {
        this.key = key;
        this.label = label;
        this.gmo = gmo;
        this.growth = growth;
        this.parentA = parentA;
        this.parentB = parentB;
    }

    /** Le nom du grain ; la culture est {@code <key>_crop}, la semence {@code <key>_seeds}. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public boolean gmo() {
        return gmo;
    }

    /** La part des ticks de croissance que la culture accepte : un OGM pousse moins vite. */
    public float growth() {
        return growth;
    }

    /** La premiere cereale parente d'un OGM, par sa cle ; {@code null} pour une cereale naturelle. */
    public String parentA() {
        return parentA;
    }

    /** La seconde ; {@code wheat} designe le ble de Minecraft. */
    public String parentB() {
        return parentB;
    }
}
