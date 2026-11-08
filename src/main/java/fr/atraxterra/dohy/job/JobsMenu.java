package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.Hub;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Les quatre metiers, et le detail de chacun.
 *
 * <p>Deux vues. La grille dit ou en est chaque metier d'un coup d'oeil ; la fiche
 * d'un metier dit ce que les prochains niveaux apportent. Un niveau apporte
 * trois choses, et la fiche a un onglet pour chacune :
 * <ul>
 *   <li><b>deblocages</b> — les machines et objets que le niveau autorise ;</li>
 *   <li><b>perks</b> — un bonus tous les cinq niveaux ;</li>
 *   <li><b>recompenses</b> — les objets que Paladium donnait a ce palier.</li>
 * </ul>
 * L'argent verse a chaque niveau tient dans la vignette : c'est volontairement
 * la plus petite des trois recompenses.
 *
 * <p>Un deblocage ou une recompense dont l'objet n'existe pas encore est marque
 * comme tel : le joueur voit ce que le mod promet sans encore le livrer, plutot
 * que de decouvrir un trou en jeu.
 */
public final class JobsMenu extends DisplayMenu {

    private static final int SLOT_HEADER = 4;
    private static final int[] SLOTS_GRID = {19, 21, 23, 25};
    private static final int[] SLOTS_LIST = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int SLOT_BACK = 45;
    private static final int SLOT_TAB_UNLOCKS = 46;
    private static final int SLOT_TAB_PERKS = 47;
    private static final int SLOT_TAB_REWARDS = 48;
    private static final int SLOT_CLOSE = 49;

    private enum Tab { UNLOCKS, PERKS, REWARDS }

    /** Metier ouvert, ou {@code null} pour la grille. */
    private Job selected;
    private Tab tab = Tab.UNLOCKS;

    public JobsMenu(int containerId, Inventory inventory, ServerPlayer player) {
        super(containerId, inventory, player, 6);
        refresh();
    }

    @Override
    protected void onClick(int slot, int button, boolean shift) {
        if (slot == SLOT_CLOSE) {
            click(0.6f);
            player.closeContainer();
            return;
        }
        if (slot == SLOT_BACK) {
            click(0.7f);
            if (selected == null) {
                Hub.open(player);
            } else {
                selected = null;
            }
            return;
        }
        if (selected != null) {
            Tab next = switch (slot) {
                case SLOT_TAB_UNLOCKS -> Tab.UNLOCKS;
                case SLOT_TAB_PERKS -> Tab.PERKS;
                case SLOT_TAB_REWARDS -> Tab.REWARDS;
                default -> null;
            };
            if (next != null && next != tab) {
                tab = next;
                click(1.1f);
            }
            return;
        }
        for (int index = 0; index < SLOTS_GRID.length && index < Job.values().length; index++) {
            if (SLOTS_GRID[index] == slot) {
                selected = Job.values()[index];
                tab = Tab.UNLOCKS;
                click(0.9f);
                return;
            }
        }
    }

    @Override
    protected void build() {
        frame();
        if (selected == null) {
            buildGrid();
        } else {
            buildDetail(selected);
        }
        set(SLOT_BACK, Gui.back(selected == null ? "le menu" : "les metiers"));
        set(SLOT_CLOSE, Gui.close());
    }

    // -- Grille --------------------------------------------------------------

    private void buildGrid() {
        int maxLevel = JobApi.maxLevel();
        int unlocked = 0;
        int total = 0;

        for (int index = 0; index < SLOTS_GRID.length && index < Job.values().length; index++) {
            Job job = Job.values()[index];
            set(SLOTS_GRID[index], tile(job, maxLevel));
            for (JobConfig.Unlock unlock : JobGate.unlocksOf(job)) {
                total++;
                if (JobGate.allows(player, unlock)) {
                    unlocked++;
                }
            }
        }

        int levels = JobApi.totalLevels(player);
        int cap = maxLevel * Job.values().length;
        set(SLOT_HEADER, Gui.button(Items.NETHER_STAR,
                Component.literal("Tes metiers").withStyle(ChatFormatting.GOLD), Gui.lore(
                        Gui.field("niveaux cumules", levels + " / " + cap),
                        Gui.bar(levels, cap, 20),
                        Gui.field("deblocages acquis", unlocked + " / " + total),
                        Gui.blank(),
                        Gui.hint("chaque niveau verse de l'argent ;"),
                        Gui.hint("les paliers donnent perks et objets,"),
                        Gui.hint("et ouvrent machines et outils"),
                        Gui.blank(),
                        Gui.action("clic sur un metier pour sa fiche"))));
    }

