package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.util.UUID;

@EventBusSubscriber
public final class AntiqueSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("antiquetest").executes(context -> {
            try {
                run(context.getSource().getServer().overworld(),
                        context.getSource().getServer().getLevel(PalaDungeon.DIMENSION));
            } catch (Throwable t) {
                LOG.error("palamod-test: ANTIQUE FAILED", t);
            }
            context.getSource().sendSuccess(() -> Component.literal("ok"), false);
            return 1;
        }));
    }

    private static void wear(ServerPlayer player, boolean all) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PalaDungeonGear.ANTIQUE_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PalaDungeonGear.ANTIQUE_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(PalaDungeonGear.ANTIQUE_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, all ? new ItemStack(PalaDungeonGear.ANTIQUE_BOOTS.get()) : ItemStack.EMPTY);
    }

    private static String state(ServerPlayer player) {
        var invisibility = player.getEffect(MobEffects.INVISIBILITY);
        return "mayfly=" + player.getAbilities().mayfly + " flying=" + player.getAbilities().flying
                + " tag=" + player.entityTags().contains(AntiqueEffects.FLIGHT_TAG)
                + " invis=" + (invisibility == null ? "none" : invisibility.getDuration() + (invisibility.isAmbient() ? "a" : ""))
                + " slowfall=" + (player.getEffect(MobEffects.SLOW_FALLING) != null)
                + " set=" + DungeonGearItem.antiqueSet(player)
                + " red=" + DungeonGearItem.reduction(player, Dungeon.TALIKUS)
                + " dmg=" + DungeonGearItem.damageBonus(player, Dungeon.TALIKUS);
    }

    private static void run(ServerLevel overworld, ServerLevel dungeon) {
        ServerPlayer fake = FakePlayerFactory.getMinecraft(overworld);
        wear(fake, true);
        AntiqueEffects.update(fake);
        LOG.info("palamod-test: full set overworld " + state(fake));
        fake.getAbilities().flying = true;
        wear(fake, false);
        AntiqueEffects.update(fake);
        LOG.info("palamod-test: boots removed while flying " + state(fake));
        fake.removeAllEffects();
        fake.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.INVISIBILITY, 2000));
        wear(fake, true);
        AntiqueEffects.update(fake);
        wear(fake, false);
        AntiqueEffects.update(fake);
        LOG.info("palamod-test: potion kept after removal " + state(fake));

        ServerPlayer inside = FakePlayerFactory.get(dungeon, new GameProfile(UUID.randomUUID(), "antique_test"));
        wear(inside, true);
        AntiqueEffects.update(inside);
        LOG.info("palamod-test: full set in dungeon " + state(inside));
    }
}
