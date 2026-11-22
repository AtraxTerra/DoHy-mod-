package fr.atraxterra.dohy.underground;

import fr.atraxterra.dohy.agri.AgriInv;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Les ecrans des postes de l'economie souterraine (lot 24c). Chaque recette puise dans
 * l'inventaire porte et y rend le produit, tout de suite : clic gauche une fois, clic droit
 * jusqu'a seize fois. Le laboratoire degage des vapeurs (masque et combinaison les arretent),
 * la table de conditionnement use la balance, l'etabli monte des caisses scellees.
 */
public final class WorkstationMenus {

    private WorkstationMenus() {
    }

    /** Un ingredient : un objet et combien. */
    record Input(Supplier<Item> item, int count) {
    }

    /**
     * Une recette.
     *
     * @param tool un objet a avoir sur soi, non consomme (la balance s'use, le materiel non), ou {@code null}
     */
    record Recipe(String key, List<Input> inputs, Supplier<Item> output, int count, Supplier<Item> tool) {
    }

    private static Input in(Supplier<Item> item, int count) {
        return new Input(item, count);
    }

    public static Station of(WorkstationBlock.Kind kind) {
        return switch (kind) {
            case LAB -> Lab.INSTANCE;
            case PACKAGING -> Packaging.INSTANCE;
            case ARMS -> Arms.INSTANCE;
        };
    }

    /** Ce que partagent les trois ecrans : le poste ouvert par chaque joueur, sa portee, les recettes. */
    public abstract static class Station extends MenuController {
        private static final double REACH = 8.0d;
        private static final int MANY = 16;
        private final Map<UUID, BlockPos> open = new ConcurrentHashMap<>();
        private final WorkstationBlock.Kind kind;

        Station(String path, WorkstationBlock.Kind kind) {
            super(path);
            this.kind = kind;
        }

        public void openAt(ServerPlayer player, BlockPos pos) {
            open.put(player.getUUID(), pos.immutable());
            open(player);
        }

        /** Vrai si le joueur est encore a portee de son poste. */
        boolean near(ServerPlayer player) {
            BlockPos pos = open.get(player.getUUID());
            return pos != null && player.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d)
                    <= REACH * REACH && player.level().getBlockState(pos).getBlock() instanceof WorkstationBlock block
                    && block.kind() == kind;
        }

        abstract List<Recipe> recipes();

        abstract String title();

        abstract String subtitle();

        /** Ce que le poste dit en plus (protections, balance). */
        abstract List<MenuView.Stat> stats(ServerPlayer player);

        /** Combien de fois cette recette peut se faire avec ce que le joueur porte. */
        int possible(ServerPlayer player, Recipe recipe) {
            if (recipe.tool() != null && AgriInv.count(player, recipe.tool().get()) <= 0) {
                return 0;
            }
            int times = MANY;
            for (Input input : recipe.inputs()) {
                times = Math.min(times, AgriInv.count(player, input.item().get()) / input.count());
            }
            if (recipe.tool() == DoHyUnderground.PRECISION_SCALE) {
                times = Math.min(times, scaleUses(player));
            }
            return Math.max(0, times);
        }

