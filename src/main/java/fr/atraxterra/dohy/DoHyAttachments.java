package fr.atraxterra.dohy;

import fr.atraxterra.dohy.economy.PlayerEconomy;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Attachments du mod.
 *
 * <p>Les attachments sont le support de toutes les donnees par joueur du mod :
 * solde, skills, collections, contrats. Ils sont persistes par NeoForge avec les
 * donnees du joueur, et {@code copyOnDeath} garantit que le solde traverse la
 * mort — la perte eventuelle est appliquee explicitement par le systeme de
 * penalite, elle n'est pas un effet de bord de la reapparition.
 */
public final class DoHyAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    public static final Supplier<AttachmentType<PlayerEconomy>> ECONOMY = ATTACHMENT_TYPES.register(
            "economy",
            () -> AttachmentType.builder(() -> PlayerEconomy.EMPTY)
                    .serialize(PlayerEconomy.MAP_CODEC)
                    .sync(PlayerEconomy.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    private DoHyAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
