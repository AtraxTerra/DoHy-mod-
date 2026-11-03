package fr.atraxterra.dohy.world;

import fr.atraxterra.dohy.DoHyAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sait si un bloc a ete pose par un joueur.
 *
 * <p>C'est le socle de tout le systeme de progression, et la raison pour
 * laquelle il est implemente avant les skills et les collections : sans lui,
 * poser puis casser la meme pierre en boucle rapporterait de l'XP et ferait
 * monter une collection. Minecraft ne marque pas les blocs poses par un joueur,
 * il faut donc le faire soi-meme.
 *
 * <p>Le suivi vit dans un attachment de chunk : il se sauvegarde et se charge
 * avec le chunk, et une position oubliee ne coute qu'un faux positif local au
 * lieu de corrompre un index global.
 *
 * <h2>Limites connues</h2>
 * <ul>
 *   <li><b>Pistons.</b> Un bloc deplace par un piston laisse sa marque a
 *       l'ancienne position. Le joueur garde donc un bloc "naturel" la ou il a
 *       pousse son bloc pose. L'exploit est marginal (un piston par bloc), mais
 *       il existe ; a traiter si une ferme le rend rentable.</li>
 *   <li><b>Memoire.</b> Une construction massive dans un seul chunk fait grossir
 *       l'ensemble. Acceptable en solo ; si ca devient genant, le stockage
 *       passera a un masque de bits par section plutot qu'a un ensemble de
 *       positions absolues.</li>
 * </ul>
 */
public final class PlacedBlocks {

    /** Verdicts deja rendus, par evenement de casse. Voir {@link #wasPlayerPlaced}. */
    private static final Map<BlockDropsEvent, Boolean> VERDICTS = new WeakHashMap<>();

    private PlacedBlocks() {
    }

    /** Marque une position comme posee par un joueur. */
    public static void mark(LevelAccessor level, BlockPos pos) {
        LevelChunk chunk = chunkAt(level, pos);
        if (chunk == null) {
            return;
        }
        PlacedBlockData data = chunk.getData(DoHyAttachments.PLACED_BLOCKS);
        if (data.add(pos.asLong())) {
            chunk.markUnsaved();
        }
    }

    /** Vrai si la position a ete posee par un joueur et n'a pas ete cassee depuis. */
    public static boolean isPlayerPlaced(LevelAccessor level, BlockPos pos) {
        LevelChunk chunk = chunkAt(level, pos);
        if (chunk == null) {
            return false;
        }
        return chunk.getData(DoHyAttachments.PLACED_BLOCKS).contains(pos.asLong());
    }

    /**
     * Retire la marque et indique si elle existait.
     *
     * <p>C'est la methode que les systemes de recompense doivent appeler a la
     * casse : elle repond "ce bloc etait-il pose ?" et nettoie dans le meme
     * geste. Retourne {@code true} si le bloc etait pose par un joueur, donc si
     * la casse ne doit rien rapporter.
     */
    public static boolean consume(LevelAccessor level, BlockPos pos) {
        LevelChunk chunk = chunkAt(level, pos);
        if (chunk == null) {
            return false;
        }
        PlacedBlockData data = chunk.getData(DoHyAttachments.PLACED_BLOCKS);
        boolean wasPlaced = data.remove(pos.asLong());
        if (wasPlaced) {
            chunk.markUnsaved();
        }
        return wasPlaced;
    }

    /**
     * Verdict memoise pour une casse donnee : ce bloc avait-il ete pose par un
     * joueur ?
     *
     * <p>Plusieurs systemes vont vouloir cette reponse pour la meme casse — les
     * skills, les collections, et le nettoyage du suivi lui-meme. Or
     * {@link #consume} retire la marque : le second appelant lirait
     * {@code false} et croirait le bloc naturel, ce qui donnerait de l'XP sur un
     * bloc pose. Un faux negatif de ce genre est exactement l'exploit que tout
     * ce fichier existe pour fermer.
     *
     * <p>La reponse est donc calculee une seule fois par evenement et mise en
     * cache, ce qui rend l'ordre d'execution des gestionnaires indifferent :
     * aucun besoin de jouer avec les priorites du bus. Le cache est a cles
     * faibles et disparait avec l'evenement.
     */
    public static boolean wasPlayerPlaced(BlockDropsEvent event) {
        synchronized (VERDICTS) {
            Boolean cached = VERDICTS.get(event);
            if (cached != null) {
                return cached;
            }
            boolean verdict = consume(event.getLevel(), event.getPos());
            VERDICTS.put(event, verdict);
            return verdict;
        }
    }

    /** Nombre de positions suivies dans le chunk contenant cette position. */
    public static int trackedInChunk(LevelAccessor level, BlockPos pos) {
        LevelChunk chunk = chunkAt(level, pos);
        return chunk == null ? 0 : chunk.getData(DoHyAttachments.PLACED_BLOCKS).size();
    }

    /**
     * Le chunk contenant la position, sans jamais forcer son chargement.
     *
     * <p>Charger un chunk depuis un gestionnaire d'evenement de bloc est le
     * meilleur moyen de provoquer un chargement en cascade ou un interblocage.
     * Si le chunk n'est pas la, on renonce : le cout est un faux negatif.
     */
    private static LevelChunk chunkAt(LevelAccessor level, BlockPos pos) {
        if (level.isClientSide()) {
            return null;
        }
        return level.getChunkSource()
                .getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
