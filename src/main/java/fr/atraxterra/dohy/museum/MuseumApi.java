package fr.atraxterra.dohy.museum;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.contract.ContractApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Le musee : un exemplaire de chaque piece rare, donne definitivement.
 *
 * <h2>Pourquoi seulement des pieces rares</h2>
 *
 * <p>Le premier musee demandait un exemplaire de chaque objet du catalogue de
 * prix : du ble, de la pierre, 152 vitrines dont l'essentiel se remplissait en
 * vidant un coffre. Depuis le 2026-11-11 il n'expose que ce qui se merite — une
 * etoile du Nether, une pierre legendaire, une bombe de NTM — et chaque piece
 * est nommee dans {@code museum.json}.
 *
 * <h2>Pourquoi par aile et non par piece</h2>
 *
 * <p>Un bonus par don serait imperceptible. Les dons alimentent des ailes, dont
 * seule la completion accorde un bonus nomme et consequent. Chaque don donne
 * de l'XP globale, ce qui garde une progression continue entre deux paliers.
 *
 * <h2>Pourquoi l'unique est a part</h2>
 *
 * <p>L'oeuf du dragon est unique par monde. Exiger son sacrifice pour completer
 * une aile transformerait un systeme de collection en source de regret. Il
 * forme le panneau mecene : on peut l'alimenter par choix, jamais par
 * obligation.
 */
@EventBusSubscriber
public final class MuseumApi {

    public static final String PATRON = MuseumConfig.Config.PATRON;

    private MuseumApi() {
    }

