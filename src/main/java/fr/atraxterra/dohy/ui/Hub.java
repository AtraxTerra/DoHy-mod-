package fr.atraxterra.dohy.ui;

import fr.atraxterra.dohy.boost.Boosters;
import fr.atraxterra.dohy.boost.BoutiqueMenu;
import fr.atraxterra.dohy.collection.CollectionsMenu;
import fr.atraxterra.dohy.contract.ActiveContract;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.contract.ContractData;
import fr.atraxterra.dohy.contract.ContractsMenu;
import fr.atraxterra.dohy.economy.Bank;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.museum.MuseumApi;
import fr.atraxterra.dohy.museum.MuseumBrowseMenu;
import fr.atraxterra.dohy.shop.BankMenu;
import fr.atraxterra.dohy.shop.SellChestMenu;
import fr.atraxterra.dohy.shop.ShopMenu;
import fr.atraxterra.dohy.skill.Skill;
import fr.atraxterra.dohy.skill.SkillApi;
import fr.atraxterra.dohy.skill.SkillsMenu;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Le menu central.
 *
 * <p>Huit systemes et autant de commandes a retenir : le menu existe pour que
 * {@code /menu} suffise. Chaque vignette porte en plus le chiffre qui dit s'il
 * faut y aller — le nombre de contrats restants, l'epargne, les pieces
 * manquantes au musee. Un menu qui ne fait que lister des portes ne sert a rien
 * de plus que la liste des commandes.
 */
public final class Hub extends DisplayMenu {

    private static final int SLOT_HEADER = 4;

    private static final int SLOT_SHOP = 19;
    private static final int SLOT_SELL = 21;
    private static final int SLOT_BOUTIQUE = 23;
    private static final int SLOT_BANK = 25;

    private static final int SLOT_CONTRACTS = 28;
    private static final int SLOT_SKILLS = 30;
    private static final int SLOT_COLLECTIONS = 32;
    private static final int SLOT_MUSEUM = 34;

    private static final int SLOT_CLOSE = 49;

    public Hub(int containerId, Inventory inventory, ServerPlayer player) {
        super(containerId, inventory, player, 6);
        refresh();
    }

