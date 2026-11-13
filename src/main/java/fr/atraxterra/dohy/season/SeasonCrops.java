package fr.atraxterra.dohy.season;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

/**
 * Les cultures suivent la saison (choix du joueur).
 *
 * <p>Chaque fois qu'une culture s'apprete a pousser, le jeu le demande
 * ({@link CropGrowEvent.Pre}) : les cereales de DoHy, celles du PalaMod, le
 * ble, les carottes, la canne a sucre, les baies et la plupart des cultures des
 * autres mods. La reponse depend du lieu et du moment :
 * <ul>
 *   <li><b>a l'abri</b> — sous un toit ou une verriere, sans vue sur le ciel —
 *       tout pousse : la serre est la reponse a l'hiver ;</li>
 *   <li><b>dehors, s'il gele</b> ({@link SeasonMath#SNOW_LINE}) : rien ne
 *       pousse ;</li>
 *   <li><b>dehors sinon</b> : la part des pousses qui passent baisse avec la
 *       rigueur de l'hiver, jusqu'a une sur cinq au coeur de l'hiver au-dela de
 *       65° ; sous les tropiques et l'ete, tout pousse.</li>
 * </ul>
 *
 * <p>La poudre d'os n'est pas freinee : c'est un geste du joueur, pas la
 * nature. Hors de la carte de la Terre, rien ne change.
 */
@EventBusSubscriber
public final class SeasonCrops {

    private SeasonCrops() {
    }

    @SubscribeEvent
    public static void onGrow(CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Seasons.covers(level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!level.canSeeSky(pos)) {
            return;
        }
        if (Seasons.temperature(level, pos) < SeasonMath.SNOW_LINE) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
            return;
        }
        double chance = SeasonMath.growth(Seasons.latitude(pos.getZ()), SeasonClimate.SERVER.phase());
        if (chance < 1.0d && level.getRandom().nextDouble() >= chance) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }
}
