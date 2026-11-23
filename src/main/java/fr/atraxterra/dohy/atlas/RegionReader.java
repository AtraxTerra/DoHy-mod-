package fr.atraxterra.dohy.atlas;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Un fichier de region du monde ({@code r.X.Z.mca}, 32 chunks de cote), lu tel qu'il est sur le
 * disque, sans passer par le jeu : l'atlas du serveur y prend le terrain des chunks qu'aucun joueur
 * n'a charges. Le fichier se lit d'un coup (une photo du moment) ; un chunk que le jeu ecrivait a cet
 * instant se lit mal, et l'atlas le reprendra plus tard.
 *
 * <p>Le format : un en-tete de 1 024 emplacements (secteur de 4 Kio sur trois octets, nombre de
 * secteurs sur un), puis chaque chunk : sa longueur, sa compression (1 gzip, 2 zlib, 3 aucune,
 * 4 LZ4 ; +128 quand il est trop gros et vit a cote, dans {@code c.X.Z.mcc}), ses donnees NBT.
 */
final class RegionReader {

    private static final int SECTOR = 4096;

    private final Path path;
    private final int regionX;
    private final int regionZ;
    private final byte[] bytes;
    private final int[] locations = new int[1024];

    RegionReader(Path path, int regionX, int regionZ) throws IOException {
        this.path = path;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.bytes = Files.readAllBytes(path);
        if (bytes.length < SECTOR) {
            return;
        }
        for (int index = 0; index < locations.length; index++) {
            locations[index] = readInt(index * 4);
        }
    }

    private int readInt(int at) {
        return (bytes[at] & 0xFF) << 24 | (bytes[at + 1] & 0xFF) << 16 | (bytes[at + 2] & 0xFF) << 8
                | bytes[at + 3] & 0xFF;
    }

    /** Vrai si le fichier porte ce chunk (coordonnees dans la region, de 0 a 31). */
    boolean has(int localX, int localZ) {
        return locations[(localX & 31) + (localZ & 31) * 32] != 0;
    }

    /** Les sections d'un chunk de la region, ou {@code null} s'il n'y est pas (ou se lit mal). */
    List<ChunkNbt.Section> sections(int localX, int localZ) {
        int location = locations[(localX & 31) + (localZ & 31) * 32];
        long sector = location >>> 8;
        int sectors = location & 0xFF;
        if (location == 0 || sector < 2 || sectors == 0) {
            return null;
        }
        long start = sector * SECTOR;
        if (start + 5 > bytes.length) {
            return null;
        }
        int at = (int) start;
        int length = readInt(at);
        if (length <= 1 || length > sectors * SECTOR) {
            return null;
        }
        int type = bytes[at + 4] & 0xFF;
        try {
            InputStream raw;
            if ((type & 128) != 0) {
                Path external = path.resolveSibling("c." + (regionX * 32 + (localX & 31)) + "."
                        + (regionZ * 32 + (localZ & 31)) + ".mcc");
                if (!Files.isRegularFile(external)) {
                    return null;
                }
                raw = new ByteArrayInputStream(Files.readAllBytes(external));
                type &= 127;
            } else {
                if (at + 4L + length > bytes.length) {
                    return null;
                }
                raw = new ByteArrayInputStream(bytes, at + 5, length - 1);
            }
            InputStream in = switch (type) {
                case 1 -> new GZIPInputStream(raw);
                case 2 -> new InflaterInputStream(raw);
                case 3 -> raw;
                case 4 -> lz4(raw);
                default -> null;
            };
            if (in == null) {
                return null;
            }
            try (DataInputStream stream = new DataInputStream(new BufferedInputStream(in, 16 * 1024))) {
                return ChunkNbt.sections(stream);
            }
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    /** La compression LZ4 du jeu (depuis 1.20.5), par sa bibliotheque, lue sans la nommer a la compilation. */
    private static InputStream lz4(InputStream raw) {
        try {
            return (InputStream) Class.forName("net.jpountz.lz4.LZ4BlockInputStream")
                    .getConstructor(InputStream.class).newInstance(raw);
        } catch (ReflectiveOperationException | LinkageError error) {
            return null;
        }
    }
}
