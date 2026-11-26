package fr.atraxterra.dohy.casino;

import fr.atraxterra.dohy.faction.enterprise.Law;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Les ecrans du casino : le livre de son maitre ({@code /casino}) et le reglage d'une machine aleatoire. */
public final class CasinoMenus {

    private CasinoMenus() {
    }

    static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f", value).replace('.', ',') + " %";
    }

    /** Le casino du joueur : son coffre, ses jeux et leurs chiffres. */
    public static final class Book extends MenuController {

        public static final Book INSTANCE = new Book();

        private Book() {
            super("casino_book");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            CasinoBook.Account account = Casinos.book(player.level().getServer()).find(player.getUUID());
            long balance = account == null ? 0L : account.balance;
            long plays = 0L;
            long played = 0L;
            long paid = 0L;
            List<Card> games = new ArrayList<>();
            if (account != null) {
                double scale = Casinos.scale(account);
                for (Map.Entry<String, CasinoBook.Game> entry : account.games.entrySet()) {
                    CasinoBook.Game game = entry.getValue();
                    plays += game.plays;
                    played += game.played;
                    paid += game.paid;
                    Law.Status law = Casinos.law(game);
                    BlockPos slot = BlockPos.of(game.slot);
                    games.add(Card.of(new ItemStack(DoHyCasino.COIN_SLOT_ITEM.get()), game.title(), List.of(
                                    MoneyFormat.exact(game.price) + " $ · " + game.plays + " parties",
                                    game.plays > 0 ? "Rend " + percent(game.redistribution()) : "Pas encore joue"))
                            .badge("JEU " + law.name()).accent(law.color())
                            .tooltip(List.of("Somme jouee : " + MoneyFormat.exact(game.played) + " $",
                                    "Somme distribuee : " + MoneyFormat.exact(game.paid) + " $",
                                    "Gagnants non payes : " + game.unpaid,
                                    "Clients : " + String.format(Locale.ROOT, "%.1f",
                                            Casinos.rate(game) * scale).replace('.', ',') + " par heure",
                                    "Monnayeur : " + slot.getX() + " " + slot.getY() + " " + slot.getZ()))
                            .right("forget:" + entry.getKey() + "|retirer ce jeu du casino"));
                }
            }
            if (games.isEmpty()) {
                games.add(Card.of(new ItemStack(Items.OAK_SIGN), "Aucun jeu", List.of("Accroche un panneau [Casino]",
                        "a un monnayeur, clique-le")).accent(0xFF55545C));
            }
            List<Card> safe = new ArrayList<>();
            safe.add(Card.of(new ItemStack(Items.GOLD_INGOT), "Retirer", List.of("Tout vers ton porte-monnaie",
                            MoneyFormat.exact(balance) + " $"))
                    .accent(balance > 0L ? 0xFF22E558 : 0xFF55545C).action("withdraw|tout retirer"));
            safe.add(Card.of(new ItemStack(Items.GOLD_BLOCK), "Remplir", List.of("Pour payer les gagnants",
                            "Clic : 10 000 $ · droit : 100 000 $"))
                    .accent(0xFFFAC400).action("deposit:10000|verser 10 000 $")
                    .right("deposit:100000|verser 100 000 $"));
            if (account != null && account.seized > 0L) {
                safe.add(Card.of(new ItemStack(Items.IRON_BARS), "Saisi", List.of("Par la police, en tout",
                        MoneyFormat.exact(account.seized) + " $")).accent(0xFFEF3826));
            }
            List<Card> rules = List.of(
                    Card.of(new ItemStack(Items.OAK_SIGN), "[Casino]", List.of("Sur un monnayeur :",
                                    "nom, prix, nom du jeu"))
                            .tooltip(List.of("Ligne 1 : [Casino]", "Ligne 2 : le nom du casino",
                                    "Ligne 3 : le prix du ticket", "Ligne 4 : le nom du jeu (facultatif)",
                                    "Payer le panneau lance le jeu ; accroupi, tu le reecris")),
                    Card.of(new ItemStack(Items.OAK_SIGN), "[Casino2]", List.of("Sur une caisse :",
                                    "nom, gain, nom du jeu"))
                            .tooltip(List.of("Ligne 1 : [Casino2]", "Ligne 2 : le nom du casino",
                                    "Ligne 3 : la somme gagnee", "Ligne 4 : le nom du jeu (le meme)",
                                    "Quand le jeu allume la caisse, le gagnant est paye")),
                    Card.of(new ItemStack(DoHyCasino.RANDOM_MACHINE_ITEM.get()), "Les clients", List.of(
                                    "Joueurs fictifs, selon", "la loi du pays sur le jeu"))
                            .tooltip(List.of("Environ 12 par heure et par jeu ; x1,5 legal, x0,4 interdit",
                                    "Moins si le jeu rend peu ou si le ticket est cher",
                                    "Legal : 20 % de taxes ; tolere : pots-de-vin",
                                    "Interdit : la police peut saisir la moitie du coffre")));
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("COFFRE", MoneyFormat.exact(balance) + " $", 0xFFFAC400),
                    new MenuView.Stat("PARTIES", String.valueOf(plays), 0xFFFFFFFF),
                    new MenuView.Stat("REDISTRIBUTION", played > 0L ? percent(100.0d * paid / played) : "-",
                            0xFF22E558));
            return new MenuView(id(), "Casino", "Machines aleatoires, des et panneaux [Casino]", stats, List.of(
                    new MenuView.Section("LE COFFRE", "resources", MenuView.GRID, safe),
                    new MenuView.Section("TES JEUX", "upgrade", MenuView.GRID, games),
                    new MenuView.Section("COMMENT CA MARCHE", "resources", MenuView.GRID, rules)), true);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            String refusal;
            if ("withdraw".equals(key)) {
                refusal = Casinos.withdraw(player);
            } else if (key.startsWith("deposit:")) {
                long amount;
                try {
                    amount = Long.parseLong(key.substring("deposit:".length()));
                } catch (NumberFormatException wrong) {
                    return;
                }
                refusal = Casinos.deposit(player, amount);
            } else if (key.startsWith("forget:")) {
                refusal = Casinos.forget(player, key.substring("forget:".length()));
                if (refusal == null) {
                    Casinos.tell(player, "Jeu retire de ton casino (le panneau reste).", ChatFormatting.YELLOW);
                }
            } else {
                return;
            }
            if (refusal != null) {
                Casinos.tell(player, refusal, ChatFormatting.RED);
            }
        }
    }

    /** Le reglage d'une machine aleatoire : sa chance de laisser passer le signal. */
    public static final class Machine extends MenuController {

        public static final Machine INSTANCE = new Machine();

        private final Map<UUID, BlockPos> open = new ConcurrentHashMap<>();

        private Machine() {
            super("casino_machine");
        }

        public void openAt(ServerPlayer player, BlockPos pos) {
            open.put(player.getUUID(), pos.immutable());
            open(player);
        }

        private CasinoBlockEntity machine(ServerPlayer player) {
            BlockPos pos = open.get(player.getUUID());
            if (pos == null || player.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d) > 64.0d
                    || !(player.level().getBlockState(pos).getBlock() instanceof RandomMachineBlock)
                    || !(player.level().getBlockEntity(pos) instanceof CasinoBlockEntity machine)
                    || !machine.ownedBy(player)) {
                return null;
            }
            return machine;
        }

        @Override
        public MenuView view(ServerPlayer player) {
            CasinoBlockEntity machine = machine(player);
            int chance = machine == null ? CasinoBlockEntity.DEFAULT_CHANCE : machine.chance();
            long rolls = machine == null ? 0L : machine.rolls();
            long passes = machine == null ? 0L : machine.passes();
            List<Card> steps = new ArrayList<>();
            for (int step : new int[] {-10, -1, 1, 10}) {
                boolean up = step > 0;
                int next = Math.max(1, Math.min(99, chance + step));
                Card card = Card.of(new ItemStack(up ? Items.LIME_DYE : Items.RED_DYE),
                        (up ? "+" : "") + step + " %", List.of("Passe a " + next + " %",
                                up ? "Plus de gagnants" : "Moins de gagnants"))
                        .accent(up ? 0xFF22E558 : 0xFFEF3826);
                steps.add(next == chance ? card.state(MenuView.DISABLED) : card.action("add:" + step + "|regler"));
            }
            List<Card> about = List.of(Card.of(new ItemStack(Items.REPEATER), "Le signal", List.of(
                    "Entre par l'arriere,", "sort par la fleche"))
                    .tooltip(List.of("A chaque front montant, un tirage",
                            "Reussi : la sortie reste allumee tant que l'entree l'est",
                            "Rate : rien jusqu'au prochain signal",
                            "Les autres joueurs voient la chance d'un clic droit")));
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("CHANCE", chance + " %", 0xFFFAC400),
                    new MenuView.Stat("TIRAGES", String.valueOf(rolls), 0xFFFFFFFF),
                    new MenuView.Stat("PASSES", rolls > 0L ? percent(100.0d * passes / rolls) : "-", 0xFF22E558));
            return new MenuView(id(), "Machine aleatoire", "Laisse passer le signal, ou non", stats, List.of(
                    new MenuView.Section("CHANCE DE PASSAGE", "upgrade", MenuView.GRID, steps),
                    new MenuView.Section("FONCTIONNEMENT", "resources", MenuView.GRID, about)), false);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            CasinoBlockEntity machine = machine(player);
            if (machine == null) {
                UiNet.close(player);
                return;
            }
            if (key.startsWith("add:")) {
                try {
                    machine.setChance(machine.chance() + Integer.parseInt(key.substring(4)));
                } catch (NumberFormatException ignored) {
                    // une cle venue du client, illisible : rien
                }
            }
        }
    }
}