    private ItemStack tile(Job job, int maxLevel) {
        int level = JobApi.level(player, job);
        List<Component> lore = new ArrayList<>();
        lore.add(Gui.field("niveau", level + " / " + maxLevel));
        if (level >= maxLevel) {
            lore.add(Gui.good("niveau maximum atteint"));
        } else {
            lore.add(Gui.bar(JobApi.progress(player, job), 20));
            long into = JobApi.xp(player, job) - JobApi.xpToReach(level);
            long need = JobApi.xpForLevel(level + 1);
            lore.add(Gui.hint("  " + MoneyFormat.exact(into) + " / "
                    + MoneyFormat.exact(need) + " XP"));
            lore.add(Gui.field("prochain niveau",
                    "+" + MoneyFormat.exact(JobApi.moneyForLevel(level + 1))));
        }

        lore.add(Gui.blank());
        JobConfig.Unlock next = JobGate.next(player, job);
        if (next == null) {
            lore.add(Gui.good("tout est debloque"));
        } else {
            lore.add(Gui.hint("deblocage au niveau " + next.level() + " :"));
            lore.add(Component.literal("  ").append(JobApi.name(next))
                    .withStyle(ChatFormatting.AQUA));
        }

        JobProgression.Perk perk = nextPerk(job, level);
        if (perk != null) {
            lore.add(Gui.hint("perk au niveau " + perk.level() + " :"));
            lore.add(Component.literal("  " + JobApi.describePerk(perk))
                    .withStyle(ChatFormatting.AQUA));
        }

        lore.add(Gui.blank());
        lore.add(Gui.action("clic pour la fiche complete"));

        return Gui.button(icon(job),
                Component.literal(job.display() + "  " + level)
                        .withStyle(level >= maxLevel ? ChatFormatting.GOLD : ChatFormatting.YELLOW),
                lore);
    }

    // -- Fiche ---------------------------------------------------------------

    private void buildDetail(Job job) {
        int level = JobApi.level(player, job);
        int maxLevel = JobApi.maxLevel();
        List<JobConfig.Unlock> unlocks = JobGate.unlocksOf(job);
        List<JobProgression.Perk> perks = perksOf(job);

        List<Component> head = new ArrayList<>();
        head.add(Gui.field("niveau", level + " / " + maxLevel));
        if (level < maxLevel) {
            head.add(Gui.bar(JobApi.progress(player, job), 20));
            head.add(Gui.field("prochain niveau",
                    "+" + MoneyFormat.exact(JobApi.moneyForLevel(level + 1))));
        } else {
            head.add(Gui.good("niveau maximum atteint"));
        }
        head.add(Gui.field("XP totale", MoneyFormat.exact(JobApi.xp(player, job))));
        head.add(Gui.blank());
        head.add(Gui.field("deblocages", countUnlocks(unlocks) + " / " + unlocks.size()));
        head.add(Gui.field("perks acquis", countPerks(perks, level) + " / " + perks.size()));
        set(SLOT_HEADER, Gui.button(icon(job),
                Component.literal(job.display()).withStyle(ChatFormatting.GOLD), head));

        set(SLOT_TAB_UNLOCKS, tabButton(Tab.UNLOCKS, Items.IRON_DOOR, "Deblocages"));
        set(SLOT_TAB_PERKS, tabButton(Tab.PERKS, Items.ENCHANTED_BOOK, "Perks"));
        set(SLOT_TAB_REWARDS, tabButton(Tab.REWARDS, Items.CHEST, "Recompenses"));

        switch (tab) {
            case UNLOCKS -> fill(unlocks.stream().map(unlock -> unlockTile(unlock, level)).toList(),
                    "Aucun deblocage", "ce metier n'ouvre encore rien");
            case PERKS -> fill(perks.stream().map(perk -> perkTile(perk, level)).toList(),
                    "Aucun perk declare", "ce metier ne rapporte que de l'argent");
            case REWARDS -> fill(rewardTiles(job, level),
                    "Aucune recompense", "ce metier ne donne pas d'objets");
        }
    }

