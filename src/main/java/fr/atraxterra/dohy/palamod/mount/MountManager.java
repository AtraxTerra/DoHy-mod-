package fr.atraxterra.dohy.palamod.mount;

import fr.atraxterra.dohy.palamod.golem.PalaGolem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * La logique de la monture equipee ({@code MountManager}, {@code CommandMount},
 * {@code SummonTask}, {@code UnsummonTask}).
 *
 * <p>Invoquer ou desinvoquer demande de rester <b>trois secondes</b> sans
 * bouger : un coup recu, un pas, ou (pour desinvoquer) descendre de la
 * monture annule. <b>Ecart</b> : l'original comptait sur un {@code Timer}
 * hors du fil du serveur et annulait au moindre ecart de position, meme d'un
 * millionieme ; ici, un compte de 60 ticks sur le fil du serveur, et une
 * tolerance d'un centieme de bloc.
 */
public final class MountManager {

    /** {@code SummonTask} : 3 000 ms. */
    private static final int COUNTDOWN_TICKS = 60;
    /** {@code FoodSlot.onSlotChanged} : +50 par croquette. */
    public static final int FOOD_PER_CROQUETTE = 50;

    private record Countdown(boolean summon, Vec3 start, int elapsed) {
        Countdown next() {
            return new Countdown(summon, start, elapsed + 1);
        }
    }

    private static final Map<UUID, Countdown> COUNTDOWNS = new HashMap<>();

    private MountManager() {
    }

    public static MountData data(ServerPlayer player) {
        return player.getData(PalaMount.MOUNT);
    }

    /** L'entite invoquee, si elle est chargee et vivante. */
    public static Dancarok summoned(ServerPlayer player) {
        UUID id = data(player).summoned();
        if (id == null) {
            return null;
        }
        Entity entity = ((ServerLevel) player.level()).getEntity(id);
        return entity instanceof Dancarok mount && mount.isAlive() ? mount : null;
    }

    // -- Invocation ------------------------------------------------------------

    /**
     * {@code /mount summon} : invoque, ou desinvoque si l'on est deja en selle.
     * Conditions dans l'ordre de l'original, chacune avec son message.
     */
    public static void requestSummon(ServerPlayer player) {
        MountData data = data(player);
        if (!data.isEquipped()) {
            MountTexts.error(player, "none_equipped");
            return;
        }
        if (data.level() < MountLevels.SUMMON_LEVEL) {
            MountTexts.error(player, "level_required", MountLevels.SUMMON_LEVEL);
            return;
        }
        if (data.isDead()) {
            MountTexts.error(player, "dead");
            return;
        }
        if (data.food() <= 0) {
            MountTexts.error(player, "hungry");
            return;
        }
        Dancarok mount = summoned(player);
        if (mount != null) {
            if (player.getVehicle() != mount) {
                MountTexts.error(player, "must_ride");
                return;
            }
            start(player, false);
            return;
        }
        // Ecart : l'original restait bloque jusqu'a la reconnexion quand
        // l'entite memorisee n'existait plus (chunk decharge). On l'oublie.
        data.setSummoned(null);
        start(player, true);
    }

    private static void start(ServerPlayer player, boolean summon) {
        COUNTDOWNS.put(player.getUUID(), new Countdown(summon, player.position(), 0));
        MountTexts.info(player, summon ? "summoning" : "unsummoning");
    }

    public static void cancel(ServerPlayer player) {
        if (COUNTDOWNS.remove(player.getUUID()) != null) {
            MountTexts.error(player, "countdown_cancelled");
        }
    }

