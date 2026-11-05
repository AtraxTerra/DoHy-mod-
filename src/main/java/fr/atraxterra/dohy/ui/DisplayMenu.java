package fr.atraxterra.dohy.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiFunction;

/**
 * Socle des interfaces d'affichage.
 *
 * <p>Un conteneur d'affichage pur : {@code super.clicked} n'est jamais appele,
 * donc aucun item ne peut etre deplace, duplique ou perdu. Chaque clic est
 * traduit en action, puis l'ecran est redessine.
 *
 * <p>Toutes les fenetres du mod en heritent, pour que la sûrete et les
 * conventions de navigation soient acquises une fois et pas re-decidees sept
 * fois.
 */
public abstract class DisplayMenu extends ChestMenu {

    protected final ServerPlayer player;
    private final Container display;
    private final int size;

    protected DisplayMenu(int containerId, Inventory inventory, ServerPlayer player, int rows) {
        this(containerId, inventory, player, Math.max(1, Math.min(6, rows)),
                new SimpleContainer(Math.max(1, Math.min(6, rows)) * 9));
    }

    private DisplayMenu(int containerId, Inventory inventory, ServerPlayer player,
                        int rows, SimpleContainer display) {
        super(typeFor(rows), containerId, inventory, display, rows);
        this.player = player;
        this.display = display;
        this.size = rows * 9;
    }

    /**
     * Le type vanilla correspondant au nombre de rangees.
     *
     * <p>Les six constantes existent bien en 26.2 (verifie a la sonde d'API).
     * Le type et le nombre de rangees doivent concorder : un type a six rangees
     * avec un conteneur de quatre ferait dessiner au client des emplacements que
     * le serveur n'a pas.
     */
    private static MenuType<?> typeFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    /** Dessine l'ecran. Appele apres chaque clic ; doit etre idempotent. */
    protected abstract void build();

    /** Traite un clic sur un emplacement de l'ecran. */
    protected abstract void onClick(int slot, int button, boolean shift);

    @Override
    public final boolean stillValid(Player candidate) {
        return candidate == player;
    }

    @Override
    public final ItemStack quickMoveStack(Player candidate, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public final void clicked(int slotId, int button, ContainerInput input, Player candidate) {
        if (!(candidate instanceof ServerPlayer serverPlayer) || serverPlayer != player) {
            return;
        }
        if (slotId >= 0 && slotId < size) {
            onClick(slotId, button, input == ContainerInput.QUICK_MOVE);
        }
        refresh();
    }

    protected final void refresh() {
        for (int slot = 0; slot < size; slot++) {
            display.setItem(slot, ItemStack.EMPTY);
        }
        build();
        broadcastChanges();
    }

    protected final void set(int slot, ItemStack stack) {
        if (slot >= 0 && slot < size) {
            display.setItem(slot, stack);
        }
    }

    protected final int size() {
        return size;
    }

    /**
     * Encadre l'ecran : premiere et derniere rangee, premiere et derniere
     * colonne.
     *
     * <p>Le cadre n'est pas une decoration gratuite — il donne au regard une
     * zone de contenu bien delimitee, et c'est ce qui fait qu'un ecran de
     * coffre se lit comme une interface plutot que comme un coffre. On l'appelle
     * en premier dans {@code build()}, les vignettes utiles ecrasent ensuite les
     * emplacements dont elles ont besoin.
     */
    protected final void frame() {
        int rows = size / 9;
        for (int column = 0; column < 9; column++) {
            set(column, Gui.filler());
            set((rows - 1) * 9 + column, Gui.filler());
        }
        for (int row = 1; row < rows - 1; row++) {
            set(row * 9, Gui.filler());
            set(row * 9 + 8, Gui.filler());
        }
    }

    /** Remplit la derniere rangee de bordure, emplacements utiles exceptes. */
    protected final void footer(int... usedSlots) {
        int firstOfLastRow = size - 9;
        outer:
        for (int slot = firstOfLastRow; slot < size; slot++) {
            for (int used : usedSlots) {
                if (used == slot) {
                    continue outer;
                }
            }
            set(slot, Gui.filler());
        }
    }

    /**
     * Ouvre un autre ecran.
     *
     * <p>Appel direct : l'appelant retourne aussitot, donc la fermeture de
     * l'ecran courant n'intervient pas au milieu du traitement d'un clic.
     */
    protected final void open(String title,
                              BiFunction<Integer, Inventory, AbstractContainerMenu> factory) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> factory.apply(containerId, inventory),
                Component.literal(title)));
    }

    protected final void click() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
    }

    protected final void click(float pitch) {
        play(SoundEvents.UI_BUTTON_CLICK.value(), pitch);
    }

    protected final void success() {
        play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f);
    }

    protected final void failure() {
        play(SoundEvents.VILLAGER_NO, 1.0f);
    }

    protected final void play(SoundEvent sound, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.MASTER, 0.4f, pitch);
    }
}
