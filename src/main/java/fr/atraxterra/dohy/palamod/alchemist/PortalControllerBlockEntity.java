package fr.atraxterra.dohy.palamod.alchemist;

import java.util.Set;
import java.util.HashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Le portail ({@code TileEntityAlchemistPortalController},
 * {@code BlockPortalAlchemist.func_149670_a}).
 *
 * <p><b>Le cadre</b> : un anneau de 5 x 5 a plat, sur la hauteur du controleur
 * qui en occupe le milieu d'un cote — les quatre coins dans le bloc du metal
 * (le bloc d'angle pour l'endium), les onze autres cases dans le bois brillant
 * de la seve. Cadre complet et cle inseree, le trou de 3 x 3 s'emplit de la
 * surface du portail.
 *
 * <p><b>La seve</b> : une fiole de la bonne seve jetee sur le portail y verse
 * ses unites, un tank aussi ; elles s'ajoutent a la cle. Chaque fois que la cle
 * atteint le seuil, le portail rend un lingot. Le joueur credite est le plus
 * proche, a 4,5 blocs, et il doit avoir le niveau du bloc de portail. Le
 * portail d'endium demande en plus deux pollens d'endium par pepite, jetes de
 * la meme facon.
 *
 * <p><b>Ecarts</b>, tous des corrections :
 * <ul>
 *   <li>les fioles et les tanks reviennent vides au joueur ; l'original les
 *       detruisait avec leur contenant ;</li>
 *   <li>sans cle, l'original vidait le trou de tout ce qu'il contenait, a
 *       chaque tick ; ici seule la surface du portail disparait, et le
 *       portail ne s'ouvre que sur un trou vide ;</li>
 *   <li>un coin du cadre n'etait pas verifie dans une orientation ; les
 *       quatre le sont ;</li>
 *   <li>endium : un tank donnait des pepites sans pollen, une fiole une seule
 *       pepite par lancer et detruisait la seve en trop, et les pollens se
 *       perdaient au redemarrage. Ici : deux pollens par pepite, toujours, et
 *       ils sont sauvegardes.</li>
 * </ul>
 */
public class PortalControllerBlockEntity extends BlockEntity {

    private static final int CHECK_PERIOD = 10;
    private static final double PLAYER_RANGE = 4.5d;
    /** Pas plus d'objets rendus d'un coup : un tank de paladium en vaut 135. */
    private static final int MAX_OUTPUTS = 4096;

    private ItemStack key = ItemStack.EMPTY;
    private int pollen;
    /** Le centre du trou quand le portail est ouvert, {@code null} sinon. */
    private BlockPos open;
    private int timer;
    /** Les objets refuses deja signales : on ne le dit qu'une fois par objet. */
    private final Set<Integer> hinted = new HashSet<>();

    public PortalControllerBlockEntity(BlockPos pos, BlockState state) {
        super(PalaAlchemist.PORTAL_CONTROLLER.get(), pos, state);
    }

    private PortalTier tier() {
        return ((PortalControllerBlock) getBlockState().getBlock()).tier();
    }

    private int total() {
        return key.getOrDefault(PalaAlchemist.KEY_SAP.get(), 0);
    }

    boolean insert(ServerPlayer player, ItemStack stack) {
        if (!key.isEmpty()) {
            return false;
        }
        key = stack.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        timer = CHECK_PERIOD;
        AlchemistXp.actionBar(player, status());
        return true;
    }

