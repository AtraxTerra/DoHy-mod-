package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.palamod.pvp.PalamachineRecipes;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber
public final class RuneSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    private static void log(String line) {
        LOG.info("palamod-test: " + line);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("runetest").executes(context -> {
            ServerLevel level = context.getSource().getServer().getLevel(PalaDungeon.DIMENSION);
            try {
                run(level);
            } catch (Throwable t) {
                LOG.error("palamod-test: RUNE FAILED", t);
            }
            context.getSource().sendSuccess(() -> Component.literal("ok"), false);
            return 1;
        }));
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
    }

    private static void run(ServerLevel level) {
        RandomSource random = RandomSource.create(7);
        for (RuneRarity rarity : RuneRarity.values()) {
            RuneData data = RuneData.roll(rarity, random);
            StringBuilder line = new StringBuilder(rarity + " roll=" + data);
            RuneData current = data;
            while (current.tier() < RuneData.MAX_TIER) {
                RuneData other = new RuneData(rarity, current.tier(), current.lines());
                line.append(" | cost ").append(current.upgradeCost());
                current = current.upgradedWith(other);
                line.append(" -> T").append(current.tier()).append(' ').append(current.lines());
            }
            for (RpgStat stat : RpgStat.values()) {
                if (current.has(stat)) {
                    line.append(" | ").append(stat).append('=').append(RpgStat.percent(current.value(stat)));
                }
            }
            log(line.toString());
        }
        RuneData a = new RuneData(RuneRarity.RARE, 1, List.of(new RuneData.Line(RpgStat.ATTACK, 0),
                new RuneData.Line(RpgStat.CRIT, 0)));
        RuneData b = new RuneData(RuneRarity.RARE, 1, List.of(new RuneData.Line(RpgStat.ATTACK, 0),
                new RuneData.Line(RpgStat.DODGE, 0)));
        log("merge shared " + a.upgradedWith(b) + " canUpgrade(epic)="
                + a.canUpgradeWith(new RuneData(RuneRarity.EPIC, 1, a.lines()))
                + " canUpgrade(T2)=" + a.canUpgradeWith(new RuneData(RuneRarity.RARE, 2, a.lines())));

        log("levels: 0->" + Rpg.level(0) + " 70->" + Rpg.level(70) + " 104000->" + Rpg.level(104_000)
                + " 103999->" + Rpg.level(103_999) + " max->" + Rpg.level(10_000_000));

        ServerPlayer fake = FakePlayerFactory.getMinecraft(level);
        Map<RpgStat, Integer> nodes = new EnumMap<>(RpgStat.class);
        nodes.put(RpgStat.ATTACK, 8);
        nodes.put(RpgStat.DEFENSE, 3);
        Rpg.store(fake, Rpg.data(fake).withXp(20_000).withNodes(nodes));
        Map<String, Integer> progress = new HashMap<>();
        progress.put("talikus", 47);
        progress.put("nimbria", 100);
        fake.setData(PalaDungeon.PROGRESS.get(), progress);
        log("points=" + Rpg.points(fake) + " spent=" + Rpg.data(fake).spent() + " available=" + Rpg.available(fake)
                + " level=" + Rpg.level(20_000));
        ItemStack helmet = new ItemStack(PalaDungeonGear.TALIKUS_LEGENDARY_HELMET.get());
        helmet.set(PalaDungeon.RUNE.get(), new RuneData(RuneRarity.EPIC, 4, List.of(
                new RuneData.Line(RpgStat.ATTACK, 3), new RuneData.Line(RpgStat.DEFENSE, 3))));
        ItemStack chest = new ItemStack(PalaDungeonGear.TALIKUS_LEGENDARY_CHESTPLATE.get());
        chest.set(PalaDungeon.RUNE.get(), new RuneData(RuneRarity.EPIC, 4, List.of(
                new RuneData.Line(RpgStat.DEFENSE, 3), new RuneData.Line(RpgStat.DODGE, 3))));
        fake.setItemSlot(EquipmentSlot.HEAD, helmet);
        fake.setItemSlot(EquipmentSlot.CHEST, chest);
        StringBuilder stats = new StringBuilder("stats tree=" + Rpg.tree(Rpg.data(fake)) + " runes=" + Rpg.runes(fake));
        for (RpgStat stat : RpgStat.values()) {
            stats.append(" ").append(stat).append('=').append(RpgStat.percent(Rpg.stat(fake, stat)));
        }
        log(stats.toString());
        fake.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        fake.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        Rpg.store(fake, RpgData.EMPTY);
        fake.setData(PalaDungeon.PROGRESS.get(), new HashMap<>());

        for (List<String> inputs : List.of(
                List.of("palamod:rune_fragment_common", "palamod:rune_fragment_common", "palamod:rune_fragment_common",
                        "palamod:rune_fragment_common", "palamod:paladium_ingot"),
                List.of("palamod:rune_fragment_rare", "palamod:rune_fragment_rare", "palamod:rune_fragment_rare",
                        "palamod:rune_fragment_rare", "palamod:paladium_block"),
                List.of("palamod:rune_rare", "palamod:rune_rare", "palamod:endium_fragment", "palamod:rune_rare",
                        "palamod:endium_fragment"))) {
            PalamachineRecipes.Recipe recipe = PalamachineRecipes.find(inputs.stream().map(RuneSelfTest::stack).toList());
            log("palamachine " + inputs.get(0) + " -> " + (recipe == null ? "NONE" : recipe.result()));
        }
        int runes = 0;
        int rolled = 0;
        int fragments = 0;
        for (int i = 0; i < 400; i++) {
            for (ItemStack loot : DungeonRewards.chest(level, random, Dungeon.TALIKUS, 60, false)) {
                if (loot.getItem() instanceof RuneItem) {
                    runes++;
                    if (loot.has(PalaDungeon.RUNE.get())) {
                        rolled++;
                    }
                } else if (loot.is(PalaDungeon.RUNE_FRAGMENT_COMMON.get()) || loot.is(PalaDungeon.RUNE_FRAGMENT_RARE.get())) {
                    fragments++;
                }
            }
        }
        log("400 chests p60: runes=" + runes + " rolled=" + rolled + " fragments=" + fragments);
        ItemStack fresh = new ItemStack(PalaDungeon.RUNE_COMMON.get());
        log("ensure fresh=" + RuneItem.ensure(fresh, random) + " again=" + RuneItem.ensure(fresh, random));
    }
}
