package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.faction.ClaimMap;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ce que l'atlas sait des claims (`ClaimMap`) : les chunks de ta nation, dimension par dimension,
 * ceux des autres que le serveur connait (ta base ennemie, la place forte contre toi, les nations
 * des autres joueurs), ta limite, et la reponse a ta derniere demande.
 */
public final class AtlasClaims {

    /** La reponse reste affichee ce temps-la, en secondes. */
    static final double TOAST_SECONDS = 4.5d;

    private static ClaimMap.State state;
    private static Map<String, Set<Long>> mine = Map.of();
    private static String toast = "";
    private static boolean toastOk;
    private static double toastAt = -100.0d;

    private AtlasClaims() {
    }

    static void accept(ClaimMap.State received) {
        Map<String, Set<Long>> chunks = new HashMap<>();
        for (ClaimMap.Mine claim : received.mine()) {
            if (claim.dimension() >= 0 && claim.dimension() < received.dimensions().size()) {
                chunks.computeIfAbsent(received.dimensions().get(claim.dimension()), key -> new HashSet<>())
                        .add(key(claim.x(), claim.z()));
            }
        }
        state = received;
        mine = chunks;
    }

    static void accept(ClaimMap.Result result) {
        toast = result.text();
        toastOk = result.ok();
        toastAt = Anim.now();
    }

    /** Un message de l'atlas lui-meme (une demande refusee avant d'etre envoyee). */
    static void tell(String text, boolean ok) {
        toast = text;
        toastOk = ok;
        toastAt = Anim.now();
    }

    static void clear() {
        state = null;
        mine = Map.of();
        toast = "";
        toastAt = -100.0d;
    }

    /** Un chunk en une cle : x dans les 32 bits du haut, z dans ceux du bas. */
    static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static boolean known() {
        return state != null;
    }

    static boolean nation() {
        return state != null && state.nation();
    }

    static String name() {
        return state == null ? "" : state.name();
    }

    static int count() {
        return state == null ? 0 : state.count();
    }

    static int cap() {
        return state == null ? 0 : state.cap();
    }

    /** La portee de la carte, en chunks autour du tien. */
    static int radius() {
        return state == null ? ClaimMap.RADIUS : state.radius();
    }

    static boolean mine(String dimension, int x, int z) {
        Set<Long> chunks = mine.get(dimension);
        return chunks != null && chunks.contains(key(x, z));
    }

    /** Les chunks de ta nation dans cette dimension (cles de {@link #key}). */
    static Set<Long> mine(String dimension) {
        return mine.getOrDefault(dimension, Set.of());
    }

    static List<ClaimMap.Zone> zones() {
        return state == null ? List.of() : state.zones();
    }

    /** La zone d'un autre qui couvre ce chunk, ou {@code null}. */
    static ClaimMap.Zone zone(String dimension, int x, int z) {
        for (ClaimMap.Zone zone : zones()) {
            if (zone.contains(dimension, x, z)) {
                return zone;
            }
        }
        return null;
    }

    static String toast() {
        return toast;
    }

    static boolean toastOk() {
        return toastOk;
    }

    /** Depuis combien de secondes la reponse est la. */
    static double toastAge() {
        return Anim.now() - toastAt;
    }

    /** Demande au serveur de revendiquer (ou de rendre) les chunks d'un rectangle. */
    static void request(String dimension, int x1, int z1, int x2, int z2, boolean claim) {
        ClientPacketDistributor.sendToServer(new ClaimMap.Request(dimension, x1, z1, x2, z2, claim));
    }
}
