package fr.atraxterra.dohy.palamod.unlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Le rituel d'endium : la forme de la structure ({@code BlockEndiumTotem.isStructureValid}),
 * et les zombies qu'on y tue.
 *
 * <p>La structure, relevee au bytecode : le totem au centre ; quatre autels a
 * trois blocs, dans les quatre directions ; quarante poudres de perlimpinpin
 * en contour de croix grecque — les bouts des bras a cinq blocs (cinq poudres
 * chacun), les cotes des bras a deux blocs du centre. Tout sur le meme niveau.
 */
@EventBusSubscriber
public final class EndiumRitual {

    /** Le nombre de zombies a tuer ({@code bipush 25}). */
    public static final int KILLS = 25;
    /** La zone du rituel, de part et d'autre du totem ({@code bipush 10}). */
    public static final int REACH = 10;

    /** Sur un zombie du rituel : la position de son totem. */
    static final String TOTEM = "palamod_ritual_totem";
    static final String TAG = "palamod_ritual";

    /** Les autels, par rapport au totem. */
    public static final List<BlockPos> ALTARS = List.of(
            new BlockPos(-3, 0, 0), new BlockPos(3, 0, 0), new BlockPos(0, 0, -3), new BlockPos(0, 0, 3));

    /** Les quarante poudres, par rapport au totem. */
    public static final List<BlockPos> POWDERS = powders();

    private EndiumRitual() {
    }

    private static List<BlockPos> powders() {
        List<BlockPos> list = new ArrayList<>();
        // Les bouts des bras : x de -2 a 2 a z = +-5, et z de -2 a 2 a x = +-5.
        for (int i = -2; i <= 2; i++) {
            add(list, i, 5);
            add(list, i, -5);
            add(list, 5, i);
            add(list, -5, i);
        }
        // Les cotes des bras, de 2 a 4 depuis le centre (les coins a +-2 servent deux fois).
        for (int i = 2; i <= 4; i++) {
            add(list, -i, 2);
            add(list, -i, -2);
            add(list, i, 2);
            add(list, i, -2);
            add(list, 2, -i);
            add(list, -2, -i);
            add(list, 2, i);
            add(list, -2, i);
        }
        return List.copyOf(list);
    }

    private static void add(List<BlockPos> list, int x, int z) {
        BlockPos pos = new BlockPos(x, 0, z);
        if (!list.contains(pos)) {
            list.add(pos);
        }
    }

    /** Les poudres manquantes autour du totem (0 : le contour est ferme). */
    static int missingPowders(Level level, BlockPos totem) {
        int missing = 0;
        for (BlockPos offset : POWDERS) {
            if (!(level.getBlockState(totem.offset(offset)).getBlock() instanceof PerlinpinpinPowderBlock)) {
                missing++;
            }
        }
        return missing;
    }

    /** Les autels manquants autour du totem. */
    static int missingAltars(Level level, BlockPos totem) {
        int missing = 0;
        for (BlockPos offset : ALTARS) {
            if (!(level.getBlockEntity(totem.offset(offset)) instanceof SacrificeAltarBlockEntity)) {
                missing++;
            }
        }
        return missing;
    }

    /** Le joueur debout sur un autel, s'il y en a un ({@code getPlayerOfHotel}). */
    static Player standingOn(Level level, BlockPos altar) {
        AABB top = new AABB(altar.getX(), altar.getY() + 1.0d, altar.getZ(),
                altar.getX() + 1.0d, altar.getY() + 1.5d, altar.getZ() + 1.0d);
        for (Player player : level.getEntitiesOfClass(Player.class, top, Player::isAlive)) {
            if (!player.isSpectator()) {
                return player;
            }
        }
        return null;
    }

    /** Un autel porte-t-il son offrande : un joueur debout dessus, ou une pierre pleine ? */
    static boolean offered(Level level, BlockPos altar) {
        return level.getBlockEntity(altar) instanceof SacrificeAltarBlockEntity entity
                && (!entity.offering().isEmpty() || standingOn(level, altar) != null);
    }

    /** Un rituel en cours autour de cet autel : sa pierre ne se reprend plus. */
    static boolean runningAround(Level level, BlockPos altar) {
        for (BlockPos offset : ALTARS) {
            BlockPos totem = altar.subtract(offset);
            if (level.getBlockEntity(totem) instanceof EndiumTotemBlockEntity entity && entity.busy()) {
                return true;
            }
        }
        return false;
    }

    /** La zone du rituel ({@code AxisAlignedBB} de +-10 autour du totem). */
    static AABB area(BlockPos totem) {
        return new AABB(totem).inflate(REACH);
    }

    /** Un zombie du rituel tue par un joueur : un de plus au compteur de son totem. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie) || !zombie.entityTags().contains(TAG)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long packed = zombie.getPersistentData().getLongOr(TOTEM, Long.MIN_VALUE);
        if (packed == Long.MIN_VALUE) {
            return;
        }
        if (zombie.level().getBlockEntity(BlockPos.of(packed)) instanceof EndiumTotemBlockEntity totem) {
            totem.onKill(player);
        }
    }
}
