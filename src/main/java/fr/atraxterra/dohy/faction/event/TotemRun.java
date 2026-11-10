package fr.atraxterra.dohy.faction.event;

import fr.atraxterra.dohy.palamod.boss.RivalFighter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le Totem (lot 22f, wiki « Java | Totem »), le mercredi a 21 h.
 *
 * <ul>
 *   <li>On descend un long escalier jusqu'a une grotte ; au centre, le totem,
 *       qu'il faut casser <b>bloc par bloc, du sommet a la base</b>.</li>
 *   <li>Un bloc casse se porte jusqu'a la zone de depot, sans mourir : il faut
 *       y rester <b>20 s</b> d'affilee (en sortir remet le compte a zero).</li>
 *   <li><b>1 point par seconde</b> passee dans la zone avec un bloc, <b>500
 *       points</b> par bloc depose.</li>
 *   <li>Mort du porteur, ou temps de port ecoule : le bloc retourne sur le
 *       totem.</li>
 *   <li>10 000 ELO repartis selon le rang aux points.</li>
 * </ul>
 *
 * <p>Le wiki ne donne ni la duree, ni la hauteur du totem, ni le temps de port :
 * ici trente minutes, douze blocs (l'event finit plus tot s'ils sont tous
 * deposes) et 90 s pour deposer.
 *
 * <p><b>Solo</b> : les combattants rivaux viennent casser le totem eux aussi ;
 * celui qui porte un bloc brille, ne se bat plus et file vers la zone. Le tuer
 * renvoie son bloc sur le totem.
 */
public final class TotemRun extends EventRun {

    static final int DURATION = 36_000;
    static final int CARRY_LIMIT = 1_800;
    static final int DEPOSIT_TICKS = 400;
    static final float DEPOSIT_POINTS = 500.0f;
    /** Ticks au pied du totem avant qu'un rival n'en arrache un bloc. */
    private static final int RIVAL_BREAK_TICKS = 60;

    /** Un bloc en route : a qui il compte, depuis quand, et le temps passe dans la zone. */
    private static final class Carry {
        final String key;
        final int since;
        int inZone;

        Carry(String key, int since) {
            this.key = key;
            this.since = since;
        }
    }

    private final Map<UUID, Carry> carriers = new LinkedHashMap<>();
    private final Map<UUID, Integer> digging = new HashMap<>();
    private int height = EventArena.TOTEM_HEIGHT;
    private int deposited;

    private TotemRun(RandomSource random) {
        super(FactionEventKind.TOTEM, random);
        bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Totem"), BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.NOTCHED_12);
    }

    static TotemRun start(MinecraftServer server) {
        ServerLevel level = EventArena.level(server);
        if (level == null) {
            return null;
        }
        EventArena.buildTotemArena(level);
        EventArena.hold(level, EventArena.TOTEM_CENTER, true);
        EventArena.setTotem(level, EventArena.TOTEM_HEIGHT, true);
        TotemRun run = new TotemRun(level.getRandom());
        run.wave(level);
        return run;
    }

    @Override
    protected BlockPos center() {
        return EventArena.TOTEM_CENTER;
    }

    @Override
    public Vec3 entry() {
        return EventArena.totemEntry();
    }

    @Override
    protected Vec3 gate(int index) {
        return EventArena.totemGate(index);
    }

    @Override
    public float entryPitch() {
        return 35.0f;
    }

    @Override
    public String title() {
        return "Totem";
    }

    @Override
    public Item icon() {
        return Items.TOTEM_OF_UNDYING;
    }

    @Override
    protected String unit() {
        return "points";
    }

    @Override
    protected boolean pvp() {
        return true;
    }

    @Override
    public float progress(MinecraftServer server) {
        return height / (float) EventArena.TOTEM_HEIGHT;
    }

