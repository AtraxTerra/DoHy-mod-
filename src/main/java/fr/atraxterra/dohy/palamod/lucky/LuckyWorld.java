package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Les outils des evenements ({@code EventUtils} et {@code PlayerUtils} de PalaMod).
 *
 * <p>Deux regles valent pour tous les evenements :
 * <ul>
 *   <li>un evenement ne touche un bloc que si le joueur pourrait le casser
 *       ({@code canInteract}) : pas dans le claim d'un autre, pas un bloc
 *       indestructible. Ici, il ne remplace jamais non plus un bloc qui porte un
 *       contenu (coffre, machine) : PalaMod l'aurait ecrase avec ce qu'il
 *       contenait ;</li>
 *   <li>les explosions n'ont pas de joueur pour auteur : dans un claim, elles ne
 *       cassent aucun bloc ({@code ClaimProtection}), comme celles des creepers.
 *       Un mauvais lucky block ouvert chez soi blesse, mais ne rase pas la base.</li>
 * </ul>
 */
public final class LuckyWorld {

    private LuckyWorld() {
    }

    /** Le joueur peut-il changer ce bloc ? */
    public static boolean canEdit(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getDestroySpeed(level, pos) < 0.0f || state.hasBlockEntity()) {
            return false;
        }
        ClaimOwner owner = FactionApi.claimAt(level, pos);
        return !owner.claimed() || !owner.enemy() && owner.ownedBy(player.getUUID());
    }

    /** Pose un bloc s'il en a le droit. */
    public static boolean place(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!canEdit(player, level, pos)) {
            return false;
        }
        return level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    /**
     * Un pave plein ({@code spawnStructure}) : centre en {@code center}, de
     * {@code center - size / 2} a {@code center + ceil(size / 2)} exclu, sur
     * chaque axe. {@code skip} epargne des cases (la place du joueur).
     */
    public static void box(ServerPlayer player, ServerLevel level, BlockPos center, int sizeX, int sizeY, int sizeZ,
                           BlockState state, Predicate<BlockPos> skip) {
        for (int x = center.getX() - sizeX / 2; x < center.getX() + (sizeX + 1) / 2; x++) {
            for (int y = center.getY() - sizeY / 2; y < center.getY() + (sizeY + 1) / 2; y++) {
                for (int z = center.getZ() - sizeZ / 2; z < center.getZ() + (sizeZ + 1) / 2; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (skip == null || !skip.test(pos)) {
                        place(player, level, pos, state);
                    }
                }
            }
        }
    }

    /** Lache des objets ({@code dropItemStack}), en piles de la bonne taille. */
    public static void drop(ServerLevel level, double x, double y, double z, ItemStack stack) {
        ItemStack left = stack.copy();
        while (!left.isEmpty()) {
            ItemStack part = left.split(Math.max(1, left.getMaxStackSize()));
            ItemEntity item = new ItemEntity(level, x, y, z, part);
            item.setPickUpDelay(10);
            level.addFreshEntity(item);
        }
    }

    /** Lache des objets au centre d'un bloc. */
    public static void drop(ServerLevel level, BlockPos pos, ItemStack stack) {
        drop(level, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, stack);
    }

    /** Un message dans le chat. */
    public static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    /** Un message au-dessus de la barre d'objets ({@code sendActionBar}). */
    public static void bar(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color), true);
    }

    public static void particles(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count,
                                 double spread, double speed) {
        level.sendParticles(particle, x, y, z, count, spread, spread, spread, speed);
    }

    public static void sound(ServerLevel level, double x, double y, double z, SoundEvent sound, float volume,
                             float pitch) {
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, volume, pitch);
    }

    /**
     * Un son du jeu par son nom ({@code entity.pig.ambient}) : les cris des animaux ont
     * des variantes selon les versions, leur nom reste.
     */
    public static void sound(ServerLevel level, double x, double y, double z, String id, SoundSource source,
                             float volume, float pitch) {
        BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.withDefaultNamespace(id))
                .ifPresent(sound -> level.playSound(null, x, y, z, sound, source, volume, pitch));
    }

    /**
     * Une creature ou une entite, placee mais pas encore ajoutee au monde (on
     * la regle d'abord) ; {@code null} si elle ne peut pas naitre.
     */
    public static <T extends Entity> T create(ServerLevel level, EntityType<T> type, double x, double y, double z) {
        T entity = type.create(level, EntitySpawnReason.EVENT);
        if (entity == null) {
            return null;
        }
        entity.snapTo(x, y, z, level.getRandom().nextFloat() * 360.0f, 0.0f);
        return entity;
    }

    /**
     * Un eclair. Le joueur en est la cause : un eclair sans cause ne tombe pas
     * dans un claim ({@code ClaimProtection}).
     */
    public static void lightning(ServerLevel level, ServerPlayer cause, double x, double y, double z,
                                 boolean visualOnly) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) {
            return;
        }
        bolt.snapTo(x, y, z);
        bolt.setCause(cause);
        bolt.setVisualOnly(visualOnly);
        level.addFreshEntity(bolt);
    }

    /** De la TNT allumee, sans auteur ({@code EntityTNTPrimed} sans lanceur : rien ne casse dans un claim). */
    public static void tnt(ServerLevel level, double x, double y, double z, int fuse) {
        PrimedTnt tnt = new PrimedTnt(level, x, y, z, null);
        tnt.setFuse(fuse);
        level.addFreshEntity(tnt);
    }

    /** Un effet, sans particules ni icone cachee. */
    public static void effect(ServerPlayer player, Holder<MobEffect> effect, int ticks, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, ticks, amplifier));
    }
}
