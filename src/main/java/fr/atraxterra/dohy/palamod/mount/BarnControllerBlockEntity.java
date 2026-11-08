package fr.atraxterra.dohy.palamod.mount;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Le gestionnaire d'enclos ({@code TileEntityBarnController}) : les gemmes
 * deposees, la taille du dernier enclos valide et la presence d'une couveuse.
 *
 * <p>Capacite selon la taille du carre de foin : 4, 8, 14, 20 et 30 montures
 * de 3x3 a 7x7, une seule tant que l'enclos n'a pas ete valide.
 *
 * <p><b>Soins</b> : une couveuse dans l'enclos, deux montures au plus a la
 * fois, un bandage par tranche de 1 h 30 de temps reel, et 10 % des PV max
 * par bandage credites d'un coup a la fin du soin.
 *
 * <p><b>Ecarts</b> :
 * <ul>
 *   <li>les gemmes ne sont plus 21 cases : un enclos 7x7 tient bien ses 30
 *       montures, que l'original annoncait sans pouvoir les loger ;</li>
 *   <li>la taille est sauvegardee : apres un rechargement, l'original
 *       retombait a une monture tant qu'on n'avait pas reclique ;</li>
 *   <li>chaque action verifie que la gemme est a soi : l'original laissait
 *       equiper, retirer ou soigner la monture d'un autre ;</li>
 *   <li>casser le gestionnaire ne credite que les tranches de soin
 *       ecoulees des soins <i>en cours</i> — l'original soignait entierement
 *       toute gemme ayant deja ete soignee ;</li>
 *   <li>une gemme rendue a un inventaire plein tombe a terre au lieu de
 *       disparaitre.</li>
 * </ul>
 */
public class BarnControllerBlockEntity extends BlockEntity {

    public static final int MAX_IN_CARE = 2;
    /** {@code 5 400 000} ms : une heure et demie par bandage. */
    public static final long CARE_MS = 5_400_000L;
    /** {@code isUseableByPlayer} : 8 blocs. */
    private static final double RANGE = 8.0d;

    private final List<MountGem> gems = new ArrayList<>();
    private int size = -1;
    private boolean incubator;
    private int timer;

    public BarnControllerBlockEntity(BlockPos pos, BlockState state) {
        super(PalaMount.BARN_ENTITY.get(), pos, state);
    }

    public List<MountGem> gems() {
        return Collections.unmodifiableList(gems);
    }

    public int size() {
        return size;
    }

    public boolean hasIncubator() {
        return incubator;
    }

    /** {@code UIBarn} : 4, 8, 14, 20, 30, ou 1 hors enclos valide. */
    public int capacity() {
        return switch (size) {
            case 3 -> 4;
            case 4 -> 8;
            case 5 -> 14;
            case 6 -> 20;
            case 7 -> 30;
            default -> 1;
        };
    }

    public int inCare() {
        return (int) gems.stream().filter(MountGem::inCare).count();
    }

    public BarnStructure.Result validate() {
        BarnStructure.Result result = BarnStructure.check(level, worldPosition);
        if (result.valid()) {
            size = result.size();
            incubator = result.incubator();
            setChanged();
        }
        return result;
    }

