package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.palamod.smeltery.GrinderModifier;
import fr.atraxterra.dohy.palamod.smeltery.PalaSmeltery;
import fr.atraxterra.dohy.palamod.smeltery.ToolModifiers;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Temporaire : l'XP verte des marteaux en fusion. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int Y = -60;
    private static int before;

    private ShotSamples() {
    }

    private static ShotHarness.Shot server(String name, int extra, Consumer<ServerPlayer> action) {
        return new ShotHarness.Shot(name, () -> {
            ShotHarness.extraWait = extra;
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                try {
                    action.accept(server.getPlayerList().getPlayers().get(0));
                } catch (Throwable t) {
                    LOG.error("dohy-xp: ECHEC etape " + name, t);
                }
            });
            return null;
        });
    }

    private static void op(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player)
                .withPosition(player.position()).withLevel((ServerLevel) player.level()), command);
    }

    private static void teleport(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        ServerLevel world = player.level().getServer().overworld();
        world.getChunk(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
        player.teleport(new TeleportTransition(world, new Vec3(x, y, z), Vec3.ZERO, yaw, pitch,
                TeleportTransition.DO_NOTHING));
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.setYRot(yaw);
                Minecraft.getInstance().player.setXRot(pitch);
            }
        });
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).orElseThrow();
    }

    /** Un mur de 3 x 3 du minerai devant le joueur, au nord, et le joueur qui le regarde. */
    private static BlockPos wall(ServerPlayer player, int x, String ore) {
        ServerLevel level = player.level().getServer().overworld();
        int z = 3000;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlock(new BlockPos(x + dx, Y + dy, z - 2), block(ore).defaultBlockState(), 3);
            }
        }
        teleport(player, x + 0.5d, Y, z + 0.5d, 180.0f, 0.0f);
        return new BlockPos(x, Y + 1, z - 2);
    }

    private static void mine(ServerPlayer player, ItemStack hammer, BlockPos center, String label) {
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
        before = player.totalExperience;
        boolean broken = player.gameMode.destroyBlock(center);
        LOG.info("dohy-xp: {} : casse {}, XP avant {}", label, broken, before);
    }

    private static void count(ServerPlayer player, int x, String label) {
        ServerLevel level = player.level().getServer().overworld();
        StringBuilder items = new StringBuilder();
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class,
                new AABB(x - 4, Y - 2, 2990, x + 4, Y + 6, 3004))) {
            items.append(drop.getItem().getCount()).append(' ')
                    .append(BuiltInRegistries.ITEM.getKey(drop.getItem().getItem())).append(", ");
            drop.discard();
        }
        int left = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                if (!level.getBlockState(new BlockPos(x + dx, Y + dy, 2998)).isAir()) {
                    left++;
                }
            }
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && !(stack.getItem() instanceof fr.atraxterra.dohy.palamod.smeltery.HammerItem)) {
                items.append(stack.getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                        .append(" (sac), ");
            }
        }
        LOG.info("dohy-xp: {} : XP gagnee {} (total {}), blocs restants {}, objets [{}]", label,
                player.totalExperience - before, player.totalExperience, left, items);
    }

    private static ItemStack smeltHammer() {
        ItemStack hammer = new ItemStack(PalaSmeltery.PALADIUM_HAMMER.get());
        hammer.set(PalaSmeltery.MODIFIERS.get(), ToolModifiers.empty(3).with(GrinderModifier.SMELT));
        return hammer;
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 100, player -> {
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule doDaylightCycle false");
            op(player, "gamerule advance_time false");
            op(player, "gamerule doMobSpawning false");
            op(player, "gamerule spawn_mobs false");
            player.setGameMode(GameType.SURVIVAL);
            teleport(player, 3000.5d, Y, 3000.5d, 180.0f, 0.0f);
        }));
        // 1. Le marteau en paladium sans modificateur : le fer brut, sans XP.
        ShotHarness.SHOTS.add(server("b0_simple", 10, player ->
                mine(player, new ItemStack(PalaSmeltery.PALADIUM_HAMMER.get()), wall(player, 3000, "minecraft:iron_ore"),
                        "marteau simple, fer")));
        ShotHarness.SHOTS.add(server("b1_simple", 60, player -> count(player, 3000, "marteau simple, fer")));
        // 2. Le meme en fusion : des lingots, et 0,7 XP par lingot.
        ShotHarness.SHOTS.add(server("c0_fusion", 10, player ->
                mine(player, smeltHammer(), wall(player, 3010, "minecraft:iron_ore"), "marteau en fusion, fer")));
        ShotHarness.SHOTS.add(server("c1_fusion", 0, player -> { }));
        ShotHarness.SHOTS.add(server("c2_fusion", 60, player -> count(player, 3010, "marteau en fusion, fer")));
        // 3. Le paladium en fusion : 1 XP par lingot.
        ShotHarness.SHOTS.add(server("d0_paladium", 10, player ->
                mine(player, smeltHammer(), wall(player, 3020, "palamod:paladium_ore"), "marteau en fusion, paladium")));
        ShotHarness.SHOTS.add(server("d1_paladium", 0, player -> { }));
        ShotHarness.SHOTS.add(server("d2_paladium", 60, player -> count(player, 3020, "marteau en fusion, paladium")));
        // 4. Le charbon : pas de recette pour le charbon, l'XP du minerai seule.
        ShotHarness.SHOTS.add(server("e0_charbon", 10, player ->
                mine(player, smeltHammer(), wall(player, 3030, "minecraft:coal_ore"), "marteau en fusion, charbon")));
        ShotHarness.SHOTS.add(server("e1_charbon", 60, player -> count(player, 3030, "marteau en fusion, charbon")));
        ShotHarness.SHOTS.add(server("z_fin", 10, player -> LOG.info("dohy-xp: fin, XP totale {}", player.totalExperience)));
    }
}
