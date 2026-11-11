package fr.atraxterra.dohy.palamod.box;

import fr.atraxterra.dohy.boost.BoostData;
import fr.atraxterra.dohy.boost.Boosters;
import fr.atraxterra.dohy.boost.Crates;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;

/**
 * Les cles des boxes du joueur : des objets dans son inventaire
 * ({@link CrateKeyItem}), une sorte par box.
 *
 * <p>Avant, c'etait un compteur ({@link BoostData#keys}). Il reste pour deux
 * choses : les cles d'un monde d'avant, converties en objets a la connexion
 * (celles des caisses commune, rare et legendaire deviennent des cles
 * d'amethyste, de paladium et d'endium), et une table ajoutee par un pack de
 * donnees, qui n'a pas d'objet — ses cles restent comptees.
 */
@EventBusSubscriber
public final class CrateKeys {

    private CrateKeys() {
    }

    /** L'objet cle d'une box, ou {@code null} si cette table n'en a pas (pack de donnees). */
    public static Item item(String crate) {
        BoxKind kind = BoxKind.byId(Crates.canonical(crate));
        return kind == null ? null : PalaBoxes.key(kind);
    }

    /** Les cles de cette box que le joueur a sur lui. */
    public static int count(ServerPlayer player, String tier) {
        int total = Boosters.data(player).keys(tier);
        Item item = item(tier);
        if (item != null) {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(item)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /** Prend une cle : celle de la main d'abord, puis la premiere de l'inventaire. */
    public static boolean take(ServerPlayer player, String tier) {
        Item item = item(tier);
        if (item != null) {
            ItemStack held = player.getMainHandItem();
            if (held.is(item)) {
                held.shrink(1);
                return true;
            }
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(item)) {
                    stack.shrink(1);
                    return true;
                }
            }
        }
        return Boosters.data(player).consumeKey(tier);
    }

    /** Donne des cles ; ce qui ne tient pas dans l'inventaire tombe aux pieds du joueur. */
    public static void give(ServerPlayer player, String tier, int amount) {
        Item item = item(tier);
        if (item == null) {
            Boosters.data(player).addKeys(tier, amount);
            return;
        }
        int left = amount;
        while (left > 0) {
            ItemStack stack = new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize()));
            left -= stack.getCount();
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    /** Les cles comptees d'un monde d'avant deviennent des objets. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            convert(player);
        }
    }

    static void convert(ServerPlayer player) {
        BoostData data = Boosters.data(player);
        int converted = 0;
        for (Map.Entry<String, Integer> entry : data.allKeys().entrySet()) {
            if (item(entry.getKey()) != null && entry.getValue() > 0) {
                data.addKeys(entry.getKey(), -entry.getValue());
                give(player, entry.getKey(), entry.getValue());
                converted += entry.getValue();
            }
        }
        if (converted > 0) {
            player.sendSystemMessage(Component.literal("Tes " + converted + " cle(s) de box sont maintenant "
                    + "des objets, dans ton inventaire.").withStyle(ChatFormatting.GOLD));
        }
    }
}
