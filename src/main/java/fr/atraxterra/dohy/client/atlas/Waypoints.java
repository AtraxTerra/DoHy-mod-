package fr.atraxterra.dohy.client.atlas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.atraxterra.dohy.DoHy;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Les points de passage d'un monde, toutes dimensions, dans
 * {@code dohy_atlas/<monde>/points.json}. Le point de mort est un point comme
 * les autres, remplace a chaque mort.
 */
public final class Waypoints {

    /** Les couleurs proposees, dans l'ordre du bouton. */
    public static final int[] COLORS = {0xFFEF3826, 0xFFFFC400, 0xFF22E558, 0xFF30C5FF, 0xFF4F6FFF, 0xFFB65BD9,
            0xFFFF70C0, 0xFFFFFFFF};
    public static final String DEATH = "Mort";

    public record Point(String name, int x, int y, int z, int color, String dimension, boolean death) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Point> POINTS = new ArrayList<>();
    private static Path file;

    private Waypoints() {
    }

    public static List<Point> all() {
        return List.copyOf(POINTS);
    }

    static void open(Path folder) {
        POINTS.clear();
        file = folder.resolve("points.json");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Point> loaded = GSON.fromJson(reader, new TypeToken<List<Point>>() {
            }.getType());
            if (loaded != null) {
                for (Point point : loaded) {
                    if (point != null && point.name() != null && point.dimension() != null) {
                        POINTS.add(point);
                    }
                }
            }
        } catch (IOException | RuntimeException error) {
            DoHy.LOGGER.warn("Atlas : points de passage illisibles ({})", error.toString());
        }
    }

    static void close() {
        POINTS.clear();
        file = null;
    }

    public static void add(Point point) {
        POINTS.add(point);
        save();
    }

    public static void replace(Point before, Point after) {
        int index = POINTS.indexOf(before);
        if (index >= 0) {
            POINTS.set(index, after);
        } else {
            POINTS.add(after);
        }
        save();
    }

    public static void remove(Point point) {
        POINTS.remove(point);
        save();
    }

    /** Le point de mort : un seul, remplace a chaque mort. */
    static void death(int x, int y, int z, String dimension) {
        POINTS.removeIf(Point::death);
        POINTS.add(new Point(DEATH, x, y, z, 0xFFEF3826, dimension, true));
        save();
    }

    private static void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(POINTS, writer);
            }
        } catch (IOException | RuntimeException error) {
            DoHy.LOGGER.warn("Atlas : points de passage non sauves ({})", error.toString());
        }
    }
}
