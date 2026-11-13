package fr.atraxterra.dohy.faction.base;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les blocs et les defenses de NTM NEXT dans les bases ennemies, sans en
 * dependre a la compilation : tout passe par le registre (les blocs, par leur
 * nom) et par reflexion (les multiblocs et les tourelles). Sans le mod, rien
 * n'est pose et chaque appel rend {@code false} ou le bloc de remplacement.
 *
 * <p><b>Les multiblocs</b> (tourelles, radars, trappes de silo, portes
 * blindees) se posent comme le fait NTM quand un joueur les pose : le bloc
 * coeur, oriente, puis {@code BlockMultiblockCore.fillSpace(level, coeur,
 * orientation)} qui remplit leurs cellules. On verifie d'abord la place avec
 * {@code placementClear}.
 *
 * <p><b>Les tourelles</b> ({@code BlockEntityTurretBaseNT}) s'arment par leurs
 * champs publics : allumees ({@code isOn}), ne visant que les joueurs
 * ({@code targetPlayers} ; ni les mobs — les gardes de la base en sont —, ni
 * les betes, ni les machines), batterie pleine ({@code setPower(getMaxPower())})
 * et les cases 1 a 9 pleines de leur premiere munition
 * ({@code getAmmoTypesForDisplay}). Elles ignorent d'elles-memes les joueurs en
 * creatif ou en spectateur. {@link #refill} les remet a plein : tant que la
 * base tient, leurs munitions ne s'epuisent pas.
 */
public final class NtmDefenses {

    public static final String MOD = "hbm";

    private static final Map<String, Block> BLOCKS = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static final Map<Class<?>, Turret> TURRETS = new HashMap<>();

    private NtmDefenses() {
    }

    public static boolean present() {
        return ModList.get().isLoaded(MOD);
    }

    /** Un bloc de NTM par son nom, ou {@code null}. */
    public static Block block(String id) {
        if (!present()) {
            return null;
        }
        return BLOCKS.computeIfAbsent(id, key -> BuiltInRegistries.BLOCK
                .getOptional(Identifier.fromNamespaceAndPath(MOD, key)).orElse(null));
    }

    /** L'etat par defaut d'un bloc de NTM, ou {@code fallback}. */
    public static BlockState state(String id, BlockState fallback) {
        Block block = block(id);
        return block == null ? fallback : block.defaultBlockState();
    }

    /** Oriente un etat de bloc s'il a une propriete {@code facing} qui accepte cette direction. */
    @SuppressWarnings("unchecked")
    public static BlockState facing(BlockState state, Direction facing) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty("facing");
        if (property instanceof EnumProperty<?> enumProperty && enumProperty.getValueClass() == Direction.class
                && enumProperty.getPossibleValues().contains(facing)) {
            return state.setValue((EnumProperty<Direction>) enumProperty, facing);
        }
        return state;
    }

    /**
     * Pose un multibloc de NTM, son coeur en {@code core}, tourne vers
     * {@code facing}. Rend vrai s'il est pose ; faux sans NTM, sans la place,
     * ou si NTM a change sa facon de faire.
     */
    public static boolean multiblock(ServerLevel level, BlockPos core, String id, Direction facing) {
        Block block = block(id);
        if (block == null) {
            return false;
        }
        try {
            Method clear = block.getClass().getMethod("placementClear", Level.class, BlockPos.class, Direction.class);
            if (!(Boolean) clear.invoke(block, level, core, facing)) {
                return false;
            }
            level.setBlock(core, facing(block.defaultBlockState(), facing), Block.UPDATE_ALL);
            Method fill = block.getClass().getMethod("fillSpace", LevelAccessor.class, BlockPos.class, Direction.class);
            fill.invoke(block, level, core, facing);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("multibloc " + id, error);
            return false;
        }
    }

    /** Une tourelle de NTM ? (son entite, au coeur du multibloc) */
    public static boolean isTurret(BlockEntity entity) {
        return entity != null && turret(entity.getClass()) != null;
    }

    /** Pose et arme une tourelle ; rend vrai si elle est en place et armee. */
    public static boolean turret(ServerLevel level, BlockPos core, String id, Direction facing) {
        if (!multiblock(level, core, id, facing)) {
            return false;
        }
        BlockEntity entity = level.getBlockEntity(core);
        if (!isTurret(entity)) {
            return false;
        }
        arm(entity, true);
        return true;
    }

    /**
     * Arme ({@code on}) ou eteint une tourelle : en marche, ne visant que les
     * joueurs, batterie et munitions pleines.
     */
    public static void arm(BlockEntity entity, boolean on) {
        Turret turret = turret(entity.getClass());
        if (turret == null) {
            return;
        }
        try {
            turret.isOn.setBoolean(entity, on);
            turret.targetPlayers.setBoolean(entity, on);
            turret.targetMobs.setBoolean(entity, false);
            turret.targetAnimals.setBoolean(entity, false);
            turret.targetMachines.setBoolean(entity, false);
            if (on) {
                refill(entity);
            }
            entity.setChanged();
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("tourelle " + entity.getClass().getSimpleName(), error);
        }
    }

    /** Remet une tourelle a plein : batterie, et ses neuf cases de munitions. */
    @SuppressWarnings("unchecked")
    public static void refill(BlockEntity entity) {
        Turret turret = turret(entity.getClass());
        if (turret == null) {
            return;
        }
        try {
            long max = (Long) turret.getMaxPower.invoke(entity);
            turret.setPower.invoke(entity, max);
            List<ItemStack> ammo = (List<ItemStack>) turret.getAmmoTypes.invoke(entity);
            if (entity instanceof Container container && ammo != null && !ammo.isEmpty()) {
                ItemStack type = ammo.get(0);
                for (int slot = 1; slot <= 9 && slot < container.getContainerSize(); slot++) {
                    ItemStack here = container.getItem(slot);
                    if (here.isEmpty() || ItemStack.isSameItemSameComponents(here, type)) {
                        container.setItem(slot, type.copyWithCount(type.getMaxStackSize()));
                    }
                }
            }
            entity.setChanged();
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("recharge " + entity.getClass().getSimpleName(), error);
        }
    }

    /** Ce que vise une tourelle en ce moment, pour les journaux et les verifications. */
    public static Object target(BlockEntity entity) {
        Turret turret = turret(entity.getClass());
        if (turret == null || turret.target == null) {
            return null;
        }
        try {
            return turret.target.get(entity);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    /** Les accesseurs d'une classe de tourelle, une fois pour toutes ; {@code null} si ce n'en est pas une. */
    private static Turret turret(Class<?> type) {
        if (!type.getName().startsWith("com.hbm.")) {
            return null;
        }
        return TURRETS.computeIfAbsent(type, NtmDefenses::lookup);
    }

    private static Turret lookup(Class<?> type) {
        try {
            return new Turret(type.getField("isOn"), type.getField("targetPlayers"), type.getField("targetMobs"),
                    type.getField("targetAnimals"), type.getField("targetMachines"), field(type, "target"),
                    type.getMethod("getMaxPower"), type.getMethod("setPower", long.class),
                    type.getMethod("getAmmoTypesForDisplay"));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    private static Field field(Class<?> type, String name) {
        try {
            return type.getField(name);
        } catch (NoSuchFieldException error) {
            return null;
        }
    }

    private record Turret(Field isOn, Field targetPlayers, Field targetMobs, Field targetAnimals, Field targetMachines,
                          Field target, Method getMaxPower, Method setPower, Method getAmmoTypes) {
    }

    private static void warn(String what, Throwable error) {
        if (WARNED.add(what)) {
            DoHy.LOGGER.warn("Bases ennemies : NTM NEXT n'a pas accepte {} ({})", what, error.toString());
        }
    }
}
