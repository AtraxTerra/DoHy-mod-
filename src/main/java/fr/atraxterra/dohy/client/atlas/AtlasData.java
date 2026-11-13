package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.earth.EarthAtlas;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

/**
 * Ce que le serveur a dit de la Terre : le globe (la grille des pays), les
 * lieux (gisements geants, zones, bases) et qui tient chaque pays. Vide hors
 * d'une carte de la Terre.
 */
public final class AtlasData {

    /** Le globe : une case pour {@code cell} blocs, le bit 0x8000 marque les eaux cotieres d'un pays. */
    public record Globe(int minX, int minZ, int cell, int width, int height, short[] cells,
                        Map<Integer, EarthAtlas.Country> countries) {

        /** La case de ce bloc ; 0 hors de la grille. */
        public int at(double x, double z) {
            int column = (int) Math.floor((x - minX) / cell);
            int row = (int) Math.floor((z - minZ) / cell);
            if (column < 0 || row < 0 || column >= width || row >= height) {
                return 0;
            }
            return cells[row * width + column] & 0xFFFF;
        }

        /** Le pays de ce bloc (eaux cotieres comprises), ou {@code null}. */
        public EarthAtlas.Country country(double x, double z) {
            int id = at(x, z) & 0x7FFF;
            return id == 0 ? null : countries.get(id);
        }
    }

    private static volatile Globe globe;
    private static volatile List<EarthAtlas.Mark> marks = List.of();
    private static volatile Map<Integer, EarthAtlas.Owner> owners = Map.of();
    private static volatile EarthAtlas.Nations nations;
    /** Augmente a chaque changement : les textures du globe se refont quand il bouge. */
    private static volatile int globeVersion;
    private static volatile int nationsVersion;

    private AtlasData() {
    }

    public static Globe globe() {
        return globe;
    }

    public static List<EarthAtlas.Mark> marks() {
        return marks;
    }

    public static EarthAtlas.Owner owner(int country) {
        return owners.get(country);
    }

    public static EarthAtlas.Nations nations() {
        return nations;
    }

    public static int globeVersion() {
        return globeVersion;
    }

    public static int nationsVersion() {
        return nationsVersion;
    }

    static void accept(EarthAtlas.Globe payload) {
        byte[] raw = inflate(payload.grid(), payload.width() * payload.height() * 2);
        short[] cells = new short[payload.width() * payload.height()];
        for (int index = 0; index < cells.length && index * 2 + 1 < raw.length; index++) {
            cells[index] = (short) ((raw[index * 2] & 0xFF) << 8 | (raw[index * 2 + 1] & 0xFF));
        }
        Map<Integer, EarthAtlas.Country> countries = new HashMap<>();
        for (EarthAtlas.Country country : payload.countries()) {
            countries.put(country.id(), country);
        }
        globe = new Globe(payload.minX(), payload.minZ(), payload.cell(), payload.width(), payload.height(), cells,
                Map.copyOf(countries));
        globeVersion++;
    }

    static void accept(EarthAtlas.Layers payload) {
        marks = List.copyOf(payload.marks());
    }

    static void accept(EarthAtlas.Nations payload) {
        Map<Integer, EarthAtlas.Owner> map = new HashMap<>();
        for (EarthAtlas.Owner owner : payload.owners()) {
            map.put(owner.country(), owner);
        }
        owners = Map.copyOf(map);
        nations = payload;
        nationsVersion++;
    }

    static void clear() {
        globe = null;
        marks = List.of();
        owners = Map.of();
        nations = null;
        globeVersion++;
        nationsVersion++;
    }

    private static byte[] inflate(byte[] data, int expected) {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(expected);
        byte[] buffer = new byte[1 << 16];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(buffer, 0, count);
            }
        } catch (java.util.zip.DataFormatException error) {
            throw new IllegalStateException("globe illisible", error);
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }
}
