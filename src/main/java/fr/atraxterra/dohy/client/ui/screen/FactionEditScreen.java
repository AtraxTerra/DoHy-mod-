package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Buttons;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.client.ui.widget.SearchBox;
import fr.atraxterra.dohy.faction.FactionEditView;
import fr.atraxterra.dohy.faction.FactionMenus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Creer ou modifier sa faction, dans la coque des menus : le nom, la description et
 * le MOTD a gauche avec les tags, le blason a droite avec le cout, et les boutons.
 */
public class FactionEditScreen extends UiScreen implements ViewScreen<FactionEditView> {

    private static final float FIELD = 14.0f;
    private static final float TAG_HEIGHT = 12.0f;
    private static final float SLOT = 21.3f;

    private FactionEditView view;
    private final Shell shell = new Shell(FactionEditView.ID);
    private final double opened = Anim.now();
    private SearchBox name;
    private SearchBox description;
    private SearchBox motd;
    private final List<String> tags = new ArrayList<>();
    private int banner;

    public FactionEditScreen(FactionEditView view) {
        super(Component.literal("Faction"), Shell.WIDTH, Shell.HEIGHT);
        this.view = view;
        this.tags.addAll(view.tags());
        this.banner = view.banner();
        shell.header(view.create() ? "Creer ta faction" : "Modifier la faction", "", List.of(), List.of(), false);
    }

    @Override
    public Identifier viewId() {
        return FactionEditView.ID;
    }

    @Override
    public void accept(FactionEditView next) {
        // Un refus du serveur : on garde ce que le joueur a tape.
        this.view = next;
    }

    @Override
    protected float lift() {
        return 5.0f * (1.0f - Anim.easeOut(Anim.progress(opened, 0.22d)));
    }

    private float left() {
        return shell.mainLeft();
    }

    private float columnWidth() {
        return (shell.mainRight() - shell.mainLeft() - 16.0f) / 2.0f;
    }

    private float rightColumn() {
        return left() + columnWidth() + 16.0f;
    }

    private float top() {
        return shell.contentTop();
    }

    @Override
    protected void build() {
        String keptName = name == null ? view.name() : name.value();
        String keptDescription = description == null ? view.description() : description.value();
        String keptMotd = motd == null ? view.motd() : motd.value();
        int x = Math.round(left());
        int width = Math.round(columnWidth());
        name = add(SearchBox.form(x, Math.round(top() + 20.0f), width, Math.round(FIELD),
                "Nom de la faction (3 a 16 caracteres)", 16, keptName));
        description = add(SearchBox.form(x, Math.round(top() + 46.0f), width, Math.round(FIELD),
                "Description (facultative)", 60, keptDescription));
        motd = add(SearchBox.form(x, Math.round(top() + 72.0f), width, Math.round(FIELD),
                "Message a la connexion (facultatif)", 60, keptMotd));
        motd.visible = view.motdUnlocked();
        float tagWidth = (columnWidth() - 3 * 3.3f) / 4.0f;
        for (int index = 0; index < view.allTags().size(); index++) {
            add(new TagChip(view.allTags().get(index), left() + index % 4 * (tagWidth + 3.3f),
                    top() + 114.0f + index / 4 * (TAG_HEIGHT + 3.3f), tagWidth));
        }
        for (int color = 0; color < 16; color++) {
            add(new BannerSlot(color, rightColumn() + color % 4 * (SLOT + 3.3f),
                    top() + 18.0f + color / 4 * (SLOT + 3.3f)));
        }
        add(new Actions());
    }

    private void save() {
        String argument = String.join(FactionEditView.SEPARATOR, name.value(), description.value(),
                view.motdUnlocked() ? motd.value() : "", String.join(",", tags));
        UiClient.send(FactionEditView.ID, "save", argument, banner);
    }

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        Paint p = g.paint();
        shell.draw(g, p, mouseX, mouseY);
        float x = left();
        float width = columnWidth();
        Shell.section(g, p, x, top(), width, "craft", "IDENTITE", "");
        g.text(Ui.PIXEL, "NOM", x, top() + 14.0f, 3.3f, Ui.Theme.MUTED);
        g.text(Ui.PIXEL, "DESCRIPTION", x, top() + 40.0f, 3.3f, Ui.Theme.MUTED);
        if (view.motdUnlocked()) {
            g.text(Ui.PIXEL, "MOTD", x, top() + 66.0f, 3.3f, Ui.Theme.MUTED);
        } else if (!view.create()) {
            g.text(Ui.PIXEL, "MOTD : PALIER 6 DE LA BRANCHE GLOBALE", x, top() + 66.0f, 3.3f, Ui.Theme.DIM);
        }
        Shell.section(g, p, x, top() + 98.0f, width, "resources", "TAGS", tags.size() + " / 2");

