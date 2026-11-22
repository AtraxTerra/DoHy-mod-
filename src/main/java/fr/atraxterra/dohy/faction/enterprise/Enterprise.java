package fr.atraxterra.dohy.faction.enterprise;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Une entreprise de la nation (lot 24b) : legale ou non, elle a un secteur, un niveau de 1 a
 * 5, un capital investi, un siege dans un pays, des employes PNJ et des ameliorations. Son
 * gerant est un cadre de l'organisation dont le poste vaut « @id ».
 *
 * @param id          son identifiant dans la nation (« e3 »)
 * @param name        son nom
 * @param sector      son secteur ({@link Sector})
 * @param line        la drogue d'une plantation ou d'un laboratoire ({@link Line}), vide sinon
 * @param level       son niveau, de 1 a 5
 * @param capital     tout ce qui y a ete investi : ouverture, niveaux, ameliorations
 * @param country     le pays de son siege (0 hors de la carte de la Terre)
 * @param x           son siege, en blocs
 * @param z           son siege, en blocs
 * @param founded     son ouverture (ms)
 * @param staff       ses employes, au plus {@link Sector#staff()} par niveau
 * @param upgrades    ses ameliorations ({@link Upgrade})
 * @param closedUntil fermee jusqu'a cette heure (descente, sabotage, greve), 0 sinon
 * @param earned      tout ce qu'elle a rapporte, propre et sale, avant les frais
 */
public record Enterprise(String id, String name, String sector, String line, int level, long capital, int country,
                         int x, int z, long founded, int staff, List<String> upgrades, long closedUntil,
                         long earned) {

    public static final int MAX_LEVEL = 5;

    public static final Codec<Enterprise> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(Enterprise::id),
            Codec.STRING.fieldOf("name").forGetter(Enterprise::name),
            Codec.STRING.fieldOf("sector").forGetter(Enterprise::sector),
            Codec.STRING.optionalFieldOf("line", "").forGetter(Enterprise::line),
            Codec.INT.optionalFieldOf("level", 1).forGetter(Enterprise::level),
            Codec.LONG.optionalFieldOf("capital", 0L).forGetter(Enterprise::capital),
            Codec.INT.optionalFieldOf("country", 0).forGetter(Enterprise::country),
            Codec.INT.optionalFieldOf("x", 0).forGetter(Enterprise::x),
            Codec.INT.optionalFieldOf("z", 0).forGetter(Enterprise::z),
            Codec.LONG.optionalFieldOf("founded", 0L).forGetter(Enterprise::founded),
            Codec.INT.optionalFieldOf("staff", 0).forGetter(Enterprise::staff),
            Codec.STRING.listOf().optionalFieldOf("upgrades", List.of()).forGetter(Enterprise::upgrades),
            Codec.LONG.optionalFieldOf("closed_until", 0L).forGetter(Enterprise::closedUntil),
            Codec.LONG.optionalFieldOf("earned", 0L).forGetter(Enterprise::earned)
    ).apply(instance, Enterprise::new));

    public Enterprise {
        upgrades = List.copyOf(upgrades);
        level = Math.max(1, Math.min(MAX_LEVEL, level));
    }

    /** Le secteur ; une entreprise d'un secteur disparu redevient un commerce plutot que de casser la sauvegarde. */
    public Sector kind() {
        Sector found = Sector.of(sector);
        return found == null ? Sector.COMMERCE : found;
    }

    /** La drogue, ou {@code null}. */
    public Line drug() {
        return line.isEmpty() ? null : Line.of(line);
    }

    public boolean legal() {
        return kind().legal();
    }

    public boolean has(Upgrade upgrade) {
        return upgrades.contains(upgrade.name());
    }

    /** Les employes au complet pour son niveau. */
    public int maxStaff() {
        return kind().staff() * level;
    }

    public boolean closed(long now) {
        return closedUntil > now;
    }

    Enterprise withName(String next) {
        return new Enterprise(id, next, sector, line, level, capital, country, x, z, founded, staff, upgrades,
                closedUntil, earned);
    }

    Enterprise withLevel(int next, long invested) {
        int staffing = staff >= maxStaff() ? kind().staff() * next : staff;
        return new Enterprise(id, name, sector, line, next, capital + invested, country, x, z, founded, staffing,
                upgrades, closedUntil, earned);
    }

    Enterprise withStaff(int next) {
        return new Enterprise(id, name, sector, line, level, capital, country, x, z, founded,
                Math.max(0, Math.min(maxStaff(), next)), upgrades, closedUntil, earned);
    }

    Enterprise withUpgrade(Upgrade upgrade, long invested) {
        List<String> next = new ArrayList<>(upgrades);
        if (!next.contains(upgrade.name())) {
            next.add(upgrade.name());
        }
        return new Enterprise(id, name, sector, line, level, capital + invested, country, x, z, founded, staff, next,
                closedUntil, earned);
    }

    Enterprise closedUntil(long until) {
        return new Enterprise(id, name, sector, line, level, capital, country, x, z, founded, staff, upgrades,
                Math.max(closedUntil, until), earned);
    }

    Enterprise earning(long amount) {
        return new Enterprise(id, name, sector, line, level, capital, country, x, z, founded, staff, upgrades,
                closedUntil, earned + Math.max(0L, amount));
    }

    Enterprise shifted(long shift) {
        return new Enterprise(id, name, sector, line, level, capital, country, x, z, founded - shift, staff, upgrades,
                closedUntil > 0L ? closedUntil - shift : 0L, earned);
    }
}
