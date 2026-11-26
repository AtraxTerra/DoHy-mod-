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
        fr.atraxterra.dohy.faction.tech.DoHyResearch.register(modBus);
        fr.atraxterra.dohy.agri.DoHyAgri.register(modBus);
        // L'economie souterraine (lot 24c) : plantes, produits, conditionnement, postes, planques.
        fr.atraxterra.dohy.underground.DoHyUnderground.register(modBus);
        fr.atraxterra.dohy.spawn.DoHySpawn.register(modBus);
        // Les kits de Paladium : le kit de depart a la premiere connexion, ceux des grades chaque semaine.
        fr.atraxterra.dohy.kits.DoHyKits.register(modBus);
        fr.atraxterra.dohy.noel.DoHyNoel.register(modBus);
        fr.atraxterra.dohy.farmland.DoHyFarmland.register(modBus);
        fr.atraxterra.dohy.earth.DoHyEarth.register(modBus);
        // Les palettes d'armement : obus, munitions et missiles de NTM NEXT en caisses.
        fr.atraxterra.dohy.palette.DoHyPalette.register(modBus);
        // La guerre des nations : les soldats des nations rivales.
        fr.atraxterra.dohy.faction.war.DoHyWar.register(modBus);
        // La garnison : tes soldats, recrutes a la caserne, payes par la banque de ta nation.
        fr.atraxterra.dohy.faction.garrison.DoHyGarrison.register(modBus);
        fr.atraxterra.dohy.faction.bcc.DoHyBcc.register(modBus);
        fr.atraxterra.dohy.faction.marks.Marks.register(modBus);
        fr.atraxterra.dohy.casino.DoHyCasino.register(modBus);
        fr.atraxterra.dohy.faction.unesco.DoHyUnesco.register(modBus);
        fr.atraxterra.dohy.guide.DoHyGuide.register(modBus);
        fr.atraxterra.dohy.pass.DoHyPass.register(modBus);
        // Arrakis : la planete Duna de NTM NEXT Space, reprise (relief, air, navette, astroport).
        fr.atraxterra.dohy.arrakis.DoHyArrakis.register(modBus);
        fr.atraxterra.dohy.cryo.DoHyCryo.register(modBus);
        // Les grades de Paladium (Heros, Legende, Divinite) et Premium, achetes a la Boutique.
        fr.atraxterra.dohy.grade.DoHyGrades.register(modBus);
        // Les bots de farming : ils recoltent, replantent et remplissent les coffres qu'on leur lie.
        fr.atraxterra.dohy.farmbot.DoHyFarmBots.register(modBus);
        fr.atraxterra.dohy.earth.mine.DoHyMines.register(modBus);
        LOGGER.info("DoHy charge");
    }
}
