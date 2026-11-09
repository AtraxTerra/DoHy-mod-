package fr.atraxterra.dohy.palamod.mount;

import fr.atraxterra.dohy.palamod.miner.AreaMining;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Le coup de corne du Dancarok ({@code CSMountMovement} 3, {@code ToolHandler}).
 *
 * <p>Monte, le clic droit maintenu donne un coup toutes les 500 ms au plus,
 * quelle que soit la corne. Un rayon de 10 blocs part des yeux de la monture
 * dans sa direction (le lacet du cavalier, la moitie de son tangage) ; s'il
 * touche un bloc de pioche ou de pelle (minerai de redstone compris) ou de
 * l'obsidienne, la zone casse. Elle est <b>ancree sur la monture</b>, pas sur
 * le bloc vise, et commence a hauteur de ses pattes — jamais vers le bas :
 * {@code (2s+1) x (s+2) x 2} blocs, soit 7x5x2, 9x6x2 et 11x7x2 pour les cornes
 * en amethyste, titane et paladium. Chaque coup coute {@code (2s+1)(s+2)}
 * points de nourriture (35, 54, 77) et une utilisation de corne.
 *
 * <p>Les blocs tombent sans condition d'outil, avec leur XP, sans toucher de
 * soie ni fusion. L'obsidienne (et l'obsidienne pleureuse, comme pour les
 * excavatrices) ne part jamais.
 *
 * <p><b>Ecarts</b> : chaque bloc passe par l'evenement de casse — protections,
 * verrous de metier et suivi des blocs poses s'appliquent, la ou l'original
 * construisait l'evenement sans jamais l'envoyer ; les blocs a block entity
 * (fours, coffres en pierre, spawners...) sont epargnes ; un coup qui ne casse
 * rien n'use pas la corne et ne coute rien ; un coup refuse faute de
 * nourriture le dit au lieu de laisser la jauge passer sous zero.
 */
public final class HornMining {

    /** {@code now - lastDiggingTick < 500} : dix ticks. */
    private static final int COOLDOWN_TICKS = 10;
    private static final double REACH = 10.0d;

    private static final Map<UUID, Long> LAST_STRIKE = new HashMap<>();

    private HornMining() {
    }

    public static void strike(ServerPlayer player) {
        if (!(player.getVehicle() instanceof Dancarok mount) || !player.getUUID().equals(mount.owner())) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Long last = LAST_STRIKE.get(player.getUUID());
        // now < last : un autre monde, a l'horloge moins avancee, a ete ouvert.
        if (last != null && now >= last && now - last < COOLDOWN_TICKS) {
            return;
        }
        LAST_STRIKE.put(player.getUUID(), now);
        mount.markStrike();

        MountData data = MountManager.data(player);
        ItemStack horn = data.horn();
        MountHornItem.Tier tier = MountHornItem.tierOf(horn);
        if (tier == null) {
            MountTexts.error(player, "no_horn");
            return;
        }

        Vec3 look = mount.getLookAngle();
        Vec3 eye = mount.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(REACH)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, mount));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockState target = level.getBlockState(hit.getBlockPos());
        if (!mineable(target) && !target.is(AreaMining.IMMUNE)) {
            return;
        }

        int size = tier.size();
        int cost = (2 * size + 1) * (size + 2);
        if (data.food() < cost) {
            MountTexts.error(player, "not_enough_energy", cost);
            return;
        }

        // Le centre : la position de la monture, un pas dans sa direction.
        // Ecart : floor plutot que la troncature vers zero de l'original, qui
        // decalait la zone d'un bloc en coordonnees negatives.
        int x0 = Mth.floor(mount.getX() + look.x);
        int y0 = Mth.floor(mount.getY());
        int z0 = Mth.floor(mount.getZ() + look.z);
        Direction.Axis axis = hit.getDirection().getAxis();
        BlockPos from;
        BlockPos to;
        if (axis == Direction.Axis.Z) {
            from = new BlockPos(x0 - size, y0, z0 - 1);
            to = new BlockPos(x0 + size, y0 + size + 1, z0);
        } else if (axis == Direction.Axis.X) {
            from = new BlockPos(x0 - 1, y0, z0 - size);
            to = new BlockPos(x0, y0 + size + 1, z0 + size);
        } else {
            from = new BlockPos(x0 - size, y0, z0 - size);
            to = new BlockPos(x0 + size, y0 + 1, z0 + size);
        }

        int[] broken = {0};
        AreaMining.withoutArea(() -> {
            for (BlockPos cursor : BlockPos.betweenClosed(from, to)) {
                BlockPos pos = cursor.immutable();
                if (breakBlock(level, player, pos)) {
                    broken[0]++;
                }
            }
        });
        if (broken[0] == 0) {
            return;
        }

        data.setFood(data.food() - cost);
        level.playSound(null, mount.getX(), mount.getY(), mount.getZ(), PalaMount.DANCAROK_DESTROY.get(),
                SoundSource.NEUTRAL, 0.4f, 1.0f);
        int wear = horn.getDamageValue() + 1;
        if (wear >= horn.getMaxDamage()) {
            data.setHorn(ItemStack.EMPTY);
            MountTexts.error(player, "horn_broken");
        } else {
            horn.setDamageValue(wear);
            if (wear + 50 >= horn.getMaxDamage()) {
                MountTexts.warning(player, "horn_worn");
            }
        }
    }

    /**
     * {@code isToolEffective("pickaxe" | "shovel")}. L'original ajoutait le
     * minerai de redstone, qui n'avait pas d'outil en 1.7.10 ; il est dans le
     * tag de la pioche en 26.2.
     */
    private static boolean mineable(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    private static boolean breakBlock(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !mineable(state) || state.is(AreaMining.IMMUNE)
                || state.getDestroySpeed(level, pos) < 0.0f || state.hasBlockEntity()) {
            return false;
        }
        if (CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled()) {
            return false;
        }
        // destroyBlock lache aussi l'XP du bloc (CommonHooks.handleBlockDrops).
        return level.destroyBlock(pos, true, player);
    }
}
