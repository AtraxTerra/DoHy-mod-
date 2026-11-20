package fr.atraxterra.dohy.farmbot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** La liste des bots poses : l'inscrire, la mettre a jour, la lire pour un joueur. */
public final class FarmBots {

    private FarmBots() {
    }

    static BotRegistry.Place place(ServerLevel level, FarmBot bot) {
        return new BotRegistry.Place(bot.owner(), level.dimension().identifier().toString(), bot.blockPosition());
    }

    /** Inscrit ou met a jour un bot. */
    static void update(ServerLevel level, FarmBot bot) {
        if (bot.owner() == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        BotRegistry registry = BotRegistry.get(server);
        BotRegistry.Place place = place(level, bot);
        if (!place.equals(registry.bots().get(bot.getUUID()))) {
            BotRegistry.set(server, registry.with(bot.getUUID(), place));
        }
    }

    static void unregister(MinecraftServer server, UUID bot) {
        BotRegistry registry = BotRegistry.get(server);
        if (registry.bots().containsKey(bot)) {
            BotRegistry.set(server, registry.with(bot, null));
        }
    }

    /**
     * Les bots d'un joueur, apres avoir oublie ceux qui ne sont plus la ou la liste les place
     * alors que leur endroit est charge.
     */
    public static List<Map.Entry<UUID, BotRegistry.Place>> of(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        forgetMissing(server, player.getUUID());
        return BotRegistry.get(server).of(player.getUUID());
    }

    static void forgetMissing(MinecraftServer server, UUID owner) {
        BotRegistry registry = BotRegistry.get(server);
        BotRegistry cleaned = registry;
        for (Map.Entry<UUID, BotRegistry.Place> entry : registry.of(owner)) {
            ServerLevel level = level(server, entry.getValue().dimension());
            BlockPos pos = entry.getValue().pos();
            if (level != null && level.isLoaded(pos) && !(level.getEntity(entry.getKey()) instanceof FarmBot)) {
                cleaned = cleaned.with(entry.getKey(), null);
            }
        }
        if (cleaned != registry) {
            BotRegistry.set(server, cleaned);
        }
    }

    static ServerLevel level(MinecraftServer server, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** « Overworld », « Nether », « End », sinon l'identifiant. */
    static String dimensionName(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> dimension;
        };
    }

    static boolean sameDimension(Level level, String dimension) {
        return level.dimension().identifier().toString().equals(dimension);
    }

    /** Les bots que le joueur a sur lui, en objets. */
    public static int carried(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).getItem() instanceof FarmBotItem) {
                count += player.getInventory().getItem(slot).getCount();
            }
        }
        return count;
    }

    static boolean hasRemote(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).getItem() instanceof BotRemoteItem) {
                return true;
            }
        }
        return false;
    }

    /** Achete un bot a la Boutique, avec une telecommande s'il n'en a pas ; la raison du refus, ou {@code null}. */
    public static String buyBot(ServerPlayer player) {
        int owned = of(player).size() + carried(player);
        if (owned >= BotRegistry.MAX_PER_PLAYER) {
            return "Tu as deja " + owned + " bots (poses ou sur toi) : " + BotRegistry.MAX_PER_PLAYER + " au plus.";
        }
        if (!fr.atraxterra.dohy.economy.Economy.tryDebit(player, DoHyFarmBots.BOT_PRICE)) {
            return "Il te faut " + fr.atraxterra.dohy.util.MoneyFormat.exact(DoHyFarmBots.BOT_PRICE) + " $ en liquide.";
        }
        boolean remote = !hasRemote(player);
        give(player, new net.minecraft.world.item.ItemStack(DoHyFarmBots.FARM_BOT_ITEM.get()));
        if (remote) {
            give(player, new net.minecraft.world.item.ItemStack(DoHyFarmBots.BOT_REMOTE.get()));
        }
        player.sendSystemMessage(Component.literal("Bot de farming achete" + (remote ? ", avec sa telecommande" : "")
                + " : pose-le pres de ton champ (clic droit sur le sol).").withStyle(ChatFormatting.GREEN));
        return null;
    }

    /** Achete une telecommande ; la raison du refus, ou {@code null}. */
    public static String buyRemote(ServerPlayer player) {
        if (!fr.atraxterra.dohy.economy.Economy.tryDebit(player, DoHyFarmBots.REMOTE_PRICE)) {
            return "Il te faut " + fr.atraxterra.dohy.util.MoneyFormat.exact(DoHyFarmBots.REMOTE_PRICE)
                    + " $ en liquide.";
        }
        give(player, new net.minecraft.world.item.ItemStack(DoHyFarmBots.BOT_REMOTE.get()));
        player.sendSystemMessage(Component.literal("Telecommande achetee : clic droit sur ton bot pour la relier a"
                + " lui.").withStyle(ChatFormatting.GREEN));
        return null;
    }

    private static void give(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** {@code /bots} : ou sont les bots du joueur. */
    public static void list(ServerPlayer player) {
        List<Map.Entry<UUID, BotRegistry.Place>> bots = of(player);
        if (bots.isEmpty()) {
            player.sendSystemMessage(Component.literal("Tu n'as aucun bot de farming pose. Ils s'achetent a la"
                    + " Boutique.").withStyle(ChatFormatting.GRAY));
            return;
        }
        MutableComponent out = Component.literal("Tes bots de farming (" + bots.size() + " / "
                + BotRegistry.MAX_PER_PLAYER + ") :").withStyle(ChatFormatting.GREEN);
        int number = 1;
        for (Map.Entry<UUID, BotRegistry.Place> entry : bots) {
            BlockPos pos = entry.getValue().pos();
            out.append(Component.literal("\n  " + number++ + ". " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    + " (" + dimensionName(entry.getValue().dimension()) + ")").withStyle(ChatFormatting.WHITE));
        }
        player.sendSystemMessage(out);
    }
}
