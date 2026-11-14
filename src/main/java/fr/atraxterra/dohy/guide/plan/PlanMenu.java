package fr.atraxterra.dohy.guide.plan;

import fr.atraxterra.dohy.guide.DoHyGuide;
import fr.atraxterra.dohy.guide.EncyclopediaMenu;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le menu du plan tenu en main : choisir la structure et sa taille, le
 * tourner, le retirer, et la liste du materiel (ce qui est deja en place).
 */
public final class PlanMenu extends MenuController {

    public static final PlanMenu INSTANCE = new PlanMenu();

    /** La main qui tient le plan ouvert, par joueur. */
    private static final Map<UUID, InteractionHand> HANDS = new ConcurrentHashMap<>();

    private PlanMenu() {
        super("plan");
    }

    public static void open(ServerPlayer player, InteractionHand hand) {
        HANDS.put(player.getUUID(), hand);
        INSTANCE.open(player);
    }

    /** Le plan ouvert : celui de la main notee, sinon le premier de l'inventaire. */
    private static ItemStack stack(ServerPlayer player) {
        InteractionHand hand = HANDS.getOrDefault(player.getUUID(), InteractionHand.MAIN_HAND);
        ItemStack held = player.getItemInHand(hand);
        if (held.is(DoHyGuide.PLAN.get())) {
            return held;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(DoHyGuide.PLAN.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static final String[] FACING = {"Sud", "Ouest", "Nord", "Est"};

    @Override
    public MenuView view(ServerPlayer player) {
        ItemStack stack = stack(player);
        PlanData data = PlanItem.data(stack);
        Blueprint blueprint = data.resolve();
        Blueprint.Variant variant = blueprint.variant(data.variant());
        PlanData.Anchor anchor = data.anchor().orElse(null);
        Level level = player.level();
        boolean here = anchor != null && anchor.dimension().equals(level.dimension().identifier().toString())
                && level.isLoaded(anchor.pos());

        // Le materiel : par bloc, ce qu'il faut et ce qui est en place.
        Map<Blueprint.Expect, int[]> materials = new LinkedHashMap<>();
        int done = 0;
        int wrong = 0;
        for (Blueprint.Cell cell : variant.cells()) {
            Blueprint.Expect expect = cell.expect();
            if (!expect.required()) {
                continue;
            }
            int[] counts = materials.computeIfAbsent(expect, key -> new int[2]);
            counts[1]++;
            if (here) {
                BlockPos pos = Blueprint.place(anchor.pos(), anchor.quarter(), cell.x(), cell.y(), cell.z());
                Blueprint.Status status = expect.status(level, pos);
                if (status == Blueprint.Status.CORRECT) {
                    counts[0]++;
                    done++;
                } else if (status == Blueprint.Status.WRONG) {
                    wrong++;
                }
            }
        }
        int total = variant.required();

        List<MenuView.Stat> stats = new ArrayList<>();
        stats.add(new MenuView.Stat("BLOCS", here ? done + " / " + total : String.valueOf(total),
                here && done == total ? 0xFF22E558 : 0xFFFFFFFF));
        stats.add(new MenuView.Stat("TAILLE", variant.width() + "x" + variant.depth() + "x" + variant.height(),
                0xFFFFFFFF));
        stats.add(new MenuView.Stat("POSE", anchor == null ? "NON" : FACING[anchor.quarter() & 3].toUpperCase(),
                anchor == null ? 0xFF8C8B93 : 0xFF55E5FF));

        List<Card> structures = new ArrayList<>();
        for (Blueprint candidate : Blueprints.all()) {
            boolean selected = candidate.id().equals(blueprint.id());
            Blueprint.Variant first = candidate.variant(0);
            structures.add(Card.of(candidate.iconStack(), candidate.name(), List.of(
                            candidate.variants().size() > 1 ? candidate.variants().size() + " tailles"
                                    : first.width() + " x " + first.depth() + " x " + first.height(),
                            first.required() + (candidate.variants().size() > 1 ? "+ blocs" : " blocs")))
                    .accent(0xFF2F5BEA).state(selected ? MenuView.DONE : MenuView.NORMAL)
                    .action(selected ? "" : "bp:" + candidate.id() + "|choisir"));
        }

        List<MenuView.Section> sections = new ArrayList<>();
        sections.add(new MenuView.Section("STRUCTURE", "craft", MenuView.GRID, structures));
        if (blueprint.variants().size() > 1) {
            List<Card> sizes = new ArrayList<>();
            for (int index = 0; index < blueprint.variants().size(); index++) {
                Blueprint.Variant candidate = blueprint.variants().get(index);
                boolean selected = index == Math.floorMod(data.variant(), blueprint.variants().size());
                sizes.add(Card.of(blueprint.iconStack(), candidate.label(), List.of(candidate.required() + " blocs",
                                candidate.width() + " x " + candidate.depth() + " x " + candidate.height()))
                        .accent(0xFF2BC4C4).state(selected ? MenuView.DONE : MenuView.NORMAL)
                        .action(selected ? "" : "var:" + index + "|choisir"));
            }
            sections.add(new MenuView.Section("TAILLE", "upgrade", MenuView.GRID, sizes));
        }

        List<Card> actions = new ArrayList<>();
        actions.add(Card.of(new ItemStack(Items.COMPASS), "Tourner", List.of("Un quart de tour",
                        anchor == null ? "Pose d'abord le plan" : "Face au " + FACING[anchor.quarter() & 3].toLowerCase()))
                .accent(0xFFFAC400).state(anchor == null ? MenuView.DISABLED : MenuView.NORMAL)
                .action(anchor == null ? "" : "turn|tourner"));
        actions.add(Card.of(new ItemStack(Items.BARRIER), "Retirer le plan", List.of("L'hologramme disparait",
                        "Le plan reste dans ton sac"))
                .accent(0xFFEF3826).state(anchor == null ? MenuView.DISABLED : MenuView.NORMAL)
                .action(anchor == null ? "" : "lift|retirer"));
        actions.add(Card.of(new ItemStack(Items.KNOWLEDGE_BOOK), "Lire la fiche", List.of("Dans l'encyclopedie",
                        blueprint.name()))
                .accent(0xFF22E558).action("entry|lire"));
        sections.add(new MenuView.Section("ACTIONS", "fuel", MenuView.GRID, actions));

        List<Card> rows = new ArrayList<>();
        for (Map.Entry<Blueprint.Expect, int[]> entry : materials.entrySet()) {
            Blueprint.Expect expect = entry.getKey();
            int[] counts = entry.getValue();
            ItemStack icon = expect.fluid() != null ? new ItemStack(expect.fluid().getBucket())
                    : new ItemStack(expect.display().getBlock().asItem());
            String name = MenuView.text(expect.display().getBlock().getName());
            String line = here ? counts[0] + " / " + counts[1] + " en place" : counts[1] + " a poser";
            if (expect.ready() != null) {
                line += " · chargee(s) a fond";
            } else if (expect.fluid() != null) {
                line += " · des sources";
            }
            Card row = Card.of(icon.isEmpty() ? new ItemStack(Items.BUCKET) : icon, name, List.of(line))
                    .accent(0xFF55545C).badge("x" + counts[1]);
            if (here) {
                row = row.progress(counts[0], counts[1]).state(counts[0] == counts[1] ? MenuView.DONE : MenuView.NORMAL);
            }
            rows.add(row);
        }
        String materialTitle = here && wrong > 0 ? "MATERIEL · " + wrong + " BLOC(S) A RETIRER" : "MATERIEL";
        sections.add(new MenuView.Section(materialTitle, "resources", MenuView.ROWS, rows));

        return new MenuView(id(), "Plan de construction", blueprint.name() + " · " + variant.label(), stats,
                sections, false);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        ItemStack stack = stack(player);
        if (stack.isEmpty()) {
            UiNet.close(player);
            return;
        }
        PlanData data = PlanItem.data(stack);
        if (key.startsWith("bp:")) {
            Blueprint blueprint = Blueprints.get(key.substring(3));
            if (blueprint != null) {
                stack.set(DoHyGuide.PLAN_DATA.get(), data.withBlueprint(blueprint.id(), 0));
                success(player);
            }
        } else if (key.startsWith("var:")) {
            try {
                int index = Integer.parseInt(key.substring(4));
                stack.set(DoHyGuide.PLAN_DATA.get(), data.withBlueprint(data.blueprint(), index));
                success(player);
            } catch (NumberFormatException ignored) {
                failure(player);
            }
        } else if ("turn".equals(key) && data.anchor().isPresent()) {
            stack.set(DoHyGuide.PLAN_DATA.get(), data.withAnchor(data.anchor().get().turned()));
            success(player);
        } else if ("lift".equals(key) && data.anchor().isPresent()) {
            stack.set(DoHyGuide.PLAN_DATA.get(), data.withAnchor(null));
            success(player);
        } else if ("entry".equals(key)) {
            EncyclopediaMenu.openEntry(player, data.resolve().entry());
        }
    }

    /** Pour la CI : les etats d'un plan pose, compte par statut. */
    public static Map<Blueprint.Status, Integer> census(Level level, PlanData data) {
        Map<Blueprint.Status, Integer> counts = new HashMap<>();
        PlanData.Anchor anchor = data.anchor().orElse(null);
        if (anchor == null) {
            return counts;
        }
        Blueprint.Variant variant = data.resolve().variant(data.variant());
        for (Blueprint.Cell cell : variant.cells()) {
            BlockPos pos = Blueprint.place(anchor.pos(), anchor.quarter(), cell.x(), cell.y(), cell.z());
            counts.merge(cell.expect().status(level, pos), 1, Integer::sum);
        }
        return counts;
    }

    /** La direction du regard d'un plan pose, pour les messages. */
    public static String facing(int quarter) {
        return FACING[quarter & 3] + " (" + Direction.from2DDataValue(quarter).getName() + ")";
    }
}
