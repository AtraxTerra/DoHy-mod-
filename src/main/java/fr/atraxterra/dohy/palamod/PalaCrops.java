package fr.atraxterra.dohy.palamod;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Les quatre cultures du PalaMod.
 *
 * <p>Elles ne donnent pas seulement un fruit : <b>a maturite, chacune a une
 * petite chance de lacher un lingot a la place</b>. C'est le mecanisme central
 * des cultures de Paladium, et il n'est <i>nulle part</i> sur le wiki — il a ete
 * lu dans {@code BaseBlockCrops.getDrops}, ou un tirage sur 10 000 decide entre
 * le lingot et le fruit :
 *
 * <table>
 *   <tr><th>Culture</th><th>Chance</th><th>Lot rare</th></tr>
 *   <tr><td>Aubergine</td><td>8 %</td><td>lingot d'amethyste</td></tr>
 *   <tr><td>Cerfeuil</td><td>5 %</td><td>lingot de titane</td></tr>
 *   <tr><td>Kiwano</td><td>2 %</td><td>lingot de paladium</td></tr>
 *   <tr><td>Orangeblue</td><td>0,25 %</td><td>pepite d'endium</td></tr>
 * </table>
 *
 * <p>Les chances descendent quand la valeur du lot monte, et les verrous de
 * metier suivent : Fermier 35, 60, 80, 100. L'agriculture est donc une voie
 * d'acces aux metaux a part entiere, parallele au minage — ce qui explique
 * pourquoi le crusher est verrouille sur le Fermier et non sur le Mineur.
 *
 * <p><b>Une culture immature ne lache rien du tout</b>, pas meme sa graine
 * ({@code func_149745_a} rend 0 et {@code BlockBush} ne donne rien). C'est plus
 * dur que le ble vanilla, et c'est reproduit tel quel : casser une pousse par
 * erreur coute la graine, et c'est ce qui donne du poids au totem de fertilite.
 */
public final class PalaCrops {

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(PalaMod.NAMESPACE);

    /**
     * Cultures a 8 stades d'origine : rien a compenser, la croissance est
     * exactement celle de Paladium.
     */
    public static final DeferredBlock<Block> EGGPLANT_CROP = crop("eggplant_crop", 1.0f);
    public static final DeferredBlock<Block> CHERVIL_CROP = crop("chervil_crop", 1.0f);

    /**
     * Cultures a 10 stades d'origine, ramenees a 8 par {@code AGE_7} : la
     * cadence est ralentie pour que le temps total de maturation ne bouge pas.
     * Voir {@link PalaCropBlock}.
     */
    public static final DeferredBlock<Block> KIWANO_CROP = crop("kiwano_crop", 0.8f);
    public static final DeferredBlock<Block> ORANGEBLUE_CROP = crop("orangeblue_crop", 0.8f);

    /** Dans l'ordre des verrous de metier, donc de la progression du joueur. */
    public static final List<DeferredBlock<Block>> ORDERED =
            List.of(EGGPLANT_CROP, CHERVIL_CROP, KIWANO_CROP, ORANGEBLUE_CROP);

    private PalaCrops() {
    }

    private static DeferredBlock<Block> crop(String name, float growthChance) {
        return BLOCKS.registerBlock(name,
                properties -> new PalaCropBlock(properties, growthChance),
                properties -> properties
                        // Les proprietes du ble vanilla. noCollision s'ecrit
                        // avec un seul « s » en 26.2 : le noCollission
                        // historique etait une faute de frappe, corrigee.
                        .noCollision()
                        .randomTicks()
                        .instabreak()
                        .sound(SoundType.CROP)
                        .pushReaction(PushReaction.DESTROY));
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
