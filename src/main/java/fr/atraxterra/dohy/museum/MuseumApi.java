package fr.atraxterra.dohy.museum;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le musee : un exemplaire de chaque item, donne definitivement.
 *
 * <h2>Pourquoi par categorie et non par item</h2>
 *
 * <p>152 items dans le catalogue, donc 152 micro-bonus si chaque don payait.
 * Chacun serait imperceptible et l'ensemble illisible. Les dons alimentent donc
 * <b>six collections de collections</b>, dont seule la completion accorde un
 * bonus nomme et consequent. Chaque don individuel donne de l'XP globale, ce qui
 * garde une progression continue entre deux paliers.
 *
 * <h2>Pourquoi les items uniques sont exclus</h2>
 *
 * <p>L'oeuf du dragon est unique par monde. Exiger son sacrifice pour completer
 * une categorie transformerait un systeme de collection en source de regret. Le
 * tier {@code RARE} est donc hors des categories obligatoires et forme le
 * panneau mecene : on peut l'alimenter par choix, jamais par obligation.
 */
@EventBusSubscriber
public final class MuseumApi {

    private MuseumApi() {
    }

    public static MuseumData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.MUSEUM);
    }

    /** Les items attendus dans une categorie, tier exclus mis a part. */
    public static List<Identifier> expected(String category) {
        List<Identifier> ids = new ArrayList<>();
        Prices.book().items().forEach((id, entry) -> {
            if (!entry.category().equalsIgnoreCase(category)) {
                return;
            }
            if (MuseumConfig.config().isExcludedTier(entry.tier())) {
                return;
            }
            ids.add(id);
        });
        ids.sort(Identifier::compareTo);
        return ids;
    }

    /** Les items du panneau mecene : ceux des tiers exclus. */
    public static List<Identifier> patronItems() {
        List<Identifier> ids = new ArrayList<>();
        Prices.book().items().forEach((id, entry) -> {
            if (MuseumConfig.config().isExcludedTier(entry.tier())) {
                ids.add(id);
            }
        });
        ids.sort(Identifier::compareTo);
        return ids;
    }

    /** Catégories connues du catalogue, hors panneau mecene. */
    public static List<String> categories() {
        List<String> found = new ArrayList<>();
        Prices.book().items().forEach((id, entry) -> {
            if (!MuseumConfig.config().isExcludedTier(entry.tier())
                    && !found.contains(entry.category())) {
                found.add(entry.category());
            }
        });
        found.sort(String::compareTo);
        return found;
    }

    public static boolean isComplete(ServerPlayer player, String category) {
        List<Identifier> expected = expected(category);
        return !expected.isEmpty() && data(player).all().containsAll(expected);
    }

    public static boolean isPatronComplete(ServerPlayer player) {
        List<Identifier> expected = patronItems();
        return !expected.isEmpty() && data(player).all().containsAll(expected);
    }

    public static int donatedIn(ServerPlayer player, String category) {
        int count = 0;
        for (Identifier id : expected(category)) {
            if (data(player).has(id)) {
                count++;
            }
        }
        return count;
    }

    /** Vrai si cet item manque encore au musee. */
    public static boolean isWanted(ServerPlayer player, Identifier id) {
        if (data(player).has(id)) {
            return false;
        }
        return Prices.book().entry(id).isPresent();
    }

    /**
     * Enregistre un don. Retourne {@code true} si l'item manquait.
     *
     * <p>N'annonce la completion qu'au moment ou elle survient : le joueur doit
     * apprendre qu'il a fini une categorie, pas avoir a le verifier.
     */
    public static boolean donate(ServerPlayer player, Identifier id) {
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        if (entry == null || !data(player).add(id)) {
            return false;
        }

        boolean patron = MuseumConfig.config().isExcludedTier(entry.tier());
        String category = patron ? "patron" : entry.category();
        boolean completedNow = patron ? isPatronComplete(player) : isComplete(player, category);

        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        Component name = item == null
                ? Component.literal(id.getPath())
                : new ItemStack(item).getHoverName();

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME.value(), SoundSource.MASTER, 0.6f, 1.0f);
        player.sendSystemMessage(Component.literal("Musee : ")
                .withStyle(ChatFormatting.GRAY)
                .append(name.copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" expose")
                        .withStyle(ChatFormatting.GRAY)));

        ContractApi.grantGlobalXp(player, MuseumConfig.config().globalXpPerDonation(), "don au musee");

        if (completedNow) {
            MuseumConfig.Section section = patron
                    ? MuseumConfig.config().patron()
                    : MuseumConfig.config().section(category);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.9f, 0.7f);
            player.sendSystemMessage(Component.literal("Aile complete : "
                            + (section.display().isEmpty() ? category : section.display()))
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
     * <p>Lu par les systemes de drop, au meme titre que les perks de skill.
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
        for (Map.Entry<String, MuseumConfig.Section> line : sections(player).entrySet()) {
            for (MuseumConfig.Reward reward : line.getValue().rewards()) {
                if (reward.type().equals(type)) {
                    total += reward.amount();
                }
            }
        }
        return total;
    }

    /** Les ailes completes du joueur, et leurs recompenses. */
    private static Map<String, MuseumConfig.Section> sections(ServerPlayer player) {
        Map<String, MuseumConfig.Section> complete = new LinkedHashMap<>();
        for (String category : categories()) {
            if (isComplete(player, category)) {
                complete.put(category, MuseumConfig.config().section(category));
            }
        }
        if (isPatronComplete(player)) {
            complete.put("patron", MuseumConfig.config().patron());
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
     * <p>Transitoires et recalcules depuis l'etat du musee, comme les perks de
     * skill : un modificateur permanent survivrait a la sauvegarde, et le
     * moindre bug de recalcul ferait s'empiler les bonus a l'infini.
     */
    public static void apply(ServerPlayer player) {
        Map<String, MuseumConfig.Section> complete = sections(player);

        for (String category : allSectionKeys()) {
            MuseumConfig.Section section = complete.containsKey(category)
                    ? complete.get(category)
                    : MuseumConfig.config().section(category);
            boolean active = complete.containsKey(category);

            for (int index = 0; index < section.rewards().size(); index++) {
                MuseumConfig.Reward reward = section.rewards().get(index);
                if (!"attribute".equals(reward.type()) || reward.attribute().isEmpty()) {
                    continue;
                }
                Identifier modifierId = Identifier.fromNamespaceAndPath(DoHy.MOD_ID,
                        "museum/" + category.toLowerCase() + "/" + index);
                applyOne(player, reward, modifierId, active);
            }
        }
    }

    private static List<String> allSectionKeys() {
        List<String> keys = new ArrayList<>(MuseumConfig.config().categories().keySet());
        keys.add("patron");
        return keys;
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
