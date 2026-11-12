package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'ecran de la machine a OGM : choisir une souche, composer, lancer l'essai,
 * relire le carnet. Les regles sont dans {@link GmoResearch}.
 *
 * <p>La composition se regle carte par carte — clic gauche pour un de plus,
 * clic droit pour un de moins — et l'essai puise dans l'inventaire : la machine
 * n'a pas de coffre a elle.
 */
public final class GmoMachineMenu extends MenuController {

    public static final GmoMachineMenu INSTANCE = new GmoMachineMenu();

    private static final Map<UUID, BlockPos> OPEN = new ConcurrentHashMap<>();
    private static final double REACH = 8.0d;
    private static final int GREEN = 0xFF22E558;
    private static final int GOLD = 0xFFFAC400;
    private static final int RED = 0xFFE5484D;

    private GmoMachineMenu() {
        super("gmo_machine");
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        OPEN.put(player.getUUID(), pos.immutable());
        INSTANCE.open(player);
    }

    private static boolean inReach(ServerPlayer player) {
        BlockPos pos = OPEN.get(player.getUUID());
        return pos != null && player.level().getBlockState(pos).getBlock() instanceof GmoMachineBlock
                && player.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d) <= REACH * REACH;
    }

    /** L'OGM vise ; le premier tant que le joueur n'a rien choisi. */
    static Cereal target(ServerPlayer player) {
        Cereal chosen = Cereal.byKey(GmoResearch.data(player).target());
        return chosen != null && chosen.gmo() ? chosen : Cereal.FONIO;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        AgriConfig.Gmo config = AgriConfig.config().gmo();
        AgriData data = GmoResearch.data(player);
        int level = JobApi.level(player, Job.FARMER);
        boolean allowed = level >= config.minLevel();
        Cereal target = target(player);
        Map<Identifier, Integer> composition = data.composition();

        // -- Les souches ------------------------------------------------------------
        List<Card> strains = new ArrayList<>();
        for (Cereal cereal : Cereal.values()) {
            if (!cereal.gmo()) {
                continue;
            }
            boolean known = GmoResearch.knows(player, cereal);
            Card card = Card.of(new ItemStack(DoHyAgri.seeds(cereal)), cereal.label(), List.of(
                            known ? "Recette connue" : "Recette a decouvrir",
                            data.attempts(cereal.key()) + " essais  ·  mute dans "
                                    + GmoResearch.untilMutation(player, cereal) + " graines"))
                    .accent(known ? GREEN : GOLD)
                    .tooltip(List.of("Une fabrication rend " + config.seedsPerCraft() + " graines",
                            "La recolte ne rend aucune semence"));
            if (cereal == target) {
                card = card.badge("CIBLE").state(MenuView.DONE);
            }
            strains.add(card.action("target:" + cereal.key() + "|viser cette souche"));
        }

        // -- La composition -----------------------------------------------------------
        List<Card> chosen = new ArrayList<>();
        boolean complete = true;
        for (Map.Entry<Identifier, Integer> entry : composition.entrySet()) {
            Item item = BuiltInRegistries.ITEM.getOptional(entry.getKey()).orElse(Items.AIR);
            int have = AgriInv.count(player, item);
            boolean enough = have >= entry.getValue();
            complete &= enough;
            chosen.add(Card.of(new ItemStack(item, entry.getValue()),
                            MenuView.text(new ItemStack(item).getHoverName()),
                            List.of("Quantite : " + entry.getValue(), "Tu en as " + have))
                    .accent(enough ? GREEN : RED).badge("x" + entry.getValue())
                    .action("more:" + entry.getKey() + "|un de plus")
                    .right("less:" + entry.getKey() + "|un de moins"));
        }
        boolean ready = allowed && !composition.isEmpty() && complete;
        Card run = Card.of(new ItemStack(DoHyAgri.GMO_MACHINE_ITEM.get()), "Lancer l'essai", List.of(
                        composition.size() + " / " + config.ingredients() + " ingredients",
                        "Tout est consomme, juste ou faux"))
                .accent(GOLD)
                .tooltip(List.of("Reussi : " + config.seedsPerCraft() + " graines de " + target.label().toLowerCase(),
                        "Rate : un indice dans le carnet"));
        chosen.add(ready ? run.action("run|lancer l'essai") : run.state(MenuView.DISABLED));
        if (GmoResearch.knows(player, target)) {
            chosen.add(Card.of(new ItemStack(Items.WRITABLE_BOOK), "Recette connue", List.of(
                            "Recharge la composition", "qui a reussi"))
                    .accent(GREEN).action("load|recharger la recette"));
        }
        if (!composition.isEmpty()) {
            chosen.add(Card.of(new ItemStack(Items.BARRIER), "Vider", List.of("Retire tout", "de la composition"))
                    .action("clear|vider la composition"));
        }

        // -- Les ingredients possibles ----------------------------------------------
        List<Card> pool = new ArrayList<>();
        boolean full = composition.size() >= config.ingredients();
        for (AgriConfig.Ingredient ingredient : config.pool()) {
            Item item = BuiltInRegistries.ITEM.getOptional(ingredient.item()).orElse(null);
            if (item == null || composition.containsKey(ingredient.item())) {
                continue;
            }
            Card card = Card.of(new ItemStack(item), MenuView.text(new ItemStack(item).getHoverName()),
                    List.of("Tu en as " + AgriInv.count(player, item)));
            pool.add(full ? card.state(MenuView.DISABLED) : card.action("add:" + ingredient.item() + "|ajouter"));
        }

        // -- Le carnet ----------------------------------------------------------------
        List<Card> notes = new ArrayList<>();
        for (String note : data.notes(target.key())) {
            String[] parts = note.split(AgriData.NOTE_SEPARATOR);
            notes.add(Card.of(new ItemStack(Items.PAPER), parts[0], List.of(parts).subList(1, parts.length)));
        }
        if (notes.isEmpty()) {
            notes.add(Card.of(new ItemStack(Items.PAPER), "Aucun essai pour l'instant", List.of(
                    "Un essai rate laisse ici son indice")).state(MenuView.DISABLED));
        }

        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("FERMIER", "NIV. " + level, 0xFFFFFFFF),
                new MenuView.Stat("CIBLE", target.label().toUpperCase(), GREEN),
                new MenuView.Stat("ESSAIS", String.valueOf(data.attempts(target.key())), GOLD));
        String subtitle = allowed
                ? "Neuf ingredients, chacun en quantite precise : cherche la recette de la souche"
                : "Il faut etre Fermier niveau " + config.minLevel();
        return new MenuView(id(), "Machine a OGM", subtitle, stats, List.of(
                new MenuView.Section("SOUCHES", "resources", MenuView.GRID, strains),
                new MenuView.Section("COMPOSITION", "resources", MenuView.GRID, chosen),
                new MenuView.Section("CARNET : " + target.label().toUpperCase(), "resources", MenuView.ROWS, notes),
                new MenuView.Section("INGREDIENTS POSSIBLES", "resources", MenuView.GRID, pool)),
                true);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (!inReach(player)) {
            UiNet.close(player);
            return;
        }
        AgriConfig.Gmo config = AgriConfig.config().gmo();
        AgriData data = GmoResearch.data(player);
        Map<Identifier, Integer> composition = data.composition();
        int colon = key.indexOf(':');
        String verb = colon < 0 ? key : key.substring(0, colon);
        String argument = colon < 0 ? "" : key.substring(colon + 1);
        switch (verb) {
            case "target" -> {
                Cereal cereal = Cereal.byKey(argument);
                if (cereal != null && cereal.gmo()) {
                    data.target(cereal.key());
                }
            }
            case "add" -> {
                Identifier id = Identifier.tryParse(argument);
                if (id != null && composition.size() < config.ingredients() && inPool(id)) {
                    composition.putIfAbsent(id, 1);
                }
            }
            case "more" -> {
                Identifier id = Identifier.tryParse(argument);
                if (id != null && composition.containsKey(id)) {
                    int most = Math.min(config.maxQuantity(),
                            new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getMaxStackSize());
                    composition.put(id, Math.min(most, composition.get(id) + 1));
                }
            }
            case "less" -> {
                Identifier id = Identifier.tryParse(argument);
                if (id != null && composition.containsKey(id)) {
                    int left = composition.get(id) - 1;
                    if (left <= 0) {
                        composition.remove(id);
                    } else {
                        composition.put(id, left);
                    }
                }
            }
            case "clear" -> composition.clear();
            case "load" -> {
                Cereal target = target(player);
                if (GmoResearch.knows(player, target)) {
                    composition.clear();
                    composition.putAll(GmoResearch.recipe(player, target).parts());
                }
            }
            case "run" -> {
                String refusal = GmoResearch.attempt(player, target(player));
                if (refusal != null) {
                    report(player, refusal, "");
                }
            }
            default -> {
            }
        }
    }

    private static boolean inPool(Identifier id) {
        for (AgriConfig.Ingredient ingredient : AgriConfig.config().gmo().pool()) {
            if (ingredient.item().equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void back(ServerPlayer player) {
        OPEN.remove(player.getUUID());
        UiNet.close(player);
    }
}
