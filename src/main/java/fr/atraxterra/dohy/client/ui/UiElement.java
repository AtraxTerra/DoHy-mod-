package fr.atraxterra.dohy.client.ui;

/**
 * Un element d'un ecran du kit, en unites de maquette.
 *
 * <p>Les coordonnees de la souris lui arrivent deja converties par l'ecran.
 */
public abstract class UiElement {

    public int x;
    public int y;
    public int width;
    public int height;
    public boolean visible = true;
    private boolean focused;

    protected UiElement(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(double mouseX, double mouseY) {
        return visible && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public abstract void render(UiGraphics graphics, double mouseX, double mouseY);

    /** Rend {@code true} si le clic est consomme. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        return false;
    }

    public void mouseReleased() {
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    public boolean keyPressed(int key, int modifiers) {
        return false;
    }

    public boolean charTyped(String text) {
        return false;
    }

    /** Un element qui prend le clavier quand on clique dessus. */
    public boolean focusable() {
        return false;
    }

    public boolean focused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }
}
