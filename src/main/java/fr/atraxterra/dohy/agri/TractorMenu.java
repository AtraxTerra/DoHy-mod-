package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'ecran du tracteur : son reservoir, son outil, ce que portent le semoir,
 * l'epandeur et la tremie. Il s'ouvre accroupi + clic sur le tracteur, ou par
 * la touche d'inventaire quand on le conduit.
 */
public final class TractorMenu extends MenuController {

    public static final TractorMenu INSTANCE = new TractorMenu();

    private static final double REACH = 8.0d;
    private static final int STACK = 64;
    private static final int GREEN = 0xFF22E558;
    private static final int GOLD = 0xFFFAC400;
    private static final int RED = 0xFFE5484D;
    private static final int GREY = 0xFF55545C;

    private final Map<UUID, UUID> open = new ConcurrentHashMap<>();

    private TractorMenu() {
        super("tractor");
    }

    public void openFor(ServerPlayer player, Tractor tractor) {
        open.put(player.getUUID(), tractor.getUUID());
        open(player);
    }

    /** Le tracteur ouvert par ce joueur, ou {@code null} s'il s'en est eloigne ou qu'il n'est plus la. */
    private Tractor tractor(ServerPlayer player) {
        UUID id = open.get(player.getUUID());
        if (id == null || !(player.level() instanceof ServerLevel level)
                || !(level.getEntity(id) instanceof Tractor tractor) || !tractor.isAlive()
                || tractor.distanceToSqr(player) > REACH * REACH) {
            return null;
        }
        return tractor;
    }

    private static String name(Item item) {
        return MenuView.text(new ItemStack(item).getHoverName());
    }

