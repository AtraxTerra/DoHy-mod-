package fr.atraxterra.dohy.palamod;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.palamod.legendary.PalaLegendary;
import fr.atraxterra.dohy.palamod.machine.PalaMachines;
import fr.atraxterra.dohy.palamod.storage.PaladiumHopperBlock;
import fr.atraxterra.dohy.palamod.storage.PalaStorage;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Temporaire : les retours en jeu du 2026-11-09. */
@EventBusSubscriber
public final class RetoursSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int X = 992;
    private static final int Z = 992;
    private static int countdown = -1;
    private static ServerLevel world;

    private static void log(String line) {
        LOG.info("palamod-test: " + line);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("retourstest").executes(context -> {
            try {
                run(context.getSource().getServer());
            } catch (Throwable t) {
                LOG.error("palamod-test: RUN FAILED", t);
            }
            context.getSource().sendSuccess(() -> Component.literal("ok"), false);
            return 1;
        }));
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (countdown < 0) {
            return;
        }
        if (--countdown == 0) {
            countdown = -1;
            try {
                check();
            } catch (Throwable t) {
                LOG.error("palamod-test: CHECK FAILED", t);
            }
        }
    }

    private static String contents(Container container) {
        if (container == null) {
            return "null";
        }
        List<String> out = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                out.add(slot + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + "x" + stack.getCount());
            }
        }
        return out.toString();
    }

    private static Container at(int x, int y, int z) {
        BlockEntity entity = world.getBlockEntity(new BlockPos(x, y, z));
        return entity instanceof Container container ? container : null;
    }

    private static void put(int x, int y, int z, BlockState state) {
        world.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_ALL);
    }

    private static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        world = level;

        for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            try {
                ServerPlayer fake = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "stone_" + mode.getName()));
                fake.setGameMode(mode);
                fake.getInventory().clearContent();
                fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PalaLegendary.RANDOM.get()));
                var result = fake.gameMode.useItem(fake, level, fake.getMainHandItem(), InteractionHand.MAIN_HAND);
                log("pierre " + mode.getName() + " resultat=" + result + " inventaire=" + contents(fake.getInventory()));
            } catch (Throwable t) {
                LOG.error("palamod-test: PIERRE FAILED " + mode, t);
            }
        }

        ServerPlayer miner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ore_test"));
        BlockPos origin = new BlockPos(X, 200, Z);
        for (String name : List.of("amethyst_ore", "titane_ore", "paladium_ore", "paladium_green_ore", "findium_ore",
                "trixium_ore", "endium_nugget_ore", "invoker_ore")) {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, name));
            List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), level, origin, null, miner,
                    new ItemStack(Items.DIAMOND_PICKAXE));
            List<String> names = drops.stream()
                    .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + "x" + stack.getCount()).toList();
            var smelted = level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                    new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(block)), level)
                    .map(holder -> holder.value().assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(block))))
                    .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()).orElse("aucune");
            var blasted = level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.BLASTING,
                    new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(block)), level).isPresent();
            log("minerai " + name + " -> " + names + " four=" + smelted + " haut_fourneau=" + blasted);
        }

        level.setChunkForced(X >> 4, Z >> 4, true);
        level.getChunk(X >> 4, Z >> 4);
        BlockState chest = Blocks.CHEST.defaultBlockState();
        BlockState breaker = PalaMachines.COBBLEBREAKER_BLOCK.get().defaultBlockState();
        BlockState crusher = PalaMachines.CRUSHER_BLOCK.get().defaultBlockState();
        BlockState hopper = Blocks.HOPPER.defaultBlockState();
        BlockState paladium = PalaStorage.PALADIUM_HOPPER_BLOCK.get().defaultBlockState();

        // A : entonnoir vanilla sous un cobblebreaker
        put(X, 200, Z, breaker);
        put(X, 199, Z, hopper);
        put(X, 198, Z, chest);
        // B : entonnoir en paladium, coffre -> coffre vers le bas
        put(X + 3, 200, Z, chest);
        put(X + 3, 199, Z, paladium);
        put(X + 3, 198, Z, chest);
        // C : entonnoir en paladium vers l'est
        put(X + 6, 200, Z, chest);
        put(X + 6, 199, Z, paladium.setValue(PaladiumHopperBlock.FACING, Direction.EAST));
        put(X + 7, 199, Z, chest);
        // D : entonnoir en paladium sous un cobblebreaker
        put(X + 10, 200, Z, breaker);
        put(X + 10, 199, Z, paladium);
        put(X + 10, 198, Z, chest);
        // E : entonnoir vanilla au-dessus d'un entonnoir en paladium
        put(X + 13, 201, Z, chest);
        put(X + 13, 200, Z, hopper);
        put(X + 13, 199, Z, paladium);
        put(X + 13, 198, Z, chest);
        // G : objets au sol sur un entonnoir en paladium
        put(X + 6, 199, Z + 4, paladium);
        put(X + 6, 198, Z + 4, chest);
        world.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(world, X + 6.5, 200.2, Z + 4.5,
                new ItemStack(Items.DIRT, 20)));
        world.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(world, X + 6.5, 200.2, Z + 4.5,
                new ItemStack(Items.COBBLESTONE, 7)));
        // F : entonnoir vanilla sous un crusher
        put(X + 2, 200, Z + 4, crusher);
        put(X + 2, 199, Z + 4, hopper);
        put(X + 2, 198, Z + 4, chest);

        for (int x : new int[]{X, X + 10}) {
            Container machine = at(x, 200, Z);
            machine.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
            machine.setItem(1, new ItemStack(PalaItems.IRON_PARTICLE.get(), 5));
        }
        for (int[] pos : new int[][]{{X + 3, 200}, {X + 6, 200}, {X + 13, 201}}) {
            Container top = at(pos[0], pos[1], Z);
            top.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
            top.setItem(1, new ItemStack(Items.DIRT, 16));
        }
        Container crush = at(X + 2, 200, Z + 4);
        log("crusher " + (crush == null ? "null" : crush.getClass().getSimpleName() + " " + crush.getContainerSize()));
        if (crush != null) {
            crush.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
            crush.setItem(2, new ItemStack(Items.DIRT, 3));
        }

        log("paladium hopper etat=" + world.getBlockState(new BlockPos(X + 3, 199, Z))
                + " be=" + world.getBlockEntity(new BlockPos(X + 3, 199, Z)));
        countdown = 100;
        log("installe, verification dans 100 ticks");
    }

    private static void check() {
        log("A machine=" + contents(at(X, 200, Z)) + " coffre=" + contents(at(X, 198, Z)));
        log("B haut=" + contents(at(X + 3, 200, Z)) + " bas=" + contents(at(X + 3, 198, Z)));
        log("C haut=" + contents(at(X + 6, 200, Z)) + " est=" + contents(at(X + 7, 199, Z)));
        log("D machine=" + contents(at(X + 10, 200, Z)) + " coffre=" + contents(at(X + 10, 198, Z)));
        log("E haut=" + contents(at(X + 13, 201, Z)) + " entonnoir=" + contents(at(X + 13, 200, Z))
                + " bas=" + contents(at(X + 13, 198, Z)));
        log("F crusher=" + contents(at(X + 2, 200, Z + 4)) + " coffre=" + contents(at(X + 2, 198, Z + 4)));
        log("G coffre=" + contents(at(X + 6, 198, Z + 4)));
        log("fin");
    }
}
