package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.collection.CollectionsMenu;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * {@code /collections} — l'avancement des collections.
 *
 * <p>Sans argument : le detail de l'item en main s'il y en a un, sinon les
 * collections les plus avancees. Les collections sont invisibles autrement, et
 * c'est pourtant elles qui debloquent la vente — il faut pouvoir savoir
 * pourquoi un item refuse de se vendre.
 */
@EventBusSubscriber
public final class CollectionsCommand {

    private static final int LIST_SIZE = 12;

    private CollectionsCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("collections")
                // Sans argument : l'interface. Le detail de l'item en main
                // passe sous « held », et reste le chemin le plus court quand
                // on a justement l'item en main.
                .executes(ctx -> open(ctx.getSource()))
                .then(Commands.literal("held")
                        .executes(ctx -> show(ctx.getSource())))
                .then(Commands.literal("all")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("prestige")
                        .executes(ctx -> prestige(ctx.getSource())));
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) ->
                        new CollectionsMenu(containerId, inventory, player),
                Component.literal("Collections")));
        return 1;
    }

    private static int show(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();

        if (held.isEmpty()) {
            return list(source);
        }

        Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        if (entry == null) {
            source.sendSuccess(() -> held.getHoverName().copy()
                    .append(Component.literal(" n'a pas de collection.")
                            .withStyle(ChatFormatting.GRAY)), false);
            return 0;
        }

        detail(source, player, id, entry, held.getHoverName());
        return 1;
    }

    /**
     * Passe un prestige sur l'item en main.
     *
     * <p>Volontairement sur l'item tenu et non par argument : le geste est
     * irreversible, et tenir l'item est une confirmation implicite plus claire
     * qu'un identifiant tape au clavier.
     */
    private static int prestige(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Tiens l'item dont tu veux passer le prestige."));
            return 0;
        }

        Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        if (entry == null) {
            source.sendFailure(held.getHoverName().copy()
                    .append(Component.literal(" n'a pas de collection.")));
            return 0;
        }

        String refusal = CollectionApi.prestige(player, id, entry);
        if (refusal != null) {
            source.sendFailure(Component.literal(refusal));
            return 0;
        }
        return 1;
    }

    private static int list(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Map<Identifier, Long> all = CollectionApi.data(player).snapshot();

        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                            "Aucune collection commencee. Mine, tue, peche — ca viendra vite.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        List<Map.Entry<Identifier, Long>> top = all.entrySet().stream()
                .sorted(Map.Entry.<Identifier, Long>comparingByValue().reversed())
                .limit(LIST_SIZE)
                .toList();

        source.sendSuccess(() -> Component.literal(all.size() + " collection(s) en cours")
                .withStyle(ChatFormatting.GOLD), false);

        for (Map.Entry<Identifier, Long> line : top) {
            Identifier id = line.getKey();
            PriceEntry entry = Prices.book().entry(id).orElse(null);
            if (entry == null) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            Component name = item == null
                    ? Component.literal(id.getPath())
                    : new ItemStack(item).getHoverName();
            long next = CollectionApi.nextThreshold(player, id, entry);
            int tier = CollectionApi.tierIndex(player, id, entry);

            source.sendSuccess(() -> Component.literal("  ")
                    .append(name.copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" " + MoneyFormat.exact(line.getValue())
                                    + (next > 0 ? " / " + MoneyFormat.exact(next) : " · complete")
                                    + "  [palier " + (tier + 1) + "]")
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        return top.size();
    }

    private static void detail(CommandSourceStack source, ServerPlayer player,
                               Identifier id, PriceEntry entry, Component name) {
        long collected = CollectionApi.countOf(player, id);
        long next = CollectionApi.nextThreshold(player, id, entry);
        int tier = CollectionApi.tierIndex(player, id, entry);
        double bonus = CollectionApi.floorBonus(player, id, entry);

        source.sendSuccess(() -> name.copy().withStyle(ChatFormatting.WHITE)
                .append(Component.literal("  [" + entry.tier() + "]")
                        .withStyle(ChatFormatting.DARK_GRAY)), false);
        source.sendSuccess(() -> Component.literal("  collecte : ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(collected))
                        .withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Component.literal("  palier : " + (tier + 1))
                .withStyle(ChatFormatting.GRAY), false);

        if (next > 0) {
            long missing = next - collected;
            source.sendSuccess(() -> Component.literal("  prochain palier : "
                            + MoneyFormat.exact(next) + " (encore " + MoneyFormat.exact(missing) + ")")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("  collection complete")
                    .withStyle(ChatFormatting.AQUA), false);
        }

        if (bonus > 0) {
            source.sendSuccess(() -> Component.literal("  prix plancher +"
                            + Math.round(bonus * 100) + " %")
                    .withStyle(ChatFormatting.GREEN), false);
        }

        int prestigeLevel = CollectionApi.prestigeLevel(player, id);
        if (prestigeLevel > 0) {
            source.sendSuccess(() -> Component.literal("  prestige " + prestigeLevel
                            + " — quantite ecoulable x"
                            + String.format("%.1f", CollectionApi.s0Multiplier(player, id)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        if (CollectionApi.canPrestige(player, id, entry)) {
            source.sendSuccess(() -> Component.literal(
                            "  prestige disponible — /collections prestige (remet la collection a zero)")
                    .withStyle(ChatFormatting.GOLD), false);
        }

        if (!CollectionApi.isUnlocked(player, id, entry)) {
            source.sendSuccess(() -> Component.literal("  vente encore verrouillee")
                    .withStyle(ChatFormatting.RED), false);
        }
    }
}