    private ItemStack tabButton(Tab target, Item icon, String label) {
        boolean active = tab == target;
        return Gui.button(icon,
                Component.literal(label).withStyle(active ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                Gui.lore(active ? Gui.good("onglet ouvert") : Gui.action("clic pour afficher")));
    }

    /**
     * Range des vignettes dans la zone de contenu.
     *
     * <p>Le dernier emplacement annonce le debordement plutot que de laisser des
     * vignettes disparaitre sans bruit si une table s'allonge.
     */
    private void fill(List<ItemStack> tiles, String emptyTitle, String emptyHint) {
        if (tiles.isEmpty()) {
            set(SLOTS_LIST[0], Gui.button(Items.PAPER, emptyTitle, Gui.lore(Gui.hint(emptyHint))));
            return;
        }
        int shown = Math.min(tiles.size(), SLOTS_LIST.length);
        int overflow = tiles.size() - shown;
        if (overflow > 0) {
            shown--;
            overflow++;
        }
        for (int index = 0; index < shown; index++) {
            set(SLOTS_LIST[index], tiles.get(index));
        }
        if (overflow > 0) {
            set(SLOTS_LIST[shown], Gui.button(Items.PAPER,
                    "+ " + overflow + " non affiche(s)", Gui.lore(
                            Gui.hint("l'ecran est plein"),
                            Gui.hint("voir /metiers " + selected.key()))));
        }
    }

    /**
     * La vignette d'un deblocage.
     *
     * <p>L'icone est l'item lui-meme quand il existe — c'est l'information la
     * plus directe possible. Quand il n'existe pas encore, on ne peut pas
     * l'afficher : la vignette le dit au lieu de montrer un cube manquant.
     */
    private ItemStack unlockTile(JobConfig.Unlock unlock, int level) {
        boolean acquired = level >= unlock.level();
        boolean real = !unlock.isTag() && JobConfig.exists(unlock.plainId());

        List<Component> lore = Gui.lore(
                Gui.field("niveau requis", String.valueOf(unlock.level())),
                acquired ? Gui.good("debloque") : Gui.bad("verrouille — il manque "
                        + (unlock.level() - level) + " niveau(x)"),
                unlock.isTag() ? Gui.hint("porte sur un tag : " + unlock.plainId()) : null,
                real || unlock.isTag() ? null : Gui.blank(),
                real || unlock.isTag() ? null : Gui.hint("item pas encore ajoute par le mod"));

        Item fallback = acquired ? Items.PAPER : Items.BOOK;
        Item icon = real ? itemOf(unlock.plainId(), fallback) : fallback;

        return Gui.button(icon,
                JobApi.name(unlock).copy().withStyle(acquired
                        ? ChatFormatting.GREEN
                        : ChatFormatting.DARK_GRAY),
                lore,
                // La pile porte le niveau requis : lisible sans survol, et c'est
                // la question la plus frequente devant cette liste.
                Math.max(1, Math.min(64, unlock.level())));
    }

    /**
     * La vignette d'un perk.
     *
     * <p>Un perk declare mais pas applique par le mod est marque comme tel :
     * mieux vaut l'avouer dans l'interface qu'afficher un bonus qui ne produit
     * rien en jeu.
     */
    private ItemStack perkTile(JobProgression.Perk perk, int level) {
        boolean unlocked = level >= perk.level();
        boolean active = JobProgression.isImplemented(perk.type());

        List<Component> lore = Gui.lore(
                Gui.field("niveau requis", String.valueOf(perk.level())),
                Gui.blank(),
                unlocked ? Gui.good("acquis") : Gui.bad("verrouille"),
                active ? null : Gui.hint("effet pas encore applique par le mod"));

        return Gui.button(unlocked ? Items.ENCHANTED_BOOK : Items.BOOK,
                Component.literal(JobApi.describePerk(perk))
                        .withStyle(unlocked
                                ? (active ? ChatFormatting.AQUA : ChatFormatting.GRAY)
                                : ChatFormatting.DARK_GRAY),
                lore,
                Math.max(1, Math.min(64, perk.level())));
    }

    /**
     * Une vignette par palier : Paladium donne souvent plusieurs objets au meme
     * niveau (lingots et trixium a chaque palier), et une vignette par objet
     * deborderait l'ecran des le Mineur.
     */
    private List<ItemStack> rewardTiles(Job job, int level) {
        Map<Integer, List<JobConfig.Reward>> byLevel = new TreeMap<>();
        for (JobConfig.Reward reward : JobConfig.config().rewardsOf(job)) {
            byLevel.computeIfAbsent(reward.level(), ignored -> new ArrayList<>()).add(reward);
        }

        List<ItemStack> tiles = new ArrayList<>();
        byLevel.forEach((rewardLevel, rewards) -> {
            boolean acquired = level >= rewardLevel;
            List<Component> lore = new ArrayList<>();
            lore.add(acquired ? Gui.good("recu") : Gui.bad("au niveau " + rewardLevel));
            lore.add(Gui.blank());
            Item icon = null;
            for (JobConfig.Reward reward : rewards) {
                boolean real = JobConfig.exists(reward.id());
                if (real && icon == null) {
                    icon = itemOf(reward.id(), Items.CHEST);
                }
                Component line = Component.literal("  " + reward.count() + "x ")
                        .append(JobApi.name(reward))
                        .withStyle(real ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY);
                lore.add(line);
                if (!reward.enchantments().isEmpty()) {
                    lore.add(Gui.hint("    enchante"));
                }
                if (!real) {
                    lore.add(Gui.hint("    pas encore ajoute par le mod"));
                }
            }
            tiles.add(Gui.button(icon == null ? Items.CHEST : icon,
                    Component.literal("Niveau " + rewardLevel)
                            .withStyle(acquired ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                    lore,
                    Math.max(1, Math.min(64, rewardLevel))));
        });
        return tiles;
    }

    // -- Outils --------------------------------------------------------------

    private static List<JobProgression.Perk> perksOf(Job job) {
        List<JobProgression.Perk> perks = new ArrayList<>(JobProgression.config().def(job).perks());
        perks.sort(Comparator.comparingInt(JobProgression.Perk::level));
        return perks;
    }

    private int countUnlocks(List<JobConfig.Unlock> unlocks) {
        int count = 0;
        for (JobConfig.Unlock unlock : unlocks) {
            if (JobGate.allows(player, unlock)) {
                count++;
            }
        }
        return count;
    }

    private static int countPerks(List<JobProgression.Perk> perks, int level) {
        int count = 0;
        for (JobProgression.Perk perk : perks) {
            if (level >= perk.level()) {
                count++;
            }
        }
        return count;
    }

    private static JobProgression.Perk nextPerk(Job job, int level) {
        JobProgression.Perk best = null;
        for (JobProgression.Perk perk : JobProgression.config().def(job).perks()) {
            if (perk.level() > level && (best == null || perk.level() < best.level())) {
                best = perk;
            }
        }
        return best;
    }

    private static Item itemOf(String raw, Item fallback) {
        Identifier id = JobConfig.parse(raw);
        return id == null ? fallback : BuiltInRegistries.ITEM.getOptional(id).orElse(fallback);
    }

    static Item icon(Job job) {
        return switch (job) {
            case MINER -> Items.IRON_PICKAXE;
            case HUNTER -> Items.BOW;
            case FARMER -> Items.IRON_HOE;
            case ALCHEMIST -> Items.BREWING_STAND;
        };
    }
}
