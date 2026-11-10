package fr.atraxterra.dohy.palamod.mount;

import fr.atraxterra.dohy.palamod.golem.PalaGolem;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les ecrans de la monture, dans le kit d'interface : l'accueil, l'enclos et
 * les soins. L'inventaire de la monture reste un vrai conteneur.
 */
public final class MountMenus {

    private MountMenus() {
    }

    public static void openHome(ServerPlayer player) {
        if (!MountManager.data(player).isEquipped()) {
            MountTexts.error(player, "none_equipped");
            return;
        }
        Home.INSTANCE.open(player);
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
        UiNet.forget(player);
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new MountInventoryMenu(id, inventory, player),
                Component.literal("Inventaire de " + MountManager.data(player).name())));
    }

    public static void openBarn(ServerPlayer player, BarnControllerBlockEntity barn) {
        Barn.INSTANCE.barns.put(player.getUUID(), barn);
        Barn.INSTANCE.open(player);
    }

    /** La fiche d'une monture, pour les bulles. */
    private static List<String> sheet(String name, int level, float xp, float damage, int food, int shared) {
        float max = MountLevels.maxHealth(level);
        double next = MountLevels.nextThreshold(level);
        List<String> lines = new ArrayList<>();
        lines.add("Nom : " + name);
        lines.add("Niveau : " + level + " / " + MountLevels.MAX_LEVEL);
        lines.add("XP : " + (level >= MountLevels.MAX_LEVEL ? "maximum" : Math.round(xp) + " / " + Math.round(next)));
        lines.add("Vie : " + Math.round(Math.max(0, max - damage)) + " / " + Math.round(max));
        if (food >= 0) {
            lines.add("Energie : " + food + " / " + MountData.MAX_FOOD);
        }
        lines.add("XP partagee : " + shared + " %");
        lines.add("Cases : " + MountLevels.slots(level));
        return lines;
    }

    // -- Accueil -------------------------------------------------------------------

    public static final class Home extends MenuController {
        public static final Home INSTANCE = new Home();

        private Home() {
            super("mount_home");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            MountData data = MountManager.data(player);
            if (!data.isEquipped()) {
                return new MenuView(id(), "Monture", "Aucune monture equipee", List.of(), List.of(), false);
            }
            boolean summoned = MountManager.summoned(player) != null;
            float max = MountLevels.maxHealth(data.level());
            int croquettes = MountManager.count(player.getInventory(), PalaGolem.CROQUETTE.get().getDefaultInstance());

            List<Card> mount = new ArrayList<>();
            mount.add(Card.of(new ItemStack(PalaMount.MOUNT_GEMSTONE.get()), data.name(), List.of(
                            "Niveau " + data.level() + " / " + MountLevels.MAX_LEVEL,
                            "Vie " + Math.round(Math.max(0, max - data.damage())) + " / " + Math.round(max)))
                    .accent(0xFFFAC400)
                    .progress(Math.round(data.xp()), Math.round(MountLevels.nextThreshold(data.level())))
                    .tooltip(sheet(data.name(), data.level(), data.xp(), data.damage(), data.food(), data.sharedXp())));
            List<String> summonTip = new ArrayList<>();
            if (data.level() < MountLevels.SUMMON_LEVEL) {
                summonTip.add("Niveau " + MountLevels.SUMMON_LEVEL + " requis");
            }
            summonTip.add("Restez immobile trois secondes");
            mount.add(Card.of(new ItemStack(summoned ? Items.ENDER_PEARL : Items.ENDER_EYE),
                            summoned ? "Desinvoquer" : "Invoquer",
                            List.of(summoned ? "En selle" : "Trois secondes immobile", "Pres de vous"))
                    .accent(0xFF2F5BEA).action(summoned ? "summon|desinvoquer" : "summon|invoquer").tooltip(summonTip));
            mount.add(Card.of(new ItemStack(PalaGolem.CROQUETTE.get()), "Nourrir", List.of(
                            "Energie " + data.food() + " / " + MountData.MAX_FOOD, croquettes + " croquette(s) sur vous"))
                    .accent(0xFF22E558).progress(data.food(), MountData.MAX_FOOD)
                    .action("feed|une croquette").right("fill|remplir")
                    .tooltip(List.of("+" + MountManager.FOOD_PER_CROQUETTE + " par croquette",
                            "Un point par bloc parcouru")));
            mount.add(Card.of(new ItemStack(Items.CHEST), "Inventaire", List.of(
                            data.slots() + " case(s)", data.horn().isEmpty() ? "Pas de corne" : "Corne posee"))
                    .accent(0xFF2F5BEA)
                    .action(summoned ? "inventory|ouvrir" : "")
                    .state(summoned ? MenuView.NORMAL : MenuView.DISABLED)
                    .tooltip(summoned ? List.of() : List.of("Invoquez d'abord la monture")));

            List<Card> xp = List.of(
                    Card.of(new ItemStack(Items.FLINT), "Moins d'XP", List.of("Clic : -10 %", "Clic droit : -1 %"))
                            .accent(0xFFEF3826).action("xp:-10|-10 %").right("xp:-1|-1 %"),
                    Card.of(new ItemStack(Items.EXPERIENCE_BOTTLE), "XP partagee : " + data.sharedXp() + " %", List.of(
                                    "Part des orbes ramassees", "Renommer : /mount nom <nom>"))
                            .accent(0xFFFAC400).progress(data.sharedXp(), 100),
                    Card.of(new ItemStack(Items.EMERALD), "Plus d'XP", List.of("Clic : +10 %", "Clic droit : +1 %"))
                            .accent(0xFF22E558).action("xp:10|+10 %").right("xp:1|+1 %"),
                    Card.of(new ItemStack(Items.IRON_PICKAXE), "Miner", List.of("Capacite de miner des blocs",
                            "Monte, clic droit maintenu, avec une corne")));

            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("NIVEAU", String.valueOf(data.level()), 0xFFFAC400),
                    new MenuView.Stat("ENERGIE", data.food() + " / " + MountData.MAX_FOOD, 0xFF22E558));
            return new MenuView(id(), "Monture", data.name(), stats, List.of(
                    new MenuView.Section("TA MONTURE", "resources", MenuView.GRID, mount),
                    new MenuView.Section("EXPERIENCE", "upgrade", MenuView.GRID, xp)), false);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            MountData data = MountManager.data(player);
            switch (key) {
                case "summon" -> {
                    UiNet.close(player);
                    MountManager.requestSummon(player);
                }
                case "feed" -> MountManager.feed(player, false);
                case "fill" -> MountManager.feed(player, true);
                case "inventory" -> openInventory(player);
                default -> {
                    if (key.startsWith("xp:")) {
                        data.setSharedXp(data.sharedXp() + Integer.parseInt(key.substring(3)));
                    }
                }
            }
        }
    }

    // -- Enclos --------------------------------------------------------------------

    public static final class Barn extends MenuController {
        public static final Barn INSTANCE = new Barn();
        final Map<UUID, BarnControllerBlockEntity> barns = new ConcurrentHashMap<>();

        private Barn() {
            super("mount_barn");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            BarnControllerBlockEntity barn = barns.get(player.getUUID());
            if (barn == null) {
                return new MenuView(id(), "Enclos", "", List.of(), List.of(), false);
            }
            MountData data = MountManager.data(player);
            List<Card> top = new ArrayList<>();
            if (data.isEquipped()) {
                top.add(Card.of(new ItemStack(Items.SADDLE), "Equipee : " + data.name(), List.of(
                                "Niveau " + data.level(), "Clic : desequiper"))
                        .accent(0xFFFAC400).action("unequip|desequiper la monture")
                        .tooltip(sheet(data.name(), data.level(), data.xp(), data.damage(), data.food(),
                                data.sharedXp())));
            } else {
                top.add(Card.of(new ItemStack(Items.SADDLE), "Aucune monture equipee", List.of(
                        "Clic sur une gemme", "pour l'equiper")).state(MenuView.DISABLED));
            }
            top.add(Card.of(new ItemStack(PalaMount.MOUNT_GEMSTONE.get()), "Deposer une monture", List.of(
                            "La gemme tenue en main,", "sinon la premiere de l'inventaire"))
                    .accent(0xFF22E558).action("deposit|deposer")
                    .tooltip(List.of("Une gemme sauvage est apprivoisee.")));

            List<Card> gems = new ArrayList<>();
            List<MountGem> list = barn.gems();
            for (int index = 0; index < list.size() && index < 36; index++) {
                MountGem gem = list.get(index);
                Card card = Card.of(MountGemstoneItem.stackOf(gem), gem.name(), List.of(
                        "Niveau " + gem.level(), "Vie " + Math.round(Math.max(0, gem.maxHealth() - gem.damage()))
                                + " / " + Math.round(gem.maxHealth())))
                        .accent(gem.inCare() ? 0xFF22E558 : 0xFF2F5BEA)
                        .badge(gem.inCare() ? "SOIN" : "");
                if (gem.ownedBy(player.getUUID())) {
                    card = card.action("gem:" + index + "|equiper (Maj : soigner)").right("withdraw:" + index
                            + "|recuperer la gemme");
                } else {
                    card = card.state(MenuView.DISABLED).tooltip(List.of("Cette monture ne vous appartient pas"));
                }
                gems.add(card);
            }
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("MONTURES", String.format("%02d/%02d", list.size(), barn.capacity()), 0xFFFFFFFF),
                    new MenuView.Stat("COUVEUSE", barn.hasIncubator() ? "OUI" : "NON",
                            barn.hasIncubator() ? 0xFF22E558 : 0xFFEF3826),
                    new MenuView.Stat("EN SOIN", barn.inCare() + " / " + BarnControllerBlockEntity.MAX_IN_CARE,
                            0xFFFFFFFF));
            return new MenuView(id(), "Enclos " + barn.size() + "x" + barn.size(),
                    "Clic : equiper · clic droit : recuperer · Maj + clic : soigner", stats, List.of(
                    new MenuView.Section("ECURIE", "resources", MenuView.GRID, top),
                    new MenuView.Section("MONTURES", "upgrade", MenuView.GRID, gems)), false);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            BarnControllerBlockEntity barn = barns.get(player.getUUID());
            if (barn == null || !barn.inRange(player)) {
                UiNet.close(player);
                return;
            }
            if ("unequip".equals(key)) {
                barn.unequip(player);
            } else if ("deposit".equals(key)) {
                barn.deposit(player);
            } else if (key.startsWith("withdraw:")) {
                int index = Integer.parseInt(key.substring(9));
                if (index < barn.gems().size()) {
                    barn.withdraw(player, index);
                }
            } else if (key.startsWith("gem:")) {
                int index = Integer.parseInt(key.substring(4));
                if (index >= barn.gems().size()) {
                    return;
                }
                if (button == 2) {
                    MountGem gem = barn.gems().get(index);
                    if (!gem.ownedBy(player.getUUID())) {
                        MountTexts.error(player, "not_yours");
                        return;
                    }
                    Heal.INSTANCE.states.put(player.getUUID(), new HealState(barn, gem));
                    Heal.INSTANCE.open(player);
                    return;
                }
                barn.equip(player, index);
            }
        }
    }

    // -- Soins ---------------------------------------------------------------------

    private static final class HealState {
        final BarnControllerBlockEntity barn;
        final MountGem gem;
        int bandages = 1;

        HealState(BarnControllerBlockEntity barn, MountGem gem) {
            this.barn = barn;
            this.gem = gem;
        }
    }

    public static final class Heal extends MenuController {
        public static final Heal INSTANCE = new Heal();
        final Map<UUID, HealState> states = new ConcurrentHashMap<>();

        private Heal() {
            super("mount_heal");
        }

        private static int owned(ServerPlayer player) {
            return MountManager.count(player.getInventory(), PalaMount.BANDAGE.get().getDefaultInstance());
        }

        @Override
        public MenuView view(ServerPlayer player) {
            HealState state = states.get(player.getUUID());
            if (state == null) {
                return new MenuView(id(), "Soins", "", List.of(), List.of(), true);
            }
            MountGem gem = state.gem;
            List<Card> cards = List.of(
                    Card.of(MountGemstoneItem.stackOf(gem), gem.name(), List.of("Niveau " + gem.level(),
                            "Vie " + Math.round(Math.max(0, gem.maxHealth() - gem.damage())) + " / "
                                    + Math.round(gem.maxHealth()))).accent(0xFFFAC400),
                    Card.of(new ItemStack(Items.FLINT), "Retirer un bandage", List.of("Un de moins", ""))
                            .accent(0xFFEF3826).action("less|retirer"),
                    Card.of(new ItemStack(PalaMount.BANDAGE.get(), Math.max(1, state.bandages)),
                                    state.bandages + " bandage(s)", List.of(
                                    MenuView.text(Component.literal("Duree ").append(MountTexts.duration(
                                            BarnControllerBlockEntity.CARE_MS * state.bandages))),
                                    "Soin +" + Math.round(gem.maxHealth() * 0.1f * state.bandages) + " PV"))
                            .accent(0xFF22E558).badge(String.valueOf(state.bandages))
                            .tooltip(List.of("Sur vous : " + owned(player))),
                    Card.of(new ItemStack(Items.EMERALD), "Ajouter un bandage", List.of("Un de plus", ""))
                            .accent(0xFF22E558).action("more|ajouter"));
            List<Card> confirm = List.of(Card.of(new ItemStack(Items.GOLDEN_APPLE), "Soigner", List.of(
                            "Une couveuse dans l'enclos", "Deux montures en soin au plus"))
                    .accent(0xFF22E558).action("confirm|placer en soin"));
            return new MenuView(id(), "Soigner " + gem.name(), "Chaque bandage rend 10 % de la vie", List.of(),
                    List.of(new MenuView.Section("BANDAGES", "resources", MenuView.GRID, cards),
                            new MenuView.Section("", "", MenuView.GRID, confirm)), true);
        }

        @Override
        protected void back(ServerPlayer player) {
            HealState state = states.remove(player.getUUID());
            if (state != null) {
                openBarn(player, state.barn);
            } else {
                UiNet.close(player);
            }
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            HealState state = states.get(player.getUUID());
            if (state == null) {
                return;
            }
            switch (key) {
                case "less" -> {
                    if (state.bandages <= 1) {
                        MountTexts.error(player, "bandage_min");
                    } else {
                        state.bandages--;
                    }
                }
                case "more" -> {
                    // UIBarnMountInfoNode : plafonne aux bandages possedes, et
                    // inutile au-dela de ce qui remet la monture a 100 %.
                    int useful = (int) Math.ceil(state.gem.damage() / Math.max(0.1f, state.gem.maxHealth() * 0.1f));
                    if (state.bandages >= owned(player)) {
                        MountTexts.error(player, "bandage_owned", owned(player));
                    } else if (state.bandages >= Math.max(1, useful)) {
                        MountTexts.error(player, "fully_healed");
                    } else {
                        state.bandages++;
                    }
                }
                case "confirm" -> {
                    int index = state.barn.gems().indexOf(state.gem);
                    if (index < 0 || !state.barn.inRange(player)) {
                        UiNet.close(player);
                        return;
                    }
                    state.barn.heal(player, index, state.bandages);
                    states.remove(player.getUUID());
                    openBarn(player, state.barn);
                }
                default -> {
                }
            }
        }
    }
}
