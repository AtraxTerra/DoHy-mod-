package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiContainerScreen;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * L'ecran d'une machine au style de Paladium : le titre en haut a gauche, la
 * machine en sections, l'inventaire en bas.
 *
 * <p>Le cadre de chaque case est dessine a la position de la case du menu ;
 * le reste (intertitres, fleches, jauges, textes) vient du {@link Design} de la
 * machine ({@link MachineDesigns}).
 */
public final class MachineKitScreen extends UiContainerScreen<MachineMenu> {

    public static final int WIDTH = 262;
    public static final int HEIGHT = 236;

    /** Les decorations d'une machine, en coordonnees du panneau. */
    @FunctionalInterface
    public interface Painter {
        void paint(UiGraphics g, MachineMenu menu, int mouseX, int mouseY);
    }

    /**
     * Une machine.
     *
     * @param big   les cases de sortie, a grand cadre
     * @param hints la bulle d'une case vide : ce qu'on y pose
     */
    public record Design(Set<Integer> big, Painter painter, Map<Integer, List<String>> hints,
                         List<Button> buttons) {

        public Design(Set<Integer> big, Painter painter, Map<Integer, List<String>> hints) {
            this(big, painter, hints, List.of());
        }
    }

    /**
     * Une zone cliquable du panneau : le clic part au serveur comme un bouton de
     * menu ({@code MachineMenu.Buttons}). Le dessin reste a la charge du
     * {@link Painter}.
     */
    public record Button(int id, int x, int y, int width, int height) {

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final Design design;

    public MachineKitScreen(MachineMenu menu, Inventory inventory, Component title, Design design) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.design = design;
    }

    private int machineSlots() {
        return menu.layout().slots().size();
    }

    @Override
    protected void drawPanel(UiGraphics g, int mouseX, int mouseY) {
        g.panel(0, 0, WIDTH, HEIGHT);
        title(g, getTitle().getString().toUpperCase(Locale.ROOT), 16, 15, 16, WIDTH - 32, Ui.RED, Ui.RED_SHADE);
        for (int index = 0; index < machineSlots(); index++) {
            frame(g, menu.slots.get(index), design.big().contains(index));
        }
        playerFrames(g, machineSlots());
        inventoryLabel(g, machineSlots());
        design.painter().paint(g, menu, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x() - getLeftPos();
        double y = event.y() - getTopPos();
        if (event.button() == 0 && minecraft != null && minecraft.gameMode != null) {
            for (Button button : design.buttons()) {
                if (button.contains(x, y)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button.id());
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void drawForeground(UiGraphics g, int mouseX, int mouseY) {
        int hovered = hoveredSlot(mouseX, mouseY, machineSlots());
        if (hovered >= 0 && !menu.slots.get(hovered).hasItem()) {
            List<String> hint = design.hints().get(hovered);
            if (hint != null) {
                tip(g, mouseX, mouseY, mouseX, mouseY, 1, 1, hint);
            }
        }
    }
}
