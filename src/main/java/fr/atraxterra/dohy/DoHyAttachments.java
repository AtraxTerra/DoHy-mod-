package fr.atraxterra.dohy;

import fr.atraxterra.dohy.boost.BoostData;
import fr.atraxterra.dohy.collection.CollectionData;
import fr.atraxterra.dohy.contract.ContractData;
import fr.atraxterra.dohy.economy.PlayerEconomy;
import fr.atraxterra.dohy.job.JobData;
import fr.atraxterra.dohy.museum.MuseumData;
import fr.atraxterra.dohy.upgrade.UpgradeData;
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
 * solde, metiers, collections, contrats. Ils sont persistes par NeoForge avec les
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
     * et chaque palier releve le prix plancher — la perdre a la mort serait
     * punitif au point d'etre absurde.
     */
    public static final Supplier<AttachmentType<CollectionData>> COLLECTIONS = ATTACHMENT_TYPES.register(
            "collections",
            () -> AttachmentType.builder(CollectionData::new)
                    .serialize(CollectionData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    /** XP de metier. Conservee a la mort, comme les collections. */
    public static final Supplier<AttachmentType<JobData>> JOBS = ATTACHMENT_TYPES.register(
            "jobs",
            () -> AttachmentType.builder(JobData::new)
                    .serialize(JobData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    /**
     * L'ancienne XP de skill, d'avant le remplacement des skills par les
     * metiers.
     *
     * <p>Encore enregistree pour que les sauvegardes existantes se relisent :
     * une piece jointe dont le type a disparu est perdue au chargement.
     * {@code JobMigration} la verse dans {@link #JOBS} a la premiere connexion
     * puis la supprime ; elle ne se reecrit donc plus jamais.
     */
    public static final Supplier<AttachmentType<JobData>> LEGACY_SKILLS = ATTACHMENT_TYPES.register(
            "skills",
            () -> AttachmentType.builder(JobData::new)
                    .serialize(JobData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    /**
     * Contrats, demande du jour, niveau global et compteur de temps de jeu.
     * Conserves a la mort : mourir ne doit pas annuler un contrat en cours.
     */
    public static final Supplier<AttachmentType<ContractData>> CONTRACTS = ATTACHMENT_TYPES.register(
            "contracts",
            () -> AttachmentType.builder(ContractData::new)
                    .serialize(ContractData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    /** Booster actif, cles de caisse, coffre d'autosell. Conserves a la mort. */
    public static final Supplier<AttachmentType<BoostData>> BOOSTS = ATTACHMENT_TYPES.register(
            "boosts",
            () -> AttachmentType.builder(BoostData::new)
                    .serialize(BoostData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    /** Le marche : les achats du jour et les renouvellements payes. Conserve a la mort. */
    public static final Supplier<AttachmentType<fr.atraxterra.dohy.auction.AuctionData>> AUCTION =
            ATTACHMENT_TYPES.register("auction",
                    () -> AttachmentType.builder(fr.atraxterra.dohy.auction.AuctionData::new)
                            .serialize(fr.atraxterra.dohy.auction.AuctionData.MAP_CODEC, data -> !data.isEmpty())
                            .copyOnDeath()
                            .build());

    /** Le clicker : ClicCoins, batiments, ameliorations, rayons du Clic Shop. Conserve a la mort. */
    public static final Supplier<AttachmentType<fr.atraxterra.dohy.palamod.clicker.ClickerData>> CLICKER =
            ATTACHMENT_TYPES.register("clicker",
                    () -> AttachmentType.builder(fr.atraxterra.dohy.palamod.clicker.ClickerData::new)
                            .serialize(fr.atraxterra.dohy.palamod.clicker.ClickerData.MAP_CODEC,
                                    data -> !data.isEmpty())
                            .copyOnDeath()
                            .build());

    /** Pieces exposees au musee. Conservees a la mort : un don est definitif. */
    /**
     * Les ameliorations permanentes achetees.
     *
     * <p>{@code copyOnDeath} comme le reste : un achat definitif paye plusieurs
     * milliards ne doit pas disparaitre a la mort.
     */
    public static final Supplier<AttachmentType<UpgradeData>> UPGRADES = ATTACHMENT_TYPES.register(
            "upgrades",
            () -> AttachmentType.builder(UpgradeData::new)
                    .serialize(UpgradeData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<MuseumData>> MUSEUM = ATTACHMENT_TYPES.register(
            "museum",
            () -> AttachmentType.builder(MuseumData::new)
                    .serialize(MuseumData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    private DoHyAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
