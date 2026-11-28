package fr.atraxterra.dohy.noel;

import fr.atraxterra.dohy.spawn.SpawnApi;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran du calendrier de l'Avent ({@code /calendrier}, le Pere Noel du parvis) : vingt-quatre
 * cases — ouverte, a ouvrir, manquee (a rattraper), ou fermee jusqu'a son jour —, puis le grand
 * cadeau. Une case fermee garde sa surprise : son contenu ne se montre qu'une fois son jour
 * venu.
 */
public final class AdventMenu extends MenuController {

    public static final AdventMenu INSTANCE = new AdventMenu();

    /** Le rouge de Noel, le vert du sapin, l'or des guirlandes. */
    private static final int RED = 0xFFD42A2A;
    private static final int GREEN = 0xFF2BA554;
    private static final int GOLD = 0xFFFAC400;
    private static final int GRAY = 0xFF55545C;

    private AdventMenu() {
        super("advent");
    }

    private static ItemStack item(String id) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).map(ItemStack::new)
                .filter(stack -> !stack.isEmpty()).orElseGet(() -> new ItemStack(Items.CHEST));
    }

    /** Le contenu d'une case, pour la bulle : « 4 x Lait chocolate », l'argent a la fin. */
    private static List<String> contents(AdventCalendar.Gift gift) {
        List<String> lines = new ArrayList<>();
        lines.add("Dedans :");
        for (ItemStack stack : gift.stacks()) {
            lines.add(MenuView.text(Component.literal(stack.getCount() + " x ").append(stack.getHoverName())));
        }
        if (gift.money() > 0L) {
            lines.add(MoneyFormat.exact(gift.money()) + " $ en liquide");
        }
        return lines;
    }

    private static Card door(ServerPlayer player, int door, boolean atSpawn) {
        AdventCalendar.Gift gift = AdventCalendar.gift(door);
        AdventCalendar.State state = AdventCalendar.state(player, door);
        String title = AdventCalendar.label(door);
        String badge = String.valueOf(door);
        ItemStack closed = item("palamod:present");
        if (gift == null) {
            return Card.of(closed, title, List.of("Case vide", "")).badge(badge).state(MenuView.DISABLED);
        }
        return switch (state) {
            case OPENED -> Card.of(gift.iconStack(), title, List.of(gift.name(), "Ouverte")).badge(badge)
                    .accent(GREEN).state(MenuView.DONE).tooltip(contents(gift));
            case READY -> {
                Card card = Card.of(closed, title, List.of("La case du jour !",
                        atSpawn ? "Clic : ouvrir" : "Au spawn")).badge(badge).accent(RED);
                yield card.action("door:" + door + "|ouvrir la case");
            }
            case MISSED -> {
                List<String> tooltip = contents(gift);
                yield Card.of(gift.iconStack(), title, List.of("Manquee : " + gift.name(),
                        "Rattraper : " + MoneyFormat.exact(AdventCalendar.catchUpPrice()) + " $")).badge(badge)
                        .accent(GOLD).action("door:" + door + "|rattraper la case")
                        .confirm("Rattraper la case du " + title + " ?|" + MoneyFormat.exact(
                                AdventCalendar.catchUpPrice()) + " $")
                        .tooltip(tooltip);
            }
            case LOCKED -> {
                int wait = door - AdventCalendar.dayIndex();
                String when = AdventCalendar.dayIndex() == 0 ? "Des le 1er decembre"
                        : wait == 1 ? "Demain" : "Dans " + wait + " jours";
                yield Card.of(closed, title, List.of("Surprise", when)).badge(badge).accent(GRAY)
                        .state(MenuView.DISABLED);
            }
        };
    }

    private static Card bonus(ServerPlayer player, boolean atSpawn) {
        AdventCalendar.Gift gift = AdventCalendar.gift(AdventCalendar.BONUS);
        int opened = AdventCalendar.openedCount(player);
        if (gift == null) {
            return Card.of(new ItemStack(Items.CHEST), "Le grand cadeau", List.of("Pas cette annee", ""))
                    .state(MenuView.DISABLED);
        }
        AdventCalendar.State state = AdventCalendar.state(player, AdventCalendar.BONUS);
        Card card = Card.of(gift.iconStack(), "Le grand cadeau", List.of(gift.name(),
                opened + " / " + AdventCalendar.DOORS + " cases ouvertes")).badge("25")
                .progress(opened, AdventCalendar.DOORS).tooltip(contents(gift));
        return switch (state) {
            case OPENED -> card.accent(GREEN).state(MenuView.DONE);
            case READY -> card.accent(RED).action("door:" + AdventCalendar.BONUS + "|ouvrir le grand cadeau")
                    .tooltip(withLine(contents(gift), atSpawn ? "Joyeux Noel !" : "Au spawn"));
            default -> card.accent(GOLD).state(MenuView.DISABLED)
                    .tooltip(withLine(contents(gift), "Les 24 cases, du 25 decembre au 6 janvier"));
        };
    }

    private static List<String> withLine(List<String> lines, String line) {
        List<String> copy = new ArrayList<>(lines);
        copy.add("");
        copy.add(line);
        return copy;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        boolean atSpawn = SpawnApi.inSpawn(player);
        int index = AdventCalendar.dayIndex();
        List<Card> doors = new ArrayList<>();
        for (int door = 1; door <= AdventCalendar.DOORS; door++) {
            doors.add(door(player, door, atSpawn));
        }
        int opened = AdventCalendar.openedCount(player);
        int missed = AdventCalendar.missedCount(player);
        String today = index == 0 ? "Hors saison" : index <= AdventCalendar.DOORS ? AdventCalendar.label(index)
                : index == AdventCalendar.BONUS ? "Noel" : "Fetes";
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("AUJOURD'HUI", today, index == 0 ? 0xFFFFFFFF : RED),
                new MenuView.Stat("OUVERTES", opened + " / " + AdventCalendar.DOORS, opened > 0 ? GREEN : 0xFFFFFFFF),
                new MenuView.Stat("MANQUEES", String.valueOf(missed), missed > 0 ? GOLD : 0xFFFFFFFF));
        String subtitle = index == 0 ? "De retour le 1er decembre"
                : atSpawn ? "Une case par jour" : "Les cases s'ouvrent au spawn";
        return new MenuView(id(), "Calendrier de l'Avent", subtitle, stats, List.of(
                new MenuView.Section("LE CALENDRIER", "resources", MenuView.GRID, doors),
                new MenuView.Section("LE GRAND CADEAU", "upgrade", MenuView.GRID, List.of(bonus(player, atSpawn)))),
                false);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (!key.startsWith("door:")) {
            return;
        }
        int door;
        try {
            door = Integer.parseInt(key.substring(5));
        } catch (NumberFormatException wrong) {
            return;
        }
        String refusal = AdventCalendar.open(player, door);
        if (refusal == null) {
            success(player);
        } else {
            failure(player);
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
        }
    }
}
