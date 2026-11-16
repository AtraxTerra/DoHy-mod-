package fr.atraxterra.dohy.client.ui;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * La fenetre de confirmation des maquettes : un voile sur l'ecran, une carte au
 * centre avec l'icone qui depasse, la question, le detail, et deux boutons.
 *
 * <p>Elle apparait en glissant vers le haut ; Entree confirme, Echap annule.
 */
public final class Popup {

    private static final float WIDTH = 206.7f;
    private static final float BUTTON_HEIGHT = 18.0f;

    private final ItemStack icon;
    private final String title;
    private final String detail;
    private final String confirmLabel;
    private final boolean danger;
    private final Runnable onConfirm;
    private final double opened = Anim.now();
    private final Anim.Value cancelHover = new Anim.Value(0.0f, 16.0f);
    private final Anim.Value confirmHover = new Anim.Value(0.0f, 16.0f);
    private double closing = -1.0d;
    private float boxX;
    private float boxY;
    private float boxHeight;
    /** Des cases chiffrees sous le detail : « TOTAL 80 000 $ », « IL TE RESTERA ». */
    private List<String[]> sums = List.of();

    /** Ajoute des cases chiffrees (libelle, valeur) entre le detail et les boutons. */
    public Popup sums(List<String[]> values) {
        this.sums = List.copyOf(values);
        return this;
    }

    public Popup(ItemStack icon, String title, String detail, String confirmLabel, boolean danger, Runnable onConfirm) {
        this.icon = icon;
        this.title = title;
        this.detail = detail;
        this.confirmLabel = confirmLabel;
        this.danger = danger;
        this.onConfirm = onConfirm;
    }

    /**
     * Une question « Question|Detail » posee par une carte. Un « ! » en tete marque une
     * action qui detruit (dissoudre, tout rendre) : le bouton passe au rouge.
     */
    public static Popup question(ItemStack icon, String question, Runnable onConfirm) {
        boolean danger = question.startsWith("!");
        String text = danger ? question.substring(1) : question;
        int bar = text.indexOf('|');
        String title = bar < 0 ? text : text.substring(0, bar);
        String detail = bar < 0 ? "" : text.substring(bar + 1);
        return new Popup(icon, title, detail, "CONFIRMER", danger, onConfirm);
    }

    public boolean closed() {
        return closing >= 0 && Anim.progress(closing, 0.12d) >= 1.0f;
    }

    public void cancel() {
        if (closing < 0) {
            UiScreen.click(0.9f);
            closing = Anim.now();
        }
    }

    public void confirm() {
        if (closing < 0) {
            UiScreen.click(1.1f);
            closing = Anim.now();
            onConfirm.run();
        }
    }

