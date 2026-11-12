package fr.atraxterra.dohy.tech;

import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** L'ecran d'un serveur de trading : son etat, son energie, sa caisse. */
public final class TradingServerMenu extends MenuController {

    public static final TradingServerMenu INSTANCE = new TradingServerMenu();

    /** Le serveur que chaque joueur regarde. */
    private static final Map<UUID, BlockPos> OPEN = new ConcurrentHashMap<>();

    /** Au-dela, le joueur s'est eloigne : l'ecran se ferme. */
    private static final double REACH = 8.0d;

    private TradingServerMenu() {
        super("trading_server");
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        OPEN.put(player.getUUID(), pos.immutable());
        INSTANCE.open(player);
    }

    private static TradingServerBlockEntity server(ServerPlayer player) {
        BlockPos pos = OPEN.get(player.getUUID());
        if (pos == null || player.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d)
                > REACH * REACH) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof TradingServerBlockEntity server ? server : null;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        TradingServerBlockEntity server = server(player);
        if (server == null) {
            return new MenuView(id(), "Serveur de trading", "Ce serveur n'est plus a portee", List.of(),
                    List.of(), true);
        }
        TradingServerConfig.Config config = TradingServerConfig.config();
        TradingServerBlockEntity.Status status = server.status();
        boolean running = status == TradingServerBlockEntity.Status.RUNNING;
        int stored = server.energyStored();
        long pending = server.pending();

        List<Card> cards = new ArrayList<>();
        cards.add(Card.of(new ItemStack(running ? Items.REDSTONE_TORCH : Items.LEVER), status.label(),
                        List.of(status.hint(), "Proprietaire : "
                                + (server.ownerName().isEmpty() ? "personne" : server.ownerName())))
                .accent(running ? 0xFF22E558 : 0xFFEF3826).action("refresh|actualiser"));
        cards.add(Card.of(new ItemStack(Items.REDSTONE), "Energie", List.of(
                        MoneyFormat.exact(stored) + " / " + MoneyFormat.exact(config.energyCapacity()) + " FE",
                        "Consomme " + config.energyPerTick() + " FE par tick"))
                .accent(0xFFFAC400).progress(stored, config.energyCapacity())
                .tooltip(List.of("Tout cable d'energie FE l'alimente",
                        "NTM NEXT : " + (config.energyPerTick() * 5L) + " HE par tick (5 HE = 1 FE)",
                        "Reserve pleine : " + (config.energyCapacity() / Math.max(1, config.energyPerTick()) / 20)
                                + " s d'autonomie")));
        cards.add(Card.of(new ItemStack(Items.EMERALD), "Production", List.of(
                        MoneyFormat.exact(config.incomePerHour()) + " $ par heure",
                        MoneyFormat.exact(server.totalProduced()) + " $ produits en tout"))
                .accent(0xFF27C4E8)
                .tooltip(List.of("Il ne produit que sous tension,", "et dans un chunk charge")));
        Card cash = Card.of(new ItemStack(Items.GOLD_INGOT), "Caisse", List.of(
                        MoneyFormat.exact(pending) + " / " + MoneyFormat.exact(config.bufferMax()) + " $",
                        pending > 0L ? "A encaisser" : "Rien a encaisser"))
                .accent(0xFFFAC400).progress(pending, config.bufferMax())
                .tooltip(List.of("Pleine, la machine s'arrete :", config.bufferHours() + " h de production au plus"));
        cards.add(pending > 0L && server.isOwner(player) ? cash.action("collect|encaisser") : cash);
        int loaded = server.loadedCount();
        cards.add(Card.of(new ItemStack(DoHyTech.TRADING_SERVER_ITEM.get()), "Tes serveurs", List.of(
                        Math.min(loaded, config.maxPerPlayer()) + " / " + config.maxPerPlayer() + " en service",
                        loaded > config.maxPerPlayer() ? (loaded - config.maxPerPlayer()) + " en attente"
                                : "Places libres : " + (config.maxPerPlayer() - loaded)))
                .accent(0xFF8F5BEA)
                .tooltip(List.of("Seuls les serveurs charges comptent", "Les premiers poses tournent d'abord")));

        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("ETAT", status.label().toUpperCase(), running ? 0xFF22E558 : 0xFFEF3826),
                new MenuView.Stat("ENERGIE", (config.energyCapacity() <= 0 ? 0
                        : stored * 100L / config.energyCapacity()) + " %", 0xFFFAC400),
                new MenuView.Stat("CAISSE", MoneyFormat.shortForm(pending), 0xFFFFFFFF));
        return new MenuView(id(), "Serveur de trading", "De l'energie en entree, de l'argent en sortie", stats,
                List.of(new MenuView.Section("MACHINE", "resources", MenuView.GRID, cards)), true);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        TradingServerBlockEntity server = server(player);
        if (server == null) {
            UiNet.close(player);
            return;
        }
        if ("collect".equals(key)) {
            String refusal = server.collect(player);
            if (refusal == null) {
                success(player);
            } else {
                report(player, refusal, "");
            }
        }
        // « refresh » ne fait rien de plus : l'ecran se redessine apres chaque action.
    }

    @Override
    protected void back(ServerPlayer player) {
        OPEN.remove(player.getUUID());
        UiNet.close(player);
    }
}
