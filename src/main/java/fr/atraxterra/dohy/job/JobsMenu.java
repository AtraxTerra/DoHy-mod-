package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.skill.SkillApi;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.Hub;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Les quatre metiers, et la liste de ce qu'ils debloquent.
 *
 * <p>Deux vues, comme pour les skills : la grille dit ou on en est, la fiche dit
 * ce qui vient. Mais ici c'est la fiche qui porte tout l'interet du systeme — un
 * metier ne donne aucun bonus propre (le skill qui le porte s'en charge deja),
 * il ne fait qu'<b>ouvrir des portes</b>. L'ecran doit donc montrer les portes,
 * ouvertes et fermees, avec le niveau de chacune.
 *
 * <p>Choix d'affichage : un deblocage acquis apparait tel quel, un deblocage
 * verrouille en papier gris, et un deblocage dont l'item n'existe pas encore est
 * marque comme tel. Le joueur voit donc aussi ce que le mod promet sans encore
 * le livrer, plutot que de decouvrir un trou en jeu.
 */
public final class JobsMenu extends DisplayMenu {

    private static final int SLOT_HEADER = 4;
    private static final int[] SLOTS_GRID = {20, 22, 29, 31};
    private static final int[] SLOTS_LIST = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLOSE = 49;

    /** Metier ouvert, ou {@code null} pour la grille. */
    private Job selected;

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
            return;
        }
        for (int index = 0; index < SLOTS_GRID.length && index < Job.values().length; index++) {
            if (SLOTS_GRID[index] == slot) {
                selected = Job.values()[index];
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

        set(SLOT_HEADER, Gui.button(Items.SHIELD,
                Component.literal("Tes metiers").withStyle(ChatFormatting.GOLD), Gui.lore(
                        Gui.field("deblocages acquis", unlocked + " / " + total),
                        Gui.bar(unlocked, total, 20),
                        Gui.blank(),
                        Gui.hint("un metier ne donne aucun bonus propre :"),
                        Gui.hint("c'est le skill qui le porte qui paye."),
                        Gui.hint("un metier ouvre des crafts et des machines."),
                        Gui.blank(),
                        Gui.action("clic sur un metier pour sa liste"))));
    }

    private ItemStack tile(Job job, int maxLevel) {
        int level = JobApi.level(player, job);
        List<Component> lore = new ArrayList<>();
        lore.add(Gui.field("niveau", level + " / " + maxLevel));
        if (level >= maxLevel) {
            lore.add(Gui.good("niveau maximum atteint"));
        } else {
            lore.add(Gui.bar(JobApi.progress(player, job), 20));
        }
        lore.add(Gui.hint("XP partagee avec le skill " + job.skill().display()));

        List<JobConfig.Unlock> unlocks = JobGate.unlocksOf(job);
        int acquired = 0;
        for (JobConfig.Unlock unlock : unlocks) {
            if (JobGate.allows(player, unlock)) {
                acquired++;
            }
        }
        lore.add(Gui.blank());
        lore.add(Gui.field("deblocages", acquired + " / " + unlocks.size()));

        JobConfig.Unlock next = JobGate.next(player, job);
        if (next == null) {
            lore.add(Gui.good("tout est debloque"));
        } else {
            lore.add(Gui.hint("prochain, au niveau " + next.level() + " :"));
            lore.add(Component.literal("  ").append(JobApi.name(next))
                    .withStyle(ChatFormatting.AQUA));
        }

        lore.add(Gui.blank());
        lore.add(Gui.action("clic pour la liste complete"));

        return Gui.button(icon(job),
                Component.literal(job.display() + "  " + level)
                        .withStyle(level >= maxLevel ? ChatFormatting.GOLD : ChatFormatting.YELLOW),
                lore);
    }

    private void buildDetail(Job job) {
        int level = JobApi.level(player, job);
        List<JobConfig.Unlock> unlocks = JobGate.unlocksOf(job);

        set(SLOT_HEADER, Gui.button(icon(job),
                Component.literal(job.display()).withStyle(ChatFormatting.GOLD), Gui.lore(
                        Gui.field("niveau", level + " / " + JobApi.maxLevel()),
                        Gui.bar(JobApi.progress(player, job), 20),
                        Gui.field("XP totale", MoneyFormat.exact(JobApi.xp(player, job))),
                        Gui.blank(),
                        Gui.hint("monte en jouant le skill " + job.skill().display()),
                        Gui.hint("voir /skills pour ses perks"))));

        if (unlocks.isEmpty()) {
            set(SLOTS_LIST[0], Gui.button(Items.PAPER, "Aucun deblocage", Gui.lore(
                    Gui.hint("ce metier n'ouvre encore rien"),
                    Gui.hint("les chapitres machines et cultures"),
                    Gui.hint("rempliront cette liste"))));
            return;
        }

        int shown = Math.min(unlocks.size(), SLOTS_LIST.length);
        int overflow = unlocks.size() - shown;
        if (overflow > 0) {
            shown--;
            overflow++;
        }
        for (int index = 0; index < shown; index++) {
            set(SLOTS_LIST[index], unlockTile(unlocks.get(index), level));
        }
        if (overflow > 0) {
            set(SLOTS_LIST[shown], Gui.button(Items.PAPER,
                    "+ " + overflow + " deblocage(s) non affiches", Gui.lore(
                            Gui.hint("l'ecran est plein"),
                            Gui.hint("voir /metiers list " + job.key()))));
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

        // Replis pris dans le vocabulaire deja employe par SkillsMenu pour un
        // perk acquis ou verrouille : les teintures n'existent plus sous ces
        // noms en 26.2, et de toute facon deux icones de plus a retenir pour
        // le joueur n'apportaient rien.
        Item fallback = acquired ? Items.PAPER : Items.BOOK;
        Item icon = real
                ? net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getOptional(JobConfig.parse(unlock.plainId()))
                        .orElse(fallback)
                : fallback;

        return Gui.button(icon,
                JobApi.name(unlock).copy().withStyle(acquired
                        ? ChatFormatting.GREEN
                        : ChatFormatting.DARK_GRAY),
                lore,
                // La pile porte le niveau requis : lisible sans survol, et c'est
                // la question la plus frequente devant cette liste.
                Math.max(1, Math.min(64, unlock.level())));
    }

    static Item icon(Job job) {
        return switch (job) {
            case MINER -> Items.IRON_PICKAXE;
            case HUNTER -> Items.BOW;
            case FARMER -> Items.IRON_HOE;
            case ALCHEMIST -> Items.BREWING_STAND;
        };
    }

    /** Niveau cumule des quatre metiers, pour la vignette du menu central. */
    public static int totalLevels(ServerPlayer player) {
        int total = 0;
        for (Job job : Job.values()) {
            total += SkillApi.levelOf(player, job.skill());
        }
        return total;
    }
}