        @Override
        public MenuView view(ServerPlayer player) {
            List<Card> cards = new ArrayList<>();
            for (Recipe recipe : recipes()) {
                ItemStack output = new ItemStack(recipe.output().get(), recipe.count());
                StringBuilder needs = new StringBuilder();
                for (Input input : recipe.inputs()) {
                    if (!needs.isEmpty()) {
                        needs.append(" + ");
                    }
                    needs.append(input.count()).append(' ').append(MenuView.text(new ItemStack(input.item().get())
                            .getHoverName()));
                }
                int possible = possible(player, recipe);
                List<String> lines = new ArrayList<>();
                lines.add(needs.toString());
                lines.add(recipe.tool() != null && AgriInv.count(player, recipe.tool().get()) <= 0
                        ? "Il faut " + MenuView.text(new ItemStack(recipe.tool().get()).getHoverName())
                        : possible > 0 ? "Tu peux en faire " + possible : "Il te manque des ingredients");
                Card card = Card.of(output, recipe.count() + " " + MenuView.text(output.getHoverName()), lines)
                        .accent(possible > 0 ? 0xFF22E558 : 0xFF55545C).badge(possible > 0 ? "x" + possible : "")
                        .tooltip(List.of("Clic gauche : une fois", "Clic droit : jusqu'a " + MANY + " fois"));
                cards.add(possible > 0 ? card.action("one:" + recipe.key() + "|une fois")
                        .right("many:" + recipe.key() + "|jusqu'a " + MANY + " fois") : card.state(MenuView.DISABLED));
            }
            return new MenuView(id(), title(), subtitle(), stats(player), List.of(
                    new MenuView.Section("RECETTES", "craft", MenuView.GRID, cards)), true);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if (!near(player)) {
                UiNet.close(player);
                return;
            }
            int colon = key.indexOf(':');
            if (colon < 0) {
                return;
            }
            String wanted = key.substring(colon + 1);
            for (Recipe recipe : recipes()) {
                if (recipe.key().equals(wanted)) {
                    int times = Math.min(key.startsWith("many:") ? MANY : 1, possible(player, recipe));
                    if (times <= 0) {
                        report(player, "Il te manque des ingredients.", "");
                        return;
                    }
                    craft(player, recipe, times);
                    return;
                }
            }
        }

        /** Fait la recette {@code times} fois : prend, rend, puis ce que le poste a de particulier. */
        void craft(ServerPlayer player, Recipe recipe, int times) {
            for (Input input : recipe.inputs()) {
                AgriInv.take(player, input.item().get(), input.count() * times);
            }
            AgriInv.give(player, recipe.output().get(), recipe.count() * times);
            if (recipe.tool() == DoHyUnderground.PRECISION_SCALE) {
                wearScale(player, times);
            }
            after(player, recipe, times);
        }

        /** Ce que le poste fait apres une recette : un son, des vapeurs. */
        abstract void after(ServerPlayer player, Recipe recipe, int times);

