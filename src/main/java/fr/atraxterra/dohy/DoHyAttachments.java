package fr.atraxterra.dohy;

import fr.atraxterra.dohy.collection.CollectionData;
import fr.atraxterra.dohy.economy.PlayerEconomy;
import fr.atraxterra.dohy.skill.SkillData;
import fr.atraxterra.dohy.economy.market.PlayerMarket;
import fr.atraxterra.dohy.world.PlacedBlockData;
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

    /**
     * Positions posees par un joueur, par chunk.
     *
     * <p>Ni {@code sync} ni {@code copyOnDeath} : le client n'a aucun besoin de
     * cette information, et un chunk ne meurt pas.
     */
    public static final Supplier<AttachmentType<PlacedBlockData>> PLACED_BLOCKS = ATTACHMENT_TYPES.register(
            "placed_blocks",
            () -> AttachmentType.builder(PlacedBlockData::new)
                    .serialize(PlacedBlockData.MAP_CODEC, data -> !data.isEmpty())
                    .build());

    /**
     * Pression de vente par item. Persistee, non synchronisee (le client n'en a
     * pas besoin), et conservee a la mort : mourir ne doit pas remettre les
     * prix a neuf, ce serait une facon tordue de reinitialiser le marche.
     */
    public static final Supplier<AttachmentType<PlayerMarket>> MARKET = ATTACHMENT_TYPES.register(
            "market",
            () -> AttachmentType.builder(PlayerMarket::new)
                    .serialize(PlayerMarket.MAP_CODEC, market -> !market.isEmpty())
                    .copyOnDeath()
                    .build());

    /**
     * Collections par item. Conservees a la mort : un acquis ne se reperd pas,
     * et le premier palier debloque la vente — la perdre a la mort serait
     * punitif au point d'etre absurde.
     */
    public static final Supplier<AttachmentType<CollectionData>> COLLECTIONS = ATTACHMENT_TYPES.register(
            "collections",
            () -> AttachmentType.builder(CollectionData::new)
                    .serialize(CollectionData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    /** XP de skill. Conservee a la mort, comme les collections. */
    public static final Supplier<AttachmentType<SkillData>> SKILLS = ATTACHMENT_TYPES.register(
            "skills",
            () -> AttachmentType.builder(SkillData::new)
                    .serialize(SkillData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    private DoHyAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
