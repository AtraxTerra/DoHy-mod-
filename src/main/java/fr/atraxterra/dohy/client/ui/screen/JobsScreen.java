package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.widget.CloseButton;
import fr.atraxterra.dohy.ui.view.JobsView;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;

/**
 * Les metiers, sur la maquette de PalaJobs : quatre hexagones avec leur
 * niveau, et les contrats du jour en bas comme « quetes quotidiennes ».
 */
public class JobsScreen extends UiScreen implements ViewScreen<JobsView> {

    /** L'ordre et les noms de l'ecran de Paladium. */
    private static final String[] ORDER = {"MINER", "FARMER", "HUNTER", "ALCHEMIST"};
    private static final String[] NAMES = {"MINEUR", "FERMIER", "CHASSEUR", "ALCHIMISTE"};
    private static final int[] CENTERS = {80, 181, 283, 384};
    private static final int[] COLORS = {0xFFEF3826, 0xFFFAC400, 0xFF2F5BEA, 0xFFEC4FB5};

    private static final int PANEL_X = 9;
    private static final int ART = 64;

    private JobsView view;

    public JobsScreen(JobsView view) {
        super(Component.literal("Metiers"), 457, 256);
        this.view = view;
    }

    @Override
    public Identifier viewId() {
        return JobsView.ID;
    }

    @Override
    public void accept(JobsView next) {
        this.view = next;
    }

    @Override
    protected void build() {
        add(new CloseButton(431, 16, this::onClose));
        add(new HomeButton());
        for (int index = 0; index < ORDER.length; index++) {
            add(new JobCard(index));
        }
    }

    private JobsView.JobLine line(String job) {
        for (JobsView.JobLine line : view.jobs()) {
            if (line.job().equals(job)) {
                return line;
            }
        }
        return null;
    }

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        g.panel(PANEL_X, 0, 448, 256);
        g.textShaded(Ui.TITLE, "MÉTIERS", 37, 18, 24, Ui.RED, Ui.RED_SHADE, 1.5f);

        g.text(Ui.WIDE, "QUÊTES QUOTIDIENNES", 37, 188.5f, 7, Ui.WHITE);
        String bonus = permille(view.questBonus());
        String multiplier = permille(view.xpMultiplier());
        float right = 440;
        g.right(Ui.BOLD, multiplier, right, 189, 5.5f, Ui.WHITE);
        right -= g.width(Ui.BOLD, multiplier, 5.5f) + 8;
        g.right(Ui.BODY, "MULTIPLICATEUR EXP. MÉTIER", right, 189, 5.5f, Ui.TEXT);
        right -= g.width(Ui.BODY, "MULTIPLICATEUR EXP. MÉTIER", 5.5f) + 6;
        g.rect(Math.round(right), 187, 1, 10, Ui.TEXT);
        right -= 6;
        g.right(Ui.BOLD, bonus, right, 189, 5.5f, Ui.WHITE);
        right -= g.width(Ui.BOLD, bonus, 5.5f) + 8;
        g.right(Ui.BODY, "BONUS QUÊTE", right, 189, 5.5f, Ui.TEXT);