        @Override
        protected void back(ServerPlayer player) {
            open.remove(player.getUUID());
            UiNet.close(player);
        }
    }

    // -- La balance -------------------------------------------------------------------------------

    /** Combien de pesees reste-t-il aux balances que le joueur porte. */
    static int scaleUses(ServerPlayer player) {
        int uses = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(DoHyUnderground.PRECISION_SCALE.get())) {
                uses += stack.getMaxDamage() - stack.getDamageValue();
            }
        }
        return uses;
    }

    /** Chaque sachet pese use la balance d'un cran ; une balance usee se casse. */
    static void wearScale(ServerPlayer player, int times) {
        int left = times;
        for (int slot = 0; slot < 36 && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(DoHyUnderground.PRECISION_SCALE.get())) {
                continue;
            }
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            int used = Math.min(left, remaining);
            left -= used;
            if (used >= remaining) {
                stack.shrink(1);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_BREAK.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.0f);
            } else {
                stack.setDamageValue(stack.getDamageValue() + used);
            }
        }
        player.getInventory().setChanged();
    }

    // -- Le laboratoire clandestin ----------------------------------------------------------------

    public static final class Lab extends Station {
        public static final Lab INSTANCE = new Lab();
        private static final List<Recipe> RECIPES = List.of(
                new Recipe("herb", List.of(in(DoHyUnderground.CANNABIS_LEAF, 4)), DoHyUnderground.DRIED_HERB, 3, null),
                new Recipe("powder", List.of(in(DoHyUnderground.COCA_LEAF, 4), in(DoHyUnderground.PRECURSOR_CAN, 1)),
                        DoHyUnderground.COCAINE_POWDER, 3, null),
                new Recipe("heroin", List.of(in(DoHyUnderground.POPPY_POD, 4), in(DoHyUnderground.PRECURSOR_CAN, 1)),
                        DoHyUnderground.HEROIN, 3, null),
                new Recipe("pills", List.of(in(DoHyUnderground.PRECURSOR_CAN, 2)), DoHyUnderground.SYNTHETIC_PILLS, 4,
                        null),
                new Recipe("crystals", List.of(in(DoHyUnderground.PRECURSOR_CAN, 3)),
                        DoHyUnderground.SYNTHETIC_CRYSTALS, 3, DoHyUnderground.LAB_GLASSWARE));

        private Lab() {
            super("clandestine_lab", WorkstationBlock.Kind.LAB);
        }

        @Override
        List<Recipe> recipes() {
            return RECIPES;
        }

        @Override
        String title() {
            return "Laboratoire clandestin";
        }

        @Override
        String subtitle() {
            return "Sans masque ni combinaison, les vapeurs rendent malade";
        }

        @Override
        List<MenuView.Stat> stats(ServerPlayer player) {
            int worn = protections(player);
            return List.of(new MenuView.Stat("PROTECTIONS", worn + " / 2", worn == 2 ? 0xFF22E558 : worn == 1
                            ? 0xFFFAC400 : 0xFFEF3826),
                    new MenuView.Stat("MATERIEL", AgriInv.count(player, DoHyUnderground.LAB_GLASSWARE.get()) > 0
                            ? "OUI" : "NON", 0xFFFFFFFF));
        }

        /** Le masque et la combinaison portes : 0, 1 ou 2. */
        static int protections(ServerPlayer player) {
            int worn = 0;
            if (player.getItemBySlot(EquipmentSlot.HEAD).is(DoHyUnderground.GAS_MASK.get())) {
                worn++;
            }
            if (player.getItemBySlot(EquipmentSlot.CHEST).is(DoHyUnderground.HAZMAT_SUIT.get())) {
                worn++;
            }
            return worn;
        }

        @Override
        void after(ServerPlayer player, Recipe recipe, int times) {
            play(player, SoundEvents.BREWING_STAND_BREW, 1.1f);
            int worn = protections(player);
            if (worn >= 2) {
                return;
            }
            int seconds = Math.min(30, 6 + 2 * times);
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, seconds * 20, 0));
            if (worn == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, Math.min(10, 2 + times) * 20, 0));
            }
            player.sendSystemMessage(Component.literal(worn == 0 ? "Les vapeurs te prennent a la gorge : porte un "
                    + "masque et une combinaison." : "Les vapeurs passent encore : il te manque une protection.")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    // -- La table de conditionnement --------------------------------------------------------------

    public static final class Packaging extends Station {
        public static final Packaging INSTANCE = new Packaging();
        private static final List<Recipe> RECIPES = List.of(
                new Recipe("herb_bag", List.of(in(DoHyUnderground.DRIED_HERB, Goods.BAG),
                        in(DoHyUnderground.EMPTY_BAG, 1)), DoHyUnderground.HERB_BAG, 1, DoHyUnderground.PRECISION_SCALE),
                new Recipe("powder_bag", List.of(in(DoHyUnderground.COCAINE_POWDER, Goods.BAG),
                        in(DoHyUnderground.EMPTY_BAG, 1)), DoHyUnderground.POWDER_BAG, 1,
                        DoHyUnderground.PRECISION_SCALE),
                new Recipe("heroin_bag", List.of(in(DoHyUnderground.HEROIN, Goods.BAG),
                        in(DoHyUnderground.EMPTY_BAG, 1)), DoHyUnderground.HEROIN_BAG, 1,
                        DoHyUnderground.PRECISION_SCALE),
                new Recipe("pill_bag", List.of(in(DoHyUnderground.SYNTHETIC_PILLS, Goods.BAG),
                        in(DoHyUnderground.EMPTY_BAG, 1)), DoHyUnderground.PILL_BAG, 1,
                        DoHyUnderground.PRECISION_SCALE),
                new Recipe("crystal_bag", List.of(in(DoHyUnderground.SYNTHETIC_CRYSTALS, Goods.BAG),
                        in(DoHyUnderground.EMPTY_BAG, 1)), DoHyUnderground.CRYSTAL_BAG, 1,
                        DoHyUnderground.PRECISION_SCALE),
                new Recipe("herb_parcel", List.of(in(DoHyUnderground.HERB_BAG, 8), in(() -> Items.PAPER, 1)),
                        DoHyUnderground.HERB_PARCEL, 1, null),
                new Recipe("powder_parcel", List.of(in(DoHyUnderground.POWDER_BAG, 8), in(() -> Items.PAPER, 1)),
                        DoHyUnderground.POWDER_PARCEL, 1, null),
                new Recipe("heroin_parcel", List.of(in(DoHyUnderground.HEROIN_BAG, 8), in(() -> Items.PAPER, 1)),
                        DoHyUnderground.HEROIN_PARCEL, 1, null),
                new Recipe("synthetic_parcel", List.of(in(DoHyUnderground.PILL_BAG, 8), in(() -> Items.PAPER, 1)),
                        DoHyUnderground.SYNTHETIC_PARCEL, 1, null));

        private Packaging() {
            super("packaging_table", WorkstationBlock.Kind.PACKAGING);
        }

        @Override
        List<Recipe> recipes() {
            return RECIPES;
        }

        @Override
        String title() {
            return "Table de conditionnement";
        }

        @Override
        String subtitle() {
            return "Quatre portions par sachet, pesees ; huit sachets par colis";
        }

        @Override
        List<MenuView.Stat> stats(ServerPlayer player) {
            int uses = scaleUses(player);
            return List.of(new MenuView.Stat("BALANCE", uses > 0 ? uses + " PESEES" : "AUCUNE", uses > 0
                            ? 0xFF22E558 : 0xFFEF3826),
                    new MenuView.Stat("SACHETS VIDES", String.valueOf(AgriInv.count(player,
                            DoHyUnderground.EMPTY_BAG.get())), 0xFFFFFFFF));
        }

        @Override
        void after(ServerPlayer player, Recipe recipe, int times) {
            play(player, SoundEvents.BUNDLE_INSERT, 1.0f);
        }
    }

    // -- L'etabli d'armurier ----------------------------------------------------------------------

    public static final class Arms extends Station {
        public static final Arms INSTANCE = new Arms();
        private static final List<Recipe> RECIPES = List.of(
                new Recipe("parts", List.of(in(() -> Items.IRON_INGOT, 3)), DoHyUnderground.MACHINED_PARTS, 2, null),
                new Recipe("crate", List.of(in(DoHyUnderground.MACHINED_PARTS, 4), in(() -> Items.IRON_INGOT, 2)),
                        DoHyUnderground.SEALED_CRATE, 1, null));

        private Arms() {
            super("arms_workbench", WorkstationBlock.Kind.ARMS);
        }

        @Override
        List<Recipe> recipes() {
            return RECIPES;
        }

        @Override
        String title() {
            return "Etabli d'armurier";
        }

        @Override
        String subtitle() {
            return "Des pieces usinees, des caisses scellees : rien ne s'ouvre ici";
        }

        @Override
        List<MenuView.Stat> stats(ServerPlayer player) {
            return List.of(new MenuView.Stat("FER", String.valueOf(AgriInv.count(player, Items.IRON_INGOT)),
                    0xFFFFFFFF), new MenuView.Stat("PIECES", String.valueOf(AgriInv.count(player,
                    DoHyUnderground.MACHINED_PARTS.get())), 0xFFFFFFFF));
        }

        @Override
        void after(ServerPlayer player, Recipe recipe, int times) {
            play(player, SoundEvents.ANVIL_USE, 1.2f);
        }
    }
}
