package fr.atraxterra.dohy.faction.base;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Le chantier des bases ennemies, ouvert aux autres systemes : les places
 * fortes des nations en guerre s'y batissent comme une base ennemie (meme
 * enceinte, memes gardes, memes tourelles, meme releve du terrain), et leur
 * terrain revient de la meme facon ({@link EnemyBaseRegen}).
 */
public final class BaseWorks {

    private final EnemyBaseBuilder builder;
    private EnemyBaseBuilder.Result result;

    /**
     * Prepare le chantier (le sol, le releve du terrain, toutes les etapes) ;
     * rien n'est encore pose. {@code name} est celui de ses claims,
     * {@code guard} celui de ses gardes (« Garde de Nordheim »).
     */
    public BaseWorks(ServerLevel level, EnemyBaseTier tier, EnemyBaseStyle style, String name, String guard, int x,
                     int z) {
        this.builder = new EnemyBaseBuilder(level, level.getRandom(), tier, style, name, x, z);
        this.builder.guardName(guard);
        this.builder.plan();
    }

    /** Avance le chantier pendant {@code budget} nanosecondes au plus ; vrai quand tout est pose. */
    public boolean work(long budget) {
        boolean done = builder.work(budget);
        if (done && result == null) {
            result = builder.result();
        }
        return done;
    }

    public BlockPos center() {
        return result.center();
    }

    public BlockPos coreMin() {
        return result.coreMin();
    }

    public BlockPos coreMax() {
        return result.coreMax();
    }

    public int guards() {
        return result.guards();
    }

    public int turrets() {
        return result.turrets();
    }

    /** Un claim deja pose (un joueur, une autre base) dans le carre de chunks ? */
    public static boolean overlapsClaim(ServerLevel level, int chunkX, int chunkZ, int radius) {
        return EnemyBaseBuilder.overlapsClaim(level, chunkX, chunkZ, radius);
    }

    /**
     * Le terrain de la base reviendra dans cinq minutes ; faux si elle n'a pas
     * de releve. {@code title} nomme ce qui s'efface (« La place forte de
     * Nordheim »).
     */
    public static boolean regen(MinecraftServer server, UUID player, String name, String title, BlockPos center) {
        return EnemyBaseRegen.schedule(server, player, name, title, center, EnemyBaseRegen.DELAY_TICKS);
    }

    /** Ce terrain attend-il de revenir, ou revient-il ? */
    public static boolean regenerating(BlockPos center) {
        return EnemyBaseRegen.regenerating(center);
    }

    /**
     * Un terrain de base attend-il de revenir pres d'ici (a moins de
     * {@code reach} blocs en x et en z) ? Une nouvelle base n'y est pas batie :
     * le retour l'effacerait.
     */
    public static boolean regeneratingNear(int x, int z, int reach) {
        return EnemyBaseRegen.regeneratingNear(x, z, reach);
    }

    /** Arme (et recharge) ou eteint les tourelles de NTM de la base, dans ses chunks charges. */
    public static int turrets(ServerLevel level, BlockPos center, int radius, boolean armed) {
        return EnemyBases.turrets(level, center, radius, armed);
    }
}
