package fr.atraxterra.dohy.guide;

import fr.atraxterra.dohy.guide.plan.Blueprint;
import fr.atraxterra.dohy.guide.plan.Blueprints;
import fr.atraxterra.dohy.guide.plan.PlanItem;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'ecran de l'encyclopedie : le sommaire (les fiches par categorie, les
 * nouvelles d'abord), puis une fiche — son texte, ta progression, ses plans de
 * construction, ses commandes (un clic les lance) et ses voisines.
 */
public final class EncyclopediaMenu extends MenuController {

    public static final EncyclopediaMenu INSTANCE = new EncyclopediaMenu();

    /** La page ouverte par joueur : l'identifiant d'une fiche, ou vide pour le sommaire. */
    private static final Map<UUID, String> PAGES = new ConcurrentHashMap<>();

    private EncyclopediaMenu() {
        super("encyclopedie");
    }

    public static void openIndex(ServerPlayer player) {
        PAGES.put(player.getUUID(), "");
        INSTANCE.open(player);
    }

    /** Ouvre une fiche, ou le sommaire si elle n'existe pas. */
    public static void openEntry(ServerPlayer player, String id) {
        Encyclopedia.Entry entry = Encyclopedia.entry(id);
        if (entry == null) {
            openIndex(player);
            return;
        }
        PAGES.put(player.getUUID(), id);
        EncyclopediaEvents.data(player).markRead(id);
        INSTANCE.open(player);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        Encyclopedia.Entry entry = Encyclopedia.entry(PAGES.getOrDefault(player.getUUID(), ""));
        return entry == null ? index(player) : page(player, entry);
    }

    // -- Le sommaire ---------------------------------------------------------------------

    private MenuView index(ServerPlayer player) {
        EncyclopediaData data = EncyclopediaEvents.data(player);
        List<Encyclopedia.Entry> entries = Encyclopedia.entries();
        List<String> fresh = new ArrayList<>();
        for (String id : data.freshIds()) {
            if (Encyclopedia.entry(id) != null) {
                fresh.add(id);
            }
        }
        int read = 0;
        for (Encyclopedia.Entry entry : entries) {
            if (data.read(entry.id())) {
                read++;
            }
        }
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("FICHES", String.valueOf(entries.size()), 0xFFFFFFFF),
                new MenuView.Stat("LUES", read + " / " + entries.size(), 0xFF22E558),
                new MenuView.Stat("NOUVELLES", String.valueOf(fresh.size()), fresh.isEmpty() ? 0xFF8C8B93 : 0xFFFAC400));

        List<MenuView.Section> sections = new ArrayList<>();
        if (entries.isEmpty()) {
            sections.add(new MenuView.Section("", "", MenuView.TEXT, List.of(Card.of(ItemStack.EMPTY,
                    "Encyclopedie vide", List.of("Le fichier des fiches n'a pas pu etre lu : voir le journal du serveur."))
                    .accent(0xFFEF3826))));
        }
        if (!fresh.isEmpty()) {
            List<Card> cards = new ArrayList<>();
            for (String id : fresh) {
                cards.add(card(Encyclopedia.entry(id), data));
            }
            sections.add(new MenuView.Section("A LIRE", "upgrade", MenuView.GRID, cards));
        }
        for (Encyclopedia.Category category : Encyclopedia.categories()) {
            List<Card> cards = new ArrayList<>();
            for (Encyclopedia.Entry entry : Encyclopedia.inCategory(category.id())) {
                cards.add(card(entry, data));
            }
            if (!cards.isEmpty()) {
                sections.add(new MenuView.Section(category.name().toUpperCase(), "", MenuView.GRID, cards));
            }
        }
        return new MenuView(id(), "Encyclopedie", "Tout DoHy, fiche par fiche · touche J", stats, sections, true);
    }

    private static Card card(Encyclopedia.Entry entry, EncyclopediaData data) {
        Encyclopedia.Category category = Encyclopedia.category(entry.category());
        List<String> lines = new ArrayList<>(entry.card());
        if (lines.isEmpty() && !entry.summary().isEmpty()) {
            lines.add(entry.summary().get(0));
        }
        Card card = Card.of(entry.iconStack(), entry.title(), lines).accent(category.color())
                .action("fiche:" + entry.id() + "|lire");
        if (data.fresh(entry.id())) {
            card = card.badge("NOUVEAU");
        }
        return card;
    }

    // -- Une fiche ------------------------------------------------------------------------

    private MenuView page(ServerPlayer player, Encyclopedia.Entry entry) {
        Encyclopedia.Category category = Encyclopedia.category(entry.category());
        int accent = category.color();
        EncyclopediaProgress.Result progress = EncyclopediaProgress.of(player, entry.progress());

        List<MenuView.Section> sections = new ArrayList<>();
        List<Card> text = new ArrayList<>();
        text.add(Card.of(entry.iconStack(), "", entry.summary()).accent(accent));
        for (Encyclopedia.Section section : entry.sections()) {
            text.add(Card.of(ItemStack.EMPTY, section.title(), section.text()).accent(accent));
        }
        sections.add(new MenuView.Section("", "", MenuView.TEXT, text));

        if (!progress.cards().isEmpty()) {
            sections.add(new MenuView.Section("TA PROGRESSION", "upgrade", MenuView.GRID, progress.cards()));
        }

        List<Card> plans = new ArrayList<>();
        for (String id : entry.plans()) {
            Blueprint blueprint = Blueprints.get(id);
            if (blueprint == null) {
                continue;
            }
            Blueprint.Variant first = blueprint.variant(0);
            plans.add(Card.of(blueprint.iconStack(), blueprint.name(), List.of(
                            blueprint.variants().size() > 1 ? blueprint.variants().size() + " tailles, au choix"
                                    : first.required() + " blocs",
                            "Un hologramme a poser"))
                    .accent(0xFF2BC4C4).action("plan:" + id + "|recevoir le plan"));
        }
        if (!plans.isEmpty()) {
            sections.add(new MenuView.Section("PLANS DE CONSTRUCTION", "craft", MenuView.GRID, plans));
        }

        List<Card> commands = new ArrayList<>();
        for (String line : entry.commands()) {
            int split = line.indexOf(" : ");
            String command = split < 0 ? line : line.substring(0, split);
            String description = split < 0 ? "" : line.substring(split + 3);
            boolean runnable = command.startsWith("/") && !command.contains("<") && !command.contains("[");
            Card card = Card.of(new ItemStack(command.startsWith("/") ? Items.COMMAND_BLOCK : Items.STONE_BUTTON),
                    command, description.isEmpty() ? List.of() : List.of(description)).accent(0xFF55545C);
            if (runnable) {
                card = card.action("cmd:" + command + "|lancer");
            }
            commands.add(card);
        }
        if (!commands.isEmpty()) {
            sections.add(new MenuView.Section("COMMANDES ET TOUCHES", "", MenuView.ROWS, commands));
        }

        List<Card> see = new ArrayList<>();
        EncyclopediaData data = EncyclopediaEvents.data(player);
        for (String id : entry.see()) {
            Encyclopedia.Entry other = Encyclopedia.entry(id);
            if (other != null) {
                see.add(card(other, data));
            }
        }
        if (!see.isEmpty()) {
            sections.add(new MenuView.Section("VOIR AUSSI", "", MenuView.GRID, see));
        }
        return new MenuView(id(), entry.title(), category.name(), progress.stats(), sections, true);
    }

    // -- Les clics ------------------------------------------------------------------------

    @Override
    protected void back(ServerPlayer player) {
        if (!PAGES.getOrDefault(player.getUUID(), "").isEmpty()) {
            openIndex(player);
        } else {
            super.back(player);
        }
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("fiche:")) {
            openEntry(player, key.substring(6));
        } else if (key.startsWith("plan:")) {
            givePlan(player, key.substring(5));
        } else if (key.startsWith("cmd:")) {
            run(player, key.substring(4));
        }
    }

    /** Donne un plan de cette structure, sauf s'il y en a deja un dans le sac. */
    private static void givePlan(ServerPlayer player, String id) {
        Blueprint blueprint = Blueprints.get(id);
        if (blueprint == null) {
            return;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof PlanItem && PlanItem.data(stack).blueprint().equals(id)) {
                report(player, "Tu as deja ce plan dans ton sac : " + blueprint.name() + ".", null);
                return;
            }
        }
        ItemStack plan = PlanItem.of(id, 0);
        if (!inventory.add(plan)) {
            player.drop(plan, false);
        }
        report(player, null, "Plan recu : " + blueprint.name() + ". Clic droit sur un bloc pour le poser.");
    }

    /** Lance une commande de la fiche ouverte, et seulement elle : l'action vient du client. */
    private static void run(ServerPlayer player, String command) {
        Encyclopedia.Entry entry = Encyclopedia.entry(PAGES.getOrDefault(player.getUUID(), ""));
        if (entry == null) {
            return;
        }
        boolean listed = false;
        for (String line : entry.commands()) {
            int split = line.indexOf(" : ");
            if ((split < 0 ? line : line.substring(0, split)).equals(command)) {
                listed = true;
            }
        }
        if (!listed) {
            return;
        }
        UiNet.close(player);
        player.sendSystemMessage(Component.literal("> " + command).withStyle(ChatFormatting.DARK_GRAY));
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }
}
