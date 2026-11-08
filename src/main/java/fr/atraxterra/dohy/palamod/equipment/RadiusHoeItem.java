package fr.atraxterra.dohy.palamod.equipment;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * Une houe qui laboure un carre d'un seul clic.
 *
 * <p>Relevee dans <b>PalaJobs</b>, pas dans PalaMod : les houes appartiennent au
 * metier de fermier ({@code ItemRadiusHoe(durabilite, rayon, nom)}), ce qui
 * explique qu'elles soient absentes du registre principal des outils.
 *
 * <p>La geometrie est exactement celle des seedplanters, et ce n'est pas un
 * hasard — on laboure le carre qu'on plantera :
 * {@code half = (int)(radius / 2.0)}, boucle de {@code -half} inclus a
 * {@code half} exclu, donc un carre de cote {@code half * 2}, decale d'un
 * demi-bloc. Les cinq houes et les cinq planteurs vont par paires.
 *
 * <p><b>Une seule durabilite par clic, pas par case.</b> C'est l'original, et
 * ca change tout : la houe en endium laboure 144 cases pour 1 de durabilite.
 * Appeler le labour vanilla case par case l'aurait consommee 144 fois — d'ou le
 * passage direct par {@code getToolModifiedState}.
 *
 * <p>Ce passage a un second avantage : chaque case emet le
 * {@code BlockToolModificationEvent} de NeoForge. C'est lui que
 * {@code PalaSoilEvents} ecoute pour transformer la terre fertilisee en terre
 * fertilisee labouree. La houe a rayon laboure donc la terre fertilisee sans une
 * ligne de plus ici — et tout ce qu'un autre mod accrocherait au labour vanilla
 * marche aussi.
 */
public class RadiusHoeItem extends HoeItem {

    /** Le {@code radius} d'origine. Le carre laboure fait {@code (radius / 2) * 2}. */
    private final int radius;

    public RadiusHoeItem(ToolMaterial material, int radius, Properties properties) {
        // Les decalages de la houe en diamant de 26.2 : attaque nulle, vitesse
        // normale. Une houe n'est pas une arme, Paladium ne la traitait pas
        // autrement.
        super(material, -3.0f, 0.0f, properties);
        this.radius = radius;
    }

    public int radius() {
        return radius;
    }

    /** Cote du carre laboure, en blocs. */
    public int side() {
        return (radius / 2) * 2;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos origin = context.getClickedPos();

        if (player == null || !canStartFrom(level.getBlockState(origin), context)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        int half = radius / 2;
        int tilled = 0;
        for (int dx = -half; dx < half; dx++) {
            for (int dz = -half; dz < half; dz++) {
                if (till(level, player, context, origin.offset(dx, 0, dz))) {
                    tilled++;
                }
            }
        }

        if (tilled == 0) {
            return InteractionResult.PASS;
        }
        level.playSound(null, origin, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        // La surcharge qui prend la main, relevee a la sonde :
        // LivingEntity.getSlotForHand n'existe pas en 26.2.
        context.getItemInHand().hurtAndBreak(1, player, context.getHand());
        return InteractionResult.SUCCESS;
    }

    /**
     * Le bloc vise doit etre labourable — ou deja laboure.
     *
     * <p>L'original exige de l'herbe, de la terre ou du sol laboure. Accepter le
     * sol laboure permet de cliquer au milieu d'un champ pour en agrandir le
     * carre. On demande au labour lui-meme plutot que de lister les blocs : c'est
     * ce qui fait que la terre fertilisee est acceptee aussi, sans cas
     * particulier.
     */
    private boolean canStartFrom(BlockState state, UseOnContext context) {
        if (state.is(Blocks.FARMLAND) || state.is(PalaBlocks.FERTILIZED_FARMLAND.get())) {
            return true;
        }
        return state.getToolModifiedState(context, ItemAbilities.HOE_TILL, true) != null;
    }

    /** Laboure une case si elle peut l'etre et qu'il y a de l'air au-dessus. */
    private static boolean till(Level level, Player player, UseOnContext origin, BlockPos pos) {
        if (!level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        UseOnContext here = new UseOnContext(player, origin.getHand(),
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        BlockState tilled = state.getToolModifiedState(here, ItemAbilities.HOE_TILL, false);
        if (tilled == null || tilled == state) {
            return false;
        }
        level.setBlockAndUpdate(pos, tilled);
        return true;
    }
}
