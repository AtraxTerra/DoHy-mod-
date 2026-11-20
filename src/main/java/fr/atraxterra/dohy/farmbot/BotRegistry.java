package fr.atraxterra.dohy.farmbot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les bots poses, tous joueurs confondus, gardes sur l'Overworld : qui les possede et ou ils sont.
 * C'est ce qui limite un joueur a {@link #MAX_PER_PLAYER} bots, et ce que liste {@code /bots}.
 *
 * <p>Un bot retire du jeu sans passer par sa mort (ce qui ne devrait pas arriver) laisserait une
 * ligne en trop : quand sa place est chargee et qu'il n'y est plus, la ligne s'efface
 * ({@link FarmBots#forgetMissing}).
 */
public record BotRegistry(Map<UUID, Place> bots) {

    public static final int MAX_PER_PLAYER = 4;

    /** Un bot : son proprietaire, sa dimension et sa derniere position connue. */
    public record Place(UUID owner, String dimension, BlockPos pos) {
        static final Codec<Place> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Place::owner),
                Codec.STRING.fieldOf("dimension").forGetter(Place::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Place::pos)
        ).apply(instance, Place::new));
    }

    public static final BotRegistry EMPTY = new BotRegistry(Map.of());

    public static final MapCodec<BotRegistry> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Place.CODEC)
            .optionalFieldOf("bots", Map.of()).xmap(BotRegistry::new, BotRegistry::bots);

    public BotRegistry {
        bots = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(bots));
    }

    public boolean isEmpty() {
        return bots.isEmpty();
    }

    /** Les bots d'un joueur, dans l'ordre ou il les a poses. */
    public List<Map.Entry<UUID, Place>> of(UUID owner) {
        List<Map.Entry<UUID, Place>> out = new ArrayList<>();
        for (Map.Entry<UUID, Place> entry : bots.entrySet()) {
            if (entry.getValue().owner().equals(owner)) {
                out.add(entry);
            }
        }
        return out;
    }

    BotRegistry with(UUID bot, Place place) {
        Map<UUID, Place> copy = new LinkedHashMap<>(bots);
        if (place == null) {
            copy.remove(bot);
        } else {
            copy.put(bot, place);
        }
        return new BotRegistry(copy);
    }

    static BotRegistry get(MinecraftServer server) {
        return server.overworld().getData(DoHyFarmBots.REGISTRY.get());
    }

    static void set(MinecraftServer server, BotRegistry registry) {
        ServerLevel overworld = server.overworld();
        overworld.setData(DoHyFarmBots.REGISTRY.get(), registry);
    }
}