    public void draw(UiGraphics g, double mouseX, double mouseY, int designWidth, int designHeight) {
        Paint p = g.paint();
        float appear = Anim.easeOut(Anim.progress(opened, 0.2d));
        float gone = closing < 0 ? 0.0f : Anim.progress(closing, 0.12d);
        float visible = appear * (1.0f - gone);
        p.rect(-400, -400, designWidth + 800, designHeight + 800, Paint.alpha(0xFF060708, 0.62f * visible));
        List<String> lines = detail.isEmpty() ? List.of() : g.wrap(Ui.BODY, detail, 5.7f, WIDTH - 24.0f);
        List<String> titleLines = g.wrap(Ui.WIDE, title, 8.0f, WIDTH - 24.0f);
        // L'icone deborde de moitie : le texte commence sous elle, les boutons 9 unites plus bas.
        boxHeight = 30.7f / 2.0f + 6.0f + titleLines.size() * 10.0f
                + (lines.isEmpty() ? 0.0f : 4.0f + lines.size() * 8.0f) + (sums.isEmpty() ? 0.0f : 25.0f) + 7.0f
                + BUTTON_HEIGHT + 10.0f;
        boxX = (designWidth - WIDTH) / 2.0f;
        boxY = (designHeight - boxHeight) / 2.0f + 6.0f * (1.0f - appear) + 4.0f * gone;
        p.shadow(boxX + 6, boxY + 10, WIDTH - 12, boxHeight - 10, 26, Paint.alpha(0xFF000000, 0.7f * visible));
        p.round(boxX, boxY, WIDTH, boxHeight, 7.3f, 0xFF3A3D43);
        p.roundGradient(boxX + 0.34f, boxY + 0.34f, WIDTH - 0.68f, boxHeight - 0.68f, 7.0f, 0xFF26282C, 0xFF18191C);
        // L'icone qui depasse du haut de la carte.
        float iconSize = 30.7f;
        float iconX = boxX + (WIDTH - iconSize) / 2.0f;
        float iconY = boxY - iconSize / 2.0f - 2.0f;
        p.shadow(iconX + 3, iconY + 5, iconSize - 6, iconSize - 5, 10, Paint.alpha(0xFF000000, 0.55f));
        p.round(iconX, iconY, iconSize, iconSize, 8.7f, 0xFF45484F);
        p.roundGradient(iconX + 0.34f, iconY + 0.34f, iconSize - 0.68f, iconSize - 0.68f, 8.4f, 0xFF2F3136, 0xFF1C1D20);
        g.item(icon, iconX + (iconSize - 18.7f) / 2.0f, iconY + (iconSize - 18.7f) / 2.0f, 18.7f, false);
        float y = boxY + iconSize / 2.0f + 6.0f;
        for (String line : titleLines) {
            g.centered(Ui.WIDE, line, boxX + WIDTH / 2.0f, y, 8.0f, Ui.Theme.TEXT);
            y += 10.0f;
        }
        if (!lines.isEmpty()) {
            y += 2.0f;
            for (String line : lines) {
                g.centered(Ui.BODY, line, boxX + WIDTH / 2.0f, y, 5.7f, Ui.Theme.TEXT2);
                y += 8.0f;
            }
            y += 2.0f;
        }
        if (!sums.isEmpty()) {
            y += 3.0f;
            float sumWidth = (WIDTH - 24.0f - 4.0f * (sums.size() - 1)) / sums.size();
            for (int index = 0; index < sums.size(); index++) {
                float sumX = boxX + 12.0f + index * (sumWidth + 4.0f);
                p.framed(sumX, y, sumWidth, 19.3f, 3.3f, 0xFF141518, 0xFF2A2C31, 1);
                g.text(Ui.PIXEL, sums.get(index)[0], sumX + 4.7f, y + 3.6f, 3.3f, Ui.Theme.MUTED);
                g.text(Ui.WIDE, g.fit(Ui.WIDE, sums.get(index)[1], 6.3f, sumWidth - 8.0f), sumX + 4.7f, y + 9.6f,
                        6.3f, index == 0 ? Ui.Theme.TEXT : Ui.Theme.TEXT2);
            }
        }
        float buttonsY = boxY + boxHeight - 10.0f - BUTTON_HEIGHT;
        float buttonWidth = (WIDTH - 24.0f - 4.0f) / 2.0f;
        float cancelX = boxX + 12.0f;
        float confirmX = cancelX + buttonWidth + 4.0f;
        boolean overCancel = inside(mouseX, mouseY, cancelX, buttonsY, buttonWidth, BUTTON_HEIGHT);
        boolean overConfirm = inside(mouseX, mouseY, confirmX, buttonsY, buttonWidth, BUTTON_HEIGHT);
        float cancelT = cancelHover.toward(overCancel ? 1.0f : 0.0f);
        float confirmT = confirmHover.toward(overConfirm ? 1.0f : 0.0f);
        p.round(cancelX, buttonsY, buttonWidth, BUTTON_HEIGHT, 4.0f, Paint.mix(0xFF3F4248, 0xFF5A5D64, cancelT));
        p.round(cancelX + 0.34f, buttonsY + 0.34f, buttonWidth - 0.68f, BUTTON_HEIGHT - 0.68f, 3.7f,
                Paint.mix(0xFF1B1C1F, 0xFF26282C, cancelT));
        g.centered(Ui.WIDE, "ANNULER", cancelX + buttonWidth / 2.0f, buttonsY + 6.0f, 6.0f, Ui.Theme.TEXT2);
        int top = danger ? Ui.Theme.RED_LIGHT : 0xFF3FD52C;
        int bottom = danger ? Ui.Theme.RED_D : 0xFF24A015;
        p.shadow(confirmX + 3, buttonsY + 5, buttonWidth - 6, BUTTON_HEIGHT - 5, 7,
                Paint.alpha(danger ? Ui.Theme.RED : Ui.Theme.GREEN, 0.30f + 0.15f * confirmT));
        p.roundGradient(confirmX, buttonsY, buttonWidth, BUTTON_HEIGHT, 4.0f, Paint.mix(top, 0xFFFFFFFF, 0.08f * confirmT),
                bottom);
        p.rect(confirmX + 4.0f, buttonsY + 0.34f, buttonWidth - 8.0f, 0.34f, 0x40FFFFFF);
        g.centered(Ui.WIDE, confirmLabel, confirmX + buttonWidth / 2.0f, buttonsY + 6.0f, 6.0f, Ui.Theme.TEXT);
    }

    private static boolean inside(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public void click(double mouseX, double mouseY, int designWidth, int designHeight) {
        float buttonsY = boxY + boxHeight - 10.0f - BUTTON_HEIGHT;
        float buttonWidth = (WIDTH - 24.0f - 4.0f) / 2.0f;
        float cancelX = boxX + 12.0f;
        float confirmX = cancelX + buttonWidth + 4.0f;
        if (inside(mouseX, mouseY, confirmX, buttonsY, buttonWidth, BUTTON_HEIGHT)) {
            confirm();
        } else if (inside(mouseX, mouseY, cancelX, buttonsY, buttonWidth, BUTTON_HEIGHT)
                || !inside(mouseX, mouseY, boxX, boxY, WIDTH, boxHeight)) {
            cancel();
        }
    }
}
