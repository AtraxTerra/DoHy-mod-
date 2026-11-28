package fr.atraxterra.dohy.palamod.botanist;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La reserve de graines d'un joueur ({@code InventoryBotanistReserve} de JobsReborn) : une case par
 * plante du laboratoire, dix graines au plus dans chacune ({@code LIMIT}). Elle est au joueur, pas au
 * bloc : toutes les reserves qu'il ouvre donnent sur la meme (comme un coffre de l'End), gardee sur
 * lui, mort comprise.
 *
 * <p>La premiere graine d'une plante rangee rapporte une fois de l'XP de Fermier, selon sa rarete
 * ({@code MutationReserveJobExperience} : « Stocker : Graines »).
 *
 * <p>JobsReborn range ensemble les graines « de memes scores » (poids, quantite) ; les graines de
 * mutation d'ici n'en ont pas : toutes les graines d'une plante vont dans sa case.
 */
public final class SeedReserve {

    /** Dix graines par case ({@code InventoryBotanistReserve.LIMIT}). */
    public static final int LIMIT = 10;

    private SeedReserve() {
    }

    /** Les cases, dans l'ordre des plantes (par climat : normal, glacial, desertique). */
    public static int size() {
        return Mutation.values().length;
    }

    public static Map<String, Integer> counts(ServerPlayer player) {
        return player.getData(PalaBotanist.SEED_RESERVE.get());
    }

    /** L'XP d'une premiere graine rangee, selon la rarete (calibree, voir BALANCE.md). */
    public static int rewardXp(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 50;
            case RARE -> 100;
            case EPIC -> 250;
            case LEGENDARY -> 600;
        };
    }

    public static boolean rewarded(ServerPlayer player, Mutation mutation) {
        return player.getData(PalaBotanist.SEED_RESERVE_REWARDS.get()).contains(mutation.id());
    }

    /**
     * Le conteneur du menu. Cote serveur, il lit et ecrit la reserve du joueur ; cote client
     * ({@code player} nul), ce n'est qu'un stock que la synchronisation remplit. Les deux acceptent
     * les memes graines, pour que le clic prevu par le client soit celui que fait le serveur.
     */
    public static final class Storage implements Container, MachineMenu.SlotLimits {

        private static final double REACH = 8.0d;

        private final ServerPlayer player;
        private final BlockPos pos;
        private final NonNullList<ItemStack> stacks = NonNullList.withSize(size(), ItemStack.EMPTY);

        public Storage(ServerPlayer player, BlockPos pos) {
            this.player = player;
            this.pos = pos;
            if (player != null) {
                Map<String, Integer> counts = counts(player);
                Mutation[] all = Mutation.values();
                for (int index = 0; index < all.length; index++) {
                    int count = Math.min(LIMIT, counts.getOrDefault(all[index].id(), 0));
                    if (count > 0) {
                        stacks.set(index, new ItemStack(PalaBotanist.MUTATION_SEEDS.get(all[index]).get(), count));
                    }
                }
            }
        }

        /**
         * Les plantes dont la premiere graine a deja rapporte son XP, une par bit (quinze bits : la
         * valeur passe dans un entier court du menu). Lu par l'ecran pour la bulle de chaque case.
         */
        public int rewardMask() {
            if (player == null) {
                return 0;
            }
            List<String> rewards = player.getData(PalaBotanist.SEED_RESERVE_REWARDS.get());
            int mask = 0;
            Mutation[] all = Mutation.values();
            for (int index = 0; index < all.length && index < 15; index++) {
                if (rewards.contains(all[index].id())) {
                    mask |= 1 << index;
                }
            }
            return mask;
        }

        /** Le conteneur cote client. */
        public static Storage client() {
            return new Storage(null, BlockPos.ZERO);
        }

        @Override
        public int getContainerSize() {
            return stacks.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return stacks.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack taken = ContainerHelper.removeItem(stacks, slot, count);
            if (!taken.isEmpty()) {
                setChanged();
            }
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack taken = ContainerHelper.takeItem(stacks, slot);
            setChanged();
            return taken;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            stacks.set(slot, stack);
            if (stack.getCount() > getMaxStackSize()) {
                stack.setCount(getMaxStackSize());
            }
            setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return LIMIT;
        }

        @Override
        public int slotLimit(int slot) {
            return LIMIT;
        }

        /** La case d'une plante ne prend que sa graine. */
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot >= 0 && slot < size() && stack.getItem() instanceof MutationSeedItem seed
                    && seed.mutation() == Mutation.values()[slot];
        }

        @Override
        public boolean stillValid(Player viewer) {
            if (player == null) {
                return true;
            }
            return viewer == player && player.level().getBlockState(pos).getBlock() instanceof SeedReserveBlock
                    && player.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d) <= REACH * REACH;
        }

        /** Ecrit la reserve du joueur ; une plante rangee pour la premiere fois rapporte son XP. */
        @Override
        public void setChanged() {
            if (player == null) {
                return;
            }
            Map<String, Integer> counts = new HashMap<>();
            List<Mutation> fresh = new ArrayList<>();
            List<String> rewards = player.getData(PalaBotanist.SEED_RESERVE_REWARDS.get());
            Mutation[] all = Mutation.values();
            for (int index = 0; index < all.length; index++) {
                ItemStack stack = stacks.get(index);
                if (!stack.isEmpty() && canPlaceItem(index, stack)) {
                    counts.put(all[index].id(), Math.min(LIMIT, stack.getCount()));
                    if (!rewards.contains(all[index].id())) {
                        fresh.add(all[index]);
                    }
                }
            }
            player.setData(PalaBotanist.SEED_RESERVE.get(), Map.copyOf(counts));
            if (fresh.isEmpty()) {
                return;
            }
            List<String> next = new ArrayList<>(rewards);
            for (Mutation mutation : fresh) {
                next.add(mutation.id());
                int xp = rewardXp(mutation.rarity());
                JobApi.grant(player, Job.FARMER, xp);
                player.sendSystemMessage(Component.literal("[Botaniste] ").withStyle(ChatFormatting.DARK_GREEN)
                        .append(Component.literal("Premiere graine de " + mutation.label() + " en reserve : +" + xp
                                + " XP de Fermier.").withStyle(ChatFormatting.GREEN)));
            }
            player.setData(PalaBotanist.SEED_RESERVE_REWARDS.get(), List.copyOf(next));
            player.level().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.2f);
        }

        @Override
        public void clearContent() {
            stacks.clear();
            setChanged();
        }
    }
}
