package fr.atraxterra.dohy.faction.war;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.base.NtmDefenses;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Les missiles de NTM NEXT au service des nations rivales, sans dependre du mod
 * a la compilation : les missiles se trouvent dans le registre des entites
 * ({@code hbm:missile_generic}...), le reste passe par reflexion. Sans NTM, ou si
 * NTM a change sa facon de faire, {@link #ready()} rend faux et les nations
 * levent le siege comme avant.
 *
 * <p><b>Le vol</b> est celui de NTM : le missile part a quelques centaines de
 * blocs, monte, puis plonge sur sa cible. Les radars et les missiles
 * antimissiles de NTM le voient et peuvent l'abattre.
 *
 * <p><b>L'impact</b> ne passe pas par le missile : au tick ou il toucherait,
 * il est retire et la charge part d'ici, avec la meme explosion que NTM
 * (meme puissance, memes rayons, memes effets), a deux differences pres :
 *
 * <ul>
 *   <li>l'explosion a pour auteur un « bouclier » (un marqueur jamais pose
 *       dans le monde) qui refuse les conteneurs, les machines et les
 *       multiblocs de NTM : les rayons les traversent sans les casser ;</li>
 *   <li>les charges nucleaires partent sans retombees (pas de pluie
 *       radioactive ni de cratere irradie), sinon le terrain ne pourrait pas
 *       revenir. Le cratere, lui, est celui de NTM.</li>
 * </ul>
 */
final class NtmArsenal {

    enum Kind {
        /** Une explosion de NTM ({@code ExplosionVNT}), d'un coup. */
        BLAST,
        /** Trente explosions en colonne : le missile perforant creuse. */
        DRILL,
        /** Le moteur nucleaire de NTM ({@code EntityNukeExplosionMK5}), sur plusieurs secondes. */
        NUKE
    }

    /**
     * Les charges, de la plus legere a la plus lourde. Puissance, resolution,
     * feu et effets sont ceux des missiles de NTM du meme nom.
     */
    enum Warhead {
        GENERIC("missile_generic", "missile explosif", Kind.BLAST, 1, 15.0f, 24, false, 0, 0,
                "composeEffectSmall", 3_000L),
        INCENDIARY("missile_incendiary", "missile incendiaire", Kind.BLAST, 1, 15.0f, 24, true, 0, 0,
                "composeEffectSmall", 3_500L),
        STRONG("missile_strong", "missile explosif lourd", Kind.BLAST, 2, 30.0f, 32, false, 0, 0,
                "composeEffectStandard", 7_000L),
        INCENDIARY_STRONG("missile_incendiary_strong", "missile incendiaire lourd", Kind.BLAST, 2, 30.0f, 32, true,
                0, 25, "composeEffectStandard", 8_000L),
        DRILL("missile_drill", "missile perforant", Kind.DRILL, 3, 10.0f, 24, false, 0, 0, null, 12_000L),
        BURST("missile_burst", "missile de rupture", Kind.BLAST, 3, 50.0f, 48, false, 0, 0,
                "composeEffectLarge", 14_000L),
        INFERNO("missile_inferno", "missile infernal", Kind.BLAST, 3, 50.0f, 48, true, 10, 25,
                "composeEffectLarge", 15_000L),
        TACTICAL("missile_micro", "ogive nucleaire tactique", Kind.NUKE, 4, 0.0f, 0, false, 0, 0, null, 25_000L),
        STRATEGIC("missile_nuclear", "missile nucleaire", Kind.NUKE, 5, 0.0f, 0, false, 0, 0, null, 40_000L);

        final String entity;
        final String label;
        final Kind kind;
        final int tier;
        final float strength;
        final int resolution;
        final boolean fire;
        final int igniteAll;
        final int ignite;
        final String effect;
        final long cost;

        Warhead(String entity, String label, Kind kind, int tier, float strength, int resolution, boolean fire,
                int igniteAll, int ignite, String effect, long cost) {
            this.entity = entity;
            this.label = label;
            this.kind = kind;
            this.tier = tier;
            this.strength = strength;
            this.resolution = resolution;
            this.fire = fire;
            this.igniteAll = igniteAll;
            this.ignite = ignite;
            this.effect = effect;
            this.cost = cost;
        }

        String label() {
            return label;
        }

        /** Une ogive, un missile. */
        boolean feminine() {
            return label.startsWith("ogive");
        }