    public boolean inRange(ServerPlayer player) {
        return player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) <= RANGE * RANGE && !isRemoved();
    }

    // -- Soins -------------------------------------------------------------------

    public void serverTick() {
        if (++timer % 20 != 0 || gems.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = 0; i < gems.size(); i++) {
            MountGem gem = gems.get(i);
            if (gem.inCare() && gem.healStart() != -1L && gem.healEnd() <= now) {
                gems.set(i, finishCare(gem, now));
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
    }

    /** Les tranches entieres ecoulees, bornees au nombre de bandages payes. */
    private static MountGem finishCare(MountGem gem, long now) {
        if (gem.healStart() == -1L) {
            return gem.withCare(-1L, -1L);
        }
        long paid = Math.round((gem.healEnd() - gem.healStart()) / (double) CARE_MS);
        long periods = Math.min(paid, Math.max(0L, now - gem.healStart()) / CARE_MS);
        float healed = periods * gem.maxHealth() * 0.1f;
        return gem.withDamage(Math.max(0.0f, gem.damage() - healed)).withCare(-1L, -1L);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (MountGem gem : gems) {
            MountGem dropped = gem.inCare() ? finishCare(gem, now) : gem;
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), MountGemstoneItem.stackOf(dropped));
        }
        gems.clear();
    }

    // -- Actions (paquets CSBarnAddGemstone, CSEquipMount, ...) -------------------

    /**
     * {@code CSBarnAddGemstone} : la gemme tenue en main, sinon la premiere de
     * l'inventaire. Une gemme sauvage est apprivoisee au passage ; l'horloge de
     * peremption d'une gemme encore valide est arretee.
     */
    public void deposit(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (MountGemstoneItem.read(stack) == null) {
            stack = ItemStack.EMPTY;
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                if (MountGemstoneItem.read(player.getInventory().getItem(slot)) != null) {
                    stack = player.getInventory().getItem(slot);
                    break;
                }
            }
        }
        MountGem gem = MountGemstoneItem.read(stack);
        if (gem == null) {
            MountTexts.error(player, "only_gems");
            return;
        }
        if (gems.size() >= capacity()) {
            MountTexts.error(player, "barn_full");
            return;
        }
        if (!gem.isWild() && !gem.ownedBy(player.getUUID())) {
            MountTexts.error(player, "not_yours");
            return;
        }
        long now = System.currentTimeMillis();
        if (!gem.isExpired(now)) {
            gem = gem.withExpiry(-1L);
        }
        if (gem.isWild()) {
            gem = gem.tame(player.getUUID());
            MountTexts.success(player, "tamed");
        }
        stack.shrink(1);
        gems.add(gem);
        setChanged();
    }

    /** {@code CSWithdrawMount} : la peremption de 30 jours demarre (si la gemme n'est pas deja perimee). */
    public void withdraw(ServerPlayer player, int index) {
        MountGem gem = mine(player, index);
        if (gem == null) {
            return;
        }
        if (gem.inCare()) {
            MountTexts.error(player, "in_care");
            return;
        }
        long now = System.currentTimeMillis();
        if (!gem.isExpired(now)) {
            gem = gem.withExpiry(now + MountGem.EXPIRY_MS);
        }
        gems.remove(index);
        setChanged();
        MountManager.give(player, MountGemstoneItem.stackOf(gem));
    }

    /** {@code CSEquipMount} : la gemme passe dans le joueur ; objets et corne restent les siens. */
    public void equip(ServerPlayer player, int index) {
        MountGem gem = mine(player, index);
        if (gem == null) {
            return;
        }
        MountData data = MountManager.data(player);
        if (data.isEquipped()) {
            MountTexts.error(player, "already_equipped");
            return;
        }
        if (gem.isDead()) {
            MountTexts.error(player, "dead");
            return;
        }
        if (gem.isExpired(System.currentTimeMillis())) {
            MountTexts.error(player, "expired");
            return;
        }
        if (gem.inCare()) {
            MountTexts.error(player, "in_care");
            return;
        }
        data.equip(gem);
        gems.remove(index);
        setChanged();
        MountTexts.success(player, "equipped");
    }

    /** {@code CSUnequipMount} : desinvoque, puis rend une gemme a l'enclos. */
    public void unequip(ServerPlayer player) {
        MountData data = MountManager.data(player);
        if (!data.isEquipped()) {
            MountTexts.error(player, "none_equipped");
            return;
        }
        if (gems.size() >= capacity()) {
            MountTexts.error(player, "barn_full");
            return;
        }
        MountManager.unsummon(player, true);
        gems.add(data.unequip(player.getUUID()));
        setChanged();
        MountTexts.success(player, "unequipped");
    }

    /** {@code CSHealMount} : n bandages, n x 1 h 30, +10 % des PV max par bandage a la fin. */
    public void heal(ServerPlayer player, int index, int bandages) {
        MountGem gem = mine(player, index);
        if (gem == null) {
            return;
        }
        if (bandages < 1 || bandages > 1000) {
            MountTexts.error(player, "bandage_count");
            return;
        }
        if (!incubator) {
            MountTexts.error(player, "need_incubator");
            return;
        }
        if (inCare() >= MAX_IN_CARE) {
            MountTexts.error(player, "care_full");
            return;
        }
        if (gem.inCare()) {
            MountTexts.error(player, "already_in_care");
            return;
        }
        ItemStack bandage = PalaMount.BANDAGE.get().getDefaultInstance();
        if (MountManager.count(player.getInventory(), bandage) < bandages) {
            MountTexts.error(player, "not_enough_bandages", bandages);
            return;
        }
        MountManager.take(player.getInventory(), bandage, bandages);
        long now = System.currentTimeMillis();
        gems.set(index, gem.withCare(now, now + CARE_MS * bandages));
        setChanged();
        MountTexts.success(player, "in_care_now");
    }

    private MountGem mine(ServerPlayer player, int index) {
        if (index < 0 || index >= gems.size()) {
            return null;
        }
        MountGem gem = gems.get(index);
        if (!gem.ownedBy(player.getUUID())) {
            MountTexts.error(player, "not_yours");
            return null;
        }
        return gem;
    }

    // -- Sauvegarde ----------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        gems.clear();
        input.read("Gems", MountGem.CODEC.listOf()).ifPresent(gems::addAll);
        size = input.getIntOr("Size", -1);
        incubator = input.getBooleanOr("Incubator", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Gems", MountGem.CODEC.listOf(), List.copyOf(gems));
        output.putInt("Size", size);
        output.putBoolean("Incubator", incubator);
    }
}