    /** Ouvre le menu. Point d'entree de {@code /menu} et du bouton « Retour ». */
    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new Hub(containerId, inventory, player),
                Component.literal("DoHy")));
    }

    @Override
    protected void onClick(int slot, int button, boolean shift) {
        switch (slot) {
            case SLOT_SHOP -> {
                click(0.9f);
                open("Shop", (id, inventory) -> new ShopMenu(id, inventory, player));
            }
            case SLOT_SELL -> {
                click(0.9f);
                open("Coffre de vente — ferme pour vendre",
                        (id, inventory) -> new SellChestMenu(id, inventory, player));
            }
            case SLOT_BOUTIQUE -> {
                click(0.9f);
                open("Boutique", (id, inventory) -> new BoutiqueMenu(id, inventory, player));
            }
            case SLOT_BANK -> {
                click(0.9f);
                open("Banque", (id, inventory) -> new BankMenu(id, inventory, player));
            }
            case SLOT_CONTRACTS -> {
                click(0.9f);
                open("Contrats", (id, inventory) -> new ContractsMenu(id, inventory, player));
            }
            case SLOT_SKILLS -> {
                click(0.9f);
                open("Skills", (id, inventory) -> new SkillsMenu(id, inventory, player));
            }
            case SLOT_COLLECTIONS -> {
                click(0.9f);
                open("Collections", (id, inventory) -> new CollectionsMenu(id, inventory, player));
            }
            case SLOT_MUSEUM -> {
                click(0.9f);
                open("Musee", (id, inventory) -> new MuseumBrowseMenu(id, inventory, player));
            }
            case SLOT_CLOSE -> {
                click(0.6f);
                player.closeContainer();
            }
            default -> {
                // Emplacement decoratif.
            }
        }
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_HEADER, header());

        set(SLOT_SHOP, Gui.button(Items.EMERALD, "Shop", Gui.lore(
                Gui.hint("acheter et vendre a l'unite"),
                Gui.hint("prix dynamiques, six categories"),
                Gui.blank(),
                Gui.action("clic pour ouvrir"))));

        set(SLOT_SELL, Gui.button(Items.CHEST, "Coffre de vente", Gui.lore(
                Gui.hint("depose tout, ferme, c'est vendu"),
                Gui.hint("les items non vendables reviennent"),
                Gui.blank(),
                Gui.action("clic pour ouvrir"))));

        set(SLOT_BOUTIQUE, Gui.button(Items.GOLD_BLOCK, "Boutique", Gui.lore(
                Gui.hint("boosters, caisses, assurance, autosell"),
                Boosters.active(player) > 1.0d
                        ? Gui.good("booster x" + (long) Boosters.active(player) + " actif")
                        : Gui.hint("aucun booster actif"),
                Gui.blank(),
                Gui.action("clic pour ouvrir"))));

        set(SLOT_BANK, Gui.button(Items.CHEST_MINECART, "Banque", Gui.lore(
                Gui.field("epargne", MoneyFormat.exact(Bank.banked(player))),
                Gui.hint("protegee de la mort, elle rapporte"),
                Gui.blank(),
                Gui.action("clic pour ouvrir"))));

        set(SLOT_CONTRACTS, contracts());
        set(SLOT_SKILLS, skills());
        set(SLOT_COLLECTIONS, Gui.button(Items.BOOKSHELF, "Collections", Gui.lore(
                Gui.hint("recolter debloque la vente,"),
                Gui.hint("puis releve le prix plancher"),
                Gui.blank(),
                Gui.action("clic pour ouvrir"))));
        set(SLOT_MUSEUM, museum());

        set(SLOT_CLOSE, Gui.close());
    }

    private ItemStack header() {
        return Gui.button(Items.NETHER_STAR,
                Component.literal("DoHy").withStyle(ChatFormatting.GOLD), Gui.lore(
                        Gui.field("liquide", MoneyFormat.exact(Economy.balance(player))),
                        Gui.field("patrimoine", MoneyFormat.exact(
                                Economy.of(player).netWorth())),
                        Gui.blank(),
                        Gui.field("niveau global",
                                String.valueOf(ContractApi.globalLevel(player))),
                        Gui.bar(ContractApi.globalXpIntoLevel(player),
                                ContractApi.globalXpPerLevel(), 20)));
    }

    private ItemStack contracts() {
        ContractData data = ContractApi.data(player);
        int left = 0;
        for (ActiveContract contract : data.daily()) {
            if (!contract.done()) {
                left++;
            }
        }
        List<ActiveContract> weekly = data.weekly();
        boolean weeklyLeft = !weekly.isEmpty() && !weekly.get(0).done();

        return Gui.button(Items.WRITABLE_BOOK, "Contrats", Gui.lore(
                left == 0
                        ? Gui.good("les trois quotidiens sont faits")
                        : Gui.field("quotidiens restants", String.valueOf(left)),
                weeklyLeft
                        ? Gui.hint("l'hebdomadaire est en cours")
                        : Gui.good("hebdomadaire termine"),
                Gui.blank(),
                Gui.action("clic pour ouvrir")));
    }

    private ItemStack skills() {
        int total = 0;
        for (Skill skill : Skill.values()) {
            total += SkillApi.levelOf(player, skill);
        }
        return Gui.button(Items.IRON_PICKAXE, "Skills", Gui.lore(
                Gui.field("niveaux cumules", String.valueOf(total)),
                Gui.hint("les perks valent plus que l'argent verse"),
                Gui.blank(),
                Gui.action("clic pour ouvrir")));
    }

    private ItemStack museum() {
        int expected = 0;
        int donated = 0;
        for (String wing : MuseumApi.categories()) {
            var items = MuseumApi.expected(wing);
            expected += items.size();
            for (var id : items) {
                if (MuseumApi.data(player).has(id)) {
                    donated++;
                }
            }
        }
        return Gui.button(Items.ENCHANTING_TABLE, "Musee", Gui.lore(
                Gui.field("pieces exposees", donated + " / " + expected),
                Gui.bar(donated, expected, 20),
                Gui.blank(),
                Gui.hint("un don est definitif, mais les bonus"),
                Gui.hint("d'une aile complete sont permanents"),
                Gui.blank(),
                Gui.action("clic pour ouvrir")));
    }
}
