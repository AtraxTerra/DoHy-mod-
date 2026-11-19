package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * « Labyrinthe » ({@code Labyrinthe}) : un labyrinthe d'obsidienne de 16 cases sur
 * 16 a la hauteur 100, ferme de tous cotes, et la fatigue de minage X sans fin.
 *
 * <p>Comme chez PalaMod : sol en 99, couloirs de deux blocs en 100 et 101, plafond en
 * 102 ; les passages sont creuses par une marche au hasard qui ouvre le mur vers chaque
 * case visitee pour la premiere fois (au plus 10 000 pas). Il n'y a pas de sortie, et les
 * commandes sont refusees ({@code onCommand}). On s'en sort en buvant du lait puis en
 * creusant, en se reconnectant ou en mourant ({@code onDeath}).
 *
 * <p><b>Ecart</b> : le joueur arrive au milieu d'une case, quand PalaMod le posait dans un
 * mur, sous le plafond.
 */
@EventBusSubscriber
public final class LuckyMaze {

    /** {@code min = -8, max = 8}. */
    private static final int HALF = 8;
    private static final int FLOOR = 99;
    private static final int CEILING = 102;
    /** {@code 10000} : les pas de la marche au hasard. */
    private static final int MAX_STEPS = 10000;
    /** {@code new PotionEffect(digSlowdown, 999999999, 9)}. */
    private static final int FATIGUE_AMPLIFIER = 9;
    private static final Set<UUID> INSIDE = ConcurrentHashMap.newKeySet();

    private LuckyMaze() {
    }

    static void build(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        BlockState wall = Blocks.OBSIDIAN.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        // Le plafond : 32 x 32, de x - 16 a x + 15.
        LuckyWorld.box(player, level, new BlockPos(x, CEILING, z), 2 * HALF * 2, 1, 2 * HALF * 2, wall, null);
        // Les cubes de 3 x 3 x 3 des cases, qui se touchent : un pave plein de x - 16 a x + 16.
        LuckyWorld.box(player, level, new BlockPos(x, FLOOR + 1, z), 4 * HALF + 1, 3, 4 * HALF + 1, wall, null);
        for (int i = -HALF; i < HALF; i++) {
            for (int j = -HALF; j < HALF; j++) {
                open(player, level, cell(x, i), cell(z, j), air);
            }
        }
        // La marche au hasard : la case de depart est (0, 0).
        RandomSource random = level.getRandom();
        List<Long> visited = new ArrayList<>();
        int ci = 0;
        int cj = 0;
        visited.add(key(ci, cj));
        int total = (2 * HALF) * (2 * HALF);
        for (int step = 0; visited.size() < total && step < MAX_STEPS; step++) {
            List<int[]> neighbours = new ArrayList<>(4);
            for (int[] offset : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int ni = ci + offset[0];
                int nj = cj + offset[1];
                if (ni >= -HALF && ni < HALF && nj >= -HALF && nj < HALF) {
                    neighbours.add(new int[] {ni, nj});
                }
            }
            int[] next = neighbours.get(random.nextInt(neighbours.size()));
            if (!visited.contains(key(next[0], next[1]))) {
                visited.add(key(next[0], next[1]));
                // Le mur entre les deux cases, a mi-chemin de leurs centres.
                open(player, level, (cell(x, ci) + cell(x, next[0])) / 2, (cell(z, cj) + cell(z, next[1])) / 2, air);
            }
            ci = next[0];
            cj = next[1];
        }
        player.teleportTo(level, cell(x, 0) + 0.5d, FLOOR + 1, cell(z, 0) + 0.5d, Set.of(), player.getYRot(),
                player.getXRot(), false);
        player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, MobEffectInstance.INFINITE_DURATION,
                FATIGUE_AMPLIFIER));
        INSIDE.add(player.getUUID());
    }

    /** Le centre d'une case : {@code x + i * 2 + 1}. */
    private static int cell(int origin, int index) {
        return origin + index * 2 + 1;
    }

    private static long key(int i, int j) {
        return ((long) i << 32) | (j & 0xFFFFFFFFL);
    }

    /** Deux blocs d'air, en 100 et 101. */
    private static void open(ServerPlayer player, ServerLevel level, int x, int z, BlockState air) {
        LuckyWorld.place(player, level, new BlockPos(x, FLOOR + 1, z), air);
        LuckyWorld.place(player, level, new BlockPos(x, FLOOR + 2, z), air);
    }

    public static boolean inside(ServerPlayer player) {
        return INSIDE.contains(player.getUUID());
    }

    /** {@code onCommand} : « la triche n'est point autorisee ». */
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player
                && INSIDE.contains(player.getUUID())) {
            LuckyWorld.tell(player, "Puisse le sort vous etre favorable, mais ici dans ce labyrinthe, la triche n'est"
                    + " point autorisee !", ChatFormatting.RED);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            INSIDE.remove(player.getUUID());
        }
    }

    /** Chez PalaMod, la liste tenait l'objet joueur : apres une reconnexion, les commandes revenaient. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        INSIDE.remove(event.getEntity().getUUID());
    }
}