    public static void tickCountdowns(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Countdown>> it = COUNTDOWNS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Countdown> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Countdown countdown = entry.getValue();
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            boolean moved = player.position().distanceToSqr(countdown.start()) > 1.0e-4d;
            boolean dismounted = !countdown.summon() && !(player.getVehicle() instanceof Dancarok);
            if (moved || dismounted) {
                it.remove();
                MountTexts.error(player, "countdown_cancelled");
                continue;
            }
            ServerLevel level = (ServerLevel) player.level();
            if (countdown.elapsed() % 2 == 0) {
                level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0d, player.getZ(),
                        30, 0.5d, 0.8d, 0.5d, 0.3d);
                level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0d, player.getZ(),
                        15, 0.5d, 0.8d, 0.5d, 0.3d);
            }
            if (countdown.elapsed() + 1 < COUNTDOWN_TICKS) {
                entry.setValue(countdown.next());
                continue;
            }
            it.remove();
            level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0d, player.getZ(),
                    100, 0.5d, 0.8d, 0.5d, 5.0d);
            if (countdown.summon()) {
                summon(player);
            } else {
                unsummon(player, true);
            }
        }
    }

    /**
     * {@code summonMount} : la monture apparait a la place du joueur, qui n'est
     * pas mis en selle. Les conditions sont reverifiees : trois secondes ont
     * passe.
     */
    private static void summon(ServerPlayer player) {
        MountData data = data(player);
        if (!data.isEquipped() || data.isDead() || data.food() <= 0) {
            return;
        }
        Dancarok old = summoned(player);
        if (old != null) {
            old.discard();
        }
        ServerLevel level = (ServerLevel) player.level();
        Dancarok mount = PalaMount.DANCAROK.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (mount == null) {
            return;
        }
        mount.setPos(player.getX(), player.getY(), player.getZ());
        mount.setYRot(player.getYRot());
        mount.setYHeadRot(player.getYRot());
        mount.setYBodyRot(player.getYRot());
        mount.bind(player);
        level.addFreshEntity(mount);
        data.setSummoned(mount.getUUID());
        level.playSound(null, mount.getX(), mount.getY(), mount.getZ(), PalaMount.MOUNT_SUMMON.get(),
                SoundSource.NEUTRAL, 0.4f, 1.0f);
        MountTexts.info(player, "summoned");
    }

    /** {@code unsummonMount} : rien a rendre ici, objets et corne n'ont jamais quitte le joueur. */
    public static void unsummon(ServerPlayer player, boolean announce) {
        COUNTDOWNS.remove(player.getUUID());
        MountData data = data(player);
        Dancarok mount = summoned(player);
        if (mount != null) {
            mount.ejectPassengers();
            mount.level().playSound(null, mount.getX(), mount.getY(), mount.getZ(), PalaMount.MOUNT_UNSUMMON.get(),
                    SoundSource.NEUTRAL, 0.4f, 1.0f);
            mount.discard();
        }
        boolean was = data.summoned() != null;
        data.setSummoned(null);
        if (announce && was) {
            MountTexts.info(player, "unsummoned");
        }
    }

    // -- PV, XP, nourriture ------------------------------------------------------

    /**
     * Les degats de la monture invoquee. A zero PV : l'inventaire accessible et
     * la corne tombent ou elle se tenait, et elle est desinvoquee.
     */
    public static void hurt(ServerPlayer player, Dancarok mount, float amount) {
        MountData data = data(player);
        data.setDamage(data.damage() + amount);
        if (!data.isDead()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        int slots = data.slots();
        for (int slot = 0; slot < slots; slot++) {
            drop(level, mount, data.inventory().get(slot));
            data.inventory().set(slot, ItemStack.EMPTY);
        }
        drop(level, mount, data.horn());
        data.setHorn(ItemStack.EMPTY);
        MountTexts.error(player, "dead");
        unsummon(player, true);
    }

    private static void drop(ServerLevel level, Entity at, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity item = new ItemEntity(level, at.getX(), at.getY() + 1.0d, at.getZ(), stack.copy());
        item.setPickUpDelay(10);
        level.addFreshEntity(item);
    }

    /** {@code incrementXP} : XP, plafond, message de niveau. */
    public static void addXp(ServerPlayer player, float amount) {
        MountData data = data(player);
        if (data.isEquipped() && amount > 0.0f && data.addXp(amount)) {
            MountTexts.info(player, "level_up", data.level());
        }
    }

    /**
     * Nourrir : une croquette, ou autant qu'il en faut pour remplir.
     *
     * <p><b>Ecart</b> : Mount consommait toute la pile posee dans la case, et
     * l'excedent au-dela de 10 000 etait perdu. Ici on ne prend que ce qui
     * sert.
     */
    public static void feed(ServerPlayer player, boolean fill) {
        MountData data = data(player);
        if (!data.isEquipped()) {
            MountTexts.error(player, "none_equipped");
            return;
        }
        if (data.food() >= MountData.MAX_FOOD) {
            MountTexts.error(player, "not_hungry");
            return;
        }
        int wanted = fill ? (MountData.MAX_FOOD - data.food() + FOOD_PER_CROQUETTE - 1) / FOOD_PER_CROQUETTE : 1;
        int taken = take(player.getInventory(), PalaGolem.CROQUETTE.get().getDefaultInstance(), wanted);
        if (taken == 0) {
            MountTexts.error(player, "no_croquette");
            return;
        }
        data.setFood(data.food() + taken * FOOD_PER_CROQUETTE);
        MountTexts.success(player, "fed", taken, data.food(), MountData.MAX_FOOD);
    }

    /** Retire jusqu'a {@code count} objets semblables des 36 cases ; rend le nombre pris. */
    public static int take(Inventory inventory, ItemStack like, int count) {
        int taken = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && taken < count; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(like.getItem())) {
                int moved = Math.min(count - taken, stack.getCount());
                stack.shrink(moved);
                taken += moved;
            }
        }
        if (taken > 0) {
            inventory.setChanged();
        }
        return taken;
    }

    public static int count(Inventory inventory, ItemStack like) {
        int total = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(like.getItem())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Donne l'objet, ou le pose aux pieds du joueur si son inventaire est plein. */
    public static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }
}