    @Override
    public List<String> status(ServerPlayer player, MinecraftServer server) {
        int rank = rankOf(player);
        Carry mine = carriers.get(player.getUUID());
        return List.of(ended() ? "Termine" : height + " / " + EventArena.TOTEM_HEIGHT + " blocs · reste "
                        + clock(DURATION - ticks),
                mine != null ? "Tu portes un bloc !" : rank > 0 ? "Ta faction : " + (rank == 1 ? "1re" : rank + "e")
                        + " · " + Math.round(scoreOf(player)) + " points" : "Pas encore de points");
    }

    @Override
    protected void onFinish(ServerLevel level) {
        EventArena.setTotem(level, 0, false);
    }

    // -- Le totem --------------------------------------------------------------

    /**
     * Un joueur casse un bloc de la Warzone. Rend vrai si c'est le totem : la
     * casse est alors annulee et geree ici (le bloc du sommet part avec lui).
     */
    boolean onBreak(ServerPlayer player, BlockPos pos) {
        int index = pos.getY() - EventArena.TOTEM_CENTER.getY();
        if (pos.getX() != EventArena.TOTEM_CENTER.getX() || pos.getZ() != EventArena.TOTEM_CENTER.getZ()
                || index < 1 || index > EventArena.TOTEM_HEIGHT) {
            return false;
        }
        if (ended()) {
            return true;
        }
        if (index != height) {
            player.sendSystemMessage(Component.literal("Le totem se casse par le haut.").withStyle(ChatFormatting.RED));
            return true;
        }
        if (carriers.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Tu portes deja un bloc : depose-le d'abord.")
                    .withStyle(ChatFormatting.RED));
            return true;
        }
        take((ServerLevel) player.level(), player);
        player.sendSystemMessage(Component.literal("Tu portes un bloc du totem : tiens 20 s dans la zone de depot, "
                + "au nord. 90 s pour y arriver, sans mourir.").withStyle(ChatFormatting.GOLD));
        return true;
    }

    private void take(ServerLevel level, LivingEntity carrier) {
        level.setBlock(EventArena.totemBlockPos(height), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
        height--;
        carriers.put(carrier.getUUID(), new Carry(keyOf(carrier), ticks));
        carrier.addEffect(new MobEffectInstance(MobEffects.GLOWING, CARRY_LIMIT, 0));
        if (carrier instanceof RivalFighter fighter) {
            fighter.setCarrying(true);
        }
        level.playSound(null, EventArena.totemBlockPos(height + 1), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 0.6f, 1.6f);
    }

    /** Le bloc retourne au sommet du totem. */
    private void putBack(ServerLevel level) {
        if (height < EventArena.TOTEM_HEIGHT) {
            height++;
            level.setBlock(EventArena.totemBlockPos(height), EventArena.totemBlock(height), Block.UPDATE_ALL);
        }
    }

    private static boolean inDeposit(Entity entity) {
        Vec3 zone = Vec3.atBottomCenterOf(EventArena.TOTEM_DEPOSIT.above());
        double dx = entity.getX() - zone.x;
        double dz = entity.getZ() - zone.z;
        return dx * dx + dz * dz <= EventArena.TOTEM_DEPOSIT_RADIUS * EventArena.TOTEM_DEPOSIT_RADIUS
                && Math.abs(entity.getY() - zone.y) <= 3.0d;
    }

    private Entity carrier(ServerLevel level, UUID id) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        if (player != null) {
            return player.level() == level ? player : null;
        }
        return level.getEntity(id);
    }

    @Override
    protected void tickRunning(ServerLevel level) {
        if (ticks >= DURATION) {
            conclude(level.getServer(), "Le Totem est termine : " + deposited + " bloc(s) depose(s).");
            return;
        }
        if (ticks % 20 != 0) {
            return;
        }
        // Les porteurs : zone, depot, ou bloc perdu.
        Iterator<Map.Entry<UUID, Carry>> it = carriers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Carry> entry = it.next();
            Carry carry = entry.getValue();
            Entity entity = carrier(level, entry.getKey());
            boolean alive = entity instanceof LivingEntity living && living.isAlive();
            if (!alive || ticks - carry.since > CARRY_LIMIT) {
                putBack(level);
                it.remove();
                if (entity instanceof ServerPlayer player) {
                    player.removeEffect(MobEffects.GLOWING);
                    player.sendSystemMessage(Component.literal("Trop tard : le bloc retourne sur le totem.")
                            .withStyle(ChatFormatting.RED));
                } else if (entity instanceof RivalFighter fighter) {
                    fighter.setCarrying(false);
                }
                continue;
            }
            if (!inDeposit(entity)) {
                carry.inZone = 0;
                continue;
            }
            carry.inZone += 20;
            addScore(carry.key, 1.0f);
            if (carry.inZone >= DEPOSIT_TICKS) {
                addScore(carry.key, DEPOSIT_POINTS);
                deposited++;
                it.remove();
                ((LivingEntity) entity).removeEffect(MobEffects.GLOWING);
                if (entity instanceof RivalFighter fighter) {
                    fighter.setCarrying(false);
                }
                level.playSound(null, entity.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                        1.0f, 1.2f);
                for (ServerPlayer player : level.players()) {
                    player.sendSystemMessage(Component.literal(nameOf(carry.key) + " depose un bloc du totem (+500).")
                            .withStyle(ChatFormatting.GOLD));
                }
            } else if (entity instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("Depot : " + carry.inZone / 20 + " / 20 s")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
        }

        // Les rivaux : au totem, ou a la zone s'ils portent un bloc.
        Vec3 totem = Vec3.atBottomCenterOf(EventArena.TOTEM_CENTER.above());
        Vec3 deposit = Vec3.atBottomCenterOf(EventArena.TOTEM_DEPOSIT.above());
        for (RivalFighter fighter : fighters(level)) {
            if (carriers.containsKey(fighter.getUUID())) {
                fighter.setObjective(deposit);
                continue;
            }
            double angle = (fighter.getId() % 8) * Math.PI / 4.0d;
            fighter.setObjective(totem.add(Math.cos(angle) * 1.6d, 0.0d, Math.sin(angle) * 1.6d));
            double dx = fighter.getX() - totem.x;
            double dz = fighter.getZ() - totem.z;
            if (dx * dx + dz * dz > 6.25d || height <= 0) {
                digging.remove(fighter.getUUID());
                continue;
            }
            int dug = digging.merge(fighter.getUUID(), 20, Integer::sum);
            if (dug >= RIVAL_BREAK_TICKS && !factionCarrying("r:" + fighter.rival())) {
                digging.remove(fighter.getUUID());
                take(level, fighter);
            }
        }

        if (height <= 0 && carriers.isEmpty()) {
            conclude(level.getServer(), "Le totem est tombe : les " + EventArena.TOTEM_HEIGHT
                    + " blocs sont deposes.");
            return;
        }
        // La zone de depot se voit de loin.
        for (int i = 0; i < 20; i++) {
            double angle = Math.PI * 2.0d * i / 20.0d;
            level.sendParticles(ParticleTypes.WAX_ON, deposit.x + Math.cos(angle) * EventArena.TOTEM_DEPOSIT_RADIUS,
                    deposit.y + 0.2d, deposit.z + Math.sin(angle) * EventArena.TOTEM_DEPOSIT_RADIUS,
                    1, 0.0d, 0.05d, 0.0d, 0.0d);
        }
        bar.setProgress(progress(level.getServer()));
        String leader = leader();
        bar.setName(Component.literal("Totem · " + height + "/" + EventArena.TOTEM_HEIGHT + " blocs · "
                + clock(DURATION - ticks) + (leader == null ? "" : " · en tete : " + nameOf(leader))));
    }

    /** Une faction rivale ne porte qu'un bloc a la fois. */
    private boolean factionCarrying(String key) {
        for (Carry carry : carriers.values()) {
            if (key.equals(carry.key)) {
                return true;
            }
        }
        return false;
    }
}
