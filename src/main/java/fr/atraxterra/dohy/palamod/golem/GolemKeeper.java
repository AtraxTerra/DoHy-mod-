package fr.atraxterra.dohy.palamod.golem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.ui.KitMenus;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code GuardianKeeperHandler} : neuf cases par joueur, ou la pierre d'un golem
 * mort (ou frappe au marteau) attend son proprietaire. N'importe quel bloc
 * gardien ouvre les siennes. Plein, le golem est perdu, comme dans l'original.
 *
 * <p>Le tout vit sur le monde principal, en piece jointe de niveau : un
 * proprietaire hors ligne recoit quand meme sa pierre.
 */
public final class GolemKeeper {

    public static final int SLOTS = 9;

    /** Les gardiens de tous les joueurs. Mutable : on le reecrit en entier a chaque changement. */
    public static final class Data {
        public static final MapCodec<Data> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC,
                        ItemStack.OPTIONAL_CODEC.listOf())
                .optionalFieldOf("keepers", Map.of())
                .xmap(Data::new, data -> data.keepers);

        private final Map<UUID, List<ItemStack>> keepers;

        public Data() {
            this(Map.of());
        }

        private Data(Map<UUID, List<ItemStack>> source) {
            keepers = new HashMap<>();
            source.forEach((owner, stacks) -> keepers.put(owner, new ArrayList<>(stacks)));
        }

        public boolean isEmpty() {
            return keepers.values().stream().allMatch(stacks -> stacks.stream().allMatch(ItemStack::isEmpty));
        }

        List<ItemStack> of(UUID owner) {
            List<ItemStack> stacks = keepers.computeIfAbsent(owner, ignored -> new ArrayList<>());
            while (stacks.size() < SLOTS) {
                stacks.add(ItemStack.EMPTY);
            }
            return stacks;
        }
    }

    private GolemKeeper() {
    }

    private static ServerLevel home(MinecraftServer server) {
        return server.overworld();
    }

    private static Data data(MinecraftServer server) {
        return home(server).getData(PalaGolem.KEEPER.get());
    }

    private static void dirty(MinecraftServer server, Data data) {
        home(server).setData(PalaGolem.KEEPER.get(), data);
    }

    /** Range la pierre dans la premiere case libre ; faux si le gardien est plein. */
    public static boolean store(MinecraftServer server, UUID owner, ItemStack stone) {
        Data data = data(server);
        List<ItemStack> stacks = data.of(owner);
        for (int slot = 0; slot < SLOTS; slot++) {
            if (stacks.get(slot).isEmpty()) {
                stacks.set(slot, stone.copy());
                dirty(server, data);
                return true;
            }
        }
        return false;
    }

    /** Ouvre le gardien du joueur. */
    public static void open(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Data data = data(server);
        List<ItemStack> stacks = data.of(player.getUUID());
        SimpleContainer container = new SimpleContainer(SLOTS) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return stack.is(PalaGolem.GUARDIAN_STONE.get());
            }

            @Override
            public boolean stillValid(Player viewer) {
                return viewer == player;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                for (int slot = 0; slot < SLOTS; slot++) {
                    stacks.set(slot, getItem(slot).copy());
                }
                dirty(server, data);
            }
        };
        for (int slot = 0; slot < SLOTS; slot++) {
            container.setItem(slot, stacks.get(slot).copy());
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new FilteredChestMenu(KitMenus.Use.GOLEM_KEEPER.type(), containerId,
                        inventory, container, 1),
                Component.translatable("container.palamod.guardian_keeper")));
    }
}