        float right = rightColumn();
        Shell.section(g, p, right, top(), width, "upgrade", "BLASON", "");
        // L'apercu du blason, a cote des seize couleurs.
        float previewX = right + 4 * (SLOT + 3.3f) + 4.0f;
        float previewWidth = right + width - previewX;
        float previewHeight = 2 * SLOT + 3.3f;
        p.framed(previewX, top() + 18.0f, previewWidth, previewHeight, 5.3f, 0xFF16171A, 0xFF2A2C31, 1);
        g.scissor(Math.round(previewX), Math.round(top() + 18.0f), Math.round(previewWidth),
                Math.round(previewHeight));
        p.glow(previewX + previewWidth / 2.0f, top() + 18.0f + previewHeight / 2.0f, 30.0f,
                Paint.alpha(Ui.Theme.RED, 0.12f));
        g.endScissor();
        g.item(FactionMenus.banner(banner), previewX + (previewWidth - 26.0f) / 2.0f, top() + 22.0f, 26.0f, false);
        g.centered(Ui.BODY, colorName(banner), previewX + previewWidth / 2.0f, top() + 18.0f + previewHeight + 3.0f,
                4.7f, Ui.Theme.TEXT2);

        if (view.create()) {
            float costY = top() + 18.0f + 4 * (SLOT + 3.3f) + 6.0f;
            p.framed(right, costY, width, 22.0f, 4.7f, 0xFF1C1D20, view.canAfford() ? 0xFF2A4A26 : 0xFF4A2626, 1);
            ItemStack ingot = new ItemStack(BuiltInRegistries.ITEM.getValue(
                    Identifier.fromNamespaceAndPath("palamod", "paladium_ingot")));
            g.item(ingot, right + 5.0f, costY + 4.0f, 14.0f, false);
            g.text(Ui.BOLD, view.cost() + " lingots de paladium", right + 24.0f, costY + 4.0f, 5.7f,
                    view.canAfford() ? Ui.Theme.GREEN_L : Ui.Theme.RED_LIGHT);
            g.text(Ui.BODY, view.canAfford() ? "Retires de ton inventaire" : "Il t'en manque sur toi",
                    right + 24.0f, costY + 11.6f, 4.7f, Ui.Theme.MUTED);
        }
        if (!view.error().isEmpty()) {
            g.wrapped(Ui.BODY, view.error(), x, Shell.HEIGHT - 31.0f, 5.0f, width, 7.0f, Ui.Theme.RED_LIGHT, 2);
        }
    }

    @Override
    protected boolean backgroundClicked(double mouseX, double mouseY, int button) {
        return button == 0 && shell.click(mouseX, mouseY);
    }

    private static final String[] COLORS = {"Blanc", "Orange", "Magenta", "Bleu clair", "Jaune", "Vert clair", "Rose",
            "Gris", "Gris clair", "Cyan", "Violet", "Bleu", "Marron", "Vert", "Rouge", "Noir"};

    private static String colorName(int color) {
        return COLORS[Math.floorMod(color, COLORS.length)];
    }

    /** Les deux boutons du bas : annuler, creer (ou enregistrer). */
    private final class Actions extends UiElement {
        private final Anim.Value cancelHover = new Anim.Value(0.0f, 16.0f);
        private final Anim.Value saveHover = new Anim.Value(0.0f, 16.0f);

        Actions() {
            super(0, 0, 1, 1);
            width = 140;
            height = 17;
            x = Math.round(shell.mainRight()) - width;
            y = Shell.HEIGHT - 9 - height;
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            float cancelWidth = 52.0f;
            boolean overCancel = mouseX >= x && mouseX < x + cancelWidth && mouseY >= y && mouseY < y + height;
            boolean overSave = mouseX >= x + cancelWidth + 4 && mouseX < x + width && mouseY >= y && mouseY < y + height;
            Buttons.draw(g, g.paint(), x, y, cancelWidth, height, "ANNULER", Buttons.GHOST,
                    cancelHover.toward(overCancel ? 1.0f : 0.0f), true, 5.3f);
            Buttons.draw(g, g.paint(), x + cancelWidth + 4.0f, y, width - cancelWidth - 4.0f, height,
                    view.create() ? "CREER" : "ENREGISTRER", Buttons.GREEN, saveHover.toward(overSave ? 1.0f : 0.0f),
                    !view.create() || view.canAfford(), 5.3f);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY) || button != 0) {
                return false;
            }
            if (mouseX < x + 52) {
                UiScreen.click(0.9f);
                UiClient.send(FactionEditView.ID, "cancel", "", 0);
            } else if (!view.create() || view.canAfford()) {
                UiScreen.click(1.1f);
                save();
            }
            return true;
        }
    }

    /** Un tag a cocher : deux au plus. */
    private final class TagChip extends UiElement {
        private final String tag;
        private final Anim.Value hover = new Anim.Value(0.0f, 16.0f);

        TagChip(String tag, float x, float y, float width) {
            super(Math.round(x), Math.round(y), Math.round(width), Math.round(TAG_HEIGHT));
            this.tag = tag;
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            Paint p = g.paint();
            boolean selected = tags.contains(tag);
            float t = hover.toward(contains(mouseX, mouseY) ? 1.0f : 0.0f);
            if (selected) {
                p.shadow(x + 2, y + 3, width - 4, height - 3, 5, Paint.alpha(Ui.Theme.RED, 0.3f));
                p.roundGradient(x, y, width, height, 3.3f, Paint.mix(Ui.Theme.RED_LIGHT, 0xFFFFFFFF, 0.08f * t),
                        Ui.Theme.RED_D);
            } else {
                p.framed(x, y, width, height, 3.3f, Paint.mix(0xFF1B1C1F, 0xFF26282C, t),
                        Paint.mix(0xFF3F4248, 0xFF5A5D64, t), 1);
            }
            String label = tag.isEmpty() ? tag : tag.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                    + tag.substring(1).toLowerCase(java.util.Locale.ROOT);
            g.centered(Ui.BOLD, g.fit(Ui.BOLD, label, 4.3f, width - 4), x + width / 2.0f, y + (height - 4.3f) / 2.0f,
                    4.3f, selected ? Ui.Theme.TEXT : Ui.Theme.TEXT2);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            if (tags.contains(tag)) {
                tags.remove(tag);
            } else if (tags.size() < 2) {
                tags.add(tag);
            } else {
                return true;
            }
            UiScreen.click(1.0f);
            return true;
        }
    }

    /** Une couleur de blason. */
    private final class BannerSlot extends UiElement {
        private final int color;
        private final Anim.Value hover = new Anim.Value(0.0f, 16.0f);

        BannerSlot(int color, float x, float y) {
            super(Math.round(x), Math.round(y), Math.round(SLOT), Math.round(SLOT));
            this.color = color;
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            Paint p = g.paint();
            boolean selected = color == banner;
            float t = hover.toward(contains(mouseX, mouseY) ? 1.0f : 0.0f);
            if (selected) {
                p.glow(x + width / 2.0f, y + height / 2.0f, width * 0.8f, Paint.alpha(Ui.Theme.RED, 0.3f));
            }
            p.framed(x, y, width, height, 4.0f, Paint.mix(0xFF16171A, 0xFF1E1F23, t),
                    selected ? Ui.Theme.RED : Paint.mix(0xFF2A2C31, 0xFF45484F, t), selected ? 2 : 1);
            g.item(FactionMenus.banner(color), x + (width - 14.0f) / 2.0f, y + (height - 14.0f) / 2.0f, 14.0f, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            banner = color;
            UiScreen.click(1.2f);
            return true;
        }
    }
}
