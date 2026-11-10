package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.widget.CloseButton;
import fr.atraxterra.dohy.client.ui.widget.TextField;
import fr.atraxterra.dohy.client.ui.widget.UiButton;
import fr.atraxterra.dohy.faction.FactionEditView;
import fr.atraxterra.dohy.faction.FactionMenus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * « Creation Faction » (touche de faction, ou {@code /f create}) : nom,
 * description, deux tags, blason ; en modification, le MOTD en plus.
 */
public class FactionEditScreen extends UiScreen implements ViewScreen<FactionEditView> {

    private static final int WIDTH = 420;
    private static final int HEIGHT = 250;
    private static final int TAG_W = 46;
    private static final int TAG_H = 12;
    private static final int BANNER = 22;

    private FactionEditView view;
    private TextField name;
    private TextField description;
    private TextField motd;
    private final List<String> tags = new ArrayList<>();
    private int banner;

    public FactionEditScreen(FactionEditView view) {
        super(Component.literal("Faction"), WIDTH, HEIGHT);
        this.view = view;
        this.tags.addAll(view.tags());
        this.banner = view.banner();
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
    protected void build() {
        String keptName = name == null ? view.name() : name.value();
        String keptDescription = description == null ? view.description() : description.value();
        String keptMotd = motd == null ? view.motd() : motd.value();
        add(new CloseButton(WIDTH - 26, 16, this::onClose));
        name = add(field(22, 62, 196, "Nom de la faction (3 a 16 caracteres)", 16, keptName));
        description = add(field(22, 92, 196, "Description (facultative)", 60, keptDescription));
        motd = add(field(22, 122, 196, "Message a la connexion (facultatif)", 60, keptMotd));
        motd.visible = !view.create();
        for (int index = 0; index < view.allTags().size(); index++) {
            add(new TagChip(view.allTags().get(index), 22 + index % 4 * (TAG_W + 4),
                    160 + index / 4 * (TAG_H + 4)));
        }
        for (int color = 0; color < 16; color++) {
            add(new BannerSlot(color, 250 + color % 4 * (BANNER + 4), 62 + color / 4 * (BANNER + 4)));
        }
        String label = view.create() ? "CRÉER" : "ENREGISTRER";
        add(new UiButton(WIDTH - 22 - 100, HEIGHT - 38, 100, 20, label, UiButton.Style.GREEN, this::save));
        add(new UiButton(WIDTH - 22 - 100 - 70, HEIGHT - 38, 64, 20, "ANNULER", UiButton.Style.GRAY,
                () -> UiClient.send(FactionEditView.ID, "cancel", "", 0)));
    }

    private static TextField field(int x, int y, int width, String placeholder, int max, String value) {
        TextField field = new TextField(x, y, width, 16, placeholder, max, text -> {
        });
        field.search = false;
        field.textSize = 5.0f;
        return field.withValue(value);
    }

    private void save() {
        String argument = String.join(FactionEditView.SEPARATOR, name.value(), description.value(),
                view.create() ? "" : motd.value(), String.join(",", tags));
        UiClient.send(FactionEditView.ID, "save", argument, banner);
    }

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        g.panel(0, 0, WIDTH, HEIGHT);
        g.textShaded(Ui.TITLE, view.create() ? "CRÉATION FACTION" : "MODIFIER LA FACTION", 22, 16, 16, Ui.RED,
                Ui.RED_SHADE, 1.0f);
        g.section(22, 44, "section_craft", "IDENTITÉ", 196);
        g.text(Ui.PIXEL, "NOM", 22, 54, 4.5f, Ui.MUTED);
        g.text(Ui.PIXEL, "DESCRIPTION", 22, 84, 4.5f, Ui.MUTED);
        if (!view.create()) {
            g.text(Ui.PIXEL, "MOTD", 22, 114, 4.5f, Ui.MUTED);
        }
        g.section(22, 146, "section_resources", "TAGS (" + tags.size() + "/2)", 196);
        g.section(240, 44, "section_upgrade", "BLASON", 158);
        ItemStack preview = FactionMenus.banner(banner);
        g.rect(352, 62, 46, 46, Ui.INSET);
        g.item(preview, 359, 69, 32, false);
        g.text(Ui.BODY, colorName(banner), 352, 112, 5, Ui.TEXT);
        if (view.create()) {
            ItemStack ingot = new ItemStack(BuiltInRegistries.ITEM.getValue(
                    Identifier.fromNamespaceAndPath("palamod", "paladium_ingot")));
            g.item(ingot, 240, 172, 16, false);
            g.text(Ui.BOLD, view.cost() + " lingots de paladium", 260, 174, 6, view.canAfford() ? Ui.GREEN : Ui.RED);
            g.text(Ui.BODY, view.canAfford() ? "Retires de ton inventaire" : "Il t'en manque sur toi", 260, 183, 5,
                    Ui.MUTED);
        }
        if (!view.error().isEmpty()) {
            g.wrapped(Ui.BODY, view.error(), 22, HEIGHT - 34, 5.5f, 200, 8, Ui.RED, 2);
        }
    }

    private static final String[] COLORS = {"Blanc", "Orange", "Magenta", "Bleu clair", "Jaune", "Vert clair", "Rose",
            "Gris", "Gris clair", "Cyan", "Violet", "Bleu", "Marron", "Vert", "Rouge", "Noir"};

    private static String colorName(int color) {
        return COLORS[Math.floorMod(color, COLORS.length)];
    }

    /** Un tag a cocher : deux au plus. */
    private final class TagChip extends UiElement {
        private final String tag;

        TagChip(String tag, int x, int y) {
            super(x, y, TAG_W, TAG_H);
            this.tag = tag;
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            boolean selected = tags.contains(tag);
            boolean hovered = contains(mouseX, mouseY);
            g.button(x, y, width, height, selected ? Ui.RED : Ui.GRAY_BUTTON,
                    selected ? Ui.RED_DARK : Ui.GRAY_BUTTON_DARK, hovered);
            g.centered(Ui.PIXEL, tag.toUpperCase(Locale.ROOT), x + width / 2.0f, y + 3, 4, Ui.WHITE);
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

        BannerSlot(int color, int x, int y) {
            super(x, y, BANNER, BANNER);
            this.color = color;
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            boolean selected = color == banner;
            g.rect(x, y, width, height, selected ? Ui.RED : contains(mouseX, mouseY) ? Ui.SLOT_BORDER : 0xFF55545C);
            g.rect(x + 1, y + 1, width - 2, height - 2, Ui.SLOT_FILL);
            g.item(FactionMenus.banner(color), x + 3, y + 3, 16, false);
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
