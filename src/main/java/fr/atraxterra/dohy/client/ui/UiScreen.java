package fr.atraxterra.dohy.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Un ecran du kit : une maquette de taille fixe, mise a l'echelle de la
 * fenetre.
 *
 * <p>Comme chez Paladium, l'interface occupe la meme part de l'ecran quelle
 * que soit l'echelle d'interface du jeu. L'echelle est arrondie pour qu'une
 * unite de maquette tombe sur un nombre entier de pixels : les filets d'un
 * pixel restent nets. Une fenetre trop petite reduit la maquette.
 *
 * <p>Chaque ecran dessine son fond ({@link #drawBackground}), ajoute ses
 * elements ({@link #build}) et peut dessiner par-dessus
 * ({@link #drawForeground}).
 */
public abstract class UiScreen extends Screen {

    protected final int designWidth;
    protected final int designHeight;
    protected float scale = 1.0f;
    protected float originX;
    protected float originY;

    private final List<UiElement> elements = new ArrayList<>();
    private UiElement focusedElement;
    private UiElement draggedElement;
    private static int modifiers;

    protected UiScreen(Component title, int designWidth, int designHeight) {
        super(title);
        this.designWidth = designWidth;
        this.designHeight = designHeight;
    }

    @Override
    protected void init() {
        int guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
        float fit = Math.min(width * 0.96f / designWidth, height * 0.96f / designHeight);
        float physical = (float) Math.floor(fit * guiScale);
        scale = physical >= 1.0f ? physical / guiScale : fit;
        originX = Math.round((width - designWidth * scale) / 2.0f * guiScale) / (float) guiScale;
        originY = Math.round((height - designHeight * scale) / 2.0f * guiScale) / (float) guiScale;
        elements.clear();
        focusedElement = null;
        draggedElement = null;
        build();
    }

    /** La vue affichee, pour les ecrans du serveur ; {@code null} sinon. */
    public net.minecraft.resources.Identifier viewId() {
        return null;
    }

    /** Previent le serveur a la fermeture, pour qu'il oublie la vue. */
    @Override
    public void removed() {
        if (viewId() != null) {
            UiClient.send(viewId(), "close", "", 0);
        }
        super.removed();
    }

    /** Ajoute les elements ; appele a chaque redimensionnement. */
    protected abstract void build();

    protected <T extends UiElement> T add(T element) {
        elements.add(element);
        return element;
    }

    protected abstract void drawBackground(UiGraphics graphics, double mouseX, double mouseY);

    protected void drawForeground(UiGraphics graphics, double mouseX, double mouseY) {
    }

    protected double designX(double screenX) {
        return (screenX - originX) / scale;
    }

    protected double designY(double screenY) {
        return (screenY - originY) / scale;
    }

    // -- Rendu -----------------------------------------------------------------

    /** Pas de flou : le monde reste visible derriere l'interface, assombri. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        } else {
            graphics.fill(0, 0, width, height, 0x70000000);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        double x = designX(mouseX);
        double y = designY(mouseY);
        UiGraphics ui = new UiGraphics(graphics, font, mouseX, mouseY);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        pose.scale(scale, scale);
        drawBackground(ui, x, y);
        for (UiElement element : elements) {
            if (element.visible) {
                element.render(ui, x, y);
            }
        }
        drawForeground(ui, x, y);
        ui.flushOverlays();
        pose.popMatrix();
    }

    // -- Entrees ---------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        modifiers = event.modifiers();
        double x = designX(event.x());
        double y = designY(event.y());
        // Du dernier ajoute au premier : un menu deroulant ouvert passe devant.
        for (int index = elements.size() - 1; index >= 0; index--) {
            UiElement element = elements.get(index);
            if (element.visible && element.mouseClicked(x, y, event.button())) {
                focus(element.focusable() ? element : null);
                draggedElement = element;
                return true;
            }
        }
        focus(null);
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggedElement != null && draggedElement.mouseDragged(designX(event.x()), designY(event.y()))) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggedElement != null) {
            draggedElement.mouseReleased();
            draggedElement = null;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = designX(mouseX);
        double y = designY(mouseY);
        for (int index = elements.size() - 1; index >= 0; index--) {
            UiElement element = elements.get(index);
            if (element.visible && element.contains(x, y) && element.mouseScrolled(x, y, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (focusedElement != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                focus(null);
                return true;
            }
            if (focusedElement.keyPressed(event.key(), event.modifiers())) {
                return true;
            }
            // Un champ de saisie garde les touches : « E » ne ferme pas l'ecran.
            return true;
        }
        if (minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focusedElement != null && focusedElement.charTyped(event.codepointAsString())) {
            return true;
        }
        return super.charTyped(event);
    }

    private void focus(UiElement element) {
        if (focusedElement != null && focusedElement != element) {
            focusedElement.setFocused(false);
        }
        focusedElement = element;
        if (element != null) {
            element.setFocused(true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Les touches de modification du dernier clic (Maj, Ctrl). */
    public static int modifiers() {
        return modifiers;
    }

    /** Le petit clic des boutons. */
    public static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }
}
