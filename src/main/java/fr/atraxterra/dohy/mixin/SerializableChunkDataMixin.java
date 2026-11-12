package fr.atraxterra.dohy.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Les cartes faites d'avance (WorldPainter) dont les chunks attendent leur
 * decoration.
 *
 * <p>Un outil de dessin de cartes qui veut laisser le jeu poser les minerais
 * et les arbres enregistre ses chunks a l'etape {@code liquid_carvers} : le
 * terrain est fait, il reste la decoration. Cette etape a disparu du jeu, et
 * les correcteurs de donnees ne touchent pas un chunk qui se dit deja recent.
 * En 26.2 le nom se lit donc {@code empty} : le jeu prend un chunk tout
 * dessine pour un chunk vide et lui repasse les biomes et le terrain de sa
 * propre generation par-dessus. Essaye le 2026-11-12 sur un chunk copie a
 * cette etape : son biome a change et la moitie de ses blocs aussi. Une carte
 * de la Terre en ressort defiguree.
 *
 * <p>Un mixin parce que rien d'autre ne passe par la : l'etape est lue dans
 * les donnees brutes du chunk, avant tout evenement, et pas par
 * {@code ChunkStatus.byName} (un premier mixin sur cette methode n'a rien
 * change). On corrige donc le nom dans les donnees elles-memes, a l'entree des
 * deux methodes qui les lisent : il reprend le sens qu'il avait — l'etape
 * juste apres le creusement, {@code carvers} — et le jeu reprend ou l'outil
 * s'est arrete : la decoration, la lumiere, puis le chunk complet. Aucun
 * autre nom n'est touche.
 */
@Mixin(SerializableChunkData.class)
public abstract class SerializableChunkDataMixin {

    private static final String KEY = "Status";

    private static void dohy$rename(CompoundTag tag) {
        String status = tag.getStringOr(KEY, "");
        if (status.equals("liquid_carvers") || status.equals("minecraft:liquid_carvers")) {
            tag.putString(KEY, "minecraft:carvers");
        }
    }

    @Inject(method = "parse", at = @At("HEAD"))
    private static void dohy$parse(LevelHeightAccessor level, PalettedContainerFactory factory, CompoundTag tag,
                                   CallbackInfoReturnable<SerializableChunkData> callback) {
        dohy$rename(tag);
    }

    @Inject(method = "getChunkStatusFromTag", at = @At("HEAD"))
    private static void dohy$status(CompoundTag tag, CallbackInfoReturnable<ChunkStatus> callback) {
        if (tag != null) {
            dohy$rename(tag);
        }
    }
}
