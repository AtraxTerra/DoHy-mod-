package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le profil RPG ({@link RpgView}) : un clic choisit un noeud dont le parent est pris ou choisi,
 * un second clic le rend (avec ce qui en descend) ; VALIDER achete les choix, RESET les rend, ou
 * remet l'arbre a zero contre {@value #RESPEC_PRICE} $.
 */
public final class RpgMenu {

    public static final long RESPEC_PRICE = 25_000L;
    public static final RpgMenu INSTANCE = new RpgMenu();

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF8B8E95;

    /** Les noeuds choisis, pas encore valides, par joueur. */
    private final Map<UUID, int[]> pending = new ConcurrentHashMap<>();

    private RpgMenu() {
    }

    public void open(ServerPlayer player) {
        Rpg.migrate(player);
        pending.put(player.getUUID(), new int[RpgStat.values().length]);
        UiNet.show(player, view(player));
    }

    private int[] pending(ServerPlayer player) {
        return pending.computeIfAbsent(player.getUUID(), ignored -> new int[RpgStat.values().length]);
    }

    private static int price(int[] masks) {
        int total = 0;
        for (int mask : masks) {
            total += RpgTree.price(mask);
        }
        return total;
    }

    private static RpgView.Row row(String icon, String label, float value) {
        return new RpgView.Row("stats/icon/" + icon, label, "+" + RpgStat.percent(value), value > 0.0f ? WHITE : GRAY);
    }

    public RpgView view(ServerPlayer player) {
        RpgData data = Rpg.data(player);
        Rpg.Points points = Rpg.points(player);
        int[] chosen = pending(player);
        int[] owned = new int[RpgStat.values().length];
        for (RpgStat stat : RpgStat.values()) {
            owned[stat.ordinal()] = data.mask(stat);
        }
        int free = points.total() - data.spent() - price(chosen);
        int level = Rpg.level(data.xp());

        Map<RpgStat, Float> tree = Rpg.tree(data);
        List<RpgView.Row> perso = new ArrayList<>();
        perso.add(row("damage", "DEGATS", tree.get(RpgStat.ATTACK)));
        perso.add(row("critical_chance", "CHANCE CRITIQUE", tree.get(RpgStat.CRIT)));
        perso.add(row("dodge", "ESQUIVE", tree.get(RpgStat.DODGE)));
        perso.add(row("resistance", "RESISTANCE", tree.get(RpgStat.DEFENSE)));
        perso.add(new RpgView.Row("profile/skilltree/cost", "POINTS DEPENSES", String.valueOf(data.spent()),
                data.spent() > 0 ? WHITE : GRAY));

        List<RpgView.Row> global = new ArrayList<>();
        global.add(new RpgView.Row("stats/icon/max_health", "VIE MAX", "+" + Math.round(player.getMaxHealth()),
                WHITE));
        global.add(row("damage", "DEGATS", Rpg.stat(player, RpgStat.ATTACK)));
        global.add(row("critical_chance", "CHANCE CRITIQUE", Rpg.stat(player, RpgStat.CRIT)));
        global.add(row("critical_damage", "DEGATS CRITIQUES", 0.5f));
        global.add(row("dodge", "ESQUIVE", Rpg.stat(player, RpgStat.DODGE)));
        global.add(row("resistance", "RESISTANCE", Rpg.stat(player, RpgStat.DEFENSE)));
        for (Dungeon dungeon : Dungeon.values()) {
            global.add(row(dungeon.key() + "_resistance", "RESISTANCE " + dungeon.display().toUpperCase(Locale.ROOT),
                    DungeonGearItem.reduction(player, dungeon)));
        }
        for (Dungeon dungeon : Dungeon.values()) {
            global.add(row(dungeon.key() + "_damage", "DEGATS " + dungeon.display().toUpperCase(Locale.ROOT),
                    DungeonGearItem.damageBonus(player, dungeon)));
        }

        List<String> sources = List.of("Niveau RPG : " + points.level(), "Paliers : " + points.paliers(),
                "Metiers : " + points.jobs());
        return new RpgView(player.getName().getString(), level, Rpg.xpInLevel(data.xp()),
                level >= Rpg.MAX_LEVEL ? 0L : Rpg.xpToNext(level), free, owned, chosen.clone(), perso, global, sources,
                RESPEC_PRICE);
    }

    private static void play(ServerPlayer player, SoundEvent sound, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.MASTER, 0.4f, pitch);
    }

    private static void refuse(ServerPlayer player) {
        play(player, SoundEvents.VILLAGER_NO, 1.0f);
    }

    /** Les clics de l'ecran. */
    public static void handle(ServerPlayer player, UiNet.Action action) {
        INSTANCE.act(player, action.verb(), action.argument());
    }

    private void act(ServerPlayer player, String verb, String argument) {
        RpgData data = Rpg.data(player);
        int[] chosen = pending(player);
        switch (verb) {
            case "node" -> {
                int colon = argument.indexOf(':');
                if (colon < 0) {
                    return;
                }
                RpgStat stat;
                int node;
                try {
                    stat = RpgStat.valueOf(argument.substring(0, colon));
                    node = Integer.parseInt(argument.substring(colon + 1));
                } catch (IllegalArgumentException wrong) {
                    return;
                }
                if (node < 0 || node >= RpgTree.NODES) {
                    return;
                }
                int index = stat.ordinal();
                if (RpgTree.has(chosen[index], node)) {
                    // rendu, avec ce qui en descend
                    chosen[index] &= ~RpgTree.subtree(chosen[index], node);
                } else {
                    int free = Rpg.points(player).total() - data.spent() - price(chosen);
                    if (!RpgTree.open(data.mask(stat) | chosen[index], node) || RpgTree.cost(node) > free) {
                        refuse(player);
                        return;
                    }
                    chosen[index] |= 1 << node;
                }
            }
            case "validate" -> {
                int cost = price(chosen);
                if (cost <= 0 || cost > Rpg.points(player).total() - data.spent()) {
                    Arrays.fill(chosen, 0);
                    refuse(player);
                    break;
                }
                Map<RpgStat, Integer> next = new EnumMap<>(RpgStat.class);
                for (RpgStat stat : RpgStat.values()) {
                    next.put(stat, data.mask(stat) | chosen[stat.ordinal()]);
                }
                Rpg.store(player, data.withTree(next));
                Arrays.fill(chosen, 0);
                play(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f);
                player.sendSystemMessage(Component.translatable("message.palamod.rpg.validated", cost)
                        .withStyle(ChatFormatting.GREEN));
            }
            case "cancel" -> Arrays.fill(chosen, 0);
            case "respec" -> {
                if (data.spent() == 0) {
                    refuse(player);
                    return;
                }
                if (!Economy.tryDebit(player, RESPEC_PRICE)) {
                    refuse(player);
                    player.sendSystemMessage(Component.translatable("message.palamod.rpg.respec_poor",
                            MoneyFormat.exact(RESPEC_PRICE)).withStyle(ChatFormatting.RED));
                    return;
                }
                Rpg.store(player, data.withTree(new EnumMap<>(RpgStat.class)));
                Arrays.fill(chosen, 0);
                play(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f);
                player.sendSystemMessage(Component.translatable("message.palamod.rpg.respec")
                        .withStyle(ChatFormatting.GOLD));
            }
            default -> {
                return;
            }
        }
        if (UiNet.isOpen(player, RpgView.ID)) {
            UiNet.show(player, view(player));
        }
    }
}
