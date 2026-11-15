package fr.atraxterra.dohy.client.ui.widget;

import fr.atraxterra.dohy.client.ui.Buttons;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;

import java.util.function.Consumer;

/** Un champ de recherche arrondi, loupe a gauche : le style des maquettes. */
public class SearchBox extends TextField {

    /** La loupe a gauche ; sans, un champ de formulaire. */
    public boolean loupe = true;

    /** Un champ de formulaire au meme style, sans loupe. */
    public static SearchBox form(int x, int y, int width, int height, String placeholder, int maxLength,
                                 String value) {
        SearchBox box = new SearchBox(x, y, width, height, placeholder, maxLength, text -> {
        });
        box.loupe = false;
        box.withValue(value);
        return box;
    }

    public SearchBox(int x, int y, int width, int height, String placeholder, int maxLength,
                     Consumer<String> onChange) {
        super(x, y, width, height, placeholder, maxLength, onChange);
        this.search = false;
        this.textSize = 4.7f;
    }

    @Override
    public void render(UiGraphics g, double mouseX, double mouseY) {
        Buttons.field(g.paint(), x, y, width, height, focused());
        int icon = loupe ? Math.max(5, height - 7) : 0;
        if (loupe) {
            g.texture(Ui.SEARCH, x + 4, y + (height - icon) / 2, icon, icon, 32, 32,
                    focused() ? Ui.Theme.TEXT2 : Ui.Theme.MUTED);
        }
        float textX = x + (loupe ? 6 + icon : 4.7f);
        float textY = y + (height - textSize) / 2.0f;
        if (value().isEmpty() && !focused()) {
            g.text(Ui.BODY, placeholder(), textX, textY, textSize, Ui.Theme.MUTED);
            return;
        }
        String shown = value();
        float max = x + width - 4 - textX;
        while (!shown.isEmpty() && g.width(Ui.BODY, shown, textSize) > max) {
            shown = shown.substring(1);
        }
        g.text(Ui.BODY, shown, textX, textY, textSize, Ui.Theme.TEXT);
        if (focused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            float caret = textX + g.width(Ui.BODY, shown, textSize) + 0.5f;
            g.paint().rect(caret, y + 3, 0.5f, height - 6, Ui.Theme.TEXT);
        }
    }
}
