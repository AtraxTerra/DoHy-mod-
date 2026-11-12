package fr.atraxterra.dohy.earth;

import com.mojang.serialization.Codec;
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

    /** La version de la table des lieux deja servie a un chunk ; 0 tant qu'il n'a rien recu. */
    public static final Supplier<AttachmentType<Integer>> SITES = ATTACHMENTS.register(
            "earth_sites", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("version"), value -> value > 0)
                    .build());

    private DoHyEarth() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