        /** « un missile explosif », « une ogive nucleaire tactique ». */
        String one() {
            return (feminine() ? "une " : "un ") + label;
        }

        /** « Un missile explosif », en debut de phrase. */
        String sentence() {
            return (feminine() ? "Une " : "Un ") + label;
        }

        /** « du missile explosif », « de l'ogive nucleaire tactique ». */
        String of() {
            return (feminine() ? "de l'" : "du ") + label;
        }

        /** « Missile explosif », pour un titre. */
        String title() {
            return Character.toUpperCase(label.charAt(0)) + label.substring(1);
        }

        boolean nuclear() {
            return kind == Kind.NUKE;
        }

        boolean burns() {
            return fire || igniteAll > 0 || ignite > 0;
        }

        /**
         * Jusqu'ou elle casse, en blocs depuis l'impact. Un rayon de NTM perd
         * 0,225 de puissance tous les 0,3 bloc dans l'air : il porte a 1,73 fois
         * la puissance, moins loin dans la roche.
         */
        int blast() {
            return switch (kind) {
                case BLAST -> Math.round(strength * 1.75f) + 2 + (ignite > 0 ? 4 : 0);
                case DRILL -> 20;
                case NUKE -> nukeRadius(this) + 4;
            };
        }

        /** Jusqu'ou elle descend sous l'impact. */
        int depth() {
            return switch (kind) {
                case BLAST -> blast();
                case DRILL -> 56;
                case NUKE -> nukeRadius(this) + 6;
            };
        }

        /** Le rayon ou il ne fait pas bon rester : les degats aux joueurs. */
        int danger() {
            return switch (kind) {
                case BLAST -> Math.round(strength * 2.0f) + 8;
                case DRILL -> 28;
                case NUKE -> nukeRadius(this) * 2 + 8;
            };
        }

        /** La charge d'en dessous, la moins chere de son rang ; {@code null} sous la premiere. */
        Warhead cheaper() {
            return switch (this) {
                case STRATEGIC -> TACTICAL;
                case TACTICAL -> BURST;
                case INFERNO, BURST, DRILL -> STRONG;
                case INCENDIARY_STRONG, STRONG, INCENDIARY -> GENERIC;
                default -> null;
            };
        }

        static Warhead find(String text) {
            String wanted = text.trim().toLowerCase(java.util.Locale.ROOT);
            for (Warhead warhead : values()) {
                if (warhead.name().toLowerCase(java.util.Locale.ROOT).equals(wanted)
                        || warhead.entity.equals(wanted) || warhead.entity.equals("missile_" + wanted)) {
                    return warhead;
                }
            }
            return null;
        }
    }

    private static final Set<String> WARNED = new HashSet<>();

    private static boolean resolved;
    private static boolean ready;
    private static ClassLoader loader;

    private static Method launch;
    private static Field velocity;
    private static Method impact;
    private static Class<?> missileBase;

    private static Constructor<?> explosionNew;
    private static Method setAllocator;
    private static Method setProcessor;
    private static Method setEntityProcessor;
    private static Method setPlayerProcessor;
    private static Method explode;
    private static Constructor<?> allocatorNew;
    private static Constructor<?> processorNew;
    private static Method noDrop;
    private static Method withEffect;
    private static Constructor<?> fireNew;
    private static Constructor<?> crossNew;
    private static Method rangeMod;
    private static Constructor<?> entitiesNew;
    private static Constructor<?> playersNew;

    private static Method nukeNew;
    private static Method torexNew;
    private static Class<?> effects;
    private static Method igniteAll;
    private static Method igniteFlammable;
    private static Method particles;
    private static Method shrapnels;
    private static Class<?> core;
    private static Class<?> cell;
    private static Class<?> data;

    private NtmArsenal() {
    }

    // -- Ce qui est la ---------------------------------------------------------------------------

    /** NTM est la, et tout ce dont un tir a besoin a ete trouve. */
    static boolean ready() {
        if (!NtmDefenses.present()) {
            return false;
        }
        resolve();
        return ready;
    }

