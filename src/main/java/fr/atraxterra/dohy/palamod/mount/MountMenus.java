package fr.atraxterra.dohy.palamod.mount;

import fr.atraxterra.dohy.palamod.golem.PalaGolem;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Les ecrans de la monture, en coffres comme le reste de DoHy.
 *
 * <p>Mount dessinait quatre ecrans plein cadre avec sa bibliotheque apollon
 * (accueil, inventaire, nourriture, enclos et depot). On garde leurs actions,
 * pas leur dessin :
 * <ul>
 *   <li><b>accueil</b> (touche K ou {@code /mount}) : etat de la monture,
 *       invoquer, nourrir, inventaire, partage d'XP ;</li>
 *   <li><b>inventaire</b> : {@link MountInventoryMenu}, aussi par la touche
 *       d'inventaire en selle ;</li>
 *   <li><b>enclos</b> : les gemmes deposees, avec equiper, recuperer, soigner,
 *       et la monture equipee a desequiper ;</li>
 *   <li><b>soins</b> : le nombre de bandages.</li>
 * </ul>
 * Le nom se change par {@code /mount nom}, le champ de saisie de l'original
 * n'ayant pas d'equivalent dans un coffre.
 */
public final class MountMenus {

    private MountMenus() {
    }

    public static void openHome(ServerPlayer player) {
        if (!MountManager.data(player).isEquipped()) {
            MountTexts.error(player, "none_equipped");
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new Home(id, inventory, player),
                Component.literal("Monture")));
    }

    /** {@code /mount inventory} : la monture invoquee, a cinq blocs au plus. */
    public static void openInventory(ServerPlayer player) {
        Dancarok mount = MountManager.summoned(player);
        if (mount == null) {
            MountTexts.error(player, "not_summoned");
            return;
        }
        if (!player.getBoundingBox().inflate(5.0d).intersects(mount.getBoundingBox())) {
            MountTexts.error(player, "not_nearby");
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new MountInventoryMenu(id, inventory, player),
                Component.literal("Inventaire de " + MountManager.data(player).name())));
    }

    public static void openBarn(ServerPlayer player, BarnControllerBlockEntity barn) {
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new Barn(id, inventory, player, barn),
                Component.literal("Enclos")));
    }

    /** La fiche d'une monture : gemme ou monture equipee. */
    private static List<Component> sheet(String name, int level, float xp, float damage, int food, int shared) {
        float max = MountLevels.maxHealth(level);
        double next = MountLevels.nextThreshold(level);
        return Gui.lore(
                Gui.field("Nom", name),
                Gui.field("Niveau", level + " / " + MountLevels.MAX_LEVEL),
                Component.literal("XP ").withStyle(ChatFormatting.GRAY)
                        .append(level >= MountLevels.MAX_LEVEL ? Gui.good("maximum") : Gui.bar(xp / next, 20)),
                Component.literal("Vie ").withStyle(ChatFormatting.GRAY)
                        .append(Gui.bar(max <= 0 ? 0 : (max - damage) / max, 20))
                        .append(Component.literal("  (" + Math.round(Math.max(0, max - damage)) + " / "
                                + Math.round(max) + ")").withStyle(ChatFormatting.DARK_GRAY)),
                food < 0 ? null : Component.literal("Energie ").withStyle(ChatFormatting.GRAY)
                        .append(Gui.bar(food, MountData.MAX_FOOD, 20)),
                Gui.field("XP partagee", shared + " %"),
                Gui.field("Cases", String.valueOf(MountLevels.slots(level))));
    }

    // -- Accueil -------------------------------------------------------------------

    private static final class Home extends DisplayMenu {

        private static final int INFO = 13;
        private static final int SUMMON = 29;
        private static final int FEED = 31;
        private static final int INVENTORY = 33;
        private static final int XP_DOWN = 38;
        private static final int XP = 40;
        private static final int XP_UP = 42;
        private static final int SKILL = 22;

        Home(int containerId, Inventory inventory, ServerPlayer player) {
            super(containerId, inventory, player, 6);
            refresh();
        }

        @Override
        protected void build() {
            frame();
            MountData data = MountManager.data(player);
            if (!data.isEquipped()) {
                set(INFO, Gui.button(Items.BARRIER, Gui.bad("Aucune monture equipee"), List.of()));
                set(Gui.FOOTER_CLOSE, Gui.close());
                return;
            }
            boolean summoned = MountManager.summoned(player) != null;
            ItemStack info = new ItemStack(PalaMount.MOUNT_GEMSTONE.get());
            info.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(
                    List.of(), List.of(true), List.of(), List.of()));
            info.set(DataComponents.CUSTOM_NAME, Component.literal(data.name()).withStyle(ChatFormatting.GOLD));
            info.set(DataComponents.LORE, new ItemLore(sheet(data.name(), data.level(), data.xp(), data.damage(),
                    data.food(), data.sharedXp())));
            set(INFO, info);

            set(SKILL, Gui.button(Items.IRON_PICKAXE, "Miner", Gui.lore(
                    Gui.hint("Capacite de miner des blocs"),
                    Gui.hint("Monte, clic droit maintenu, avec une corne"))));

            List<Component> summonLore = new ArrayList<>();
            if (data.level() < MountLevels.SUMMON_LEVEL) {
                summonLore.add(Gui.bad("Niveau " + MountLevels.SUMMON_LEVEL + " requis"));
            }
            summonLore.add(Gui.hint("Restez immobile trois secondes"));
            summonLore.add(Gui.action(summoned ? "Clic : desinvoquer (en selle)" : "Clic : invoquer"));
            set(SUMMON, Gui.button(summoned ? Items.ENDER_PEARL : Items.ENDER_EYE,
                    summoned ? "Desinvoquer" : "Invoquer", summonLore));

            int croquettes = MountManager.count(player.getInventory(), PalaGolem.CROQUETTE.get().getDefaultInstance());
            set(FEED, Gui.button(PalaGolem.CROQUETTE.get(), Component.literal("Nourrir")
                    .withStyle(ChatFormatting.YELLOW), Gui.lore(
                    Gui.field("Energie", data.food() + " / " + MountData.MAX_FOOD),
                    Gui.hint("+" + MountManager.FOOD_PER_CROQUETTE + " par croquette ; un point par bloc parcouru"),
                    Gui.field("Croquettes sur vous", String.valueOf(croquettes)),
                    Gui.action("Clic : une croquette"),
                    Gui.action("Maj + clic : remplir"))));

            set(INVENTORY, Gui.button(Items.CHEST, "Inventaire", Gui.lore(
                    Gui.field("Cases", String.valueOf(data.slots())),
                    data.horn().isEmpty() ? Gui.bad("Pas de corne") : Gui.good("Corne posee"),
                    summoned ? Gui.action("Clic : ouvrir") : Gui.bad("Invoquez d'abord la monture"))));

            set(XP_DOWN, Gui.button(Items.FLINT, "Moins d'XP pour la monture", Gui.lore(
                    Gui.action("Clic : -10 %"), Gui.action("Clic droit : -1 %"))));
            set(XP, Gui.button(Items.EXPERIENCE_BOTTLE, Component.literal("XP partagee : " + data.sharedXp() + " %")
                    .withStyle(ChatFormatting.YELLOW), Gui.lore(
                    Gui.hint("Part des orbes d'XP ramassees qui va a la monture"),
                    Gui.hint("Renommer : /mount nom <nom>"))));
            set(XP_UP, Gui.button(Items.EMERALD, "Plus d'XP pour la monture", Gui.lore(
                    Gui.action("Clic : +10 %"), Gui.action("Clic droit : +1 %"))));
            set(Gui.FOOTER_CLOSE, Gui.close());
        }

        @Override
        protected void onClick(int slot, int button, boolean shift) {
            MountData data = MountManager.data(player);
            switch (slot) {
                case SUMMON -> {
                    player.closeContainer();
                    MountManager.requestSummon(player);
                }
                case FEED -> {
                    MountManager.feed(player, shift);
                    click();
                }
                case INVENTORY -> openInventory(player);
                case XP_DOWN, XP_UP -> {
                    int step = (button == 1 ? 1 : 10) * (slot == XP_UP ? 1 : -1);
                    data.setSharedXp(data.sharedXp() + step);
                    click();
                }
                case Gui.FOOTER_CLOSE -> player.closeContainer();
                default -> {
                }
            }
        }
    }

    // -- Enclos --------------------------------------------------------------------

    private static final class Barn extends DisplayMenu {

        private static final int STATUS = 1;
        private static final int EQUIPPED = 4;
        private static final int DEPOSIT = 7;
        private static final int FIRST_GEM = 9;
        private static final int GEM_SLOTS = 36;

        private final BarnControllerBlockEntity barn;

        Barn(int containerId, Inventory inventory, ServerPlayer player, BarnControllerBlockEntity barn) {
            super(containerId, inventory, player, 6);
            this.barn = barn;
            refresh();
        }

        @Override
        protected void build() {
            for (int slot = 0; slot < 9; slot++) {
                set(slot, Gui.filler());
            }
            footer(Gui.FOOTER_CLOSE);
            set(Gui.FOOTER_CLOSE, Gui.close());
            if (barn == null) {
                return;
            }

            set(STATUS, Gui.button(Items.HAY_BLOCK, "Enclos " + barn.size() + "x" + barn.size(), Gui.lore(
                    Gui.field("Montures", String.format("%02d/%02d", barn.gems().size(), barn.capacity())),
                    barn.hasIncubator() ? Gui.good("Couveuse : oui") : Gui.bad("Couveuse : non"),
                    Gui.field("En soin", barn.inCare() + " / " + BarnControllerBlockEntity.MAX_IN_CARE))));

            MountData data = MountManager.data(player);
            if (data.isEquipped()) {
                set(EQUIPPED, Gui.button(Items.SADDLE, Component.literal("Monture equipee : " + data.name())
                        .withStyle(ChatFormatting.GOLD), withAction(sheet(data.name(), data.level(), data.xp(),
                        data.damage(), data.food(), data.sharedXp()), "Clic : desequiper la monture")));
            } else {
                set(EQUIPPED, Gui.button(Items.SADDLE, "Monture equipee : aucune", List.of()));
            }

            set(DEPOSIT, Gui.button(PalaMount.MOUNT_GEMSTONE.get(), "Deposer une monture", Gui.lore(
                    Gui.hint("La gemme tenue en main,"),
                    Gui.hint("sinon la premiere de l'inventaire."),
                    Gui.hint("Une gemme sauvage est apprivoisee."),
                    Gui.action("Clic : deposer"))));

            List<MountGem> gems = barn.gems();
            for (int i = 0; i < gems.size() && i < GEM_SLOTS; i++) {
                MountGem gem = gems.get(i);
                ItemStack icon = MountGemstoneItem.stackOf(gem);
                List<Component> lore = new ArrayList<>();
                if (!gem.ownedBy(player.getUUID())) {
                    lore.add(Gui.bad("Cette monture ne vous appartient pas"));
                } else {
                    lore.add(Gui.action("Clic : equiper la monture"));
                    lore.add(Gui.action("Clic droit : recuperer la gemme"));
                    lore.add(Gui.action("Maj + clic : soigner la monture"));
                }
                icon.set(DataComponents.LORE, new ItemLore(lore));
                set(FIRST_GEM + i, icon);
            }
        }

        private static List<Component> withAction(List<Component> lines, String action) {
            List<Component> copy = new ArrayList<>(lines);
            copy.add(Gui.blank());
            copy.add(Gui.action(action));
            return copy;
        }

        @Override
        protected void onClick(int slot, int button, boolean shift) {
            if (slot == Gui.FOOTER_CLOSE) {
                player.closeContainer();
                return;
            }
            if (!barn.inRange(player)) {
                player.closeContainer();
                return;
            }
            if (slot == EQUIPPED) {
                barn.unequip(player);
            } else if (slot == DEPOSIT) {
                barn.deposit(player);
            } else if (slot >= FIRST_GEM && slot < FIRST_GEM + GEM_SLOTS) {
                int index = slot - FIRST_GEM;
                if (index >= barn.gems().size()) {
                    return;
                }
                if (shift) {
                    MountGem gem = barn.gems().get(index);
                    if (!gem.ownedBy(player.getUUID())) {
                        MountTexts.error(player, "not_yours");
                        return;
                    }
                    open("Soigner " + gem.name(), (id, inventory) -> new Heal(id, inventory, player, barn, gem));
                    return;
                }
                if (button == 1) {
                    barn.withdraw(player, index);
                } else {
                    barn.equip(player, index);
                }
            } else {
                return;
            }
            click();
        }
    }

    // -- Soins ---------------------------------------------------------------------

    private static final class Heal extends DisplayMenu {

        private static final int GEM = 10;
        private static final int LESS = 12;
        private static final int COUNT = 13;
        private static final int MORE = 14;
        private static final int CONFIRM = 16;
        private static final int BACK = 22;

        private final BarnControllerBlockEntity barn;
        private final MountGem gem;
        private int bandages = 1;

        Heal(int containerId, Inventory inventory, ServerPlayer player, BarnControllerBlockEntity barn, MountGem gem) {
            super(containerId, inventory, player, 3);
            this.barn = barn;
            this.gem = gem;
            refresh();
        }

        @Override
        protected void build() {
            frame();
            if (gem == null) {
                return;
            }
            set(GEM, MountGemstoneItem.stackOf(gem));
            set(LESS, Gui.button(Items.FLINT, "Retirer un bandage", List.of()));
            set(COUNT, Gui.button(PalaMount.BANDAGE.get(), Component.literal(bandages + " bandage(s)")
                    .withStyle(ChatFormatting.YELLOW), Gui.lore(
                    Gui.field("Duree", MountTexts.duration(BarnControllerBlockEntity.CARE_MS * bandages).getString()),
                    Gui.field("Soin", "+" + Math.round(gem.maxHealth() * 0.1f * bandages) + " PV a la fin"),
                    Gui.field("Sur vous", String.valueOf(owned()))), bandages));
            set(MORE, Gui.button(Items.EMERALD, "Ajouter un bandage", List.of()));
            set(CONFIRM, Gui.button(Items.GOLDEN_APPLE, "Soigner", Gui.lore(
                    Gui.hint("Une couveuse dans l'enclos,"),
                    Gui.hint("deux montures en soin au plus."),
                    Gui.action("Clic : placer en soin"))));
            set(BACK, Gui.back("l'enclos"));
        }

        private int owned() {
            return MountManager.count(player.getInventory(), PalaMount.BANDAGE.get().getDefaultInstance());
        }

        @Override
        protected void onClick(int slot, int button, boolean shift) {
            switch (slot) {
                case LESS -> {
                    if (bandages <= 1) {
                        MountTexts.error(player, "bandage_min");
                    } else {
                        bandages--;
                        click();
                    }
                }
                case MORE -> {
                    // UIBarnMountInfoNode : plafonne aux bandages possedes, et
                    // inutile au-dela de ce qui remet la monture a 100 %.
                    int useful = (int) Math.ceil(gem.damage() / Math.max(0.1f, gem.maxHealth() * 0.1f));
                    if (bandages >= owned()) {
                        MountTexts.error(player, "bandage_owned", owned());
                    } else if (bandages >= Math.max(1, useful)) {
                        MountTexts.error(player, "fully_healed");
                    } else {
                        bandages++;
                        click();
                    }
                }
                case CONFIRM -> {
                    int index = barn.gems().indexOf(gem);
                    if (index < 0 || !barn.inRange(player)) {
                        player.closeContainer();
                        return;
                    }
                    barn.heal(player, index, bandages);
                    open("Enclos", (id, inventory) -> new Barn(id, inventory, player, barn));
                }
                case BACK -> open("Enclos", (id, inventory) -> new Barn(id, inventory, player, barn));
                default -> {
                }
            }
        }
    }
}
