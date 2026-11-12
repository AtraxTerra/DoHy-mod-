package fr.atraxterra.dohy;

import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.ui.KitMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entree du mod.
 *
 * <p>L'ordre d'implementation est fixe dans {@code docs/DESIGN.md} section 11,
 * et il n'est pas negociable sur deux points : les sauvegardes de secours
 * arrivent avec la monnaie, donc avant qu'il y ait des donnees a perdre, et le
 * suivi des blocs poses arrive avant les metiers et les collections, qui en
 * dependent entierement.
 */
@Mod(DoHy.MOD_ID)
public final class DoHy {

    public static final String MOD_ID = "dohy";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public DoHy(IEventBus modBus, ModContainer container) {
        DoHyAttachments.register(modBus);
        KitMenus.register(modBus);
        Factions.register(modBus);
        // Le contenu converti du PalaMod s'enregistre sous son propre
        // namespace, « palamod », pour que le pack de textures officiel de
        // Paladium s'applique tel quel. Voir PalaMod.
        PalaMod.register(modBus);
        // Les machines propres a DoHy, sous l'espace de noms « dohy ».
        fr.atraxterra.dohy.tech.DoHyTech.register(modBus);
        fr.atraxterra.dohy.agri.DoHyAgri.register(modBus);
        LOGGER.info("DoHy charge");
    }
}