        List<JobsView.Quest> quests = view.quests();
        int[] cardX = {38, 173, 308};
        for (int index = 0; index < 3; index++) {
            quest(g, cardX[index], 201, index < quests.size() ? quests.get(index) : null);
        }
    }

    private static String permille(int value) {
        return String.format(Locale.FRANCE, "%.1f%%", value / 10.0);
    }

    private static void quest(UiGraphics g, int x, int y, JobsView.Quest quest) {
        g.rect(x, y, 132, 39, Ui.INSET);
        if (quest == null) {
            g.centered(Ui.BODY, "Aucun contrat", x + 66, y + 17, 4.5f, Ui.DISABLED);
            return;
        }
        g.texture(quest.done() ? Ui.QUEST_DONE : Ui.QUEST_TODO, x + 7, y + 7, 21, 24, 87, 100);
        g.text(Ui.BOLD, g.fit(Ui.BOLD, quest.title().toUpperCase(Locale.ROOT), 4.6f, 70), x + 35, y + 8, 4.6f,
                Ui.WHITE);
        g.wrapped(Ui.BODY, quest.description().toUpperCase(Locale.ROOT), x + 35, y + 14.5f, 4.0f, 90, 5.0f,
                Ui.TEXT, 2);
        int barWidth = 51;
        long goal = Math.max(1, quest.goal());
        int filled = (int) Math.min(barWidth, barWidth * quest.progress() / goal);
        g.rect(x + 35, y + 29, barWidth, 2, 0xFF2E2D34);
        g.rect(x + 35, y + 29, filled, 2, quest.done() ? 0xFF39CF44 : Ui.GREEN);
        g.text(Ui.WIDE, MoneyFormat.shortForm(quest.progress()) + "/" + MoneyFormat.shortForm(quest.goal()),
                x + 89, y + 28, 3.8f, Ui.WHITE);
        String reward = MoneyFormat.shortForm(quest.reward());
        float badge = 12 + g.width(Ui.WIDE, reward, 3.6f);
        int badgeX = Math.round(x + 130 - badge);
        g.rect(badgeX, y + 2, Math.round(badge), 8, 0xFF2B2A31);
        g.texture(Ui.MONEY, badgeX + 2, y + 4, 7, 4, 56, 34);
        g.text(Ui.WIDE, reward, badgeX + 10, y + 4.2f, 3.6f, Ui.WHITE);
    }

    /** Un metier : son nom, l'hexagone, la banniere du niveau. */
    private final class JobCard extends UiElement {
        private final int index;

        JobCard(int index) {
            super(CENTERS[index] - ART / 2, 66, ART, 102);
            this.index = index;
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            int center = CENTERS[index];
            boolean hovered = contains(mouseX, mouseY);
            g.centered(Ui.WIDE, NAMES[index], center, 70, 10, Ui.WHITE);
            String key = ORDER[index].toLowerCase(Locale.ROOT);
            int lift = hovered ? 2 : 0;
            g.texture(Ui.texture("jobs/" + key), center - ART / 2, 88 - lift, ART, 66, 256, 266);
            JobsView.JobLine line = line(ORDER[index]);
            int level = line == null ? 0 : line.level();
            int bannerX = center - 15;
            int bannerY = 146 - lift;
            g.rect(bannerX, bannerY, 30, 20, COLORS[index]);
            g.rect(bannerX, bannerY + 18, 30, 2, darker(COLORS[index]));
            g.centered(Ui.WIDE, "LVL.", center, bannerY + 2.5f, 3.4f, Ui.WHITE);
            g.centered(Ui.WIDE, Integer.toString(level), center, bannerY + 7, 9, Ui.WHITE);
            if (hovered && line != null) {
                g.tooltip(List.of(
                        Component.literal(NAMES[index]).withColor(COLORS[index] & 0xFFFFFF),
                        Component.literal("Niveau " + line.level() + " / " + line.maxLevel()),
                        Component.literal(MoneyFormat.exact(line.xp()) + " / " + MoneyFormat.exact(line.xpNext())
                                + " XP  (" + line.progress() / 10 + " %)").withColor(0xAAAAAA),
                        Component.literal("Clic : deblocages, bonus et recompenses").withColor(0x777777)));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            UiScreen.click(1.0f);
            UiClient.send(JobsView.ID, "open", ORDER[index], 0);
            return true;
        }
    }

    private static int darker(int color) {
        int r = ((color >> 16) & 0xFF) * 6 / 10;
        int g = ((color >> 8) & 0xFF) * 6 / 10;
        int b = (color & 0xFF) * 6 / 10;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** L'hexagone rouge du bord gauche : retour au menu. */
    private final class HomeButton extends UiElement {
        HomeButton() {
            super(0, 108, 34, 39);
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            boolean hovered = contains(mouseX, mouseY);
            g.texture(Ui.HEXA, x, y, width, height, 128, 146, hovered ? 0xFFFFDDDD : 0xFFFFFFFF);
            g.texture(Ui.HOME, x + 10, y + 13, 14, 12, 40, 32);
            if (hovered) {
                g.tooltip(List.of(Component.literal("Retour au menu")));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            UiScreen.click(0.9f);
            UiClient.send(JobsView.ID, "home", "", 0);
            return true;
        }
    }
}
