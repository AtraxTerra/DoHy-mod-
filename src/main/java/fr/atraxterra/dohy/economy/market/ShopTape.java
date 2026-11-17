package fr.atraxterra.dohy.economy.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHyAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le ruban du shop : combien de chaque objet s'est echange, heure par heure, sur
 * tout le serveur. Ce sont les barres de volume sous les courbes du shop.
 *
 * <p>Les heures sont celles du cours ({@link HourlyCourse#slot()}) : une barre tombe
 * sous chaque point de la courbe. Le ruban garde les {@link #HOURS} dernieres ; une
 * heure qui sort par la gauche est oubliee.
 *
 * <p>Tout ce qui vend au catalogue passe par {@link DynamicPrice#sell} (shop, coffre
 * de vente, vente automatique, poche du mineur, {@code /sell}) et y est compte ;
 * les achats sont comptes par le shop et le silo.
 */
public final class ShopTape {

    /** Les heures gardees : celles de la courbe. */
    public static final int HOURS = 24;

    private ShopTape() {
    }

    /** Une ligne du ruban : les volumes d'un objet, de la plus ancienne heure a {@code slot}. */
    public record Line(Identifier id, List<Long> volumes) {
        static final Codec<Line> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(Line::id),
                Codec.LONG.listOf().fieldOf("volumes").forGetter(Line::volumes)
        ).apply(instance, Line::new));
    }

    /**
     * Le ruban, sur le monde principal. Mutable, comme {@link PlayerMarket} : une
     * vente le touche a chaque fois, le recreer serait du gaspillage.
     */
    public static final class Tape {

        public static final MapCodec<Tape> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.LONG.optionalFieldOf("slot", 0L).forGetter(tape -> tape.slot),
                Line.CODEC.listOf().optionalFieldOf("lines", List.of()).forGetter(Tape::lines)
        ).apply(instance, Tape::new));

        /** L'heure de la derniere colonne. */
        private long slot;
        private final Map<Identifier, long[]> volumes = new HashMap<>();

        public Tape() {
        }

        private Tape(long slot, List<Line> lines) {
            this.slot = slot;
            for (Line line : lines) {
                long[] row = new long[HOURS];
                int offset = HOURS - Math.min(HOURS, line.volumes().size());
                for (int index = 0; index < HOURS - offset; index++) {
                    row[offset + index] = line.volumes().get(line.volumes().size() - (HOURS - offset) + index);
                }
                volumes.put(line.id(), row);
            }
        }

        private List<Line> lines() {
            List<Line> lines = new ArrayList<>();
            volumes.forEach((id, row) -> {
                List<Long> list = new ArrayList<>(HOURS);
                for (long value : row) {
                    list.add(value);
                }
                lines.add(new Line(id, list));
            });
            return lines;
        }

        public boolean isEmpty() {
            return volumes.isEmpty();
        }

        /** Fait glisser le ruban jusqu'a l'heure {@code now} ; les objets sans plus aucun echange sortent. */
        void roll(long now) {
            if (now <= slot) {
                return;
            }
            long shift = now - slot;
            slot = now;
            volumes.values().removeIf(row -> {
                if (shift >= HOURS) {
                    return true;
                }
                System.arraycopy(row, (int) shift, row, 0, HOURS - (int) shift);
                Arrays.fill(row, HOURS - (int) shift, HOURS, 0L);
                for (long value : row) {
                    if (value > 0L) {
                        return false;
                    }
                }
                return true;
            });
        }

        void add(Identifier id, long quantity) {
            volumes.computeIfAbsent(id, key -> new long[HOURS])[HOURS - 1] += quantity;
        }

        /** Les volumes alignes sur l'heure {@code now}, de la plus ancienne a la plus recente ; vide sans echange. */
        List<Long> volumes(Identifier id, long now) {
            long[] row = volumes.get(id);
            if (row == null || now - slot >= HOURS) {
                return List.of();
            }
            int shift = (int) Math.max(0L, now - slot);
            List<Long> list = new ArrayList<>(HOURS);
            boolean any = false;
            for (int index = 0; index < HOURS; index++) {
                int source = index + shift;
                long value = source < HOURS ? row[source] : 0L;
                any |= value > 0L;
                list.add(value);
            }
            return any ? list : List.of();
        }
    }

    private static Tape tape(MinecraftServer server) {
        return server.overworld().getData(DoHyAttachments.SHOP_TAPE.get());
    }

    /** Compte {@code quantity} exemplaires echanges maintenant (achat ou vente). */
    public static void record(MinecraftServer server, Identifier id, long quantity) {
        if (server == null || quantity <= 0L) {
            return;
        }
        ServerLevel level = server.overworld();
        Tape tape = tape(server);
        tape.roll(HourlyCourse.slot());
        tape.add(id, quantity);
        // setData plutot qu'une simple mutation : c'est lui qui marque le monde a sauvegarder.
        level.setData(DoHyAttachments.SHOP_TAPE.get(), tape);
    }

    /** Les volumes des {@link #HOURS} dernieres heures, alignes sur la courbe ; vide si rien ne s'est echange. */
    public static List<Long> volumes(MinecraftServer server, Identifier id) {
        if (server == null) {
            return List.of();
        }
        return tape(server).volumes(id, HourlyCourse.slot());
    }
}
