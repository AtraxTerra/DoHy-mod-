package fr.atraxterra.dohy.palamod;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Une culture du PalaMod.
 *
 * <p>Elle herite de {@link CropBlock} parce que la mecanique d'origine
 * <b>est</b> celle du ble vanilla : {@code BaseBlockCrops.func_149674_a} fait
 * exactement le meme calcul — lumiere au-dessus superieure ou egale a 9, puis
 * {@code random.nextInt((int)(25 / facteur) + 1) == 0} pour avancer d'un stade,
 * avec le meme bonus de voisinage sur la terre labouree. Reimplementer tout cela
 * aurait ete recopier du code vanilla avec des bugs en plus.
 *
 * <p><b>Le compromis sur le nombre de stades, et ce qu'on fait pour le payer.</b>
 * Le PalaMod donne 8 stades a l'aubergine et au cerfeuil, mais <b>10</b> au
 * kiwano et a l'orangeblue. En 26.2, {@code CropBlock} est cable sur
 * {@code AGE_7}, soit 8 stades, et n'expose plus de {@code getAgeProperty()}
 * surchargeable — la propriete d'age ne peut donc plus etre changee sans
 * reecrire la croissance entiere.
 *
 * <p>Les deux premieres cultures sont donc <b>exactes</b>. Pour les deux autres,
 * 8 stades au lieu de 10 les feraient murir 20 % plus vite, ce qui n'est pas
 * anodin : l'orangeblue est la seule source d'endium du jeu. On compense donc
 * sur la cadence plutot que sur le nombre d'etapes — {@link #growthChance} fait
 * sauter une partie des ticks de croissance, de sorte que le <i>temps total</i>
 * de maturation reste celui de Paladium. Le joueur voit huit etapes au lieu de
 * dix ; il attend aussi longtemps.
 */
public class PalaCropBlock extends CropBlock {

    /**
     * Part des ticks de croissance qui comptent, entre 0 et 1.
     *
     * <p>1 pour une culture a 8 stades d'origine : rien a corriger. 0,8 pour une
     * culture a 10 stades, puisque 8 / 0,8 = 10 attentes de stade.
     */
    private final float growthChance;

    public PalaCropBlock(Properties properties, float growthChance) {
        super(properties);
        this.growthChance = growthChance;
    }

    /**
     * Croissance ralentie pour les cultures qui avaient dix stades.
     *
     * <p>Le tirage est fait avant d'appeler la logique vanilla : un tick ignore
     * n'avance rien, exactement comme un tick ou le tirage vanilla echoue. La
     * distribution reste geometrique, seule son esperance change.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (growthChance < 1.0f && random.nextFloat() >= growthChance) {
            return;
        }
        super.randomTick(state, level, pos, random);
    }

    /**
     * Le codec du parent.
     *
     * <p>{@code codec()} sert a la serialisation pilotee par donnees, qui
     * n'intervient pas pour un bloc enregistre : l'instance vient du registre.
     * Rendre celui de {@link CropBlock} evite d'ecrire un codec qui ne serait
     * jamais lu, et le type declare est celui du parent pour que la surcharge
     * soit valide.
     */
    @Override
    public MapCodec<? extends CropBlock> codec() {
        return CropBlock.CODEC;
    }
}
