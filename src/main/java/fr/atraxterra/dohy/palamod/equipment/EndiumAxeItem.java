package fr.atraxterra.dohy.palamod.equipment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hache en endium : abat l'arbre d'un coup, et ne casse jamais.
 *
 * <p>Releve dans {@code BaseItemAxe.onBlockDestroyed} et {@code breakTree},
 * quand le materiau est l'endium :
 * <ul>
 *   <li>une buche cassee abat toutes les buches qui la touchent — les 26
 *       voisines, de proche en proche, jusqu'a 15 pas — sans limite de nombre ;
 *       les feuilles restent, et pourrissent d'elles-memes ;</li>
 *   <li><b>une seule usure par arbre</b> : celle de la buche cassee ;</li>
 *   <li>l'usure est plafonnee : la hache ne disparait pas. A bout, elle ne fait
 *       plus qu'abattre ; elle coupe et frappe toujours. La reparer relance
 *       l'abattage.</li>
 * </ul>
 * Comme la pioche en endium, le plafond est un point sous le maximum : en 26.2
 * une pile disparait quand l'usure l'<i>atteint</i>.
 *
 * <p><b>Ecart</b> : chaque buche abattue passe par {@code destroyBlock}, et non
 * par une casse brute sans joueur. Les protections, les drops normaux et le
 * suivi des blocs poses voient donc chaque buche — l'original ne voyait que la
 * premiere. Une buche est ce que {@code #minecraft:logs} contient, comme
 * {@code isWood} etait vrai de tout {@code BlockLog}.
 */
public class EndiumAxeItem extends AxeItem {

    /** {@code if (depth > 15) return}. */
    private static final int MAX_DEPTH = 15;

    /** Vrai pendant l'abattage : les buches suivantes n'usent pas la hache. */
    private static boolean felling;

    public EndiumAxeItem(ToolMaterial material, float attackDamage, float attackSpeed, Properties properties) {
        super(material, attackDamage, attackSpeed, properties);
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        super.setDamage(stack, Math.min(damage, stack.getMaxDamage() - 1));
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (felling) {
            return false;
        }
        boolean fell = !level.isClientSide() && miner instanceof ServerPlayer player
                && state.is(BlockTags.LOGS) && stack.getDamageValue() < stack.getMaxDamage() - 1;
        boolean result = super.mineBlock(stack, level, state, pos, miner);
        if (fell) {
            felling = true;
            try {
                // La buche d'origine est deja retiree : on part de ses voisines.
                neighbours(level, (ServerPlayer) miner, pos, 1);
            } finally {
                felling = false;
            }
        }
        return result;
    }

    /** {@code breakTree} : parcours en profondeur, dans l'ordre dx, dy, dz. */
    private static void fell(Level level, ServerPlayer player, BlockPos pos, int depth) {
        if (depth > MAX_DEPTH || !level.getBlockState(pos).is(BlockTags.LOGS)) {
            return;
        }
        if (!player.gameMode.destroyBlock(pos)) {
            return;
        }
        neighbours(level, player, pos, depth + 1);
    }

    private static void neighbours(Level level, ServerPlayer player, BlockPos pos, int depth) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        fell(level, player, pos.offset(dx, dy, dz), depth);
                    }
                }
            }
        }
    }
}