    @Override
    public MenuView view(ServerPlayer player) {
        AgriConfig.Farming config = AgriConfig.config().farming();
        Tractor tractor = tractor(player);
        int fuel = tractor == null ? 0 : tractor.fuel();
        TractorTool tool = tractor == null ? TractorTool.NONE : tractor.tool();
        int cropCount = tractor == null ? 0 : tractor.cropCount();
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("CARBURANT", fuel + " / " + config.tank(), fuel > 0 ? GREEN : RED),
                new MenuView.Stat("OUTIL", tool.label().toUpperCase(java.util.Locale.ROOT),
                        tool == TractorTool.NONE ? GOLD : 0xFFFFFFFF),
                new MenuView.Stat("TREMIE", cropCount + " / " + config.cropCapacity(),
                        cropCount >= config.cropCapacity() ? RED : 0xFFFFFFFF));
        List<MenuView.Section> sections = new ArrayList<>();
        if (tractor != null) {
            sections.add(machine(player, tractor, config));
            sections.add(seeder(player, tractor, config));
            sections.add(hopper(tractor, config));
        }
        return new MenuView(id(), "Tracteur", "Touche de saut au volant : outil en marche ou a l'arret", stats,
                sections, true);
    }

    private static MenuView.Section machine(ServerPlayer player, Tractor tractor, AgriConfig.Farming config) {
        List<Card> cards = new ArrayList<>();
        boolean fuelInHand = TractorFuel.isFuel(player, InteractionHand.MAIN_HAND);
        Card fuel = Card.of(new ItemStack(Items.LAVA_BUCKET), "Reservoir", List.of(
                        tractor.fuel() + " / " + config.tank() + " unites",
                        fuelInHand ? "Clic : verser ce que tu tiens" : "Tiens un carburant en main"))
                .accent(tractor.fuel() > 0 ? GREEN : RED)
                .tooltip(List.of("Une unite par bloc parcouru,", "deux quand l'outil travaille",
                        "Bidon de carburant, seau de lave, charbon :", "clic sur le tracteur, l'objet en main",
                        "Gare a cote d'une pompe, il se remplit seul"));
        cards.add(fuelInHand && tractor.fuel() < config.tank() ? fuel.action("fuel|faire le plein")
                : fuel.state(MenuView.DISABLED));
        TractorTool tool = tractor.tool();
        if (tool != TractorTool.NONE) {
            cards.add(Card.of(new ItemStack(DoHyAgri.tool(tool)), tool.label(), List.of(
                            tractor.working() ? "En marche" : "A l'arret", "Clic droit : deteler"))
                    .accent(tractor.working() ? GREEN : GOLD)
                    .action("work|" + (tractor.working() ? "arreter l'outil" : "mettre en marche"))
                    .right("detach|deteler l'outil"));
        }
        for (TractorTool other : TractorTool.values()) {
            if (other == TractorTool.NONE || other == tool) {
                continue;
            }
            Item item = DoHyAgri.tool(other);
            boolean owned = AgriInv.count(player, item) > 0;
            Card card = Card.of(new ItemStack(item), other.label(), List.of(describe(other),
                    owned ? "Clic : atteler" : "Tu n'en as pas sur toi")).accent(GREY);
            cards.add(owned ? card.action("attach:" + other.key() + "|atteler") : card.state(MenuView.DISABLED));
        }
        return new MenuView.Section("MACHINE", "resources", MenuView.GRID, cards);
    }

    private static String describe(TractorTool tool) {
        return switch (tool) {
            case PLOW -> "Laboure la terre et l'herbe";
            case SEEDER -> "Seme les semences chargees";
            case SPREADER -> "Epand la poudre d'os";
            case HARVESTER -> "Moissonne les cereales mures";
            default -> "";
        };
    }

    private static MenuView.Section seeder(ServerPlayer player, Tractor tractor, AgriConfig.Farming config) {
        List<Card> cards = new ArrayList<>();
        int seedsCarried = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (Tractor.cropOf(stack.getItem()) != null) {
                seedsCarried += stack.getCount();
            }
        }
        Card load = Card.of(new ItemStack(Items.WHEAT_SEEDS), "Charger les semences", List.of(
                        tractor.seedCount() + " / " + config.seedCapacity() + " dans le semoir",
                        seedsCarried + " dans ton sac"))
                .accent(GOLD);
        cards.add(seedsCarried > 0 && tractor.seedCount() < config.seedCapacity()
                ? load.action("seeds|charger les semences") : load.state(MenuView.DISABLED));
        int meal = AgriInv.count(player, Items.BONE_MEAL);
        Card fertilizer = Card.of(new ItemStack(Items.BONE_MEAL), "Engrais", List.of(
                        tractor.fertilizer() + " / " + config.fertilizerCapacity() + " dans l'epandeur",
                        meal + " dans ton sac  ·  clic droit : reprendre"))
                .accent(GOLD).badge(tractor.fertilizer() > 0 ? "x" + tractor.fertilizer() : "");
        if (meal > 0 && tractor.fertilizer() < config.fertilizerCapacity()) {
            fertilizer = fertilizer.action("meal|charger l'engrais");
        }
        if (tractor.fertilizer() > 0) {
            fertilizer = fertilizer.right("meal_back|reprendre une pile");
        }
        cards.add(meal > 0 || tractor.fertilizer() > 0 ? fertilizer : fertilizer.state(MenuView.DISABLED));
        Item sowing = tractor.sowing();
        if (!tractor.seeds().isEmpty()) {
            cards.add(Card.of(new ItemStack(Items.COMPASS), "Semer : au mieux", List.of(
                            "La premiere semence chargee", "qui pousse dans le climat du champ"))
                    .accent(sowing == null ? GREEN : GREY).state(sowing == null ? MenuView.DONE : MenuView.NORMAL)
                    .action("sow:auto|semer au mieux"));
        }
        for (Map.Entry<Item, Integer> entry : tractor.seeds().entrySet()) {
            Item seed = entry.getKey();
            cards.add(Card.of(new ItemStack(seed), name(seed), List.of(
                            sowing == seed ? "Semence choisie" : "Clic : ne semer que celle-ci",
                            "Clic droit : reprendre une pile"))
                    .accent(sowing == seed ? GREEN : GREY).badge("x" + entry.getValue())
                    .action("sow:" + BuiltInRegistries.ITEM.getKey(seed) + "|choisir cette semence")
                    .right("unseed:" + BuiltInRegistries.ITEM.getKey(seed) + "|reprendre une pile"));
        }
        return new MenuView.Section("SEMOIR ET EPANDEUR", "resources", MenuView.GRID, cards);
    }

    private static MenuView.Section hopper(Tractor tractor, AgriConfig.Farming config) {
        List<Card> cards = new ArrayList<>();
        if (tractor.crops().isEmpty()) {
            cards.add(Card.of(new ItemStack(Items.WHEAT), "Tremie vide", List.of(
                    "La moissonneuse la remplit", "Elle se vide seule a " + config.siloRange() + " blocs d'un silo"))
                    .state(MenuView.DISABLED));
        }
        for (Map.Entry<Item, Integer> entry : tractor.crops().entrySet()) {
            Item crop = entry.getKey();
            cards.add(Card.of(new ItemStack(crop), name(crop), List.of("Clic : prendre une pile",
                            "Se vide seule pres d'un silo"))
                    .accent(GREEN).badge("x" + entry.getValue())
                    .action("crop:" + BuiltInRegistries.ITEM.getKey(crop) + "|prendre une pile"));
        }
        return new MenuView.Section("TREMIE", "resources", MenuView.GRID, cards);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        Tractor tractor = tractor(player);
        if (tractor == null) {
            UiNet.close(player);
            return;
        }
        if (key.equals("fuel")) {
            int gained = TractorFuel.drain(player, InteractionHand.MAIN_HAND, Tractor.tank() - tractor.fuel());
            tractor.setFuel(tractor.fuel() + gained);
            if (gained > 0) {
                play(player, SoundEvents.BUCKET_EMPTY, 1.0f);
            } else {
                failure(player);
            }
        } else if (key.equals("work")) {
            tractor.setWorking(!tractor.working());
            success(player);
        } else if (key.equals("detach")) {
            tractor.detach(player);
        } else if (key.startsWith("attach:")) {
            attach(player, tractor, key.substring("attach:".length()));
        } else if (key.equals("seeds")) {
            int moved = 0;
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                int taken = tractor.addSeeds(stack.getItem(), stack.getCount());
                stack.shrink(taken);
                moved += taken;
            }
            player.getInventory().setChanged();
            done(player, moved > 0);
        } else if (key.equals("meal")) {
            int taken = tractor.addFertilizer(AgriInv.count(player, Items.BONE_MEAL));
            AgriInv.take(player, Items.BONE_MEAL, taken);
            done(player, taken > 0);
        } else if (key.equals("meal_back")) {
            AgriInv.give(player, Items.BONE_MEAL, tractor.removeFertilizer(STACK));
            success(player);
        } else if (key.equals("sow:auto")) {
            tractor.setSowing(null);
            success(player);
        } else if (key.startsWith("sow:")) {
            Item seed = item(key.substring("sow:".length()));
            if (seed != null && tractor.seeds().containsKey(seed)) {
                tractor.setSowing(seed);
                success(player);
            }
        } else if (key.startsWith("unseed:")) {
            Item seed = item(key.substring("unseed:".length()));
            if (seed != null) {
                AgriInv.give(player, seed, tractor.removeSeeds(seed, STACK));
                success(player);
            }
        } else if (key.startsWith("crop:")) {
            Item crop = item(key.substring("crop:".length()));
            Integer held = crop == null ? null : tractor.crops().get(crop);
            if (held != null) {
                int taken = Math.min(STACK, held);
                if (held - taken > 0) {
                    tractor.crops().put(crop, held - taken);
                } else {
                    tractor.crops().remove(crop);
                }
                AgriInv.give(player, crop, taken);
                success(player);
            }
        }
    }

    private static void attach(ServerPlayer player, Tractor tractor, String key) {
        for (TractorTool tool : TractorTool.values()) {
            if (tool != TractorTool.NONE && tool.key().equals(key) && AgriInv.count(player, DoHyAgri.tool(tool)) > 0) {
                AgriInv.take(player, DoHyAgri.tool(tool), 1);
                tractor.attach(player, tool);
                return;
            }
        }
        failure(player);
    }

    private static void done(ServerPlayer player, boolean good) {
        if (good) {
            success(player);
        } else {
            failure(player);
        }
    }

    private static Item item(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? null : BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
    }

    @Override
    protected void back(ServerPlayer player) {
        open.remove(player.getUUID());
        UiNet.close(player);
    }
}
