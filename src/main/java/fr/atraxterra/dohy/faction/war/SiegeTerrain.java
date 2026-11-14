package fr.atraxterra.dohy.faction.war;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.Factions;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;

/**
 * Le terrain revient apres un siege.
 *
 * <p>Tout ce qu'une armee change — un bloc souffle par une charge, creuse par
 * un ingenieur, une echelle, une planche de pont, une tente du camp — passe
 * par ce registre, qui garde l'etat d'origine du bloc (le premier ecrit
 * l'emporte) et ce qui a ete mis a sa place.
 *
 * <p><b>Rien ne tombe, rien ne s'ecoule, rien ne se perd</b> : les blocs sont
 * changes sans voisinage (une torche reste accrochee, le sable ne coule pas),
 * sans objet lache — sans quoi le retour du mur doublerait sa pierre —, et
 * jamais sur un conteneur ni une machine : le siege les epargne.
 *
 * <p><b>Le retour</b>, une fois la bataille finie, repose chaque bloc d'un
 * coup de poussiere, quelques dizaines par tick. Un bloc que tu as pose
 * entre-temps (une breche rebouchee a la main) reste le tien. Le registre est
 * ecrit avec le monde : un serveur qui tombe en plein siege rend le terrain au
 * demarrage suivant.
 */
public final class SiegeTerrain {

    /** Sans voisinage, sans {@code onPlace}, sans vider de conteneur. */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;

    /** Ce que l'on change d'un coup quand le terrain revient. */
    private static final int RESTORE_PER_TICK = 48;

    /** Un bloc change : sa position, son etat d'origine et ce qu'on y a laisse. */
    record Entry(long pos, BlockState original, BlockState placed) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
                BlockState.CODEC.fieldOf("original").forGetter(Entry::original),
                BlockState.CODEC.fieldOf("placed").forGetter(Entry::placed)
        ).apply(instance, Entry::new));
    }

    /** Ce qui reste a rendre dans une dimension. Piece jointe du niveau. */
    public record State(List<Entry> entries) {
        public static final State EMPTY = new State(List.of());
        public static final MapCodec<State> MAP_CODEC = Entry.CODEC.listOf()
                .optionalFieldOf("entries", List.of()).xmap(State::new, State::entries);

        public boolean exists() {
            return !entries.isEmpty();
        }
    }

    /** Le registre vivant d'un niveau ; ecrit dans la piece jointe quand il change. */
    static final class Ledger {
        final ServerLevel level;
        final Long2ObjectLinkedOpenHashMap<Entry> entries = new Long2ObjectLinkedOpenHashMap<>();
        boolean dirty;
        int restored;

        Ledger(ServerLevel level) {
            this.level = level;
            for (Entry entry : level.getData(Factions.SIEGE_TERRAIN.get()).entries()) {
                entries.put(entry.pos(), entry);
            }
        }

        boolean empty() {
            return entries.isEmpty();
        }

        int size() {
            return entries.size();
        }

        boolean touched(BlockPos pos) {
            return entries.containsKey(pos.asLong());
        }

        /** Note ce bloc avant qu'on le change ; le premier etat note reste l'origine. */
        private void note(BlockPos pos, BlockState original, BlockState placed) {
            long key = pos.asLong();
            Entry known = entries.get(key);
            entries.put(key, new Entry(key, known == null ? original : known.original(), placed));
            dirty = true;
        }

        /** Vide la case : il y reste l'eau ou la lave qui la baignait. */
        void remove(BlockPos pos, boolean effects) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return;
            }
            FluidState fluid = state.getFluidState();
            BlockState left = fluid.isEmpty() ? Blocks.AIR.defaultBlockState() : fluid.createLegacyBlock();
            note(pos, state, left);
            if (effects) {
                level.levelEvent(2001, pos, Block.getId(state));
            }
            level.setBlock(pos, left, FLAGS);
        }

        /** Pose un bloc du siege (echelle, planche, tente). */
        void place(BlockPos pos, BlockState state) {
            BlockState before = level.getBlockState(pos);
            note(pos, before, state);
            level.setBlock(pos, state, FLAGS);
        }

        /** Ecrit le registre dans la piece jointe du niveau, s'il a change. */
        void save() {
            if (!dirty) {
                return;
            }
            dirty = false;
            level.setData(Factions.SIEGE_TERRAIN.get(), entries.isEmpty() ? State.EMPTY
                    : new State(List.copyOf(entries.values())));
        }

        /**
         * Rend quelques blocs ; vrai quand tout est rendu.
         *
         * @param budget le nombre de blocs a rendre, ou -1 pour tout rendre d'un coup
         */
        boolean restore(int budget) {
            int left = budget < 0 ? Integer.MAX_VALUE : budget;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            var iterator = entries.long2ObjectEntrySet().fastIterator();
            while (iterator.hasNext() && left > 0) {
                Long2ObjectMap.Entry<Entry> next = iterator.next();
                Entry entry = next.getValue();
                iterator.remove();
                dirty = true;
                pos.set(BlockPos.getX(entry.pos()), BlockPos.getY(entry.pos()), BlockPos.getZ(entry.pos()));
                if (!level.isLoaded(pos)) {
                    // Hors memoire (un retour au demarrage) : on charge le chunk, le retour ne se perd pas.
                    level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                }
                BlockState current = level.getBlockState(pos);
                if (current != entry.placed() && !current.isAir() && current.getFluidState().isEmpty()
                        && !current.canBeReplaced()) {
                    // Un bloc pose entre-temps (une breche rebouchee a la main) : il reste.
                    continue;
                }
                level.setBlock(pos, entry.original(), FLAGS);
                if (budget >= 0 && !entry.original().isAir() && level.getRandom().nextInt(3) == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, entry.original()),
                            pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, 4, 0.3d, 0.3d, 0.3d, 0.05d);
                }
                restored++;
                left--;
            }
            if (budget >= 0 && restored > 0 && restored % 96 < RESTORE_PER_TICK) {
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.STONE_PLACE, SoundSource.BLOCKS,
                        0.6f, 0.8f + level.getRandom().nextFloat() * 0.3f);
            }
            if (entries.isEmpty()) {
                save();
                return true;
            }
            return false;
        }

        boolean restoreStep() {
            return restore(RESTORE_PER_TICK);
        }

        /** Les positions notees, pour ce que le siege veut verifier (le camp, les breches). */
        List<BlockPos> positions() {
            List<BlockPos> out = new ArrayList<>();
            for (Entry entry : entries.values()) {
                out.add(BlockPos.of(entry.pos()));
            }
            return out;
        }
    }

    private SiegeTerrain() {
    }

    /** Rend d'un coup tout ce qu'un siege a laisse dans ce niveau (un serveur tombe en plein siege). */
    static void recover(ServerLevel level) {
        State state = level.getData(Factions.SIEGE_TERRAIN.get());
        if (!state.exists()) {
            return;
        }
        Ledger ledger = new Ledger(level);
        int count = ledger.size();
        ledger.restore(-1);
        ledger.save();
        DoHy.LOGGER.info("Guerre : {} blocs d'un siege interrompu rendus dans {}", count,
                level.dimension().identifier());
    }
}
