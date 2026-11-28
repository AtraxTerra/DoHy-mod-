package fr.atraxterra.dohy.palamod.pet;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.palamod.alchemist.PalaWoods;
import fr.atraxterra.dohy.palamod.alchemist.Sap;
import fr.atraxterra.dohy.palamod.miner.AreaMining;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Les competences actives (wiki « Java | Competences Actives »), lancees depuis
 * la roue du familier. Chacune a sa recharge ; celles qui agissent « au prochain
 * coup » posent un effet dans {@link PetData}, lu par {@link PetEvents} et les
 * machines.
 *
 * <p>Une active qui ne trouve rien a faire (aucune culture mure, aucun monstre)
 * le dit et ne lance pas sa recharge.
 */
public final class PetActives {

    // Les effets « au prochain coup », gardes avec le familier.
    public static final String LUCKY_MINER = "lucky_miner";
    public static final String ENCHANTED = "enchanted";
    public static final String UNBREAKABLE_UNTIL = "unbreakable_until";
    public static final String UNBREAKABLE_FACTOR = "unbreakable_factor";
    public static final String QUICK_CHANGE = "quick_change";
    public static final String BLESSED_EXPLOSION = "blessed_explosion";
    public static final String REPAIR = "repair";
    public static final String VETERINARIAN = "veterinarian";
    public static final String ELECTRIC_CLOUD = "electric_cloud";

    /** Pioche incassable : soixante secondes. */
    static final long UNBREAKABLE_MS = 60_000L;
    /** Explosion ! : au plus 4 096 blocs, les plus proches d'abord. */
    static final int EXPLOSION_CAP = 4096;
    /** Hache gravitationnelle : au plus 1 024 buches. */
    static final int AXE_CAP = 1024;

    private PetActives() {
    }

