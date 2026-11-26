package fr.atraxterra.dohy.palamod.pet;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'apprivoisement (wiki « Java | Familiers ») : un mini-jeu proche de la peche,
 * en trois paliers de 100, 200 et 400 points. Le curseur parcourt la barre ; la
 * barre espace l'arrete : vert +50, jaune +25, rouge -50, noir -150.
 *
 * <p>Le wiki ne dit pas d'ou partent les points : chaque palier part ici de
 * {@value #START_POINTS}, pour qu'un premier essai rouge ne suffise pas a tout
 * perdre. Retombe a 0 ou moins au palier 1, la cage casse et le familier
 * s'enfuit ; aux paliers 2 et 3, on revient au palier d'avant. Deux minutes
 * separent deux paliers, et il faut revenir dans les sept minutes
 * ({@link PetCageBlockEntity}). Reussi : le familier est a toi, et 5 000 XP de
 * Chasseur.
 */
@EventBusSubscriber
public final class PetTaming {

    public static final int START_POINTS = 50;
    static final int[] TARGETS = {100, 200, 400};
    public static final int GREEN = 50;
    public static final int YELLOW = 25;
    public static final int RED = -50;
    public static final int BLACK = -150;
    public static final int GREEN_COLOR = 0x30CB1F;
    public static final int YELLOW_COLOR = 0xFAC400;
    public static final int RED_COLOR = 0xEF3926;
    /** Les zones se resserrent et le curseur accelere d'un palier a l'autre. */
    private static final float[] GREEN_WIDTH = {0.10f, 0.08f, 0.06f};
    private static final float[] YELLOW_WIDTH = {0.10f, 0.09f, 0.08f};
    private static final float[] RED_WIDTH = {0.14f, 0.13f, 0.12f};
    private static final float[] SPEED = {0.45f, 0.60f, 0.75f};
    /** La portee pour jouer : on reste pres de la cage. */
    private static final double REACH = 8.0d;

    private static final class Session {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        int round;
        List<TamingView.Zone> zones = List.of();

        Session(ResourceKey<Level> dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private PetTaming() {
    }

    public static int target(int stage) {
        return TARGETS[Math.max(1, Math.min(3, stage)) - 1];
    }

    /** Ouvre (ou reprend) le palier en cours de cette cage. */
    static void open(ServerPlayer player, PetCageBlockEntity cage) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.pos.equals(cage.getBlockPos())) {
            session = new Session(player.level().dimension(), cage.getBlockPos().immutable());
            SESSIONS.put(player.getUUID(), session);
        }
        show(player, session, cage, 0);
    }

    private static void show(ServerPlayer player, Session session, PetCageBlockEntity cage, int last) {
        int stage = Math.max(1, Math.min(3, cage.stage));
        RandomSource random = player.getRandom();
        session.round++;
        session.zones = layout(stage, random);
        Familiar familiar = Familiar.byId(cage.occupant);
        float speed = SPEED[stage - 1] + random.nextFloat() * 0.1f;
        UiNet.show(player, new TamingView(session.round, familiar == null ? "" : familiar.id(),
                familiar == null ? "?" : familiar.label(), stage, cage.points, target(stage), last, speed,
                session.zones));
    }

    /** Le rouge, le jaune puis le vert, l'un dans l'autre, a une place tiree au hasard. */
    static List<TamingView.Zone> layout(int stage, RandomSource random) {
        float green = GREEN_WIDTH[stage - 1];
        float yellow = YELLOW_WIDTH[stage - 1];
        float red = RED_WIDTH[stage - 1];
        float half = green / 2.0f + yellow + red;
        float center = half + 0.02f + random.nextFloat() * (1.0f - 2.0f * (half + 0.02f));
        List<TamingView.Zone> zones = new ArrayList<>();
        zones.add(new TamingView.Zone(RED, RED_COLOR, center - half, 2.0f * half));
        zones.add(new TamingView.Zone(YELLOW, YELLOW_COLOR, center - green / 2.0f - yellow, green + 2.0f * yellow));
        zones.add(new TamingView.Zone(GREEN, GREEN_COLOR, center - green / 2.0f, green));
        return zones;
    }

    /** Ce que rapporte un arret a {@code position} (0 a 1) : la plus etroite zone touchee, noir sinon. */
    static int value(List<TamingView.Zone> zones, float position) {
        TamingView.Zone hit = null;
        for (TamingView.Zone zone : zones) {
            if (position >= zone.start() && position <= zone.start() + zone.width()
                    && (hit == null || zone.width() < hit.width())) {
                hit = zone;
            }
        }
        return hit == null ? BLACK : hit.value();
    }

    /** L'appui sur la barre espace : {@code amount} est la position du curseur, en milliemes. */
    public static void handle(ServerPlayer player, UiNet.Action action) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !"stop".equals(action.verb())
                || !String.valueOf(session.round).equals(action.argument())) {
            return;
        }
        ServerLevel level = player.level().getServer().getLevel(session.dimension);
        if (level == null || !(level.getBlockEntity(session.pos) instanceof PetCageBlockEntity cage)
                || cage.occupant.isEmpty() || !cage.isOwner(player)) {
            end(player);
            return;
        }
        if (player.level() != level || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(session.pos)) > REACH * REACH) {
            end(player);
            player.sendSystemMessage(Component.literal("[Familier] Reste pres de la cage pour l'apprivoiser.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        float position = Math.max(0, Math.min(1000, action.amount())) / 1000.0f;
        int gained = value(session.zones, position);
        cage.points += gained;
        int stage = Math.max(1, Math.min(3, cage.stage));
        Familiar familiar = Familiar.byId(cage.occupant);
        String label = familiar == null ? "Le familier" : familiar.label();
        player.level().playSound(null, player.blockPosition(), gained > 0 ? SoundEvents.NOTE_BLOCK_CHIME.value()
                : SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 0.7f, gained > 0 ? 1.4f : 0.7f);
        if (cage.points >= target(stage)) {
            if (stage >= 3) {
                tame(player, cage, familiar);
                return;
            }
            end(player);
            cage.nextStage(stage + 1);
            player.sendSystemMessage(Component.literal("[Familier] Palier " + stage + " reussi ! " + label
                    + " se laisse approcher. Le palier " + (stage + 1) + " s'ouvre dans 2 minutes : tu auras "
                    + "ensuite 7 minutes pour revenir.").withStyle(ChatFormatting.GREEN));
            return;
        }
        if (cage.points <= 0) {
            end(player);
            if (stage == 1) {
                player.sendSystemMessage(Component.literal("[Familier] " + label + " s'affole et brise la cage.")
                        .withStyle(ChatFormatting.RED));
                cage.flee(true);
            } else {
                cage.nextStage(stage - 1);
                player.sendSystemMessage(Component.literal("[Familier] Rate ! " + label + " se mefie : retour au "
                        + "palier " + (stage - 1) + ", dans 2 minutes.").withStyle(ChatFormatting.RED));
            }
            return;
        }
        cage.sync();
        show(player, session, cage, gained);
    }

    private static void tame(ServerPlayer player, PetCageBlockEntity cage, Familiar familiar) {
        end(player);
        if (familiar == null || PetApi.has(player)) {
            cage.tamed();
            return;
        }
        cage.tamed();
        PetApi.tame(player, familiar);
        JobApi.grant(player, Job.HUNTER, PetApi.TAME_HUNTER_XP);
        player.sendSystemMessage(Component.literal("[Familier] Le familier a ete apprivoise ! " + familiar.label()
                + " te suit desormais (+" + PetApi.TAME_HUNTER_XP + " XP de Chasseur). /pet pour t'en occuper.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                1.0f, 1.2f);
    }

    private static void end(ServerPlayer player) {
        if (SESSIONS.remove(player.getUUID()) != null) {
            UiNet.close(player);
        }
    }

    /** La cage a disparu ou le familier s'est enfui : les parties en cours se ferment. */
    static void cancel(ServerLevel level, BlockPos pos) {
        for (UUID id : List.copyOf(SESSIONS.keySet())) {
            Session session = SESSIONS.get(id);
            if (session.pos.equals(pos) && session.dimension == level.dimension()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    end(player);
                } else {
                    SESSIONS.remove(id);
                }
            }
        }
    }

    /** Un joueur parti : sa partie est oubliee (la cage garde son palier). */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty() || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (UUID id : List.copyOf(SESSIONS.keySet())) {
            if (event.getServer().getPlayerList().getPlayer(id) == null) {
                SESSIONS.remove(id);
            }
        }
    }
}
