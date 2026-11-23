package fr.atraxterra.dohy.kits;

import fr.atraxterra.dohy.grade.Grades;
import fr.atraxterra.dohy.grade.PlayerGrade;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran des kits ({@code /kit}, l'entree Kits de la barre laterale, l'intendant du spawn) :
 * une carte par kit — pret, de retour dans tant d'heures, deja pris, ou reserve a un grade —
 * et son contenu dans la bulle. Un clic sur une carte prete prend le kit.
 */
public final class PlayerKitsMenu extends MenuController {

    public static final PlayerKitsMenu INSTANCE = new PlayerKitsMenu();

    private PlayerKitsMenu() {
        super("kits");
    }

    private static ItemStack icon(PlayerKits.Kit kit) {
        return BuiltInRegistries.ITEM.getOptional(kit.icon()).map(ItemStack::new)
                .filter(stack -> !stack.isEmpty()).orElseGet(() -> new ItemStack(Items.CHEST));
    }

    /** Le contenu, pour la bulle : « 32 x Steak », une ligne par pile, l'argent a la fin. */
    private static List<String> contents(PlayerKits.Kit kit) {
        List<String> lines = new ArrayList<>();
        lines.add("Contenu :");
        for (ItemStack stack : kit.stacks()) {
            lines.add(MenuView.text(Component.literal(stack.getCount() + " x ").append(stack.getHoverName())));
        }
        if (kit.money() > 0L) {
            lines.add(MoneyFormat.exact(kit.money()) + " $ en liquide");
        }
        return lines;
    }

    /** La ligne courte de la carte : ce que contient le kit, en gros. */
    private static String summary(PlayerKits.Kit kit) {
        int stacks = kit.stacks().size();
        String objects = stacks + (stacks > 1 ? " objets" : " objet");
        return kit.money() > 0L ? objects + " · " + MoneyFormat.shortForm(kit.money()) + " $" : objects;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        long now = System.currentTimeMillis();
        List<Card> cards = new ArrayList<>();
        int ready = 0;
        long soonest = Long.MAX_VALUE;
        for (PlayerKits.Kit kit : PlayerKits.kits()) {
            int accent = 0xFF000000 | kit.color();
            Card card = Card.of(icon(kit), kit.name(), List.of(summary(kit), ""))
                    .badge(kit.delay());
            List<String> tooltip = contents(kit);
            if (!PlayerKits.allowed(player, kit)) {
                card = new Card(card.icon(), card.title(), List.of(summary(kit), PlayerKits.requirement(kit)),
                        card.badge(), card.accent(), MenuView.DISABLED, "", "", -1, List.of(), "", null);
                tooltip.add("Reserve : " + PlayerKits.requirement(kit) + ". Les grades s'achetent a la Boutique.");
            } else if (PlayerKits.spent(player, kit)) {
                card = new Card(card.icon(), card.title(), List.of(summary(kit), "Deja pris"), card.badge(),
                        card.accent(), MenuView.DONE, "", "", -1, List.of(), "", null);
            } else {
                long wait = PlayerKits.remaining(player, kit, now);
                if (wait > 0L) {
                    soonest = Math.min(soonest, wait);
                    card = new Card(card.icon(), card.title(), List.of(summary(kit), "Dans " + PlayerKits.duration(wait)),
                            card.badge(), card.accent(), MenuView.DISABLED, "", "", -1, List.of(), "", null);
                } else {
                    ready++;
                    card = new Card(card.icon(), card.title(), List.of(summary(kit), "Pret"), card.badge(), accent,
                            MenuView.NORMAL, "kit:" + kit.id() + "|prendre le kit", "", -1, List.of(), "", null);
                }
            }
            cards.add(card.tooltip(tooltip));
        }
        PlayerGrade grade = Grades.grade(player);
        String gradeLabel = grade == PlayerGrade.NONE ? (Grades.premium(player) ? "Premium" : "Joueur")
                : grade.label() + (Grades.premium(player) ? " +" : "");
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("TON GRADE", gradeLabel, 0xFF000000 | (grade.color() & 0xFFFFFF)),
                new MenuView.Stat("PRETS", String.valueOf(ready), ready > 0 ? 0xFF22E558 : 0xFFFFFFFF),
                new MenuView.Stat("LE PROCHAIN", soonest == Long.MAX_VALUE ? "-" : PlayerKits.duration(soonest),
                        0xFFFFFFFF));
        List<Card> rules = List.of(
                Card.of(new ItemStack(Items.IRON_PICKAXE), "Le kit de depart", List.of("A ta premiere connexion",
                        "Une seule fois")).accent(0xFF2F5BEA)
                        .tooltip(List.of("Il arrive de lui-meme dans ton sac.",
                                "Sac trop plein ? Il t'attend ici.")),
                Card.of(new ItemStack(Items.CLOCK), "Les delais", List.of("En heures reelles",
                        "Meme partie fermee")).accent(0xFF2F5BEA)
                        .tooltip(List.of("Un kit pris revient apres son delai.",
                                "Le kit entier, ou rien : garde de la place.")),
                Card.of(new ItemStack(fr.atraxterra.dohy.grade.DoHyGrades.LEGENDE.get()), "Les grades",
                        List.of("Un kit par grade, chaque semaine", "Ceux d'en dessous aussi")).accent(0xFF2F5BEA)
                        .tooltip(List.of("Heros, Legende, Divinite et Premium :",
                                "a la Boutique, avec ton argent.")));
        return new MenuView(id(), "Kits", "", stats, List.of(
                new MenuView.Section("TES KITS", "resources", MenuView.GRID, cards),
                new MenuView.Section("REGLES", "upgrade", MenuView.GRID, rules)), false);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (!key.startsWith("kit:")) {
            return;
        }
        String refusal = PlayerKits.claim(player, key.substring(4));
        if (refusal == null) {
            success(player);
        } else {
            failure(player);
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
        }
    }
}
