package fr.atraxterra.dohy.palamod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La terre fertilisee, et sa version labouree.
 *
 * <p>Deux blocs pour une seule classe, comme dans le PalaMod ou
 * {@code BlockConnectedDirt} prend un booleen {@code tilled}. La houe fait
 * passer de l'un a l'autre — voir {@link PalaSoilEvents}.
 *
 * <p><b>Ce qu'elle fait vraiment.</b> Le wiki n'en dit rien ; le mecanisme est
 * dans {@code BlockConnectedDirt.func_149674_a}. A chaque tick aleatoire, le
 * bloc regarde la plante au-dessus de lui et <b>lui fait passer deux ticks de
 * croissance supplementaires</b>. La plante continue par ailleurs de recevoir
 * les siens : elle avance donc a peu pres <b>trois fois</b> plus vite.
 *
 * <p>S'y ajoute {@code isFertile} qui rend toujours vrai : la culture touche le
 * bonus de terre labouree <b>irriguee sans aucune eau a proximite</b>. En 26.2,
 * {@code CropBlock.getGrowthSpeed} donne 3 points a un sol fertile, 1 a un sol
 * sec et 0 a tout le reste, en deux temps :
 * <ul>
 *   <li>le sol doit etre dans le tag {@code minecraft:grows_crops}. NeoForge
 *       demande bien {@code canSustainPlant}, mais en passant le sol lui-meme
 *       comme plante : ce bloc ne peut pas reconnaitre une culture et rend
 *       {@code DEFAULT}. La version labouree est donc ajoutee au tag par
 *       {@code tools/palamod_assets.py} ;</li>
 *   <li>puis {@code isFertile}, qui par defaut ne rend vrai que pour une
 *       {@code FarmlandBlock} humide.</li>
 * </ul>
 * Il faut les deux. Avant, la terre fertilisee valait 0 : moins qu'une terre
 * labouree seche.
 *
 * <p><b>Les deux variantes accelerent</b>, pas seulement la labouree : la
 * version brute nourrit aussi les pousses d'arbre et les fleurs posees dessus.
 * C'est le comportement d'origine, la methode n'etant pas conditionnee au
 * booleen.
 */
public class PalaFertileSoilBlock extends Block {

    /**
     * Ticks de croissance offerts a la plante au-dessus, par tick aleatoire.
     *
     * <p>Deux, comme la boucle {@code for (i = 0; i < 2; i++)} du PalaMod. Avec
     * le tick que la plante recoit deja par elle-meme, cela fait trois.
     */
    private static final int EXTRA_TICKS = 2;

    /** Effet vanilla des particules d'os, joue quand la plante peut pousser. */
    private static final int BONEMEAL_PARTICLES = 2005;

    private final boolean tilled;

    public PalaFertileSoilBlock(Properties properties, boolean tilled) {
        super(properties);
        this.tilled = tilled;
    }

    /**
     * Pousse la plante posee dessus.
     *
     * <p>On passe par {@code BlockState.randomTick}, qui est public, et non par
     * la methode {@code randomTick} du bloc, qui est protegee et donc
     * inaccessible depuis une autre classe. C'est d'ailleurs par la que le
     * serveur tique lui-meme les blocs.
     *
     * <p>L'etat est relu a chaque tour de boucle : la plante peut avoir change
     * de bloc entre deux ticks — une pousse qui devient un arbre, par exemple —
     * et continuer a tiquer l'ancien etat ferait pousser un fantome.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        BlockState plant = level.getBlockState(above);
        if (!(plant.getBlock() instanceof BonemealableBlock growable)) {
            return;
        }

        if (growable.isValidBonemealTarget(level, above, plant)) {
            level.levelEvent(BONEMEAL_PARTICLES, above, 0);
        }

        for (int tick = 0; tick < EXTRA_TICKS; tick++) {
            BlockState current = level.getBlockState(above);
            if (!(current.getBlock() instanceof BonemealableBlock) || !current.isRandomlyTicking()) {
                return;
            }
            current.randomTick(level, above, random);
        }
    }

    /**
     * Toujours irriguee, sans eau.
     *
     * <p>C'est le {@code isFertile} du PalaMod, qui rendait {@code true} sans
     * condition. {@code CropBlock.getGrowthSpeed} l'interroge pour chacun des
     * neuf blocs sous la culture et ses voisines : sol fertile, 3 points ; sol
     * qui porte la plante mais sec, 1 point. Les tiges de melon et de citrouille
     * passent par le meme calcul et en profitent aussi.
     *
     * <p>Les deux variantes repondent oui, comme l'original. Pour la brute ce
     * oui n'a aucun effet : elle n'est pas dans {@code grows_crops}, et
     * {@code getGrowthSpeed} ne consulte {@code isFertile} que sur un sol qui
     * l'est.
     */
    @Override
    public boolean isFertile(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /**
     * Ce qui peut pousser dessus.
     *
     * <p>Les cultures verifient normalement que le bloc du dessous <i>est</i> de
     * la terre labouree vanilla, ce qu'aucun tag ne peut contourner. D'ou ce
     * crochet, que NeoForge a conserve : la variante labouree repond oui pour
     * une culture, la variante brute repond non.
     *
     * <p>Le reste rend {@code DEFAULT}, donc la regle vanilla s'applique : la
     * terre fertilisee brute est dans le tag {@code minecraft:dirt}, ce qui
     * suffit aux pousses d'arbre, aux fleurs et a l'herbe sans une ligne de
     * code de plus.
     */
    @Override
    public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos pos,
                                    Direction facing, BlockState plant) {
        if (facing != Direction.UP) {
            return TriState.DEFAULT;
        }
        if (!(plant.getBlock() instanceof CropBlock)) {
            return TriState.DEFAULT;
        }
        return tilled ? TriState.TRUE : TriState.FALSE;
    }
}
