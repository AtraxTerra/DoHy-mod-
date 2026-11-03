package fr.atraxterra.dohy;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entree du mod.
 *
 * <p>Volontairement vide pour l'instant : l'objectif de la phase 0 est d'avoir
 * une boucle de build verte avant d'ajouter la moindre fonctionnalite. L'ordre
 * d'implementation est fixe dans {@code docs/DESIGN.md} section 11, et il n'est
 * pas negociable sur deux points : les sauvegardes avant d'avoir des donnees a
 * perdre, et le suivi des blocs poses avant les skills et les collections qui en
 * dependent entierement.
 */
@Mod(DoHy.MOD_ID)
public final class DoHy {

    public static final String MOD_ID = "dohy";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public DoHy(IEventBus modBus, ModContainer container) {
        LOGGER.info("DoHy charge — phase 0, aucun systeme actif");
    }
}
