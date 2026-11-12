package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les croisements de la machine a OGM.
 *
 * <p>Un OGM ne se trouve pas : il se tente. Un essai consomme deux lots de
 * semences naturelles et de la poudre d'os, qu'il reussisse ou non, et reussit
 * avec une chance qui monte avec le niveau de Fermier. NationsGlory en fait une
 * recherche ; ici c'est le metier qui tient lieu de laboratoire.
 */
public final class GmoMachineMenu extends MenuController {

    public static final GmoMachineMenu INSTANCE = new GmoMachineMenu();

    private static final Map<UUID, BlockPos> OPEN = new ConcurrentHashMap<>();
    private static final double REACH = 8.0d;
    private static final int CARRIED_SLOTS = 36;

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

    private static int carried(ServerPlayer player, Item item) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < CARRIED_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void take(ServerPlayer player, Item item, int wanted) {
        int left = wanted;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < CARRIED_SLOTS && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int part = Math.min(left, stack.getCount());
                stack.shrink(part);
                left -= part;
            }
        }
        inventory.setChanged();
    }

    @Override
    public MenuView view(ServerPlayer player) {
        AgriConfig.Config config = AgriConfig.config();
        int level = JobApi.level(player, Job.FARMER);
        boolean allowed = level >= config.gmoMinLevel();
        double chance = config.gmoChance(level);
        int yield = config.gmoYield() + (level >= config.gmoBonusLevel() ? 1 : 0);

        List<Card> cards = new ArrayList<>();
        for (Cereal cereal : Cereal.values()) {
            if (!cereal.gmo()) {
                continue;
            }
            Item first = DoHyAgri.seeds(cereal.parentA());
            Item second = DoHyAgri.seeds(cereal.parentB());
            int haveFirst = carried(player, first);
            int haveSecond = carried(player, second);
            int haveMeal = carried(player, Items.BONE_MEAL);
            boolean ready = allowed && haveFirst >= config.gmoParentSeeds() && haveSecond >= config.gmoParentSeeds()
                    && haveMeal >= config.gmoBoneMeal();
            String firstName = MenuView.text(Component.literal(Math.min(haveFirst, config.gmoParentSeeds()) + " / "
                    + config.gmoParentSeeds() + " ").append(new ItemStack(first).getHoverName()));
            String secondName = MenuView.text(Component.literal(Math.min(haveSecond, config.gmoParentSeeds()) + " / "
                    + config.gmoParentSeeds() + " ").append(new ItemStack(second).getHoverName()));
            Card card = Card.of(new ItemStack(DoHyAgri.seeds(cereal.key())), cereal.label(), List.of(
                            firstName, secondName,
                            Math.min(haveMeal, config.gmoBoneMeal()) + " / " + config.gmoBoneMeal()
                                    + " poudre d'os"))
                    .accent(0xFF22E558).badge(Math.round(chance * 100.0d) + " %")
                    .tooltip(List.of("Un essai consomme tout, reussi ou non",
                            "Reussite : " + yield + " graines de " + cereal.label().toLowerCase()));
            cards.add(ready ? card.action("cross:" + cereal.key() + "|tenter le croisement")
                    : card.state(MenuView.DISABLED));
        }

        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("FERMIER", "NIV. " + level, 0xFFFFFFFF),
                new MenuView.Stat("REUSSITE", Math.round(chance * 100.0d) + " %", 0xFF22E558),
                new MenuView.Stat("RECOLTE", yield + " GRAINES", 0xFFFAC400));
        String subtitle = allowed ? "Croise deux semences ; la chance monte avec ton niveau de Fermier"
                : "Il faut etre Fermier niveau " + config.gmoMinLevel();
        return new MenuView(id(), "Machine a OGM", subtitle, stats,
                List.of(new MenuView.Section("CROISEMENTS", "resources", MenuView.GRID, cards)), true);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (!inReach(player)) {
            UiNet.close(player);
            return;
        }
        if (!key.startsWith("cross:")) {
            return;
        }
        Cereal target = null;
        for (Cereal cereal : Cereal.values()) {
            if (cereal.gmo() && cereal.key().equals(key.substring("cross:".length()))) {
                target = cereal;
            }
        }
        if (target == null) {
            return;
        }
        String refusal = cross(player, target);
        if (refusal != null) {
            report(player, refusal, "");
        }
    }

    /**
     * Tente un croisement.
     *
     * @return le refus a afficher, ou {@code null} si l'essai a eu lieu (reussi ou non)
     */
    static String cross(ServerPlayer player, Cereal target) {
        AgriConfig.Config config = AgriConfig.config();
        int level = JobApi.level(player, Job.FARMER);
        if (level < config.gmoMinLevel()) {
            return "Il faut etre Fermier niveau " + config.gmoMinLevel() + ".";
        }
        Item first = DoHyAgri.seeds(target.parentA());
        Item second = DoHyAgri.seeds(target.parentB());
        if (carried(player, first) < config.gmoParentSeeds() || carried(player, second) < config.gmoParentSeeds()
                || carried(player, Items.BONE_MEAL) < config.gmoBoneMeal()) {
            return "Il te manque des semences ou de la poudre d'os.";
        }
        take(player, first, config.gmoParentSeeds());
        take(player, second, config.gmoParentSeeds());
        take(player, Items.BONE_MEAL, config.gmoBoneMeal());

        if (player.getRandom().nextDouble() >= config.gmoChance(level)) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                    0.6f, 0.8f);
            player.sendSystemMessage(Component.literal("Machine a OGM : le croisement n'a rien donne.")
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        int yield = config.gmoYield() + (level >= config.gmoBonusLevel() ? 1 : 0);
        ItemStack seeds = new ItemStack(DoHyAgri.seeds(target.key()), yield);
        player.getInventory().add(seeds);
        if (!seeds.isEmpty()) {
            player.drop(seeds, false);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS,
                0.7f, 1.4f);
        player.sendSystemMessage(Component.literal("Machine a OGM : " + yield + " graines de "
                + target.label().toLowerCase() + " !").withStyle(ChatFormatting.GREEN));
        JobApi.grant(player, Job.FARMER, config.gmoFarmerXp());
        return null;
    }

    @Override
    protected void back(ServerPlayer player) {
        OPEN.remove(player.getUUID());
        UiNet.close(player);
    }
}
