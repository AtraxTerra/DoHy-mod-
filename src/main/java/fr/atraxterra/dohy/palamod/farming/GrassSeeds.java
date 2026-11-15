package fr.atraxterra.dohy.palamod.farming;

import fr.atraxterra.dohy.job.JobGate;
import fr.atraxterra.dohy.palamod.PalaCrops;
import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.List;

/**
 * Les graines de PalaMod dans l'herbe.
 *
 * <p>Releve dans {@code EventHandlerAlchemist.FarmerDropHandler} : a chaque herbe
 * cassee ({@code Blocks.tallgrass} en 1.7.10, l'herbe et la fougere d'aujourd'hui),
 * cinq tirages independants de {@code (int) (Math.random() * 10000)}, chacun garde
 * par le verrou de la culture ({@code JobsBridge.canUseCraft}) :
 * <ul>
 *   <li>aubergine : tirage {@code <= 2000}, soit 20 % ;</li>
 *   <li>cerfeuil : {@code <= 2000}, 20 % ;</li>
 *   <li>kiwano : {@code <= 25}, 0,26 % ;</li>
 *   <li>orangeblue : {@code <= 5}, 0,06 % ({@code <= 7} avec la permission
 *       {@code palamod.boostseedorangeblue} du serveur, qu'un joueur seul n'a pas) ;</li>
 *   <li>trois graines de cristal de glace : {@code <= 1000}, 10 %, sans verrou —
 *       une culture d'evenement que DoHy n'a pas, donc pas portee.</li>
 * </ul>
 * Le wiki dit la meme chose sans les chiffres : on les recupere en cassant de
 * l'herbe, comme les graines de ble, le kiwano bien plus rarement que l'aubergine
 * et le cerfeuil, l'orangeblue plus rarement encore.
 *
 * <p><b>Ecart</b> : les hautes herbes et les grandes fougeres comptent aussi, une
 * fois par plante, comme pour les cereales de DoHy ({@code AgriEvents}) ; l'original
 * ne connaissait que l'herbe basse. La graine passe par les drops du bloc, en
 * priorite basse, donc apres Grass Breaker : l'enchantement ne la multiplie pas,
 * comme dans l'original ou il ne relancait que les drops vanilla de l'herbe.
 */
@EventBusSubscriber
public final class GrassSeeds {

    /** Le tirage de l'original : un entier de 0 a 9 999. */
    public static final int ROLL = 10_000;

    /**
     * Une graine : la culture dont le verrou s'applique, la graine qui tombe, et la
     * borne haute du tirage, incluse comme dans l'original.
     */
    public record Drop(Block crop, Item seed, int bound) {

        /** La chance d'une herbe cassee, entre 0 et 1. */
        public double chance() {
            return (bound + 1) / (double) ROLL;
        }
    }

    private GrassSeeds() {
    }

    /** Les quatre graines, construites a l'appel : les objets ne sont pas prets au chargement de la classe. */
    public static List<Drop> drops() {
        return List.of(new Drop(PalaCrops.EGGPLANT_CROP.get(), PalaItems.EGGPLANT_SEEDS.get(), 2000),
                new Drop(PalaCrops.CHERVIL_CROP.get(), PalaItems.CHERVIL_SEEDS.get(), 2000),
                new Drop(PalaCrops.KIWANO_CROP.get(), PalaItems.KIWANO_SEEDS.get(), 25),
                new Drop(PalaCrops.ORANGEBLUE_CROP.get(), PalaItems.ORANGEBLUE_SEEDS.get(), 5));
    }

    /** L'herbe et la fougere, basses ou hautes. */
    public static boolean isGrass(BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.getAbilities().instabuild
                || !(event.getLevel() instanceof ServerLevel level) || !isGrass(event.getState())) {
            return;
        }
        BlockPos pos = event.getPos();
        for (Drop drop : drops()) {
            if (player.getRandom().nextInt(ROLL) > drop.bound()) {
                continue;
            }
            // Le verrou de la culture, comme JobsBridge.canUseCraft : sous son niveau, rien ne tombe.
            if (!JobGate.allows(player, JobGate.of(drop.crop().defaultBlockState()))) {
                continue;
            }
            event.getDrops().add(new ItemEntity(level, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                    new ItemStack(drop.seed())));
        }
    }
}
