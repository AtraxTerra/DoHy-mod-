package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Le moulin : trois cereales et un peu d'energie font une farine. A la main :
 * clic gauche, une farine ; clic droit, tout ce que l'inventaire et la reserve
 * permettent, jusqu'a une pile — et de l'XP de Fermier. En automate : ce qui est
 * depose dans la machine est moulu tout seul ({@link AgriMachineBlockEntity}).
 */
public final class MillMenu extends MachineMenu {

    public static final MillMenu INSTANCE = new MillMenu();

    private static final int STACK = 64;

    private MillMenu() {
        super("flour_mill", AgriMachineBlock.Kind.MILL);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        AgriConfig.Machines config = AgriConfig.config().machines();
        AgriMachineBlockEntity machine = machine(player);
        int energy = machine == null ? 0 : machine.energyStored();
        List<Card> cards = new ArrayList<>();
        for (String key : DoHyAgri.flourKeys()) {
            Item grain = DoHyAgri.grain(key);
            Item flour = DoHyAgri.flour(key);
            int have = AgriInv.count(player, grain);
            int possible = possible(have, energy, config);
            Card card = Card.of(new ItemStack(flour), MenuView.text(new ItemStack(flour).getHoverName()), List.of(
                            MenuView.text(Component.literal(config.millRatio() + " x ")
                                    .append(new ItemStack(grain).getHoverName())),
                            "Tu en as " + have + "  ·  " + config.millEnergy() + " FE"))
                    .accent(possible > 0 ? GREEN : 0xFF55545C).badge(possible > 0 ? "x" + possible : "");
            cards.add(possible > 0
                    ? card.action("one:" + key + "|moudre une farine").right("all:" + key + "|moudre jusqu'a une pile")
                    : card.state(MenuView.DISABLED));
        }
        List<MenuView.Stat> stats = List.of(energyStat(machine),
                new MenuView.Stat("UNE FARINE", config.millRatio() + " CEREALES, " + config.millEnergy() + " FE",
                        0xFFFFFFFF),
                workStat(machine));
        return new MenuView(id(), "Moulin", "Clic gauche : une farine  ·  clic droit : jusqu'a une pile", stats,
                List.of(stock(player, machine),
                        new MenuView.Section("MOUDRE A LA MAIN", "resources", MenuView.GRID, cards)), true);
    }

    private static int possible(int grains, int energy, AgriConfig.Machines config) {
        int byGrain = grains / Math.max(1, config.millRatio());
        int byEnergy = config.millEnergy() <= 0 ? STACK : energy / config.millEnergy();
        return Math.max(0, Math.min(STACK, Math.min(byGrain, byEnergy)));
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        AgriMachineBlockEntity machine = machine(player);
        if (machine == null) {
            UiNet.close(player);
            return;
        }
        if (stockCard(player, machine, key)) {
            return;
        }
        int colon = key.indexOf(':');
        if (colon < 0) {
            return;
        }
        String refusal = mill(player, machine, key.substring(colon + 1), key.startsWith("all:") ? STACK : 1);
        if (refusal != null) {
            report(player, refusal, "");
        } else {
            play(player, SoundEvents.GRINDSTONE_USE, 1.2f);
        }
    }

    /**
     * Moud jusqu'a {@code wanted} farines.
     *
     * @return le refus, ou {@code null} si au moins une farine est sortie
     */
    static String mill(ServerPlayer player, AgriMachineBlockEntity machine, String cereal, int wanted) {
        if (!DoHyAgri.flourKeys().contains(cereal)) {
            return "Le moulin ne connait pas cette cereale.";
        }
        AgriConfig.Machines config = AgriConfig.config().machines();
        Item grain = DoHyAgri.grain(cereal);
        int count = Math.min(wanted, possible(AgriInv.count(player, grain), machine.energyStored(), config));
        if (count <= 0) {
            return machine.energyStored() < config.millEnergy()
                    ? "Le moulin n'a pas assez d'energie." : "Il faut " + config.millRatio() + " cereales par farine.";
        }
        if (!machine.spend(count * config.millEnergy())) {
            return "Le moulin n'a pas assez d'energie.";
        }
        AgriInv.take(player, grain, count * config.millRatio());
        AgriInv.give(player, DoHyAgri.flour(cereal), count);
        // Le travail a la main paie en XP de Fermier ; l'automate, non.
        if (config.millXp() > 0) {
            JobApi.grant(player, Job.FARMER, config.millXp() * count);
        }
        AgriProduction.produced(player, DoHyAgri.flour(cereal), count);
        return null;
    }
}