    public static MuseumData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.MUSEUM);
    }

    /** Les pieces d'une aile qui existent dans ce jeu, dans l'ordre des vitrines. */
    public static List<Identifier> expected(String wing) {
        return MuseumConfig.config().wing(wing).pieces();
    }

    /** Les pieces du panneau mecene. */
    public static List<Identifier> patronItems() {
        return MuseumConfig.config().patron().pieces();
    }

    /** Les ailes ouvertes, hors panneau mecene : celles dont une piece au moins existe. */
    public static List<String> wings() {
        List<String> found = new ArrayList<>();
        for (MuseumConfig.Wing wing : MuseumConfig.config().wings()) {
            if (!wing.pieces().isEmpty()) {
                found.add(wing.id());
            }
        }
        return found;
    }

    /** L'aile qui expose cette piece, panneau mecene compris ; {@code null} si aucune. */
    public static String wingOf(Identifier id) {
        for (MuseumConfig.Wing wing : MuseumConfig.config().wings()) {
            if (wing.pieces().contains(id)) {
                return wing.id();
            }
        }
        return patronItems().contains(id) ? PATRON : null;
    }

    public static boolean isComplete(ServerPlayer player, String wing) {
        List<Identifier> expected = expected(wing);
        if (expected.isEmpty()) {
            return false;
        }
        MuseumData data = data(player);
        for (Identifier id : expected) {
            if (!data.has(id)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isPatronComplete(ServerPlayer player) {
        return isComplete(player, PATRON);
    }

    public static int donatedIn(ServerPlayer player, String wing) {
        int count = 0;
        MuseumData data = data(player);
        for (Identifier id : expected(wing)) {
            if (data.has(id)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Le nombre de pieces exposees, panneau mecene compris.
     *
     * <p>Compte parmi les pieces d'aujourd'hui : les dons faits a l'ancien
     * musee (du ble, de la pierre) restent enregistres mais ne sont plus des
     * pieces.
     */
    public static int exposed(ServerPlayer player) {
        int count = donatedIn(player, PATRON);
        for (String wing : wings()) {
            count += donatedIn(player, wing);
        }
        return count;
    }

    /** Vrai si cet objet est une piece qui manque encore au musee. */
    public static boolean isWanted(ServerPlayer player, Identifier id) {
        return !data(player).has(id) && wingOf(id) != null;
    }

    /**
     * Enregistre un don. Retourne {@code true} si la piece manquait.
     *
     * <p>N'annonce la completion qu'au moment ou elle survient : le joueur doit
     * apprendre qu'il a fini une aile, pas avoir a le verifier.
     */
    public static boolean donate(ServerPlayer player, Identifier id) {
        String wing = wingOf(id);
        if (wing == null || !data(player).add(id)) {
            return false;
        }

        boolean completedNow = isComplete(player, wing);

        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        Component name = item == null
                ? Component.literal(id.getPath())
                : new ItemStack(item).getHoverName();

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.MASTER, 0.6f, 1.0f);
        player.sendSystemMessage(Component.literal("Musee : ")
                .withStyle(ChatFormatting.GRAY)
                .append(name.copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" expose")
                        .withStyle(ChatFormatting.GRAY)));

        ContractApi.grantGlobalXp(player, MuseumConfig.config().globalXpPerDonation(), "don au musee");

        if (completedNow) {
            MuseumConfig.Wing section = MuseumConfig.config().wing(wing);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.9f, 0.7f);
            player.sendSystemMessage(Component.literal("Aile complete : " + section.name())
                    .withStyle(ChatFormatting.GOLD));
            section.rewards().forEach(reward -> player.sendSystemMessage(
                    Component.literal("  " + describe(reward)).withStyle(ChatFormatting.GREEN)));
            apply(player);
        }
        return true;
    }

    /**
     * Somme des bonus de double drop accordes par les ailes completes.
     *
     * <p>Lu par les systemes de drop. Depuis que les perks de metier sont
     * devenus des primes, c'est la seule chance de double drop du mod.
     */
    public static double doubleDropBonus(ServerPlayer player) {
        return sum(player, "double_drop_chance");
    }

    /** Remise sur les prix d'achat du shop, entre 0 et 0,9. */
    public static double buyDiscount(ServerPlayer player) {
        return Math.min(0.9d, sum(player, "buy_discount"));
    }

    private static double sum(ServerPlayer player, String type) {
        double total = 0.0d;
        for (MuseumConfig.Wing wing : complete(player)) {
            for (MuseumConfig.Reward reward : wing.rewards()) {
                if (reward.type().equals(type)) {
                    total += reward.amount();
                }
            }
        }
        return total;
    }

    /** Les ailes completes du joueur, panneau mecene compris. */
    private static List<MuseumConfig.Wing> complete(ServerPlayer player) {
        List<MuseumConfig.Wing> complete = new ArrayList<>();
        for (MuseumConfig.Wing wing : MuseumConfig.config().wings()) {
            if (isComplete(player, wing.id())) {
                complete.add(wing);
            }
        }
        if (isPatronComplete(player)) {
            complete.add(MuseumConfig.config().patron());
        }
        return complete;
    }

    /** Recalcule les modificateurs d'attribut a la connexion. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            apply(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            apply(player);
        }
    }

    /**
     * Applique les bonus d'attribut des ailes completes.
     *
     * <p>Transitoires et recalcules depuis l'etat du musee : un modificateur
     * permanent survivrait a la sauvegarde, et le moindre bug de recalcul ferait
     * s'empiler les bonus a l'infini.
     */
    public static void apply(ServerPlayer player) {
        List<MuseumConfig.Wing> all = new ArrayList<>(MuseumConfig.config().wings());
        all.add(MuseumConfig.config().patron());

        for (MuseumConfig.Wing wing : all) {
            boolean active = isComplete(player, wing.id());
            for (int index = 0; index < wing.rewards().size(); index++) {
                MuseumConfig.Reward reward = wing.rewards().get(index);
                if (!"attribute".equals(reward.type()) || reward.attribute().isEmpty()) {
                    continue;
                }
                Identifier modifierId = Identifier.fromNamespaceAndPath(DoHy.MOD_ID,
                        "museum/" + wing.id().toLowerCase() + "/" + index);
                applyOne(player, reward, modifierId, active);
            }
        }
    }

    private static void applyOne(ServerPlayer player, MuseumConfig.Reward reward,
                                 Identifier modifierId, boolean active) {
        Identifier attributeId = Identifier.bySeparator(reward.attribute().orElseThrow(), ':');
        Holder.Reference<Attribute> attribute =
                BuiltInRegistries.ATTRIBUTE.get(attributeId).orElse(null);
        if (attribute == null) {
            DoHy.LOGGER.warn("Recompense de musee ignoree : attribut inconnu {}", attributeId);
            return;
        }
        AttributeInstance instance = player.getAttributes().getInstance(attribute);
        if (instance == null) {
            return;
        }
        if (!active) {
            instance.removeModifier(modifierId);
            return;
        }
        AttributeModifier.Operation operation = parseOperation(reward.operation().orElse("add_value"));
        instance.addOrUpdateTransientModifier(
                new AttributeModifier(modifierId, reward.amount(), operation));
    }

    private static AttributeModifier.Operation parseOperation(String raw) {
        try {
            return AttributeModifier.Operation.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException error) {
            return AttributeModifier.Operation.ADD_VALUE;
        }
    }

    public static String describe(MuseumConfig.Reward reward) {
        String amount = Math.abs(reward.amount()) >= 1
                ? String.format("%+.1f", reward.amount())
                : String.format("%+.0f %%", reward.amount() * 100);
        return switch (reward.type()) {
            case "attribute" -> reward.attribute().orElse("attribut") + " " + amount;
            case "double_drop_chance" -> "chance de double drop " + amount;
            case "buy_discount" -> "remise sur les achats " + amount;
            default -> reward.type() + " " + amount;
        };
    }
}