    /** Lance une active posee ; dit pourquoi si elle ne part pas. */
    public static void activate(ServerPlayer player, PetSkill skill) {
        PetData data = PetApi.data(player);
        if (!data.has()) {
            tell(player, "Tu n'as pas de familier.", ChatFormatting.RED);
            return;
        }
        if (!skill.active() || !data.isEquipped(skill)) {
            tell(player, skill.label() + " n'est pas posee sur ton familier.", ChatFormatting.RED);
            return;
        }
        if (data.level() < skill.unlock()) {
            tell(player, skill.label() + " se debloque au niveau " + skill.unlock() + ".", ChatFormatting.RED);
            return;
        }
        long wait = PetApi.cooldownRemaining(player, skill);
        if (wait > 0L) {
            tell(player, skill.label() + " se recharge encore " + PetApi.duration(wait) + ".", ChatFormatting.GRAY);
            return;
        }
        double value = PetApi.value(player, skill);
        if (value <= 0.0d) {
            tell(player, data.familiar().label() + " n'a plus de bonheur : ses competences ne font plus rien.",
                    ChatFormatting.RED);
            return;
        }
        String done = run(player, data, skill, value);
        if (done == null) {
            return;
        }
        PetApi.startCooldown(player, skill);
        PetApi.syncLook(player);
        tell(player, skill.label() + " : " + done, ChatFormatting.LIGHT_PURPLE);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.8f, 1.3f);
    }

    /** L'effet ; rend le message de reussite, ou {@code null} si rien n'a ete fait. */
    private static String run(ServerPlayer player, PetData data, PetSkill skill, double value) {
        ServerLevel level = player.level();
        int radius = Math.max(1, (int) Math.floor(value));
        return switch (skill) {
            case TWIRLING_SCYTHE -> {
                int count = harvest(player, level, radius);
                yield none(player, count, "Aucune culture mure autour de toi.", count + " cultures recoltees et "
                        + "replantees.");
            }
            case GRAVITATIONAL_AXE -> {
                int count = fellLogs(player, level, radius);
                yield none(player, count, "Aucune buche de PalaMod autour de toi.", count + " buches abattues.");
            }
            case LUCKY_MINER -> {
                data.setEffect(LUCKY_MINER, value);
                yield "le prochain minerai a " + skill.format(value) + " de chances de s'ameliorer.";
            }
            case ELECTRIC_CLOUD -> {
                int strikes = Math.max(1, (int) Math.round(value));
                data.setEffect(ELECTRIC_CLOUD, strikes);
                yield strikes + " eclairs vont tomber autour de toi. Casque en fer ou en or et fiole vide en main : "
                        + "une potion de foudre a chaque eclair, avec de la chance.";
            }
            case MONSTER_SLAYER -> {
                int count = slay(player, level, value);
                yield none(player, count, "Aucun monstre autour de toi.", count + " monstres frappes.");
            }
            case POCKET_HAPPINESS -> {
                int gained = (int) Math.round(value);
                PetApi.addHappiness(player, gained);
                yield "+" + gained + " de bonheur (" + data.happiness() + "/" + PetApi.maxHappiness(data.level())
                        + ").";
            }
            case ENCHANTED -> {
                data.setEffect(ENCHANTED, value);
                yield "ta prochaine enclume ou gravure coute " + skill.format(value) + " de moins.";
            }
            case UNBREAKABLE_PICKAXE -> {
                data.setEffect(UNBREAKABLE_UNTIL, System.currentTimeMillis() + UNBREAKABLE_MS);
                data.setEffect(UNBREAKABLE_FACTOR, value);
                yield "l'obsidienne se mine " + skill.format(value) + " plus vite pendant 60 s.";
            }
            case EARTH_BLESSING -> {
                int count = bless(level, player.blockPosition(), radius);
                yield none(player, count, "Aucune plante a faire pousser autour de toi.", count
                        + " plantes ont pousse d'un coup.");
            }
            case QUICK_CHANGE -> {
                data.setEffect(QUICK_CHANGE, value);
                yield "ton prochain changement de competence attend " + skill.format(value) + " de moins.";
            }
            case EXPLOSION -> {
                int count = explode(player, level, value);
                yield none(player, count, "Pas de roche a faire sauter ici.", count + " blocs de roche ont saute.");
            }
            case BLESSED_EXPLOSION -> {
                data.setEffect(BLESSED_EXPLOSION, value);
                yield "le prochain spawner que fait sauter un creeper pres de toi a " + skill.format(value)
                        + " de chances d'en donner un second.";
            }
            case REPAIR -> {
                data.setEffect(REPAIR, value);
                yield "le prochain objet qui casse a " + skill.format(value) + " de chances de se reparer.";
            }
            case SKILLFUL_VETERINARIAN -> {
                data.setEffect(VETERINARIAN, 1.0d);
                yield "le prochain bandage soigne ta monture d'un coup.";
            }
            case BEDROCK_DRILL -> {
                int count = drill(player, level, Math.max(1, (int) Math.round(value)));
                yield none(player, count, "Pas de bedrock a retirer ici.", count + " blocs de bedrock retires.");
            }
            default -> null;
        };
    }

    private static String none(ServerPlayer player, int count, String empty, String done) {
        if (count > 0) {
            return done;
        }
        tell(player, empty, ChatFormatting.GRAY);
        return null;
    }

    static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[Familier] " + text).withStyle(color));
    }

    /** Un bloc ou ce joueur n'a rien a faire : le claim d'un autre. */
    static boolean foreign(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ClaimOwner owner = FactionApi.claimAt(level, pos);
        return owner.claimed() && !owner.ownedBy(player.getUUID());
    }

    // -- Faux tournoyante ({@code AutoHarvestSkill}) --------------------------------------------------

    /**
     * Les cultures mures et les melons et citrouilles autour du joueur passent par
     * la casse normale (XP de Fermier, protections), puis la culture est replantee ;
     * la recolte va dans l'inventaire.
     */
    static int harvest(ServerPlayer player, ServerLevel level, int radius) {
        BlockPos center = player.blockPosition();
        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-radius, -1, -radius),
                center.offset(radius, 1, radius))) {
            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
                    || state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
                targets.add(cursor.immutable());
            }
        }
        if (targets.isEmpty()) {
            return 0;
        }
        int[] done = {0};
        PetEvents.collect(player, () -> AreaMining.withoutArea(() -> {
            for (BlockPos pos : targets) {
                BlockState state = level.getBlockState(pos);
                if (!player.gameMode.destroyBlock(pos)) {
                    continue;
                }
                done[0]++;
                if (state.getBlock() instanceof CropBlock crop && level.getBlockState(pos).isAir()) {
                    BlockState young = crop.getStateForAge(0);
                    if (young.canSurvive(level, pos)) {
                        level.setBlock(pos, young, Block.UPDATE_ALL);
                    }
                }
            }
        }));
        return done[0];
    }

    // -- Hache gravitationnelle -------------------------------------------------------------------

    private static Set<Block> palaLogs() {
        Set<Block> logs = new HashSet<>();
        for (Sap sap : Sap.values()) {
            logs.add(PalaWoods.log(sap));
            logs.add(PalaWoods.shiny(sap));
        }
        return logs;
    }

    /** Les buches de PalaMod autour du joueur, les plus proches d'abord. */
    static int fellLogs(ServerPlayer player, ServerLevel level, int radius) {
        Set<Block> logs = palaLogs();
        BlockPos center = player.blockPosition();
        int up = Math.min(24, radius * 2);
        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-radius, -2, -radius),
                center.offset(radius, up, radius))) {
            if (logs.contains(level.getBlockState(cursor).getBlock())) {
                targets.add(cursor.immutable());
            }
        }
        targets.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        int[] done = {0};
        PetEvents.collect(player, () -> AreaMining.withoutArea(() -> {
            for (BlockPos pos : targets) {
                if (done[0] >= AXE_CAP) {
                    return;
                }
                if (player.gameMode.destroyBlock(pos)) {
                    done[0]++;
                }
            }
        }));
        return done[0];
    }

    // -- Tueur de monstres --------------------------------------------------------------------------

    /** Neuf coeurs (18 points) a chaque monstre du rayon, portes par le joueur. */
    static int slay(ServerPlayer player, ServerLevel level, double radius) {
        int count = 0;
        AABB box = player.getBoundingBox().inflate(radius);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box,
                entity -> entity instanceof Enemy && entity.isAlive() && entity.distanceTo(player) <= radius)) {
            if (mob.hurtServer(level, player.damageSources().playerAttack(player), 18.0f)) {
                count++;
                level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY(1.0d), mob.getZ(), 8, 0.3d, 0.3d, 0.3d,
                        0.1d);
            }
        }
        return count;
    }

    // -- Benediction de la terre ({@code BlessedGrowthSkill}) ------------------------------------------

    /** Cultures et tiges du rayon : a maturite d'un coup. */
    static int bless(ServerLevel level, BlockPos center, int radius) {
        int count = 0;
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-radius, -1, -radius),
                center.offset(radius, 1, radius))) {
            BlockState state = level.getBlockState(cursor);
            BlockState grown = null;
            if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
                grown = crop.getStateForAge(crop.getMaxAge());
            } else if (state.getBlock() instanceof StemBlock && state.getValue(StemBlock.AGE) < StemBlock.MAX_AGE) {
                grown = state.setValue(StemBlock.AGE, StemBlock.MAX_AGE);
            }
            if (grown != null) {
                level.setBlock(cursor, grown, Block.UPDATE_ALL);
                level.levelEvent(2005, cursor, 0);
                count++;
            }
        }
        return count;
    }

    // -- Explosion ! ----------------------------------------------------------------------------------

    static boolean rock(BlockState state) {
        return (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE)) && !state.is(Tags.Blocks.ORES);
    }

    /**
     * La roche du rayon saute, sans rien laisser ; les minerais restent. Rien dans
     * le claim d'un autre (wiki : pas en faction).
     */
    static int explode(ServerPlayer player, ServerLevel level, double radius) {
        BlockPos center = player.blockPosition();
        int r = (int) Math.ceil(radius);
        double max = radius * radius;
        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (cursor.distSqr(center) <= max && rock(level.getBlockState(cursor))) {
                targets.add(cursor.immutable());
            }
        }
        targets.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        int count = 0;
        for (BlockPos pos : targets) {
            if (count >= EXPLOSION_CAP) {
                break;
            }
            if (!foreign(player, level, pos) && level.removeBlock(pos, false)) {
                count++;
            }
        }
        if (count > 0) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 1,
                    0.0d, 0.0d, 0.0d, 0.0d);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                    1.0f, 1.0f);
        }
        return count;
    }

    // -- Foreuse a bedrock ----------------------------------------------------------------------------

    /**
     * La bedrock d'un carre de {@code size} blocs, depuis le joueur vers le sud-est,
     * sur toute la hauteur sauf la couche du fond.
     */
    static int drill(ServerPlayer player, ServerLevel level, int size) {
        BlockPos at = player.blockPosition();
        int bottom = level.getMinY();
        int count = 0;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                for (int y = bottom + 1; y < level.getMaxY(); y++) {
                    BlockPos pos = new BlockPos(at.getX() + dx, y, at.getZ() + dz);
                    if (level.getBlockState(pos).is(Blocks.BEDROCK) && !foreign(player, level, pos)
                            && level.removeBlock(pos, false)) {
                        count++;
                    }
                }
            }
        }
        if (count > 0) {
            level.playSound(null, at, SoundEvents.ANVIL_DESTROY, SoundSource.PLAYERS, 0.8f, 0.7f);
        }
        return count;
    }

    // -- Les effets « au prochain coup » ------------------------------------------------------------

    /** Lit et consomme un effet ; 0 sans effet. */
    public static double consume(ServerPlayer player, String key) {
        PetData data = PetApi.data(player);
        double value = data.effect(key);
        if (value != 0.0d) {
            data.setEffect(key, 0.0d);
        }
        return value;
    }

    public static double peek(ServerPlayer player, String key) {
        return PetApi.data(player).effect(key);
    }

    /** Un eclair du nuage electrique ({@code ElectricCloudSkill}) : 15 % de chances de remplir une fiole. */
    static void strike(ServerPlayer player) {
        ServerLevel level = player.level();
        PetData data = PetApi.data(player);
        double left = data.effect(ELECTRIC_CLOUD);
        if (left <= 0.0d) {
            return;
        }
        data.setEffect(ELECTRIC_CLOUD, left - 1.0d);
        double x = player.getX() + player.getRandom().nextInt(3) - 1;
        double z = player.getZ() + player.getRandom().nextInt(3) - 1;
        net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(
                level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.snapTo(x, player.getY(), z, 0.0f, 0.0f);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        ItemStack helmet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        ItemStack held = player.getMainHandItem();
        if (player.getRandom().nextDouble() * 100.0d >= 15.0d
                || !(helmet.is(net.minecraft.world.item.Items.IRON_HELMET)
                || helmet.is(net.minecraft.world.item.Items.GOLDEN_HELMET))
                || !held.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
            return;
        }
        held.shrink(1);
        ItemStack potion = new ItemStack(fr.atraxterra.dohy.palamod.alchemist.PalaAlchemist.LIGHTNING_POTION.get());
        if (!player.getInventory().add(potion)) {
            player.drop(potion, false);
        }
        fr.atraxterra.dohy.palamod.alchemist.AlchemistXp.grant(player,
                fr.atraxterra.dohy.palamod.alchemist.AlchemistEvents.LIGHTNING_XP, 0);
        tell(player, "L'eclair remplit ta fiole : une potion de foudre !", ChatFormatting.YELLOW);
    }
}