    void removeKey(ServerPlayer player) {
        if (key.isEmpty()) {
            AlchemistXp.actionBar(player, Component.translatable("message.palamod.portal.no_key")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        ItemStack given = key;
        key = ItemStack.EMPTY;
        setChanged();
        close();
        if (!player.getInventory().add(given)) {
            player.drop(given, false);
        }
    }

    private Component status() {
        int pollenNeeded = tier().pollen();
        Component line = Component.translatable("message.palamod.portal.sap", total(), tier().required());
        if (pollenNeeded > 0) {
            line = Component.translatable("message.palamod.portal.sap_pollen", total(), tier().required(),
                    pollen, pollenNeeded);
        }
        return line.copy().withStyle(ChatFormatting.YELLOW);
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (++timer >= CHECK_PERIOD) {
            timer = 0;
            BlockPos center = key.isEmpty() ? null : findCenter(server);
            if (center == null || (open == null && !canOpen(server, center))) {
                close();
            } else {
                openAt(server, center);
            }
        }
        if (open != null) {
            absorb(server);
        }
    }

    /** Le centre du trou si le cadre est complet dans l'une des quatre orientations. */
    private BlockPos findCenter(Level level) {
        PortalTier tier = tier();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos center = worldPosition.relative(direction, 2);
            if (isFrame(level, center, tier)) {
                return center;
            }
        }
        return null;
    }

    private boolean isFrame(Level level, BlockPos center, PortalTier tier) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) {
                    continue;
                }
                BlockPos cell = center.offset(dx, 0, dz);
                if (cell.equals(worldPosition)) {
                    continue;
                }
                Block expected = Math.abs(dx) == 2 && Math.abs(dz) == 2 ? tier.corner() : tier.frame();
                if (!level.getBlockState(cell).is(expected)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canOpen(Level level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockState state = level.getBlockState(center.offset(dx, 0, dz));
                if (!state.isAir() && !state.is(PalaAlchemist.ALCHEMIST_PORTAL.get())) {
                    return false;
                }
            }
        }
        return true;
    }

    private void openAt(Level level, BlockPos center) {
        if (open != null && !open.equals(center)) {
            close();
        }
        BlockState surface = PalaAlchemist.ALCHEMIST_PORTAL.get().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos cell = center.offset(dx, 0, dz);
                if (level.getBlockState(cell).isAir()) {
                    level.setBlock(cell, surface, Block.UPDATE_ALL);
                }
            }
        }
        if (open == null) {
            open = center;
            setChanged();
        }
    }

    private void close() {
        if (open == null || level == null) {
            open = null;
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos cell = open.offset(dx, 0, dz);
                if (level.getBlockState(cell).is(PalaAlchemist.ALCHEMIST_PORTAL.get())) {
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        open = null;
        setChanged();
    }

    /** La surface tient-elle a un portail ouvert ? Sinon elle disparait. */
    static boolean holds(Level level, BlockPos surface) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (level.getBlockEntity(surface.offset(dx, 0, dz)) instanceof PortalControllerBlockEntity controller
                        && controller.open != null && controller.open.getY() == surface.getY()
                        && Math.abs(controller.open.getX() - surface.getX()) <= 1
                        && Math.abs(controller.open.getZ() - surface.getZ()) <= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private void absorb(ServerLevel server) {
        AABB hole = new AABB(open).inflate(1.0d, 0.0d, 1.0d).expandTowards(0.0d, 0.75d, 0.0d);
        List<ItemEntity> items = server.getEntitiesOfClass(ItemEntity.class, hole, ItemEntity::isAlive);
        if (items.isEmpty()) {
            return;
        }
        ServerPlayer player = nearestPlayer(server, hole);
        if (player == null) {
            return;
        }
        PortalTier tier = tier();
        int added = 0;
        int pollenAdded = 0;
        for (ItemEntity entity : items) {
            ItemStack stack = entity.getItem();
            int count = stack.getCount();
            if (stack.is(PalaAlchemist.ENDIUM_POLLEN.get()) && tier.pollen() > 0) {
                pollenAdded += count;
                entity.discard();
                continue;
            }
            int units = unitsOf(stack, tier);
            if (units <= 0) {
                // Rien ne se passait, sans un mot, avec une fiole vide ou la
                // mauvaise seve : le portail semblait casse.
                if (hinted.size() > 256) {
                    hinted.clear();
                }
                if (hinted.add(entity.getId())) {
                    AlchemistXp.actionBar(player, Component.translatable("message.palamod.portal.wrong_item",
                            tier.sap().displayName()).withStyle(ChatFormatting.GOLD));
                }
                continue;
            }
            if (!AlchemistXp.allows(player, getBlockState().getBlock())) {
                AlchemistXp.refuse(player, getBlockState().getBlock());
                return;
            }
            added += units * count;
            give(player, new ItemStack(stack.getItem(), count));
            entity.discard();
        }
        if (added == 0 && pollenAdded == 0) {
            return;
        }
        pollen += pollenAdded;
        int total = total() + added;
        int outputs = 0;
        while (total >= tier.required() && pollen >= tier.pollen() && outputs < MAX_OUTPUTS) {
            total -= tier.required();
            pollen -= tier.pollen();
            outputs++;
        }
        key.set(PalaAlchemist.KEY_SAP.get(), total);
        setChanged();
        server.playSound(null, open, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (outputs > 0) {
            int left = outputs;
            while (left > 0) {
                ItemStack result = new ItemStack(tier.output(), Math.min(left, tier.output().getDefaultMaxStackSize()));
                left -= result.getCount();
                server.addFreshEntity(new ItemEntity(server, open.getX() + 0.5d, open.getY() + 1.0d,
                        open.getZ() + 0.5d, result, 0.0d, 0.2d, 0.0d));
            }
            AlchemistXp.grant(player, tier.xp() * outputs, tier.xpLevel());
            server.playSound(null, open, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
        AlchemistXp.actionBar(player, status());
    }

    /** Les unites qu'apporte un objet jete : fiole ou tank de la seve du portail. */
    private static int unitsOf(ItemStack stack, PortalTier tier) {
        FlaskSap flask = stack.get(PalaAlchemist.FLASK_SAP.get());
        if (flask != null && stack.is(PalaAlchemist.FLASK.get())) {
            return flask.type() == tier.sap() ? flask.units() : 0;
        }
        TankSap tank = stack.get(PalaAlchemist.TANK_SAP.get());
        if (tank != null && stack.getItem() instanceof TankItem) {
            return tank.type() == tier.sap() ? tank.amount() : 0;
        }
        return 0;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static ServerPlayer nearestPlayer(ServerLevel server, AABB hole) {
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class,
                hole.inflate(PLAYER_RANGE, 2.0d, PLAYER_RANGE), ServerPlayer::isAlive)) {
            double distance = player.distanceToSqr(hole.getCenter());
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    /** La cle tombe et le portail se referme quand le controleur disparait. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        close();
        if (level != null && !key.isEmpty()) {
            Block.popResource(level, pos, key);
            key = ItemStack.EMPTY;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        key = input.read("Key", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        pollen = input.getIntOr("Pollen", 0);
        open = input.read("Open", BlockPos.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!key.isEmpty()) {
            output.store("Key", ItemStack.CODEC, key);
        }
        output.putInt("Pollen", pollen);
        if (open != null) {
            output.store("Open", BlockPos.CODEC, open);
        }
    }
}
