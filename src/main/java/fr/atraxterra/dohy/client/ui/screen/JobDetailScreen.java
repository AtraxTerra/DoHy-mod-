package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.widget.CloseButton;
import fr.atraxterra.dohy.client.ui.widget.RowList;
import fr.atraxterra.dohy.ui.view.JobDetailView;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La fiche d'un metier : l'hexagone et l'XP a gauche, trois onglets a droite
 * (deblocages, bonus, recompenses), chacun une liste qui defile.
 */
public class JobDetailScreen extends UiScreen implements ViewScreen<JobDetailView> {

    private static final String[] TABS = {"DÉBLOCAGES", "BONUS", "RÉCOMPENSES"};
    private static final int LIST_X = 165;
    private static final int LIST_Y = 70;

    private JobDetailView view;
    private int tab;
    private RowList list;

    public JobDetailScreen(JobDetailView view) {
        super(Component.literal("Metier"), 457, 256);
        this.view = view;
    }

    @Override
    public Identifier viewId() {
        return JobDetailView.ID;
    }

    @Override
    public void accept(JobDetailView next) {
        boolean sameJob = next.job().equals(view.job());
        this.view = next;
        if (!sameJob) {
            tab = 0;
            list.reset();
        }
        list.setCount(rows().size());
    }

    private List<JobDetailView.Row> rows() {
        return switch (tab) {
            case 1 -> view.perks();
            case 2 -> view.rewards();
            default -> view.unlocks();
        };
    }

    @Override
    protected void build() {
        add(new CloseButton(431, 16, this::onClose));
        add(new HexButton(Ui.texture("icon_back"), "Retour aux métiers",
                () -> UiClient.send(JobDetailView.ID, "back", "", 0)));
        for (int index = 0; index < TABS.length; index++) {
            add(new Tab(index));
        }
        list = add(new RowList(LIST_X, LIST_Y, 282, 176, 22, 2, this::paintRow));
        list.setCount(rows().size());
    }

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        g.panel(9, 0, 448, 256);
        int color = JobStyle.color(view.job());
        g.textShaded(Ui.TITLE, JobStyle.name(view.job()), 37, 18, 24, Ui.RED, Ui.RED_SHADE, 1.5f);

        int center = 93;
        g.texture(JobStyle.texture(view.job()), center - 38, 50, 76, 79, 256, 266);
        g.rect(center - 15, 124, 30, 20, color);
        g.rect(center - 15, 142, 30, 2, JobStyle.darker(color));
        g.centered(Ui.WIDE, "LVL.", center, 126.5f, 3.4f, Ui.WHITE);
        g.centered(Ui.WIDE, Integer.toString(view.level()), center, 131, 9, Ui.WHITE);

        g.text(Ui.WIDE, "EXPÉRIENCE", 37, 156, 4.2f, Ui.MUTED);
        int bar = 113;
        g.rect(37, 163, bar, 4, 0xFF2E2D34);
        g.rect(37, 163, bar * view.progress() / 1000, 4, Ui.GREEN);
        String xp = view.level() >= view.maxLevel() ? "NIVEAU MAXIMUM"
                : MoneyFormat.exact(view.xpInto()) + " / " + MoneyFormat.exact(view.xpNeed()) + " XP";
        g.text(Ui.BODY, xp, 37, 170, 4.0f, Ui.TEXT);

        g.text(Ui.WIDE, "PROCHAIN NIVEAU", 37, 184, 4.2f, Ui.MUTED);
        g.texture(Ui.MONEY, 37, 191, 10, 6, 56, 34);
        g.text(Ui.WIDE, view.nextMoney() > 0 ? "+" + MoneyFormat.exact(view.nextMoney()) : "-", 50, 190.5f, 5,
                Ui.WHITE);
        g.text(Ui.WIDE, "XP TOTALE", 37, 204, 4.2f, Ui.MUTED);
        g.text(Ui.WIDE, MoneyFormat.exact(view.totalXp()), 37, 211, 5, Ui.WHITE);

