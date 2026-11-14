package fr.atraxterra.dohy.guide;

import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.guide.plan.Blueprint;
import fr.atraxterra.dohy.guide.plan.PlanData;
import fr.atraxterra.dohy.guide.plan.PlanItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La decouverte des fiches, et la barre d'action des plans tenus en main.
 *
 * <p>Toutes les cinq secondes, le sac et la dimension du joueur sont compares
 * aux declencheurs des fiches ; un menu du kit ouvert decouvre aussi les
 * siennes ({@link #viewed}, appele par {@code UiNet.show}). Une fiche
 * decouverte s'annonce dans le chat avec un lien pour la lire. Les fiches de
 * depart ne s'annoncent pas : l'accueil, a la premiere connexion, invite a
 * ouvrir l'encyclopedie.
 */
@EventBusSubscriber
public final class EncyclopediaEvents {

    private static volatile Map<Item, List<String>> byItem = Map.of();
    private static volatile Map<TagKey<Item>, List<String>> byTag = Map.of();
    private static volatile Map<String, List<String>> byDimension = Map.of();
    private static volatile Map<String, List<String>> byView = Map.of();
    private static volatile List<String> always = List.of();

    private EncyclopediaEvents() {
    }

    /** Refait les index des declencheurs, apres un chargement de l'encyclopedie. */
    static void reindex() {
        Map<Item, List<String>> items = new HashMap<>();
        Map<TagKey<Item>, List<String>> tags = new HashMap<>();
        Map<String, List<String>> dimensions = new HashMap<>();
        Map<String, List<String>> views = new HashMap<>();
        List<String> start = new ArrayList<>();
        for (Encyclopedia.Entry entry : Encyclopedia.entries()) {
            Encyclopedia.Discover discover = entry.discover();
            if (discover.always()) {
                start.add(entry.id());
            }
            for (String id : discover.items()) {
                if (id.startsWith("#")) {
                    Identifier key = Identifier.tryParse(id.substring(1));
                    if (key != null) {
                        tags.computeIfAbsent(TagKey.create(Registries.ITEM, key), k -> new ArrayList<>())
                                .add(entry.id());
                    }
                } else {
                    Identifier key = Identifier.tryParse(id);
                    if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
                        items.computeIfAbsent(BuiltInRegistries.ITEM.getValue(key), k -> new ArrayList<>())
                                .add(entry.id());
                    }
                }
            }
            for (String dimension : discover.dimensions()) {
                dimensions.computeIfAbsent(dimension, k -> new ArrayList<>()).add(entry.id());
            }
            for (String view : discover.views()) {
                views.computeIfAbsent(view, k -> new ArrayList<>()).add(entry.id());
            }
        }
        byItem = items;
        byTag = tags;
        byDimension = dimensions;
        byView = views;
        always = start;
    }

    public static EncyclopediaData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.ENCYCLOPEDIA.get());
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (player.tickCount % 20 == 7) {
            planStatus(player);
        }
        if (player.tickCount % 100 == 40) {
            scan(player);
        }
    }

    /** Le sac et la dimension : les fiches qu'ils decouvrent. */
    private static void scan(ServerPlayer player) {
        if (Encyclopedia.entries().isEmpty()) {
            return;
        }
        EncyclopediaData data = data(player);
        boolean first = !data.welcomed();
        for (String id : always) {
            data.discover(id);
        }
        List<String> found = new ArrayList<>();
        for (String id : byDimension.getOrDefault(player.level().dimension().identifier().toString(), List.of())) {
            if (data.discover(id)) {
                found.add(id);
            }
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            for (String id : byItem.getOrDefault(stack.getItem(), List.of())) {
                if (data.discover(id)) {
                    found.add(id);
                }
            }
            if (!byTag.isEmpty()) {
                for (Map.Entry<TagKey<Item>, List<String>> tag : byTag.entrySet()) {
                    if (stack.is(tag.getKey())) {
                        for (String id : tag.getValue()) {
                            if (data.discover(id)) {
                                found.add(id);
                            }
                        }
                    }
                }
            }
        }
        if (first) {
            data.welcome();
            player.sendSystemMessage(Component.literal("Bienvenue sur DoHy ! ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Tout le mod est explique dans l'encyclopedie : touche J, ou ")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(link("[Ouvrir]", "/encyclopedie")));
            return;
        }
        announce(player, found);
    }

    /** Un menu du kit ouvert : les fiches qu'il decouvre. */
    public static void viewed(ServerPlayer player, Identifier view) {
        List<String> ids = byView.get(view.toString());
        if (ids == null) {
            return;
        }
        EncyclopediaData data = data(player);
        List<String> found = new ArrayList<>();
        for (String id : ids) {
            if (data.discover(id)) {
                found.add(id);
            }
        }
        announce(player, found);
    }

    private static void announce(ServerPlayer player, List<String> found) {
        if (found.isEmpty()) {
            return;
        }
        MutableComponent line = Component.literal("[Encyclopedie] ").withStyle(ChatFormatting.AQUA);
        if (found.size() == 1) {
            Encyclopedia.Entry entry = Encyclopedia.entry(found.get(0));
            line.append(Component.literal("Nouvelle fiche : " + (entry == null ? found.get(0) : entry.title()) + " ")
                    .withStyle(ChatFormatting.WHITE)).append(link("[Lire]", "/encyclopedie " + found.get(0)));
        } else {
            List<String> titles = new ArrayList<>();
            for (String id : found) {
                Encyclopedia.Entry entry = Encyclopedia.entry(id);
                titles.add(entry == null ? id : entry.title());
            }
            line.append(Component.literal(found.size() + " nouvelles fiches : " + String.join(", ", titles) + " ")
                    .withStyle(ChatFormatting.WHITE)).append(link("[Ouvrir]", "/encyclopedie"));
        }
        player.sendSystemMessage(line);
    }

    static MutableComponent link(String label, String command) {
        return Component.literal(label).withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }

    // -- Les plans tenus en main ------------------------------------------------------------

    /** Chaque seconde, ou en est le plan tenu : blocs en place, blocs a retirer. */
    private static void planStatus(ServerPlayer player) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof PlanItem)) {
            stack = player.getItemInHand(InteractionHand.OFF_HAND);
            if (!(stack.getItem() instanceof PlanItem)) {
                return;
            }
        }
        PlanData data = PlanItem.data(stack);
        Blueprint blueprint = data.resolve();
        Blueprint.Variant variant = blueprint.variant(data.variant());
        PlanData.Anchor anchor = data.anchor().orElse(null);
        if (anchor == null) {
            player.sendSystemMessage(Component.literal(blueprint.name() + " (" + variant.label()
                    + ") · clic droit sur un bloc pour poser le plan").withStyle(ChatFormatting.AQUA), true);
            return;
        }
        if (!anchor.dimension().equals(player.level().dimension().identifier().toString())
                || !player.level().isLoaded(anchor.pos())) {
            player.sendSystemMessage(Component.literal(blueprint.name() + " · plan pose ailleurs, en "
                    + anchor.pos().toShortString()).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int done = 0;
        int wrong = 0;
        int partial = 0;
        int total = 0;
        for (Blueprint.Cell cell : variant.cells()) {
            if (!cell.expect().required()) {
                continue;
            }
            total++;
            BlockPos pos = Blueprint.place(anchor.pos(), anchor.quarter(), cell.x(), cell.y(), cell.z());
            switch (cell.expect().status(player.level(), pos)) {
                case CORRECT -> done++;
                case WRONG -> wrong++;
                case PARTIAL -> partial++;
                default -> {
                }
            }
        }
        MutableComponent line = Component.literal(blueprint.name() + " · " + done + " / " + total + " blocs")
                .withStyle(done == total ? ChatFormatting.GREEN : ChatFormatting.AQUA);
        if (done == total) {
            line.append(Component.literal(" · structure complete !").withStyle(ChatFormatting.GREEN));
        }
        if (wrong > 0) {
            line.append(Component.literal(" · " + wrong + " a retirer").withStyle(ChatFormatting.RED));
        }
        if (partial > 0) {
            line.append(Component.literal(" · " + partial + " a finir").withStyle(ChatFormatting.YELLOW));
        }
        player.sendSystemMessage(line, true);
    }
}
