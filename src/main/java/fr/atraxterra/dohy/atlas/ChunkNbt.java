package fr.atraxterra.dohy.atlas;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le NBT binaire d'un chunk, lu sans le charger en entier : seulement ses sections, leur hauteur,
 * leur palette et leurs indices, ce que la carte demande. Le reste (lumiere, biomes, entites,
 * structures) est saute sans etre copie, et la lecture s'arrete des que les sections sont lues :
 * c'est l'essentiel du temps de rendu d'une region.
 *
 * <p>Le format : un compose racine (type, nom, entrees), chaque entree un type sur un octet, un nom
 * (longueur sur deux octets, UTF-8 modifie) et sa valeur ; une liste porte le type de ses elements et
 * leur nombre, un tableau sa longueur.
 */
final class ChunkNbt {

    private static final byte END = 0;
    private static final byte BYTE = 1;
    private static final byte SHORT = 2;
    private static final byte INT = 3;
    private static final byte LONG = 4;
    private static final byte FLOAT = 5;
    private static final byte DOUBLE = 6;
    private static final byte BYTE_ARRAY = 7;
    private static final byte STRING = 8;
    private static final byte LIST = 9;
    private static final byte COMPOUND = 10;
    private static final byte INT_ARRAY = 11;
    private static final byte LONG_ARRAY = 12;
    /** Au-dela, ce n'est pas un chunk lisible : on l'abandonne. */
    private static final int MAX_LENGTH = 1 << 24;
    private static final int MAX_DEPTH = 64;

    static final BlockState UNKNOWN = Blocks.STONE.defaultBlockState();
    /** Les etats de bloc deja lus, par nom et proprietes : les palettes se repetent d'un chunk a l'autre. */
    private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();

    /** Une section de 16 blocs de haut : sa palette et ses indices ({@code null} pour un seul etat). */
    record Section(int y, BlockState[] palette, long[] data) {
    }

    private ChunkNbt() {
    }

    /** Les sections du chunk ; une liste vide s'il n'en a pas. */
    static List<Section> sections(DataInputStream in) throws IOException {
        if (in.readByte() != COMPOUND) {
            throw new IOException("le chunk n'est pas un compose");
        }
        skipFully(in, in.readUnsignedShort());
        while (true) {
            byte type = in.readByte();
            if (type == END) {
                return List.of();
            }
            String name = in.readUTF();
            if (type == LIST && name.equals("sections")) {
                return sectionList(in);
            }
            skip(in, type, 1);
        }
    }

