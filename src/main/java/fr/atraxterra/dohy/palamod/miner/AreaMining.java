package fr.atraxterra.dohy.palamod.miner;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * La casse en 3x3 des excavatrices et de la pioche en endium.
 *
 * <p>Releve dans {@code ToolHandler.removeBlocksInIteration} et
 * {@code removeBlockWithDrops} :
 * <ul>
 *   <li>le plan est perpendiculaire a la face visee, lue par un lancer de rayon
 *       depuis les yeux du joueur ;</li>
 *   <li>un bloc du plan n'est casse que s'il est <b>au plus aussi dur</b> que le
 *       bloc vise, et d'une durete positive — la bedrock ne part jamais ;</li>
 *   <li>il faut pouvoir le recolter avec l'outil, et qu'il soit de la famille de
 *       l'outil ;</li>
 *   <li>l'obsidienne est exclue : sur un serveur de faction, une pioche qui
 *       ouvre neuf blocs d'obsidienne d'un coup ouvrirait les bases.</li>
 * </ul>
 *
 * <p><b>Une seule usure par clic</b>, comme dans l'original : les blocs du plan ne
 * passent pas par {@code onBlockDestroyed}, seul le bloc vise use l'outil.
 * Ici, chaque bloc du plan passe par {@code destroyBlock} — c'est ce qui donne
 * les drops, l'XP de Mineur, la protection des zones et le suivi des blocs
 * poses — et l'outil ignore l'usure pendant que {@link #isBreakingExtras()} est
 * vrai.
 *
 * <p>Priorite basse et evenements annules ignores : un verrou de metier ou une
 * protection qui refuse le bloc vise refuse aussi le plan.
 */
@EventBusSubscriber
public final class AreaMining {

    /** Portee du lancer de rayon de PalaMod ({@code raytraceFromEntity(..., 10.0)}). */
    private static final double REACH = 10.0d;

    /** Blocs qu'aucun outil de zone ne casse. Obsidienne et obsidienne pleureuse. */
    public static final TagKey<Block> IMMUNE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "area_mining_immune"));

    /**
     * Vrai pendant la casse du plan. {@code destroyBlock} refait passer chaque
     * bloc par {@link BreakBlockEvent} : sans ce verrou, chaque bloc du plan
     * relancerait un plan autour de lui.
     */
    private static boolean breaking;

    private AreaMining() {
    }

    /** Lu par les outils de zone pour ne pas s'user sur les blocs du plan. */
    public static boolean isBreakingExtras() {
        return breaking;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreak(BreakBlockEvent event) {
        if (breaking || event.isCanceled()) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof AreaTool area) || !area.areaActive(tool)) {
            return;
        }

        BlockPos center = event.getPos();
        Direction face = face(player, center);
        if (face == null) {
            return;
        }
        float centerHardness = event.getState().getDestroySpeed(level, center);

        breaking = true;
        try {
            for (BlockPos cursor : plane(center, face)) {
                // betweenClosed rend une position mutable reutilisee : on la
                // fige avant de la confier a destroyBlock et aux evenements.
                BlockPos target = cursor.immutable();
                if (target.equals(center)) {
                    continue;
                }
                BlockState state = level.getBlockState(target);
                if (state.isAir() || state.is(IMMUNE) || !area.breaksInArea(state)) {
                    continue;
                }
                float hardness = state.getDestroySpeed(level, target);
                if (hardness < 0 || hardness > centerHardness) {
                    continue;
                }
                if (!player.hasCorrectToolForDrops(state)) {
                    continue;
                }
                player.gameMode.destroyBlock(target);
                if (tool.isEmpty()) {
                    return;
                }
            }
        } finally {
            breaking = false;
        }
    }

    /** La face visee, ou {@code null} si le rayon ne touche pas ce bloc. */
    private static Direction face(ServerPlayer player, BlockPos center) {
        HitResult hit = player.pick(REACH, 1.0f, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && block.getBlockPos().equals(center)) {
            return block.getDirection();
        }
        return null;
    }

    /** Les neuf positions du plan perpendiculaire a la face. */
    private static Iterable<BlockPos> plane(BlockPos center, Direction face) {
        Direction.Axis axis = face.getAxis();
        int dx = axis == Direction.Axis.X ? 0 : 1;
        int dy = axis == Direction.Axis.Y ? 0 : 1;
        int dz = axis == Direction.Axis.Z ? 0 : 1;
        return BlockPos.betweenClosed(center.offset(-dx, -dy, -dz), center.offset(dx, dy, dz));
    }
}
