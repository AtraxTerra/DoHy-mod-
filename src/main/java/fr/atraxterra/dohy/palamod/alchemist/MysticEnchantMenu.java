package fr.atraxterra.dohy.palamod.alchemist;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.defense.DefenseGate;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.MenuView;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;

/** La table d'enchantement mystique : l'objet tenu, et les deux enchantements de l'alchimiste. */
public final class MysticEnchantMenu extends MenuController {

    public static final MysticEnchantMenu INSTANCE = new MysticEnchantMenu();

    private MysticEnchantMenu() {
        super("mystic_enchant");
    }

    @Override
    public MenuView view(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        List<Card> item = List.of(held.isEmpty()
                ? Card.of(new ItemStack(Items.BARRIER), MenuView.text(Component.translatable(
                        "message.palamod.mystic_enchant.no_item")), List.of("Prends un objet en main", "puis rouvre la table")).state(MenuView.DISABLED)
                : Card.of(held.copyWithCount(1), MenuView.text(held.getHoverName()), List.of("L'objet tenu en main",
                        "Niveaux d'XP : " + player.experienceLevel)).accent(0xFFEC4FB5));
        List<Card> enchants = new ArrayList<>();
        for (MysticEnchant enchant : MysticEnchant.values()) {
            Holder<Enchantment> holder = enchant.holder(player).orElse(null);
            if (holder == null) {
                continue;
            }
            int level = enchant.levelOn(player, held);
            List<String> tooltip = new ArrayList<>();
            tooltip.add(MenuView.text(Component.translatable("enchantment.palamod." + enchant.id() + ".desc")));
            if (enchant.job() != null) {
                tooltip.add(MenuView.text(Component.translatable("message.palamod.mystic_enchant.requires",
                        enchant.job().display(), enchant.jobLevel())));
            }
            Component refusal = refusal(player, enchant);
            if (refusal != null) {
                tooltip.add(MenuView.text(refusal));
            }
            Card card = Card.of(new ItemStack(Items.ENCHANTED_BOOK), MenuView.text(holder.value().description()),
                            List.of(MenuView.text(Component.translatable("message.palamod.mystic_enchant.level", level,
                                            holder.value().getMaxLevel())),
                                    MenuView.text(Component.translatable("message.palamod.mystic_enchant.cost",
                                            MysticEnchant.COST))))
                    .accent(0xFFEC4FB5).progress(level, holder.value().getMaxLevel()).tooltip(tooltip);
            enchants.add(refusal == null ? card.action("apply:" + enchant.name() + "|enchanter")
                    : card.state(MenuView.DISABLED));
        }
        return new MenuView(id(), "Enchantement mystique", "Les enchantements de l'alchimiste", List.of(
                new MenuView.Stat("NIVEAUX D'XP", String.valueOf(player.experienceLevel), 0xFF22E558)), List.of(
                new MenuView.Section("OBJET", "resources", MenuView.GRID, item),
                new MenuView.Section("ENCHANTEMENTS", "upgrade", MenuView.GRID, enchants)), false);
    }

    /** Pourquoi l'enchantement ne peut pas se faire, ou {@code null}. */
    private static Component refusal(ServerPlayer player, MysticEnchant enchant) {
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
        if (enchant.unlock() != null && !DefenseGate.allows(player, enchant.unlock())) {
            return Component.translatable("message.palamod.mystic_enchant.locked");
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
    protected void card(ServerPlayer player, String key, int button) {
        if (!key.startsWith("apply:")) {
            return;
        }
        MysticEnchant enchant;
        try {
            enchant = MysticEnchant.valueOf(key.substring(6));
        } catch (IllegalArgumentException unknown) {
            return;
        }
        Component refusal = refusal(player, enchant);
        if (refusal != null) {
            failure(player);
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
        if (enchant.counter() != null) {
            FactionApi.stat(player, enchant.counter(), 1);
        }
        success(player);
        player.sendSystemMessage(Component.translatable("message.palamod.mystic_enchant.done",
                Enchantment.getFullname(holder, next)).withStyle(ChatFormatting.GREEN));
    }
}