    /** Le type d'entite de ce missile, ou {@code null}. */
    static EntityType<?> type(Warhead warhead) {
        if (!NtmDefenses.present()) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.fromNamespaceAndPath(NtmDefenses.MOD,
                warhead.entity)).orElse(null);
    }

    /** La charge peut-elle partir : NTM pret, son missile connu, le nucleaire permis s'il en est ? */
    static boolean available(Warhead warhead) {
        if (!ready() || type(warhead) == null) {
            return false;
        }
        return !warhead.nuclear() || nuclearAllowed();
    }

    /** Le reglage de NTM {@code disable_nuclear} n'interdit pas les bombes. */
    static boolean nuclearAllowed() {
        Object disabled = param("DISABLE_NUCLEAR");
        return nukeNew != null && !(disabled instanceof Boolean flag && flag);
    }

    /** Le rayon du cratere de cette charge nucleaire : celui de NTM, borne pour ne pas raser une region. */
    static int nukeRadius(Warhead warhead) {
        if (warhead == Warhead.TACTICAL) {
            return clamp(param("FATMAN_RADIUS"), 35, 20, 50);
        }
        return clamp(param("MISSILE_RADIUS"), 100, 40, 80);
    }

    private static int clamp(Object value, int fallback, int min, int max) {
        int radius = value instanceof Number number ? number.intValue() : fallback;
        return Math.max(min, Math.min(max, radius));
    }

    private static Object param(String name) {
        resolve();
        if (data == null) {
            return null;
        }
        try {
            Object param = data.getField(name).get(null);
            return param.getClass().getMethod("get").invoke(param);
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("reglage " + name, error);
            return null;
        }
    }

    /** Un missile de NTM, en vol ou non ? */
    static boolean isMissile(Entity entity) {
        resolve();
        return missileBase != null && missileBase.isInstance(entity);
    }

    /**
     * Un bloc a epargner : un conteneur, une machine (tout ce qui porte une
     * entite de bloc), ou une piece d'un multibloc de NTM (ses cellules n'ont
     * pas d'entite, mais en casser une demolit toute la machine).
     */
    static boolean spared(BlockState state) {
        if (state.hasBlockEntity()) {
            return true;
        }
        if (core == null && cell == null) {
            return false;
        }
        Block block = state.getBlock();
        return core != null && core.isInstance(block) || cell != null && cell.isInstance(block);
    }

    // -- Le vol ----------------------------------------------------------------------------------

    /** Lance un missile de {@code from} vers la colonne {@code target} ; rend le missile, ou {@code null}. */
    static Entity launch(ServerLevel level, Warhead warhead, double x, double y, double z, BlockPos target) {
        EntityType<?> type = type(warhead);
        if (type == null || !ready()) {
            return null;
        }
        Entity missile = type.create(level, EntitySpawnReason.TRIGGERED);
        if (missile == null || !missileBase.isInstance(missile)) {
            return null;
        }
        try {
            launch.invoke(missile, x, y, z, target.getX(), target.getZ());
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("lancement", error);
            return null;
        }
        return level.addFreshEntity(missile) ? missile : null;
    }

    /** Sa vitesse de NTM : son deplacement par tick est {@code deltaMovement * vitesse}. */
    static double velocity(Entity missile) {
        try {
            return velocity.getDouble(missile);
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("vitesse", error);
            return 0.0d;
        }
    }

    // -- L'impact --------------------------------------------------------------------------------

    /**
     * L'auteur des explosions : un marqueur, jamais ajoute au monde, qui refuse
     * a l'explosion les blocs a epargner, et tout ce qui se trouve dans le
     * claim d'un autre joueur que la cible (un voisin n'a rien demande). Les
     * rayons traversent ce qu'il refuse avec leur resistance ordinaire : un
     * coffre ne protege pas ce qu'il y a derriere lui.
     */
    static Entity shield(ServerLevel level, double x, double y, double z, String name, UUID target) {
        Long2BooleanOpenHashMap foreign = new Long2BooleanOpenHashMap();
        ServerLevel world = level;
        UUID victim = target;
        Marker shield = new Marker(EntityTypes.MARKER, level) {
            @Override
            public boolean shouldBlockExplode(Explosion explosion, BlockGetter getter, BlockPos pos, BlockState state,
                                              float power) {
                if (spared(state)) {
                    return false;
                }
                long chunk = StrikeTerrain.key(pos.getX() >> 4, pos.getZ() >> 4);
                if (!foreign.containsKey(chunk)) {
                    ClaimOwner owner = FactionApi.claimAt(world, pos);
                    foreign.put(chunk, owner.claimed() && !owner.enemy() && !owner.ownedBy(victim));
                }
                return !foreign.get(chunk) && super.shouldBlockExplode(explosion, getter, pos, state, power);
            }

            @Override
            public float getBlockExplosionResistance(Explosion explosion, BlockGetter getter, BlockPos pos,
                                                     BlockState state, FluidState fluid, float resistance) {
                return resistance;
            }
        };
        shield.setPos(x, y, z);
        shield.setCustomName(Component.literal(name));
        return shield;
    }

    /**
     * La charge explose ici. Rend l'entite de l'explosion nucleaire, a suivre
     * jusqu'a ce qu'elle disparaisse ; {@code null} pour une explosion d'un
     * coup, ou si rien n'est parti ({@code false} dans {@code done[0]}).
     */
    static Entity detonate(ServerLevel level, Warhead warhead, double x, double y, double z, Entity shield,
                           boolean[] done) {
        done[0] = false;
        if (!ready()) {
            return null;
        }
        try {
            switch (warhead.kind) {
                case BLAST -> {
                    blast(level, x, y, z, warhead.strength, warhead.resolution, warhead.fire, shield, true);
                    effect(level, warhead.effect, x, y, z);
                    BlockPos at = BlockPos.containing(x, y, z);
                    if (warhead.igniteAll > 0 && igniteAll != null) {
                        igniteAll.invoke(null, level, at.getX(), at.getY(), at.getZ(), warhead.igniteAll);
                    }
                    if (warhead.ignite > 0 && igniteFlammable != null) {
                        igniteFlammable.invoke(null, level, at.getX(), at.getY(), at.getZ(), warhead.ignite);
                    }
                    done[0] = true;
                    return null;
                }
                case DRILL -> {
                    for (int i = 0; i < 30; i++) {
                        blast(level, x, y + 2.0d - i, z, warhead.strength, warhead.resolution, false, shield, false);
                    }
                    if (particles != null) {
                        particles.invoke(null, level, x, y, z, 25);
                    }
                    if (shrapnels != null) {
                        shrapnels.invoke(null, level, x, y, z, 12);
                    }
                    done[0] = true;
                    return null;
                }
                default -> {
                    int radius = nukeRadius(warhead);
                    Entity nuke = (Entity) nukeNew.invoke(null, level, radius, x, y, z);
                    if (nuke == null || !level.addFreshEntity(nuke)) {
                        return null;
                    }
                    if (torexNew != null) {
                        torexNew.invoke(null, level, x, y, z, (float) radius);
                    }
                    done[0] = true;
                    return nuke;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("explosion " + warhead.entity, error);
            return null;
        }
    }

    /** Une explosion de NTM, comme {@code EntityMissileBaseNT.explodeStandard}, sans rien laisser tomber. */
    private static void blast(Level level, double x, double y, double z, float strength, int resolution, boolean fire,
                              Entity shield, boolean cross) throws ReflectiveOperationException {
        Object processor = processorNew.newInstance();
        noDrop.invoke(processor);
        if (fire && fireNew != null) {
            withEffect.invoke(processor, fireNew.newInstance());
        }
        Object explosion = explosionNew.newInstance(level, x, y, z, strength, shield);
        setAllocator.invoke(explosion, allocatorNew.newInstance(resolution));
        setProcessor.invoke(explosion, processor);
        Object entities = cross ? rangeMod.invoke(crossNew.newInstance(7.5d), 2.0f) : entitiesNew.newInstance();
        setEntityProcessor.invoke(explosion, entities);
        setPlayerProcessor.invoke(explosion, playersNew.newInstance());
        explode.invoke(explosion);
    }

    private static void effect(Level level, String name, double x, double y, double z) {
        if (name == null || effects == null) {
            return;
        }
        try {
            effects.getMethod(name, Level.class, double.class, double.class, double.class).invoke(null, level, x, y, z);
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("effet " + name, error);
        }
    }

    /** Le dernier recours, si le tir ne peut partir d'ici : l'impact de NTM lui-meme. */
    static boolean impactAsNtm(Entity missile, BlockHitResult hit) {
        if (impact == null) {
            return false;
        }
        try {
            impact.invoke(missile, hit);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("impact", error);
            return false;
        }
    }

    // -- La reflexion ------------------------------------------------------------------------------

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!NtmDefenses.present()) {
            return;
        }
        try {
            loader = NtmArsenal.class.getClassLoader();
            missileBase = load("com.hbm.entity.missile.EntityMissileBaseNT");
            launch = missileBase.getMethod("launch", double.class, double.class, double.class, int.class, int.class);
            velocity = missileBase.getField("velocity");
            impact = missileBase.getMethod("onMissileImpact", BlockHitResult.class);

            Class<?> explosion = load("com.hbm.explosion.vanillant.ExplosionVNT");
            Class<?> allocatorType = load("com.hbm.explosion.vanillant.interfaces.IBlockAllocator");
            Class<?> processorType = load("com.hbm.explosion.vanillant.interfaces.IBlockProcessor");
            Class<?> entityType = load("com.hbm.explosion.vanillant.interfaces.IEntityProcessor");
            Class<?> playerType = load("com.hbm.explosion.vanillant.interfaces.IPlayerProcessor");
            Class<?> mutatorType = load("com.hbm.explosion.vanillant.interfaces.IBlockMutator");
            explosionNew = explosion.getConstructor(Level.class, double.class, double.class, double.class,
                    float.class, Entity.class);
            setAllocator = explosion.getMethod("setBlockAllocator", allocatorType);
            setProcessor = explosion.getMethod("setBlockProcessor", processorType);
            setEntityProcessor = explosion.getMethod("setEntityProcessor", entityType);
            setPlayerProcessor = explosion.getMethod("setPlayerProcessor", playerType);
            explode = explosion.getMethod("explode");

            String standard = "com.hbm.explosion.vanillant.standard.";
            allocatorNew = load(standard + "BlockAllocatorStandard").getConstructor(int.class);
            Class<?> processor = load(standard + "BlockProcessorStandard");
            processorNew = processor.getConstructor();
            noDrop = processor.getMethod("setNoDrop");
            withEffect = processor.getMethod("withBlockEffect", mutatorType);
            Class<?> cross = load(standard + "EntityProcessorCross");
            crossNew = cross.getConstructor(double.class);
            rangeMod = cross.getMethod("withRangeMod", float.class);
            entitiesNew = load(standard + "EntityProcessorStandard").getConstructor();
            playersNew = load(standard + "PlayerProcessorStandard").getConstructor();
            ready = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn("missiles", error);
            ready = false;
            return;
        }
        // Le reste est facultatif : sans lui, pas de feu, d'effets, de nucleaire ou de multiblocs epargnes.
        fireNew = constructor("com.hbm.explosion.vanillant.standard.BlockMutatorFire");
        try {
            nukeNew = load("com.hbm.entity.logic.EntityNukeExplosionMK5").getMethod("statFacNoRad", Level.class,
                    int.class, double.class, double.class, double.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn("nucleaire", error);
        }
        torexNew = method("com.hbm.entity.effect.EntityNukeTorex", "statFac", Level.class, double.class,
                double.class, double.class, float.class);
        effects = optional("com.hbm.particle.helper.ExplosionCreator");
        igniteAll = method("com.hbm.explosion.ExplosionChaos", "igniteAllBlocks", ServerLevel.class, int.class,
                int.class, int.class, int.class);
        igniteFlammable = method("com.hbm.explosion.ExplosionChaos", "igniteFlammableBlocks", ServerLevel.class,
                int.class, int.class, int.class, int.class);
        particles = method("com.hbm.explosion.ExplosionLarge", "spawnParticles", Level.class, double.class,
                double.class, double.class, int.class);
        shrapnels = method("com.hbm.explosion.ExplosionLarge", "spawnShrapnels", Level.class, double.class,
                double.class, double.class, int.class);
        core = optional("com.hbm.blocks.multiblock.BlockMultiblockCore");
        cell = optional("com.hbm.blocks.multiblock.BlockMultiblockCell");
        data = optional("com.hbm.data.ExplosionData");
        DoHy.LOGGER.info("Guerre : missiles de NTM prets (nucleaire {}, multiblocs {})", nukeNew != null,
                core != null && cell != null);
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException missing) {
            return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
        }
    }

    private static Class<?> optional(String name) {
        try {
            return load(name);
        } catch (ClassNotFoundException | LinkageError error) {
            warn(name, error);
            return null;
        }
    }

    private static Constructor<?> constructor(String name) {
        try {
            return load(name).getConstructor();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(name, error);
            return null;
        }
    }

    private static Method method(String owner, String name, Class<?>... parameters) {
        try {
            return load(owner).getMethod(name, parameters);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(owner + "." + name, error);
            return null;
        }
    }

    private static void warn(String what, Throwable error) {
        if (WARNED.add(what)) {
            DoHy.LOGGER.warn("Guerre : NTM ne repond pas comme prevu ({}) : {}", what, error.toString());
        }
    }
}