    private static List<Section> sectionList(DataInputStream in) throws IOException {
        byte element = in.readByte();
        int count = length(in.readInt());
        if (element != COMPOUND) {
            skipElements(in, element, count, 2);
            return List.of();
        }
        List<Section> sections = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Section section = section(in);
            if (section != null) {
                sections.add(section);
            }
        }
        return sections;
    }

    private static Section section(DataInputStream in) throws IOException {
        boolean hasY = false;
        int y = 0;
        BlockState[] palette = null;
        long[] data = null;
        while (true) {
            byte type = in.readByte();
            if (type == END) {
                break;
            }
            String name = in.readUTF();
            if (type == BYTE && name.equals("Y")) {
                y = in.readByte();
                hasY = true;
            } else if (type == COMPOUND && name.equals("block_states")) {
                while (true) {
                    byte inner = in.readByte();
                    if (inner == END) {
                        break;
                    }
                    String field = in.readUTF();
                    if (inner == LIST && field.equals("palette")) {
                        palette = palette(in);
                    } else if (inner == LONG_ARRAY && field.equals("data")) {
                        data = longs(in);
                    } else {
                        skip(in, inner, 3);
                    }
                }
            } else {
                skip(in, type, 2);
            }
        }
        return !hasY || palette == null || palette.length == 0 ? null : new Section(y, palette, data);
    }

    private static BlockState[] palette(DataInputStream in) throws IOException {
        byte element = in.readByte();
        int count = length(in.readInt());
        if (element != COMPOUND) {
            skipElements(in, element, count, 4);
            return null;
        }
        BlockState[] states = new BlockState[count];
        StringBuilder key = new StringBuilder(64);
        List<String> properties = new ArrayList<>(8);
        for (int index = 0; index < count; index++) {
            states[index] = entry(in, key, properties);
        }
        return states;
    }

    /** Un etat de la palette : {@code {Name: "...", Properties: {cle: "valeur", ...}}}. */
    private static BlockState entry(DataInputStream in, StringBuilder key, List<String> properties)
            throws IOException {
        String block = null;
        properties.clear();
        while (true) {
            byte type = in.readByte();
            if (type == END) {
                break;
            }
            String field = in.readUTF();
            if (type == STRING && field.equals("Name")) {
                block = in.readUTF();
            } else if (type == COMPOUND && field.equals("Properties")) {
                while (true) {
                    byte inner = in.readByte();
                    if (inner == END) {
                        break;
                    }
                    String property = in.readUTF();
                    if (inner == STRING) {
                        properties.add(property);
                        properties.add(in.readUTF());
                    } else {
                        skip(in, inner, 6);
                    }
                }
            } else {
                skip(in, type, 5);
            }
        }
        if (block == null) {
            return UNKNOWN;
        }
        key.setLength(0);
        key.append(block);
        for (int index = 0; index + 1 < properties.size(); index += 2) {
            key.append(index == 0 ? '[' : ',').append(properties.get(index)).append('=').append(properties.get(index + 1));
        }
        if (!properties.isEmpty()) {
            key.append(']');
        }
        String cacheKey = key.toString();
        BlockState known = STATES.get(cacheKey);
        if (known != null) {
            return known;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", block);
        if (!properties.isEmpty()) {
            CompoundTag values = new CompoundTag();
            for (int index = 0; index + 1 < properties.size(); index += 2) {
                values.putString(properties.get(index), properties.get(index + 1));
            }
            tag.put("Properties", values);
        }
        // Un bloc inconnu (d'un mod retire) se voit comme de la pierre.
        BlockState parsed = BlockState.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(UNKNOWN);
        if (STATES.size() < 65_536) {
            STATES.put(cacheKey, parsed);
        }
        return parsed;
    }

    private static long[] longs(DataInputStream in) throws IOException {
        int count = length(in.readInt());
        byte[] bytes = new byte[count * 8];
        in.readFully(bytes);
        long[] values = new long[count];
        ByteBuffer.wrap(bytes).asLongBuffer().get(values);
        return values;
    }

    // -- Ce qui se saute ---------------------------------------------------------------------------------

    private static void skip(DataInputStream in, byte type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT trop profond");
        }
        switch (type) {
            case BYTE -> skipFully(in, 1);
            case SHORT -> skipFully(in, 2);
            case INT, FLOAT -> skipFully(in, 4);
            case LONG, DOUBLE -> skipFully(in, 8);
            case BYTE_ARRAY -> skipFully(in, length(in.readInt()));
            case STRING -> skipFully(in, in.readUnsignedShort());
            case INT_ARRAY -> skipFully(in, (long) length(in.readInt()) * 4L);
            case LONG_ARRAY -> skipFully(in, (long) length(in.readInt()) * 8L);
            case LIST -> {
                byte element = in.readByte();
                skipElements(in, element, length(in.readInt()), depth + 1);
            }
            case COMPOUND -> {
                while (true) {
                    byte inner = in.readByte();
                    if (inner == END) {
                        break;
                    }
                    skipFully(in, in.readUnsignedShort());
                    skip(in, inner, depth + 1);
                }
            }
            default -> throw new IOException("type NBT inconnu : " + type);
        }
    }

    private static void skipElements(DataInputStream in, byte element, int count, int depth) throws IOException {
        if (count == 0) {
            return;
        }
        switch (element) {
            case BYTE -> skipFully(in, count);
            case SHORT -> skipFully(in, count * 2L);
            case INT, FLOAT -> skipFully(in, count * 4L);
            case LONG, DOUBLE -> skipFully(in, count * 8L);
            default -> {
                for (int index = 0; index < count; index++) {
                    skip(in, element, depth);
                }
            }
        }
    }

    private static void skipFully(DataInputStream in, long bytes) throws IOException {
        long left = bytes;
        while (left > 0) {
            int step = (int) Math.min(left, Integer.MAX_VALUE);
            int skipped = in.skipBytes(step);
            if (skipped <= 0) {
                in.readByte();
                skipped = 1;
            }
            left -= skipped;
        }
    }

    private static int length(int value) throws IOException {
        if (value < 0 || value > MAX_LENGTH) {
            throw new IOException("longueur NBT impossible : " + value);
        }
        return value;
    }
}
