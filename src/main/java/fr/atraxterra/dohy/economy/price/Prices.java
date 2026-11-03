package fr.atraxterra.dohy.economy.price;

import com.mojang.serialization.JsonOps;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedReader;

/**
 * Charge et tient a disposition le catalogue de prix.
 *
 * <p>Le fichier est lu via le gestionnaire de ressources du serveur, donc
 * depuis {@code data/dohy/prices.json}. Un datapack peut le remplacer sans
 * toucher au mod : c'est le principe pose dans {@code docs/DESIGN.md} —
 * tout ce qui est chiffre vit dans un datapack, pas dans le code.
 *
 * <p>Le fichier du jar est lui-meme copie depuis {@code data/generated/prices.json}
 * au moment du build, pour qu'il n'existe qu'une seule source de verite et
 * qu'aucune copie ne puisse deriver.
 */
@EventBusSubscriber
public final class Prices {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "prices.json");

    private static volatile PriceBook book = PriceBook.EMPTY;

    private Prices() {
    }

    /** Le catalogue en service. Jamais {@code null}, vide au pire. */
    public static PriceBook book() {
        return book;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    /**
     * Recharge le catalogue. Appele au demarrage et par {@code /dohy reload}.
     *
     * <p>En cas d'echec, le catalogue precedent reste en service : mieux vaut
     * des prix perimes qu'une economie ou plus rien ne se vend.
     */
    public static boolean load(MinecraftServer server) {
        ResourceManager resources = server.getResourceManager();
        var resource = resources.getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.error("Catalogue de prix introuvable : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }

        try (BufferedReader reader = resource.get().openAsReader()) {
            var json = GsonHelper.parse(reader);
            var result = PriceBook.CODEC.parse(JsonOps.INSTANCE, json);

            if (result.isError()) {
                DoHy.LOGGER.error("Catalogue de prix illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }

            PriceBook loaded = result.getOrThrow();
            book = loaded;
            DoHy.LOGGER.info("Catalogue de prix charge : {} items, multiplicateur {}",
                    loaded.size(), loaded.moneyMultiplier());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement du catalogue de prix", error);
            return false;
        }
    }
}
