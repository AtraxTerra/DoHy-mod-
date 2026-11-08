package fr.atraxterra.dohy.palamod.alchemist;

import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * La table de l'Alchimiste ({@code ContainerEnchantment}, {@code CSEnchantItem}).
 *
 * <p>Elle s'ouvre a la place de la table vanilla quand la puissance des
 * bibliotheques autour depasse 60. Elle enchante <b>l'objet tenu en main</b> —
 * l'original le posait dans une case unique, c'est le meme geste — d'un niveau
 * par clic, 30 niveaux d'experience chaque fois, jusqu'au maximum.
 */
public final class MysticEnchantMenu extends DisplayMenu {

    private static final int SLOT_ITEM = 4;
    private static final int[] SLOTS = {12, 14};
    private static final int SLOT_CLOSE = 22;

    public MysticEnchantMenu(int containerId, Inventory inventory, ServerPlayer player) {
        super(containerId, inventory, player, 3);
        refresh();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new MysticEnchantMenu(containerId, inventory, player),
                Component.translatable("container.palamod.mystic_enchant")));
    }

    @Override
    protected void onClick(int slot, int button, boolean shift) {
        if (slot == SLOT_CLOSE) {
            click(0.6f);
            player.closeContainer();
            return;
        }
        MysticEnchant[] enchants = MysticEnchant.values();
        for (int index = 0; index < SLOTS.length && index < enchants.length; index++) {
            if (SLOTS[index] == slot) {
                apply(enchants[index]);
                return;
            }
        }
    }

    private void apply(MysticEnchant enchant) {
        Component refusal = refusal(enchant);
        if (refusal != null) {
            failure();
            player.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.RED));
            return;
        }
        ItemStack held = player.getMainHandItem();
        Holder<Enchantment> holder = enchant.holder(player).orElseThrow();
        int next = enchant.levelOn(player, held) + 1;
        EnchantmentHelper.updateEnchantments(held, enchantments -> enchantments.set(holder, next));
        if (!player.getAbilities().instabuild) {
            player.giveExperienceLevels(-MysticEnchant.COST);
        }
        success();
        player.sendSystemMessage(Component.translatable("message.palamod.mystic_enchant.done",
                Enchantment.getFullname(holder, next)).withStyle(ChatFormatting.GREEN));
    }

    /** Pourquoi l'enchantement ne peut pas se faire, ou {@code null}. */
    private Component refusal(MysticEnchant enchant) {
        ItemStack held = player.getMainHandItem();
        Holder<Enchantment> holder = enchant.holder(player).orElse(null);
        if (holder == null) {
            return Component.translatable("message.palamod.mystic_enchant.missing");
        }
        if (held.isEmpty()) {
            return Component.translatable("message.palamod.mystic_enchant.no_item");
        }
        if (!holder.value().isSupportedItem(held)) {
            return Component.translatable("message.palamod.mystic_enchant.unsupported");
        }
        if (enchant.levelOn(player, held) >= holder.value().getMaxLevel()) {
            return Component.translatable("message.palamod.mystic_enchant.max");
        }
        if (enchant.job() != null && !JobApi.meets(player, enchant.job(), enchant.jobLevel())) {
            return Component.translatable("message.palamod.mystic_enchant.job",
                    enchant.job().display(), enchant.jobLevel());
        }
        if (player.experienceLevel < MysticEnchant.COST && !player.getAbilities().instabuild) {
            return Component.translatable("message.palamod.mystic_enchant.xp", MysticEnchant.COST);
        }
        return null;
    }

    @Override
    protected void build() {
        frame();
        ItemStack held = player.getMainHandItem();
        set(SLOT_ITEM, held.isEmpty()
                ? Gui.button(Items.BARRIER, Component.translatable("message.palamod.mystic_enchant.no_item")
                        .withStyle(ChatFormatting.RED), List.of())
                : held.copyWithCount(1));
        MysticEnchant[] enchants = MysticEnchant.values();
        for (int index = 0; index < SLOTS.length && index < enchants.length; index++) {
            set(SLOTS[index], tile(enchants[index]));
        }
        set(SLOT_CLOSE, Gui.close());
    }

    private ItemStack tile(MysticEnchant enchant) {
        Holder<Enchantment> holder = enchant.holder(player).orElse(null);
        if (holder == null) {
            return Gui.filler();
        }
        ItemStack held = player.getMainHandItem();
        int level = enchant.levelOn(player, held);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("enchantment.palamod." + enchant.id() + ".desc")
                .withStyle(ChatFormatting.GOLD));
        lore.add(Component.translatable("message.palamod.mystic_enchant.level", level, holder.value().getMaxLevel())
                .withStyle(ChatFormatting.GRAY));
        lore.add(Component.translatable("message.palamod.mystic_enchant.cost", MysticEnchant.COST)
                .withStyle(ChatFormatting.GRAY));
        if (enchant.job() != null) {
            boolean meets = JobApi.meets(player, enchant.job(), enchant.jobLevel());
            lore.add(Component.translatable("message.palamod.mystic_enchant.requires",
                    enchant.job().display(), enchant.jobLevel())
                    .withStyle(meets ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        Component refusal = refusal(enchant);
        lore.add(refusal == null
                ? Component.translatable("message.palamod.mystic_enchant.click").withStyle(ChatFormatting.GREEN)
                : refusal.copy().withStyle(ChatFormatting.RED));
        return Gui.button(Items.ENCHANTED_BOOK, holder.value().description().copy()
                .withStyle(ChatFormatting.LIGHT_PURPLE), lore);
    }
}
