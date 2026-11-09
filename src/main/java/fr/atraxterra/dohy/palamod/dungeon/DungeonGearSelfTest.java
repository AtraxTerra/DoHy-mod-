package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@EventBusSubscriber
public final class DungeonGearSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    private static void log(String line) {
        LOG.info("palamod-test: " + line);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("donjongear").executes(context -> {
            ServerLevel level = context.getSource().getServer().getLevel(PalaDungeon.DIMENSION);
            try {
                run(level);
            } catch (Throwable t) {
                LOG.error("palamod-test: GEAR FAILED", t);
            }
            context.getSource().sendSuccess(() -> Component.literal("ok"), false);
            return 1;
        }));
    }

    private static void wear(ServerPlayer player, Dungeon dungeon, DungeonGearTier tier) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            player.setItemSlot(slots[i], new ItemStack(PalaDungeonGear.armorPiece(dungeon, tier, i)));
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(PalaDungeonGear.sword(dungeon, tier)));
    }

    private static void run(ServerLevel level) throws Exception {
        ServerPlayer fake = FakePlayerFactory.getMinecraft(level);
        for (DungeonGearTier tier : List.of(DungeonGearTier.BASIC, DungeonGearTier.REINFORCED, DungeonGearTier.LEGENDARY)) {
            wear(fake, Dungeon.TALIKUS, tier);
            log(tier + " in TALIKUS red=" + DungeonGearItem.reduction(fake, Dungeon.TALIKUS)
                    + " dmg=" + DungeonGearItem.damageBonus(fake, Dungeon.TALIKUS)
                    + " | in NIMBRIA red=" + DungeonGearItem.reduction(fake, Dungeon.NIMBRIA)
                    + " dmg=" + DungeonGearItem.damageBonus(fake, Dungeon.NIMBRIA)
                    + " | armor=" + fake.getArmorValue()
                    + " sword=" + fake.getMainHandItem().get(DataComponents.ATTRIBUTE_MODIFIERS));
        }
        fake.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PalaDungeonGear.ANTIQUE_HELMET.get()));
        fake.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PalaDungeonGear.ANTIQUE_CHESTPLATE.get()));
        fake.setItemSlot(EquipmentSlot.LEGS, new ItemStack(PalaDungeonGear.ANTIQUE_LEGGINGS.get()));
        fake.setItemSlot(EquipmentSlot.FEET, new ItemStack(PalaDungeonGear.ANTIQUE_BOOTS.get()));
        fake.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        log("ANTIQUE set red=" + DungeonGearItem.reduction(fake, Dungeon.VITALYS)
                + " dmg=" + DungeonGearItem.damageBonus(fake, Dungeon.VITALYS) + " armor=" + fake.getArmorValue());
        for (int i = 0; i < 4; i++) {
            fake.setItemSlot(new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                    EquipmentSlot.FEET}[i], ItemStack.EMPTY);
        }

        ItemStack helmet = new ItemStack(PalaDungeonGear.TALIKUS_HELMET.get());
        ItemStack legendary = new ItemStack(PalaDungeonGear.TALIKUS_LEGENDARY_HELMET.get());
        ItemStack antique = new ItemStack(PalaDungeonGear.ANTIQUE_BOOTS.get());
        log("repair basic+fragment=" + helmet.isValidRepairItem(new ItemStack(PalaDungeonGear.FOREST_ESSENCE_FRAGMENT.get()))
                + " basic+essence=" + helmet.isValidRepairItem(new ItemStack(PalaDungeonGear.FOREST_ESSENCE.get()))
                + " basic+cavernFragment=" + helmet.isValidRepairItem(new ItemStack(PalaDungeonGear.CAVERN_ESSENCE_FRAGMENT.get()))
                + " legendary+concentrate=" + legendary.isValidRepairItem(new ItemStack(PalaDungeonGear.FOREST_ESSENCE_CONCENTRATE.get()))
                + " antique+aquaticConcentrate=" + antique.isValidRepairItem(new ItemStack(PalaDungeonGear.AQUATIC_ESSENCE_CONCENTRATE.get()))
                + " antique+fragment=" + antique.isValidRepairItem(new ItemStack(PalaDungeonGear.FOREST_ESSENCE_FRAGMENT.get()))
                + " sword+fragment=" + new ItemStack(PalaDungeonGear.TALIKUS_SWORD.get()).isValidRepairItem(new ItemStack(PalaDungeonGear.FOREST_ESSENCE_FRAGMENT.get()))
                + " durability=" + helmet.getMaxDamage() + "/" + legendary.getMaxDamage() + "/" + antique.getMaxDamage());

        RandomSource random = RandomSource.create(1);
        for (Dungeon dungeon : Dungeon.values()) {
            StringBuilder line = new StringBuilder(dungeon + " uniques:");
            for (int palier : new int[]{5, 10, 15, 20, 35, 40, 45, 50, 60, 65, 70, 75, 80, 100}) {
                line.append(" ").append(palier).append('=').append(DungeonRewards.unique(dungeon, palier, random));
            }
            log(line.toString());
        }
        for (Grade grade : Grade.values()) {
            log("grade " + grade + " chest p75 = " + DungeonRewards.gradeChest(level, random, Dungeon.MANELIOS, 75, grade));
        }
        int essences = 0;
        for (int i = 0; i < 400; i++) {
            for (ItemStack stack : DungeonRewards.chest(level, random, Dungeon.NIMBRIA, 80, false)) {
                if (stack.getItem() instanceof EssenceItem) {
                    essences++;
                }
            }
        }
        log("essences in 400 chests p80 = " + essences);

        DungeonRun run = new DungeonRun(92_000, level, Dungeon.TALIKUS, 30, new BlockPos(995_000, 64, 0));
        run.members.add(fake.getUUID());
        Field fights = DungeonRun.class.getDeclaredField("fights");
        fights.setAccessible(true);
        Method mark = DungeonRun.class.getDeclaredMethod("mark", ServerPlayer.class, List.class);
        mark.setAccessible(true);
        fights.setInt(run, 3);
        run.recordKill(fake);
        for (float taken : new float[]{0, 20, 50, 80, 130}) {
            Field stats = DungeonRun.class.getDeclaredField("stats");
            stats.setAccessible(true);
            ((java.util.Map<?, ?>) stats.get(run)).clear();
            run.recordKill(fake);
            run.recordDealt(fake, 200);
            run.recordTaken(fake, taken);
            log("solo taken=" + taken + " -> " + mark.invoke(run, fake, List.of(fake)));
        }
    }
}
