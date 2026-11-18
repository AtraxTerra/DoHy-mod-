"""Temporaire, pour l'essai : le port de Diesel Generators relit le petrole des chunks tel que
la version Adapted l'ecrivait (une table plate "x,z" -> mB, sans liste OilChunks) et l'ecrit
de la meme facon.

Usage : python3 -I ci/cdg_legacy_oil.py <clone de Mod-ported, branche port/cdg-neoforge-26.2>
"""
import pathlib
import sys

path = pathlib.Path(sys.argv[1]) / 'src/main/java/com/jesz/createdieselgenerators/world/OilChunksSavedData.java'
text = path.read_text(encoding='utf-8')


def swap(old, new):
    global text
    if text.count(old) != 1:
        raise SystemExit('%r trouve %d fois' % (old[:60], text.count(old)))
    text = text.replace(old, new)


swap('import com.mojang.serialization.Codec;\n',
     'import com.mojang.datafixers.util.Pair;\n'
     'import com.mojang.serialization.Codec;\n'
     'import com.mojang.serialization.DataResult;\n'
     'import com.mojang.serialization.DynamicOps;\n')

swap('''    public static final Codec<OilChunksSavedData> CODEC = ChunkAmount.CODEC.listOf()
        .optionalFieldOf("OilChunks", List.of())
        .xmap(OilChunksSavedData::fromList, OilChunksSavedData::toList)
        .codec();
''', '''    private static final Codec<OilChunksSavedData> LIST_CODEC = ChunkAmount.CODEC.listOf()
        .optionalFieldOf("OilChunks", List.of())
        .xmap(OilChunksSavedData::fromList, OilChunksSavedData::toList)
        .codec();

    // Port: "Create: Diesel Generators Adapted", the NeoForge 26.2 build this port replaces, saved
    // the chunks as one flat map, "x,z" -> mB, with no "OilChunks" list. A world coming from it is
    // read in that layout, and written back the same way, so the amounts survive the swap.
    private static final Codec<OilChunksSavedData> MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
        .xmap(OilChunksSavedData::fromMap, OilChunksSavedData::toMap);

    public static final Codec<OilChunksSavedData> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<OilChunksSavedData, T>> decode(DynamicOps<T> ops, T input) {
            boolean list = ops.getMap(input).result().map(map -> map.get("OilChunks") != null).orElse(false);
            return (list ? LIST_CODEC : MAP_CODEC).decode(ops, input);
        }

        @Override
        public <T> DataResult<T> encode(OilChunksSavedData input, DynamicOps<T> ops, T prefix) {
            return MAP_CODEC.encode(input, ops, prefix);
        }
    };
''')

swap('''    public static OilChunksSavedData load(ServerLevel level) {''', '''    private static OilChunksSavedData fromMap(Map<String, Integer> map) {
        OilChunksSavedData data = new OilChunksSavedData();
        map.forEach((key, amount) -> {
            int comma = key.indexOf(',');
            if (comma < 0)
                return;
            try {
                data.chunks.put(new ChunkPos(Integer.parseInt(key.substring(0, comma).trim()),
                    Integer.parseInt(key.substring(comma + 1).trim())), amount);
            } catch (NumberFormatException ignored) {
            }
        });
        return data;
    }

    private Map<String, Integer> toMap() {
        Map<String, Integer> map = new HashMap<>(chunks.size());
        chunks.forEach((pos, amount) -> map.put(pos.x() + "," + pos.z(), amount));
        return map;
    }

    public static OilChunksSavedData load(ServerLevel level) {''')

path.write_text(text, encoding='utf-8')
print('OilChunksSavedData : table "x,z" lue et ecrite')