        g.rect(LIST_X - 4, LIST_Y - 4, 290, 184, Ui.INSET);
        if (rows().isEmpty()) {
            g.centered(Ui.BODY, "Rien ici pour ce métier", LIST_X + 141, LIST_Y + 80, 5, Ui.DISABLED);
        }
    }

    private void paintRow(UiGraphics g, int index, int x, int y, int width, int height, boolean hovered) {
        JobDetailView.Row row = rows().get(index);
        g.rect(x, y, width, height, hovered ? 0xFF2A292F : 0xFF1C1B21);
        int accent = switch (row.state()) {
            case JobDetailView.ACQUIRED -> Ui.GREEN;
            case JobDetailView.MISSING -> 0xFFF0961E;
            default -> 0xFF55545C;
        };
        g.rect(x, y, 2, height, accent);
        g.item(row.icon(), x + 7, y + 3, 16, false);
        boolean acquired = row.state() == JobDetailView.ACQUIRED;
        g.text(Ui.BOLD, g.fit(Ui.BOLD, row.title(), 4.6f, width - 96), x + 29, y + 5, 4.6f,
                acquired ? Ui.WHITE : Ui.TEXT);
        g.text(Ui.BODY, row.detail(), x + 29, y + 13, 3.8f, acquired ? 0xFF7BE38F : Ui.MUTED);
        String level = "NIV. " + row.level();
        g.right(Ui.WIDE, level, x + width - 20, y + 8.5f, 4.5f, acquired ? Ui.WHITE : Ui.MUTED);
        g.texture(acquired ? Ui.QUEST_DONE : Ui.QUEST_TODO, x + width - 15, y + 6, 9, 10, 87, 100,
                acquired ? 0xFFFFFFFF : 0xFF777777);
        if (hovered) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(row.title()));
            lines.add(Component.literal("Niveau " + row.level()).withColor(0xAAAAAA));
            for (String line : row.lines()) {
                lines.add(Component.literal(line).withColor(0x888888));
            }
            g.tooltip(lines);
        }
    }

    /** Un onglet : le libelle et le compte acquis / total. */
    private final class Tab extends UiElement {
        private final int index;

        Tab(int index) {
            super(LIST_X - 4 + index * 97, 50, 94, 14);
            this.index = index;
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            boolean active = tab == index;
            boolean hovered = contains(mouseX, mouseY);
            int face = active ? Ui.RED : hovered ? 0xFF3A3940 : Ui.FIELD;
            g.button(x, y, width, height, face, active ? Ui.RED_DARK : 0xFF1B1A20, false);
            List<JobDetailView.Row> rows = switch (index) {
                case 1 -> view.perks();
                case 2 -> view.rewards();
                default -> view.unlocks();
            };
            long done = rows.stream().filter(row -> row.state() == JobDetailView.ACQUIRED).count();
            String label = TABS[index];
            String count = done + "/" + rows.size();
            float labelWidth = g.width(Ui.WIDE, label, 4.4f);
            float countWidth = g.width(Ui.BODY, count, 3.6f);
            float start = x + (width - labelWidth - countWidth - 4) / 2.0f;
            g.text(Ui.WIDE, label, start, y + 4, 4.4f, Ui.WHITE);
            g.text(Ui.BODY, count, start + labelWidth + 4, y + 4.4f, 3.6f, active ? 0xFFFFD0CC : Ui.MUTED);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            if (tab != index) {
                tab = index;
                list.reset();
                list.setCount(rows().size());
                UiScreen.click(1.1f);
            }
            return true;
        }
    }

    /** L'hexagone rouge du bord gauche, avec une icone. */
    static final class HexButton extends UiElement {
        private final Identifier icon;
        private final String tooltip;
        private final Runnable action;

        HexButton(Identifier icon, String tooltip, Runnable action) {
            // L'icone fait 40x28 (fleche) ou 40x32 (maison) : on la centre.
            super(0, 108, 34, 39);
            this.icon = icon;
            this.tooltip = tooltip;
            this.action = action;
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            boolean hovered = contains(mouseX, mouseY);
            g.texture(Ui.HEXA, x, y, width, height, 128, 146, hovered ? 0xFFFFDDDD : 0xFFFFFFFF);
            g.texture(icon, x + 10, y + 14, 14, 10, 40, 28);
            if (hovered) {
                g.tooltip(List.of(Component.literal(tooltip)));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            UiScreen.click(0.9f);
            action.run();
            return true;
        }
    }

    static String upper(String text) {
        return text.toUpperCase(Locale.ROOT);
    }
}
