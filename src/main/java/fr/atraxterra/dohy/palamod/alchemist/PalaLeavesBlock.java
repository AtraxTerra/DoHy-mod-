package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.OptionalInt;

/**
 * Les feuilles des quatre essences de PalaMod : de vraies feuilles, qui tombent
 * quand plus aucune buche ne les tient a six blocs (et vite, avec un mod comme
 * Leaves Be Gone, qui ne traite que les {@link LeavesBlock}). Posees a la main,
 * elles restent, comme toutes les feuilles.
 *
 * <p><b>Feuilles d'avant ce changement</b> : elles etaient sauvegardees sans
 * aucune propriete, et reviennent donc a l'etat par defaut. Le defaut est ici
 * « distance 1, non persistante » : elles tiennent tant que rien ne bouge
 * autour (sinon tous les arbres deja pousses se seraient effeuilles d'un coup),
 * et des qu'une buche ou une feuille voisine change, la vraie distance se
 * recalcule de proche en proche ; un feuillage sans tronc finit alors par
 * tomber. Celles posees a la main avant ce changement ne se distinguent pas
 * des autres : loin d'une buche, elles tombent aussi si on touche leur voisine.
 *
 * <p><b>Diagonales</b> : les patrons de PalaMod sont clairsemes, et un
 * cinquieme de leurs feuilles ne touche ni buche ni feuille par une face —
 * avec le calcul vanilla (faces seules), elles tomberaient des la pousse. Ici,
 * la distance passe par les 26 voisins, coins compris, et ne compte que les
 * buches et les feuilles de la meme essence : toutes les feuilles des deux
 * patrons sont alors a une ou deux cases d'une buche.
 *
 * <p>Une feuille sur cent qui pend dans le vide lache une particule de feuille
 * a la couleur de l'essence, comme les feuilles vanilla.
 */
public class PalaLeavesBlock extends LeavesBlock {
    public static final MapCodec<PalaLeavesBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("particle_color").forGetter(block -> block.particleColor),
            propertiesCodec()).apply(instance, PalaLeavesBlock::new));

    /** Couleur des particules qui tombent (0xRRGGBB), la teinte moyenne de la texture. */
    private final int particleColor;

    public PalaLeavesBlock(int particleColor, Properties properties) {
        super(0.01f, properties);
        this.particleColor = particleColor;
        registerDefaultState(defaultBlockState().setValue(DISTANCE, 1));
    }

    @Override
    public MapCodec<PalaLeavesBlock> codec() {
        return CODEC;
    }

    /**
     * Le recalcul vanilla de la distance (faces seules), refait sur les 26
     * voisins. Les voisins en diagonale ne recoivent aucune mise a jour quand
     * cette feuille change : ils sont prevenus a la main, sans quoi un
     * feuillage sans tronc ne finirait pas de tomber.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState updated = state.setValue(DISTANCE, distance(level, pos));
        if (updated == state) {
            return;
        }
        level.setBlock(pos, updated, Block.UPDATE_ALL);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) >= 2
                            && level.getBlockState(at.setWithOffset(pos, dx, dy, dz)).is(this)) {
                        level.scheduleTick(at.immutable(), this, 1);
                    }
                }
            }
        }
    }

    /** 1 contre une buche, sinon un de plus que la plus proche feuille de la meme essence ; 7 = tombe. */
    public int distance(BlockGetter level, BlockPos pos) {
        int best = 7;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockState near = level.getBlockState(at.setWithOffset(pos, dx, dy, dz));
                    OptionalInt distance = getOptionalDistanceAt(near);
                    if (distance.isEmpty()) {
                        continue;
                    }
                    if (distance.getAsInt() == 0) {
                        return 1;
                    }
                    if (near.is(this)) {
                        best = Math.min(best, distance.getAsInt() + 1);
                    }
                }
            }
        }
        return best;
    }

    @Override
    protected void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random) {
        ParticleUtils.spawnParticleBelow(level, pos, random,
                ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF000000 | particleColor));
    }
}
