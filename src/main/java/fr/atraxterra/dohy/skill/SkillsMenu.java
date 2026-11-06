package fr.atraxterra.dohy.skill;

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
import java.util.Comparator;
import java.util.List;

/**
 * Les huit skills, et le detail de chacun.
 *
 * <p>Deux vues. La grille dit ou en est chaque skill d'un coup d'oeil ; la fiche
 * d'un skill dit ce que les prochains niveaux apportent. C'est la deuxieme qui
 * compte : un niveau de skill ne paye presque rien en argent — c'est volontaire,
 * les huit skills maxes rapportent moins qu'un seul palier d'autosell — donc
 * l'ecran doit montrer les perks, qui sont la vraie recompense.
 */
public final class SkillsMenu extends DisplayMenu {

    private static final int SLOT_HEADER = 4;
    private static final int[] SLOTS_GRID = {19, 21, 23, 25, 28, 30, 32, 34};
    private static final int[] SLOTS_PERKS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLOSE = 49;

    /** Skill ouvert, ou {@code null} pour la grille. */
    private Skill selected;

    public SkillsMenu(int containerId, Inventory inventory, ServerPlayer player) {
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
        for (int index = 0; index < SLOTS_GRID.length; index++) {
            if (SLOTS_GRID[index] == slot) {
                selected = Skill.values()[index];
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
        set(SLOT_BACK, Gui.back(selected == null ? "le menu" : "les skills"));
        set(SLOT_CLOSE, Gui.close());
    }

    private void buildGrid() {
        int maxLevel = SkillConfig.config().curve().maxLevel();
        int totalLevels = 0;
        int maxed = 0;

        for (int index = 0; index < SLOTS_GRID.length && index < Skill.values().length; index++) {
            Skill skill = Skill.values()[index];
            int level = SkillApi.levelOf(player, skill);
            totalLevels += level;
            if (level >= maxLevel) {
                maxed++;
            }
            set(SLOTS_GRID[index], tile(skill, level, maxLevel));
        }

        int cap = maxLevel * Skill.values().length;
        set(SLOT_HEADER, Gui.button(Items.NETHER_STAR, "Tes skills", Gui.lore(
                Gui.field("niveaux cumules", totalLevels + " / " + cap),
                Gui.bar(totalLevels, cap, 20),
                Gui.blank(),
                maxed > 0
                        ? Gui.good(maxed + " skill(s) au maximum")
                        : Gui.hint("aucun skill au maximum"),
                Gui.blank(),
                Gui.hint("un skill est un bonus, pas un revenu :"),
                Gui.hint("les perks valent plus que l'argent verse"),
                Gui.blank(),
                Gui.action("clic sur un skill pour sa fiche"))));
    }

    private ItemStack tile(Skill skill, int level, int maxLevel) {
        List<Component> lore = new ArrayList<>();
        lore.add(Gui.field("niveau", level + " / " + maxLevel));
        if (level >= maxLevel) {
            lore.add(Gui.good("niveau maximum atteint"));
        } else {
            lore.add(Gui.bar(SkillApi.progress(player, skill), 20));
            long into = SkillApi.data(player).xpOf(skill) - SkillApi.xpToReach(level);
            long need = SkillApi.xpForLevel(level + 1);
            lore.add(Gui.hint("  " + MoneyFormat.exact(into) + " / "
                    + MoneyFormat.exact(need) + " XP"));
            lore.add(Gui.blank());
            lore.add(Gui.field("prochain niveau",
                    "+" + MoneyFormat.exact(SkillApi.moneyForLevel(level + 1))));
        }

        SkillConfig.Perk next = nextPerk(skill, level);
        lore.add(Gui.blank());
        if (next == null) {
            lore.add(Gui.hint("plus aucun perk a venir"));
        } else {
            lore.add(Gui.hint("perk au niveau " + next.level() + " :"));
            lore.add(Component.literal("  " + SkillApi.describePerk(next))
                    .withStyle(ChatFormatting.AQUA));
        }
        lore.add(Gui.blank());
        lore.add(Gui.action("clic pour la fiche complete"));

        return Gui.button(icon(skill),
                Component.literal(skill.display() + "  " + level)
                        .withStyle(level >= maxLevel ? ChatFormatting.GOLD : ChatFormatting.YELLOW),
                lore);
    }

    private void buildDetail(Skill skill) {
        int maxLevel = SkillConfig.config().curve().maxLevel();
        int level = SkillApi.levelOf(player, skill);
        List<SkillConfig.Perk> perks = new ArrayList<>(SkillConfig.config().def(skill).perks());
        perks.sort(Comparator.comparingInt(SkillConfig.Perk::level));

        List<Component> head = new ArrayList<>();
        head.add(Gui.field("niveau", level + " / " + maxLevel));
        if (level < maxLevel) {
            head.add(Gui.bar(SkillApi.progress(player, skill), 20));
            head.add(Gui.field("prochain niveau",
                    "+" + MoneyFormat.exact(SkillApi.moneyForLevel(level + 1))));
        } else {
            head.add(Gui.good("niveau maximum atteint"));
        }
        head.add(Gui.blank());
        head.add(Gui.field("XP totale", MoneyFormat.exact(SkillApi.data(player).xpOf(skill))));
        head.add(Gui.field("perks acquis", countUnlocked(perks, level) + " / " + perks.size()));
        set(SLOT_HEADER, Gui.button(icon(skill), skill.display(), head));

        if (perks.isEmpty()) {
            set(SLOTS_PERKS[0], Gui.button(Items.PAPER, "Aucun perk declare", Gui.lore(
                    Gui.hint("ce skill ne rapporte que de l'argent"),
                    Gui.hint("et l'XP de niveau global"))));
            return;
        }

        // Vingt perks par skill pour vingt-et-un emplacements : ca tient, mais de
        // justesse. Le dernier emplacement annonce le debordement plutot que de
        // laisser des perks disparaitre sans bruit si la table s'allonge.
        int shown = Math.min(perks.size(), SLOTS_PERKS.length);
        int overflow = perks.size() - shown;
        if (overflow > 0) {
            shown--;
            overflow++;
        }
        for (int index = 0; index < shown; index++) {
            set(SLOTS_PERKS[index], perkTile(perks.get(index), level));
        }
        if (overflow > 0) {
            set(SLOTS_PERKS[shown], Gui.button(Items.PAPER,
                    "+ " + overflow + " perk(s) non affiches", Gui.lore(
                            Gui.hint("l'ecran est plein"),
                            Gui.hint("voir /skills list pour la liste complete"))));
        }
    }

    /**
     * La vignette d'un perk.
     *
     * <p>Un perk declare mais pas encore applique par le mod est marque comme
     * tel : mieux vaut l'avouer dans l'interface qu'afficher un bonus qui ne
     * produit rien en jeu.
     */
    private ItemStack perkTile(SkillConfig.Perk perk, int level) {
        boolean unlocked = level >= perk.level();
        boolean active = SkillConfig.isImplemented(perk.type());

        List<Component> lore = Gui.lore(
                Gui.field("niveau requis", String.valueOf(perk.level())),
                Gui.blank(),
                unlocked ? Gui.good("acquis") : Gui.bad("verrouille"),
                active ? null : Gui.hint("effet pas encore applique par le mod"));

        return Gui.button(unlocked ? Items.ENCHANTED_BOOK : Items.BOOK,
                Component.literal(SkillApi.describePerk(perk))
                        .withStyle(unlocked
                                ? (active ? ChatFormatting.AQUA : ChatFormatting.GRAY)
                                : ChatFormatting.DARK_GRAY),
                lore);
    }

    private static int countUnlocked(List<SkillConfig.Perk> perks, int level) {
        int count = 0;
        for (SkillConfig.Perk perk : perks) {
            if (level >= perk.level()) {
                count++;
            }
        }
        return count;
    }

    private SkillConfig.Perk nextPerk(Skill skill, int level) {
        SkillConfig.Perk best = null;
        for (SkillConfig.Perk perk : SkillConfig.config().def(skill).perks()) {
            if (perk.level() > level && (best == null || perk.level() < best.level())) {
                best = perk;
            }
        }
        return best;
    }

    private static Item icon(Skill skill) {
        return switch (skill) {
            case MINING -> Items.IRON_PICKAXE;
            case COMBAT -> Items.IRON_SWORD;
            case FARMING -> Items.IRON_HOE;
            case FORAGING -> Items.IRON_AXE;
            case FISHING -> Items.FISHING_ROD;
            case SMITHING -> Items.ANVIL;
            case BUILDING -> Items.BRICKS;
            case NUCLEAR -> Items.REDSTONE_BLOCK;
        };
    }
}
