package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * La machine a pain : une liste de pains, un clic par fournee. Clic droit,
 * huit fournees d'un coup si l'inventaire et la reserve suivent.
 *
 * <p>Les fournees sont dans {@code agriculture.json} ; les pains simples
 * sortent par quatre. Un Fermier confirme a une chance de sortir un pain de
 * plus par fournee.
 */
public final class BreadMachineMenu extends MachineMenu {

    public static final BreadMachineMenu INSTANCE = new BreadMachineMenu();

    private static final int MANY = 8;

    private BreadMachineMenu() {
        super("bread_machine", AgriMachineBlock.Kind.OVEN);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        AgriConfig.Config config = AgriConfig.config();
        AgriMachineBlockEntity machine = machine(player);
        int energy = machine == null ? 0 : machine.energyStored();
        List<Card> cards = new ArrayList<>();
        for (AgriConfig.Batch batch : config.breads()) {
            Bread bread = Bread.byKey(batch.id());
            if (bread == null) {
                continue;
            }
            int possible = possible(player, batch, energy);
            List<String> lines = new ArrayList<>();
            for (Map.Entry<Identifier, Integer> input : batch.inputs().entrySet()) {
                Item item = BuiltInRegistries.ITEM.getOptional(input.getKey()).orElse(null);
                if (item == null) {
                    continue;
                }
                lines.add(MenuView.text(Component.literal(Math.min(AgriInv.count(player, item), input.getValue())
                        + " / " + input.getValue() + " ").append(new ItemStack(item).getHoverName())));
            }
            List<String> tooltip = new ArrayList<>();
            tooltip.add(batch.energy() + " FE la fournee");
            for (int line = 1; line <= bread.loreLines(); line++) {
                tooltip.add(MenuView.text(Component.translatable("tooltip.dohy." + bread.key() + "." + line)));
            }
            Card card = Card.of(new ItemStack(DoHyAgri.bread(bread), batch.count()),
                            MenuView.text(new ItemStack(DoHyAgri.bread(bread)).getHoverName()), lines)
                    .accent(possible > 0 ? GREEN : 0xFF55545C).badge("x" + batch.count()).tooltip(tooltip);
            cards.add(possible > 0
                    ? card.action("one:" + batch.id() + "|cuire une fournee")
                    .right("many:" + batch.id() + "|cuire " + MANY + " fournees")
                    : card.state(MenuView.DISABLED));
        }
        int level = JobApi.level(player, Job.FARMER);
        boolean bonus = level >= config.machines().bonusBreadLevel();
        List<MenuView.Stat> stats = List.of(energyStat(machine),
                new MenuView.Stat("FERMIER", "NIV. " + level, 0xFFFFFFFF),
                new MenuView.Stat("PAIN EN PLUS", bonus
                        ? Math.round(config.machines().bonusBreadChance() * 100.0d) + " %"
                        : "NIV. " + config.machines().bonusBreadLevel(), bonus ? GREEN : GOLD));
        return new MenuView(id(), "Machine a pain", "Clic gauche : une fournee  ·  clic droit : " + MANY
                + " fournees", stats,
                List.of(new MenuView.Section("PAINS", "resources", MenuView.GRID, cards)), true);
    }

    /** Fournees possibles avec l'inventaire du joueur et l'energie de la machine. */
    private static int possible(ServerPlayer player, AgriConfig.Batch batch, int energy) {
        int most = batch.energy() <= 0 ? MANY : energy / batch.energy();
        for (Map.Entry<Identifier, Integer> input : batch.inputs().entrySet()) {
            Item item = BuiltInRegistries.ITEM.getOptional(input.getKey()).orElse(null);
            if (item == null) {
                return 0;
            }
            most = Math.min(most, AgriInv.count(player, item) / input.getValue());
        }
        return Math.max(0, most);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        AgriMachineBlockEntity machine = machine(player);
        if (machine == null) {
            UiNet.close(player);
            return;
        }
        int colon = key.indexOf(':');
        if (colon < 0) {
            return;
        }
        AgriConfig.Batch batch = AgriConfig.config().batch(key.substring(colon + 1));
        if (batch == null) {
            return;
        }
        String refusal = bake(player, machine, batch, key.startsWith("many:") ? MANY : 1);
        if (refusal != null) {
            report(player, refusal, "");
        } else {
            play(player, SoundEvents.FIRECHARGE_USE, 1.3f);
        }
    }

    /**
     * Cuit jusqu'a {@code wanted} fournees.
     *
     * @return le refus, ou {@code null} si au moins une fournee est sortie
     */
    static String bake(ServerPlayer player, AgriMachineBlockEntity machine, AgriConfig.Batch batch, int wanted) {
        Bread bread = Bread.byKey(batch.id());
        if (bread == null) {
            return "Ce pain n'existe pas.";
        }
        int count = Math.min(wanted, possible(player, batch, machine.energyStored()));
        if (count <= 0) {
            return machine.energyStored() < batch.energy()
                    ? "La machine a pain n'a pas assez d'energie." : "Il te manque des ingredients.";
        }
        if (!machine.spend(count * batch.energy())) {
            return "La machine a pain n'a pas assez d'energie.";
        }
        batch.inputs().forEach((id, quantity) ->
                AgriInv.take(player, BuiltInRegistries.ITEM.getValue(id), quantity * count));
        int made = count * batch.count();
        AgriConfig.Machines config = AgriConfig.config().machines();
        if (JobApi.level(player, Job.FARMER) >= config.bonusBreadLevel()) {
            int extra = 0;
            for (int index = 0; index < count; index++) {
                if (player.getRandom().nextDouble() < config.bonusBreadChance()) {
                    extra++;
                }
            }
            if (extra > 0) {
                made += extra;
                player.sendSystemMessage(Component.literal("Machine a pain : +" + extra + " pain"
                        + (extra > 1 ? "s" : "") + " grace au metier de Fermier.").withStyle(ChatFormatting.GREEN), true);
            }
        }
        AgriInv.give(player, DoHyAgri.bread(bread), made);
        return null;
    }
}
