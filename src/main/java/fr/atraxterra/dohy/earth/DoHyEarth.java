package fr.atraxterra.dohy.earth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.DoHy;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * La Terre : ce que DoHy sait faire d'une carte du monde reel — dire dans quel
 * pays on se trouve, lacher le joueur au hasard sur une terre, poser les
 * minerais la ou le monde les exploite. Tout est inerte tant qu'un datapack
 * n'a pas decrit la carte ({@link EarthData}).
 */
public final class DoHyEarth {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    /** La version de la table des gisements deja servie a un chunk ; 0 tant qu'il n'a rien recu. */
    public static final Supplier<AttachmentType<Integer>> DEPOSITS = ATTACHMENTS.register(
            "earth_deposits", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("version"), value -> value > 0)
                    .build());

    /**
     * La version du sol deja servie a un chunk : ses minerais d'origine retires, sa part des gisements
     * geants posee ; 0 tant qu'il n'a rien recu.
     */
    public static final Supplier<AttachmentType<Integer>> GROUND = ATTACHMENTS.register(
            "earth_ground", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("version"), value -> value > 0)
                    .build());

    /** La version de la table des lieux deja servie a un chunk ; 0 tant qu'il n'a rien recu. */
    public static final Supplier<AttachmentType<Integer>> SITES = ATTACHMENTS.register(
            "earth_sites", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("version"), value -> value > 0)
                    .build());

    /**
     * Le palier de saison auquel un chunk a ete remis a la saison (neige, glace) ; -1 tant qu'il ne
     * l'a jamais ete. Voir {@code season.SeasonGround}.
     */
    public static final Supplier<AttachmentType<Integer>> SEASON_GROUND = ATTACHMENTS.register(
            "season_ground", () -> AttachmentType.builder(() -> -1)
                    .serialize(Codec.INT.fieldOf("step"), value -> value >= 0)
                    .build());

    /*
     * Les villes des capitales ont ete retirees (2026-11-13). Leurs deux marques restent enregistrees
     * pour lire sans erreur les chunks et le monde qui les portent encore ; elles ne sont plus jamais
     * ecrites, et disparaissent a la sauvegarde suivante. Les villes deja posees restent en place.
     */
    private static final Supplier<AttachmentType<Integer>> OLD_CITY = ATTACHMENTS.register(
            "earth_city", () -> AttachmentType.builder(() -> 0)
                    .serialize(MapCodec.unit(0), value -> false)
                    .build());
    private static final Supplier<AttachmentType<Integer>> OLD_CITY_STATES = ATTACHMENTS.register(
            "earth_cities", () -> AttachmentType.builder(() -> 0)
                    .serialize(MapCodec.unit(0), value -> false)
                    .build());

    /** Les pays pris par des joueurs, sur le monde de la carte ; rien n'est ecrit tant qu'aucun n'est pris. */
    public static final Supplier<AttachmentType<EarthNations.Ledger>> LEDGER = ATTACHMENTS.register(
            "earth_nations", () -> AttachmentType.builder(() -> EarthNations.Ledger.EMPTY)
                    .serialize(EarthNations.Ledger.MAP_CODEC, EarthNations.Ledger::exists)
                    .build());

    /** La capitale, le pays vise et les points de conquete d'une faction ; gardes a la mort. */
    public static final Supplier<AttachmentType<EarthNations.Nation>> NATION = ATTACHMENTS.register(
            "earth_nation", () -> AttachmentType.builder(() -> EarthNations.Nation.EMPTY)
                    .serialize(EarthNations.Nation.MAP_CODEC, EarthNations.Nation::exists)
                    .copyOnDeath()
                    .build());

    private DoHyEarth() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
