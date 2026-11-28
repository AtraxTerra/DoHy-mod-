package fr.atraxterra.dohy.client.pet;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Buttons;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.palamod.pet.Familiar;
import fr.atraxterra.dohy.palamod.pet.PetView;
import fr.atraxterra.dohy.ui.view.MenuView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Le menu du familier ({@code /pet}), dans la coque des menus : a gauche la fiche du
 * familier (portrait, niveau, bonheur, quetes du jour), a droite ses vingt
 * emplacements et la liste des competences. Un clic sur un emplacement le choisit,
 * un clic sur une competence l'y pose ; RETIRER vide l'emplacement choisi.
 *
 * <p>Sans familier, l'ecran explique comment en obtenir un et montre les vingt et un
 * familiers de PalaPet, cadres a la couleur de leur rarete.
 */
public class PetScreen extends UiScreen implements ViewScreen<PetView> {

    /** La fiche, a gauche : la largeur de celle d'un metier, puis l'espace avant le reste. */
    private static final float HERO = 126.7f;
    private static final float GAP = 8.0f;
    private static final float PORTRAIT = 58.0f;
    private static final float SLOT = 30.0f;
    private static final float SLOT_ROW = 34.3f;
    private static final float TAB_H = 17.0f;
    private static final float TAB_W = 70.0f;
    private static final float ROW_H = 15.3f;
    private static final float ROW_STEP = 17.3f;
    private static final float SCROLLBAR = 2.7f;
    private static final float BUTTON_W = 62.0f;
    private static final float BUTTON_H = 14.7f;
    private static final float TOGGLE_H = 14.0f;
    /** Sans familier : la colonne des etapes, puis la grille des familiers, sept par rangee. */
    private static final float STEPS_W = 214.0f;
    private static final int PER_ROW = 7;

    private static final int PINK = Ui.Theme.PINK;
    /** Les competences actives (celles de la roue), dans les emplacements et la liste. */
    private static final int ACTIVE = 0xFF3A86FF;
    private static final int LOCKED = 0xFF55575D;

    /** Les icones des competences : des objets du jeu qui disent ce qu'elles font. */
    static final Map<String, Item> ICONS = Map.ofEntries(
            Map.entry("aviator", Items.PHANTOM_MEMBRANE), Map.entry("good_pickaxe", Items.IRON_PICKAXE),
            Map.entry("budding_chemist", Items.BREWING_STAND), Map.entry("stomach", Items.COOKED_BEEF),
            Map.entry("aerial_expert", Items.ELYTRA), Map.entry("farming_expert", Items.WHEAT_SEEDS),
            Map.entry("miraculous_extractor", Items.GLASS_BOTTLE), Map.entry("fast_walking", Items.SUGAR),
            Map.entry("skilled_fisherman", Items.FISHING_ROD), Map.entry("experienced_pickaxe", Items.GOLDEN_PICKAXE),
            Map.entry("lightweight", Items.FEATHER), Map.entry("weak_spot", Items.IRON_SWORD),
            Map.entry("experience_absorber", Items.EXPERIENCE_BOTTLE), Map.entry("magnet", Items.LODESTONE),
            Map.entry("croquette_maker", Items.COOKIE), Map.entry("sharp_crusher", Items.GRINDSTONE),
            Map.entry("floral_chance", Items.POPPY), Map.entry("geology", Items.COBBLESTONE),
            Map.entry("self_sufficient", Items.CLOCK), Map.entry("magic_portal", Items.ENDER_EYE),
            Map.entry("decreased_cooldown", Items.AMETHYST_SHARD), Map.entry("portable_coffin", Items.CHEST),
            Map.entry("enhanced_growth", Items.OAK_SAPLING), Map.entry("powerful_knockback", Items.PISTON),
            Map.entry("legendary_expert", Items.NETHER_STAR), Map.entry("twirling_scythe", Items.IRON_HOE),
            Map.entry("gravitational_axe", Items.IRON_AXE), Map.entry("lucky_miner", Items.DIAMOND),
            Map.entry("electric_cloud", Items.TRIDENT), Map.entry("monster_slayer", Items.DIAMOND_SWORD),
            Map.entry("pocket_happiness", Items.CAKE), Map.entry("enchanted", Items.ENCHANTED_BOOK),
            Map.entry("unbreakable_pickaxe", Items.OBSIDIAN), Map.entry("earth_blessing", Items.BONE_MEAL),
            Map.entry("quick_change", Items.RECOVERY_COMPASS), Map.entry("explosion", Items.TNT),
            Map.entry("blessed_explosion", Items.SPAWNER), Map.entry("repair", Items.ANVIL),
            Map.entry("skillful_veterinarian", Items.SADDLE), Map.entry("bedrock_drill", Items.BEDROCK));

    /** Sans familier : les etapes, dans l'ordre. */
    private static final String[] STEPS = {
        "Fabrique une cage a familier (Chasseur niveau 15) : 4 batons et 4 lingots de titane, "
                + "1 amethyste compressee.",
        "Tue des serpents (75 %) ou des escargots (25 %) pour un appat.",
        "Clic droit sur ta cage avec l'appat : elle est chargee 5 minutes, et toutes les 2 minutes un "
                + "familier a 5 % de chances d'y entrer.",
        "Tu as alors 5 minutes pour venir l'apprivoiser : trois paliers (100, 200, 400 points) ; arrete "
                + "le curseur sur le vert (+50) ou le jaune (+25), evite le rouge (-50) et le noir (-150)."};

    private PetView view;
    private final Shell shell = new Shell(PetView.ID);
    private final Map<String, Anim.Value> hovers = new HashMap<>();
    private boolean actives;
    private int selected = -1;
    private float scroll;
    private final double opened = Anim.now();

    public PetScreen(PetView view) {
        super(Component.literal("Familier"), Shell.WIDTH, Shell.HEIGHT);
        this.view = view;
        header();
    }

    /** L'en-tete : avec un familier, ses competences posees et ses quetes du jour. */
    private void header() {
        List<MenuView.Stat> stats = new ArrayList<>();
        if (view.has()) {
            int used = 0;
            for (PetView.Slot slot : view.slots()) {
                if (!slot.skill().isEmpty()) {
                    used++;
                }
            }
            int done = 0;
            for (PetView.Mission mission : view.missions()) {
                if (mission.done()) {
                    done++;
                }
            }
            boolean all = !view.missions().isEmpty() && done == view.missions().size();
            stats.add(new MenuView.Stat("COMPETENCES POSEES", used + " / " + view.slotsOpen(), 0xFFFFFFFF));
            stats.add(new MenuView.Stat("QUETES DU JOUR", done + " / " + view.missions().size(),
                    all ? Ui.Theme.GREEN_L : 0xFFFFFFFF));
        } else {
            stats.add(new MenuView.Stat("FAMILIERS", Integer.toString(Familiar.values().length), 0xFFFFFFFF));
        }
        shell.header("Familier", "", stats, List.of(), false);
    }

    @Override
    public Identifier viewId() {
        return PetView.ID;
    }

    @Override
    public void accept(PetView next) {
        this.view = next;
        if (selected >= next.slotsOpen()) {
            selected = -1;
        }
        header();
    }

    @Override
    protected void build() {
    }

    @Override
    protected float lift() {
        return 5.0f * (1.0f - Anim.easeOut(Anim.progress(opened, 0.22d)));
    }

    static ItemStack icon(String skill) {
        return new ItemStack(ICONS.getOrDefault(skill, Items.BOOK));
    }

    private static boolean over(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private float hover(String key, boolean over) {
        return hovers.computeIfAbsent(key, ignored -> new Anim.Value(0.0f, 16.0f)).toward(over ? 1.0f : 0.0f);
    }

    private PetView.Skill skill(String id) {
        for (PetView.Skill skill : view.skills()) {
            if (skill.id().equals(id)) {
                return skill;
            }
        }
        return null;
    }

    private List<PetView.Skill> listed() {
        List<PetView.Skill> out = new ArrayList<>();
        for (PetView.Skill skill : view.skills()) {
            if (skill.active() == actives) {
                out.add(skill);
            }
        }
        return out;
    }

    private int count(boolean active) {
        int count = 0;
        for (PetView.Skill skill : view.skills()) {
            if (skill.active() == active) {
                count++;
            }
        }
        return count;
    }

    private boolean equipped(String id) {
        for (PetView.Slot slot : view.slots()) {
            if (slot.skill().equals(id)) {
                return true;
            }
        }
        return false;
    }

    // -- La geometrie, dans la zone de contenu de la coque ------------------------------------------

    private float top() {
        return shell.contentTop();
    }

    private float left() {
        return shell.mainLeft();
    }

    private static float bottom() {
        return Shell.HEIGHT - 7.0f;
    }

    private float skillsX() {
        return left() + HERO + GAP;
    }

    private float skillsWidth() {
        return shell.mainRight() - skillsX();
    }

    private float slotX(int index) {
        return skillsX() + (index % 10) * (skillsWidth() - SLOT) / 9.0f;
    }

    private float slotY(int index) {
        return top() + 14.0f + (index / 10) * SLOT_ROW;
    }

    private float tabsY() {
        return slotY(10) + SLOT + 22.0f;
    }

    private float tabX(boolean active) {
        return skillsX() + 2.0f + (active ? TAB_W + 2.0f : 0.0f);
    }

    private float listY() {
        return tabsY() + TAB_H + 5.0f;
    }

    private float buttonY() {
        return bottom() - BUTTON_H;
    }

    private float listHeight() {
        return buttonY() - 6.0f - listY();
    }

    private float listWidth() {
        return skillsWidth() - SCROLLBAR - 3.0f;
    }

    private float toggleY() {
        return top() + 8.0f + PORTRAIT + 3.0f + 17.3f + 13.0f + 14.3f + 25.3f;
    }

    // -- Le dessin ---------------------------------------------------------------------------------

    @Override
    protected void drawBackground(UiGraphics g, double mx, double my) {
        Paint p = g.paint();
        shell.draw(g, p, mx, my);
        List<Component> tip = new ArrayList<>();
        if (view.has()) {
            drawHero(g, p, mx, my, tip);
            drawSlots(g, p, mx, my, tip);
            drawSkills(g, p, mx, my, tip);
        } else {
            drawNone(g, p, mx, my, tip);
        }
        if (!tip.isEmpty()) {
            g.tooltip(tip);
        }
    }

    /** La fiche : portrait, nom, niveau et rarete, XP, bonheur, visibilite et quetes du jour. */
    private void drawHero(UiGraphics g, Paint p, double mx, double my, List<Component> tip) {
        float x = left();
        float y = top();
        float height = bottom() - y;
        float center = x + HERO / 2.0f;
        Familiar familiar = Familiar.byId(view.familiar());
        p.framed(x, y, HERO, height, 5.3f, 0xFF1C1D20, Ui.Theme.EDGE, 1);
        g.scissor(Math.round(x), Math.round(y), Math.round(HERO), Math.round(height));
        p.glow(center, y + 8.0f + PORTRAIT / 2.0f, 70.0f, Paint.alpha(PINK, 0.2f));
        g.endScissor();
        g.scissor(Math.round(x), Math.round(y), Math.round(HERO), 1);
        p.round(x, y, HERO, height, 5.3f, PINK);
        g.endScissor();

        float cursor = y + 8.0f;
        g.texture(FamiliarModels.portrait(view.familiar()), Math.round(center - PORTRAIT / 2.0f), Math.round(cursor),
                Math.round(PORTRAIT), Math.round(PORTRAIT), 96, 96);
        cursor += PORTRAIT + 3.0f;

        String name = view.label().toUpperCase(Locale.ROOT);
        float size = 13.3f;
        while (size > 8.0f && g.width(Ui.TITLE, name, size) > HERO - 12.0f) {
            size -= 0.5f;
        }
        g.textShaded(Ui.TITLE, name, center - g.width(Ui.TITLE, name, size) / 2.0f, cursor + (13.3f - size) / 2.0f,
                size, Ui.Theme.RED, Ui.Theme.RED_DD, 1.0f);
        cursor += 17.3f;

        // Le niveau et la rarete, deux pastilles centrees.
        String level = "NIVEAU " + view.level();
        String rarity = familiar == null ? "" : rarity(familiar).toUpperCase(Locale.ROOT);
        float levelWidth = pillWidth(g, level);
        float rarityWidth = rarity.isEmpty() ? 0.0f : pillWidth(g, rarity) + 3.0f;
        float pillX = center - (levelWidth + rarityWidth) / 2.0f;
        Shell.pill(g, p, level, pillX + levelWidth, cursor, PINK, true);
        if (!rarity.isEmpty()) {
            Shell.pill(g, p, rarity, pillX + levelWidth + rarityWidth, cursor, rarityColor(familiar), false);
        }
        cursor += 13.0f;

        // L'XP vers le niveau suivant.
        float barX = x + 8.0f;
        float barWidth = HERO - 16.0f;
        float appear = Anim.easeOut(Anim.progress(opened + 0.1d, 0.7d));
        boolean max = view.xpNext() <= 0;
        float done = max ? 1.0f : Math.min(1.0f, view.xp() / (float) view.xpNext());
        bar(p, barX, cursor, barWidth, 2.7f, done * appear, max ? Ui.Theme.YELLOW : PINK);
        cursor += 5.3f;
        g.text(Ui.PIXEL, max ? "NIVEAU MAXIMUM" : view.xp() + " / " + view.xpNext() + " XP", barX, cursor, 3.3f,
                Ui.Theme.MUTED);
        g.right(Ui.PIXEL, Math.round(done * 100.0f) + " %", barX + barWidth, cursor, 3.3f, Ui.Theme.MUTED);
        cursor += 9.0f;

        // Le bonheur et ce qu'il fait aux competences.
        float boxWidth = (barWidth - 4.0f) / 2.0f;
        float joy = view.maxHappiness() > 0 ? Math.min(1.0f, view.happiness() / (float) view.maxHappiness()) : 0.0f;
        statBox(g, p, barX, cursor, boxWidth, "BONHEUR", view.happiness() + " / " + view.maxHappiness(), PINK);
        bar(p, barX + 4.7f, cursor + 16.3f, boxWidth - 9.4f, 1.3f, joy * appear, PINK);
        String multiplier = String.format(Locale.FRANCE, "x%.2f", view.happiness() / 100.0f);
        statBox(g, p, barX + boxWidth + 4.0f, cursor, boxWidth, "COMPETENCES", multiplier, Ui.Theme.YELLOW);
        if (over(mx, my, barX, cursor, barWidth, 19.3f)) {
            tip.add(Component.literal("Le bonheur multiplie tes competences : 100 = x1, 200 = x2."));
            tip.add(Component.literal("-25 si les quatre quetes du jour manquent a minuit."));
        }

        // Visible sur l'epaule, ou cache : un clic bascule.
        float toggleY = toggleY();
        boolean overToggle = over(mx, my, barX, toggleY, barWidth, TOGGLE_H);
        Buttons.draw(g, p, barX, toggleY, barWidth, TOGGLE_H, view.visible() ? "VISIBLE SUR L'EPAULE" : "CACHE",
                view.visible() ? Buttons.GHOST_ON : Buttons.GHOST, hover("visible", overToggle), true, 4.3f);
        int light = view.visible() ? Ui.Theme.GREEN : Ui.Theme.DIM;
        if (view.visible()) {
            p.glow(barX + 6.7f, toggleY + TOGGLE_H / 2.0f, 5.0f, Paint.alpha(light, 0.5f));
        }
        p.dot(barX + 6.7f, toggleY + TOGGLE_H / 2.0f, 1.7f, light);
        if (overToggle) {
            tip.add(Component.literal(view.visible() ? "Clic : cacher ton familier" : "Clic : montrer ton familier"));
        }

        // Les quetes du jour.
        cursor = toggleY + TOGGLE_H + 9.0f;
        g.text(Ui.PIXEL, "QUETES DU JOUR", barX, cursor, 3.3f, Ui.Theme.MUTED);
        g.right(Ui.BODY, "remise a zero dans " + duration(view.resetMs()), barX + barWidth, cursor - 0.4f, 4.0f,
                Ui.Theme.DIM);
        cursor += 8.0f;
        for (PetView.Mission mission : view.missions()) {
            if (cursor + 15.0f > y + height) {
                break;
            }
            String reward = "+" + mission.xp() + " XP";
            float rewardWidth = g.width(Ui.WIDE, reward, 4.0f);
            g.text(Ui.BODY, g.fit(Ui.BODY, mission.label(), 4.7f, barWidth - rewardWidth - 4.0f), barX, cursor, 4.7f,
                    mission.done() ? Ui.Theme.GREEN_L : Ui.Theme.TEXT2);
            g.right(Ui.WIDE, reward, barX + barWidth, cursor + 0.4f, 4.0f, Ui.Theme.YELLOW);
            float part = Math.min(1.0f, mission.progress() / (float) Math.max(1, mission.goal()));
            bar(p, barX, cursor + 7.0f, barWidth, 2.0f, part * appear, mission.done() ? Ui.Theme.GREEN : ACTIVE);
            g.text(Ui.BODY, mission.progress() + " / " + mission.goal() + " " + mission.unit(), barX, cursor + 10.7f,
                    4.0f, Ui.Theme.MUTED);
            cursor += 21.0f;
        }
    }

    private static float pillWidth(UiGraphics g, String text) {
        return Math.max(8.0f, g.width(Ui.PIXEL, text, 3.3f) + 5.4f);
    }

    /** Une case chiffree de la fiche, comme celles d'un metier. */
    private static void statBox(UiGraphics g, Paint p, float x, float y, float width, String label, String value,
                                int color) {
        p.framed(x, y, width, 19.3f, 3.3f, 0xFF16171A, 0xFF2A2C31, 1);
        g.text(Ui.PIXEL, g.fit(Ui.PIXEL, label, 3.3f, width - 8.0f), x + 4.7f, y + 3.6f, 3.3f, Ui.Theme.MUTED);
        g.text(Ui.WIDE, g.fit(Ui.WIDE, value, 5.7f, width - 8.0f), x + 4.7f, y + 8.6f, 5.7f, color);
    }

    /** Une barre arrondie : le creux, et le plein avec son halo. */
    private static void bar(Paint p, float x, float y, float width, float height, float progress, int color) {
        p.round(x, y, width, height, height / 2.0f, 0xFF2C2E33);
        float filled = Math.max(0.0f, Math.min(1.0f, progress)) * width;
        if (filled < 0.5f) {
            return;
        }
        p.glow(x + filled, y + height / 2.0f, 5.0f, Paint.alpha(color, 0.4f));
        p.round(x, y, Math.max(height, filled), height, height / 2.0f, color);
    }

    /** Les vingt emplacements, deux rangees de dix ; un s'ouvre tous les cinq niveaux. */
    private void drawSlots(UiGraphics g, Paint p, double mx, double my, List<Component> tip) {
        int open = view.slotsOpen();
        String count = open + " / 20" + (open < 20 ? "   PROCHAIN AU NIVEAU " + (open + 1) * 5 : "");
        Shell.section(g, p, skillsX(), top(), skillsWidth(), "upgrade", "EMPLACEMENTS", count);
        for (int index = 0; index < 20; index++) {
            float x = slotX(index);
            float y = slotY(index);
            boolean unlocked = index < open;
            boolean overSlot = over(mx, my, x, y, SLOT, SLOT);
            if (!unlocked) {
                p.framed(x, y, SLOT, SLOT, 4.0f, 0xFF121316, 0xFF1F2124, 1);
                g.centered(Ui.PIXEL, "NIV. " + (index + 1) * 5, x + SLOT / 2.0f, y + SLOT / 2.0f - 1.7f, 3.3f,
                        Ui.Theme.DIM);
                if (overSlot) {
                    tip.add(Component.literal("S'ouvre au niveau " + (index + 1) * 5));
                }
                continue;
            }
            PetView.Slot slot = index < view.slots().size() ? view.slots().get(index) : new PetView.Slot("", 0L);
            boolean chosen = index == selected;
            float hover = hover("slot:" + index, overSlot);
            if (chosen) {
                p.glow(x + SLOT / 2.0f, y + SLOT / 2.0f, SLOT * 0.8f, Paint.alpha(Ui.Theme.RED, 0.32f));
            }
            int border = chosen ? Ui.Theme.RED : Paint.mix(0xFF2A2C31, 0xFF5A5D65, hover);
            p.framed(x, y, SLOT, SLOT, 4.0f, Paint.mix(0xFF16171A, 0xFF1F2024, hover), border, chosen ? 2 : 1);
            if (slot.skill().isEmpty()) {
                int plus = Paint.mix(0xFF34363C, 0xFF6A6D75, hover);
                p.rect(x + SLOT / 2.0f - 3.0f, y + SLOT / 2.0f - 0.34f, 6.0f, 0.67f, plus);
                p.rect(x + SLOT / 2.0f - 0.34f, y + SLOT / 2.0f - 3.0f, 0.67f, 6.0f, plus);
                if (overSlot) {
                    tip.add(Component.literal("Emplacement libre : choisis-le, puis une competence"));
                }
                continue;
            }
            PetView.Skill skill = skill(slot.skill());
            g.item(icon(slot.skill()), x + 7.0f, y + 7.0f, 16.0f, false);
            if (skill != null && skill.active()) {
                p.dot(x + 4.7f, y + 4.7f, 1.7f, ACTIVE);
            }
            if (slot.lockMs() > 0) {
                p.dot(x + SLOT - 4.7f, y + 4.7f, 1.7f, Ui.Theme.RED);
            }
            if (overSlot && skill != null) {
                describe(skill, tip);
                tip.add(Component.literal(slot.lockMs() > 0 ? "Verrouille encore " + duration(slot.lockMs())
                        : "Changeable"));
            }
        }
    }

    /** Les onglets passives et actives, la liste, puis RETIRER et le message du serveur. */
    private void drawSkills(UiGraphics g, Paint p, double mx, double my, List<Component> tip) {
        float x = skillsX();
        float width = skillsWidth();
        Shell.section(g, p, x, slotY(10) + SLOT + 9.0f, width, "craft", "COMPETENCES", "");

        // Les deux onglets, dans leur creux, et la consigne a cote.
        float tabsY = tabsY();
        p.framed(x, tabsY, TAB_W * 2.0f + 6.0f, TAB_H, 5.3f, Ui.Theme.INSET, 0xFF24262A, 1);
        tab(g, p, "Passives", count(false), false, mx, my);
        tab(g, p, "Actives", count(true), true, mx, my);
        String hint;
        if (selected >= 0) {
            hint = "Emplacement " + (selected + 1) + " : clique une competence pour l'y poser";
        } else if (actives) {
            KeyMapping wheel = PetClient.wheelKey();
            hint = "Les actives se lancent depuis la roue : touche "
                    + (wheel == null ? "R" : wheel.getTranslatedKeyMessage().getString());
        } else {
            hint = "Choisis un emplacement, puis une competence";
        }
        float hintX = x + TAB_W * 2.0f + 14.0f;
        g.text(Ui.BODY, g.fit(Ui.BODY, hint, 4.7f, x + width - hintX), hintX, tabsY + 6.0f, 4.7f,
                selected >= 0 ? Ui.Theme.TEXT2 : Ui.Theme.MUTED);

        // La liste.
        List<PetView.Skill> rows = listed();
        float listY = listY();
        float listHeight = listHeight();
        float listWidth = listWidth();
        float content = rows.size() * ROW_STEP - (ROW_STEP - ROW_H);
        float maxScroll = Math.max(0.0f, content - listHeight);
        scroll = Math.max(0.0f, Math.min(maxScroll, scroll));
        boolean inList = over(mx, my, x, listY, listWidth, listHeight);
        g.scissor(Math.round(x), Math.round(listY), Math.round(listWidth + 1.0f), Math.round(listHeight));
        for (int index = 0; index < rows.size(); index++) {
            float y = listY + index * ROW_STEP - scroll;
            if (y + ROW_H < listY || y > listY + listHeight) {
                continue;
            }
            PetView.Skill skill = rows.get(index);
            boolean overRow = inList && my >= y && my < y + ROW_H;
            row(g, p, skill, x, y, listWidth, hover("row:" + skill.id(), overRow));
            if (overRow) {
                describe(skill, tip);
                tip.add(Component.literal(selected >= 0 ? "Clic : poser dans l'emplacement " + (selected + 1)
                        : "Choisis d'abord un emplacement"));
            }
        }
        g.endScissor();
        if (maxScroll > 0.0f) {
            float trackX = x + width - SCROLLBAR;
            p.round(trackX, listY, SCROLLBAR, listHeight, SCROLLBAR / 2.0f, 0xFF1F2124);
            float thumb = Math.max(16.0f, listHeight * listHeight / content);
            float thumbY = listY + (listHeight - thumb) * scroll / maxScroll;
            p.round(trackX, thumbY, SCROLLBAR, thumb, SCROLLBAR / 2.0f, 0xFF4A4D55);
        }

        // RETIRER vide l'emplacement choisi ; le message du serveur a cote.
        float buttonY = buttonY();
        boolean canClear = selected >= 0 && selected < view.slots().size()
                && !view.slots().get(selected).skill().isEmpty();
        boolean overButton = canClear && over(mx, my, x, buttonY, BUTTON_W, BUTTON_H);
        Buttons.draw(g, p, x, buttonY, BUTTON_W, BUTTON_H, "RETIRER", Buttons.RED, hover("clear", overButton),
                canClear, 5.0f);
        if (!view.notice().isEmpty()) {
            float noticeX = x + BUTTON_W + 8.0f;
            g.text(Ui.BODY, g.fit(Ui.BODY, view.notice(), 5.0f, x + width - noticeX), noticeX, buttonY + 4.8f, 5.0f,
                    view.noticeError() ? Ui.Theme.RED_LIGHT : Ui.Theme.GREEN_L);
        }
    }

    private void tab(UiGraphics g, Paint p, String label, int count, boolean active, double mx, double my) {
        boolean on = actives == active;
        float x = tabX(active);
        float y = tabsY() + 2.0f;
        float height = TAB_H - 4.0f;
        float hover = hover(active ? "tab:actives" : "tab:passives", !on && over(mx, my, x, y, TAB_W, height));
        if (on) {
            p.shadow(x + 2, y + 3, TAB_W - 4, height - 2, 5.0f, Paint.alpha(Ui.Theme.RED, 0.28f));
            p.roundGradient(x, y, TAB_W, height, 3.7f, Ui.Theme.RED_LIGHT, Ui.Theme.RED_D);
            p.rect(x + 3.7f, y + 0.34f, TAB_W - 7.4f, 0.34f, 0x38FFFFFF);
        } else if (hover > 0.01f) {
            p.round(x, y, TAB_W, height, 3.7f, Paint.alpha(0xFFFFFFFF, 0.05f * hover));
        }
        String number = Integer.toString(count);
        float labelWidth = g.width(Ui.BOLD, label, 5.3f);
        float numberWidth = g.width(Ui.PIXEL, number, 3.6f);
        float textX = x + (TAB_W - labelWidth - 3.3f - numberWidth) / 2.0f;
        g.text(Ui.BOLD, label, textX, y + 4.0f, 5.3f,
                on ? Ui.Theme.TEXT : Paint.mix(Ui.Theme.TEXT2, Ui.Theme.TEXT, hover));
        g.text(Ui.PIXEL, number, textX + labelWidth + 3.3f, y + 5.0f, 3.6f, on ? 0xCCFFFFFF : Ui.Theme.MUTED);
    }

    /** Une competence de la liste : le lisere a sa couleur, l'icone, le nom, et ce qu'elle vaut. */
    private void row(UiGraphics g, Paint p, PetView.Skill skill, float x, float y, float width, float hover) {
        boolean unlocked = view.level() >= skill.unlock();
        boolean used = equipped(skill.id());
        int accent = used ? Ui.Theme.YELLOW : !unlocked ? LOCKED : skill.active() ? ACTIVE : Ui.Theme.GREEN;
        int fill = unlocked ? Paint.mix(Ui.Theme.CARD, Ui.Theme.CARD_HOVER, hover) : 0xFF1D1F22;
        p.framed(x, y, width, ROW_H, 4.0f, fill, Paint.mix(Ui.Theme.EDGE, Paint.mix(Ui.Theme.EDGE, accent, 0.5f), hover),
                1);
        p.round(x, y + 3.7f, 1.34f, ROW_H - 7.4f, 0.67f, accent);
        g.item(icon(skill.id()), x + 5.3f, y + (ROW_H - 10.7f) / 2.0f, 10.7f, false);
        float textX = x + 20.7f;
        float right = x + width - 5.3f;
        float middle = y + ROW_H / 2.0f;
        if (!unlocked) {
            g.texture(Ui.texture("spawner_lock"), Math.round(right - 6.0f), Math.round(middle - 3.0f), 6, 6, 32, 32);
            right -= 9.0f;
            String level = "NIV. " + skill.unlock();
            g.right(Ui.WIDE, level, right, middle - 2.3f, 4.7f, Ui.Theme.DIM);
            right -= g.width(Ui.WIDE, level, 4.7f) + 6.0f;
        } else {
            if (!skill.now().isEmpty()) {
                String now = g.fit(Ui.WIDE, skill.now(), 4.7f, width * 0.4f);
                g.right(Ui.WIDE, now, right, middle - 2.3f, 4.7f, used ? Ui.Theme.YELLOW : Ui.Theme.GREEN_L);
                right -= g.width(Ui.WIDE, now, 4.7f) + 5.0f;
            }
            if (used) {
                right -= Shell.pill(g, p, "POSEE", right, middle - 4.0f, Ui.Theme.YELLOW, false) + 4.0f;
            }
        }
        g.text(Ui.BOLD, g.fit(Ui.BOLD, skill.label(), 5.7f, right - textX), textX, middle - 2.9f, 5.7f,
                unlocked ? Ui.Theme.TEXT : Ui.Theme.MUTED);
    }

    private static void describe(PetView.Skill skill, List<Component> tip) {
        tip.add(Component.literal(skill.label() + (skill.active() ? " (active)" : " (passive)")));
        tip.add(Component.literal(skill.description()));
        tip.add(Component.literal(skill.tiers()));
        if (!skill.now().isEmpty()) {
            tip.add(Component.literal("Pour ton familier : " + skill.now()));
        }
        if (skill.active()) {
            tip.add(Component.literal("Recharge : " + duration(skill.cooldownSeconds() * 1000L)
                    + (skill.cooldownMs() > 0 ? " (encore " + duration(skill.cooldownMs()) + ")" : "")));
        }
    }

    /** Sans familier : comment en obtenir un, et les vingt et un familiers possibles. */
    private void drawNone(UiGraphics g, Paint p, double mx, double my, List<Component> tip) {
        float x = left();
        float y = top();

        // Ce qui manque, et ce qu'un familier apporte.
        float cardHeight = 45.0f;
        p.framed(x, y, STEPS_W, cardHeight, 5.3f, 0xFF1C1D20, Ui.Theme.EDGE, 1);
        g.scissor(Math.round(x), Math.round(y), Math.round(STEPS_W), Math.round(cardHeight));
        p.glow(x + 16.0f, y + 10.0f, 46.0f, Paint.alpha(PINK, 0.16f));
        g.endScissor();
        g.scissor(Math.round(x), Math.round(y), Math.round(STEPS_W), 1);
        p.round(x, y, STEPS_W, cardHeight, 5.3f, PINK);
        g.endScissor();
        g.text(Ui.WIDE, "TU N'AS PAS ENCORE DE FAMILIER", x + 8.0f, y + 8.0f, 6.0f, PINK);
        g.wrapped(Ui.BODY, "Un seul familier par joueur, pour toujours. Il gagne des niveaux (1 a 100), ouvre "
                + "vingt emplacements de competences et suit ton bonheur.", x + 8.0f, y + 19.0f, 5.0f,
                STEPS_W - 16.0f, 7.3f, Ui.Theme.TEXT2, 3);

        // Les etapes, numerotees.
        float cursor = y + cardHeight + 9.0f;
        Shell.section(g, p, x, cursor, STEPS_W, "craft", "COMMENT L'OBTENIR", "");
        cursor += 15.0f;
        for (int index = 0; index < STEPS.length; index++) {
            p.framed(x, cursor - 1.3f, 10.0f, 10.0f, 5.0f, Ui.Theme.CARD, 0xFF34363C, 1);
            g.centered(Ui.BOLD, Integer.toString(index + 1), x + 5.0f, cursor + 1.6f, 5.0f, Ui.Theme.RED);
            int lines = g.wrapped(Ui.BODY, STEPS[index], x + 15.0f, cursor, 5.0f, STEPS_W - 15.0f, 7.3f,
                    Ui.Theme.TEXT2, 4);
            cursor += lines * 7.3f + 6.0f;
        }

        // Les familiers de PalaPet, cadres a la couleur de leur rarete.
        float gridX = x + STEPS_W + 12.0f;
        float gridWidth = shell.mainRight() - gridX;
        Familiar[] all = Familiar.values();
        Shell.section(g, p, gridX, y, gridWidth, "upgrade", "LES FAMILIERS", Integer.toString(all.length));
        float step = (gridWidth + 4.0f) / PER_ROW;
        float tile = step - 4.0f;
        float gridY = y + 15.0f;
        for (int index = 0; index < all.length; index++) {
            Familiar familiar = all[index];
            float tileX = gridX + (index % PER_ROW) * step;
            float tileY = gridY + (index / PER_ROW) * step;
            boolean overTile = over(mx, my, tileX, tileY, tile, tile);
            float hover = hover("familiar:" + familiar.id(), overTile);
            int color = rarityColor(familiar);
            if (hover > 0.01f) {
                p.glow(tileX + tile / 2.0f, tileY + tile / 2.0f, tile * 0.75f, Paint.alpha(color, 0.3f * hover));
            }
            p.framed(tileX, tileY, tile, tile, 4.0f, Paint.mix(0xFF16171A, 0xFF22232A, hover),
                    Paint.mix(Paint.mix(0xFF2A2C31, color, 0.7f), color, hover), 1);
            g.texture(FamiliarModels.portrait(familiar.id()), Math.round(tileX + 1.0f), Math.round(tileY + 1.0f),
                    Math.round(tile - 2.0f), Math.round(tile - 2.0f), 96, 96);
            if (overTile) {
                tip.add(Component.literal(familiar.label()).withStyle(ChatFormatting.WHITE));
                tip.add(Component.literal(rarity(familiar)).withStyle(Style.EMPTY.withColor(color & 0xFFFFFF)));
            }
        }
        int rows = (all.length + PER_ROW - 1) / PER_ROW;
        cursor = gridY + rows * step + 4.0f;

        // La legende des raretes, puis la note sur la cage.
        float legendX = gridX;
        for (int weight : new int[] {10, 5, 2, 1}) {
            String label = rarityLabel(weight);
            p.dot(legendX + 2.0f, cursor + 2.6f, 2.0f, rarityColor(weight));
            g.text(Ui.BODY, label, legendX + 6.7f, cursor, 4.7f, Ui.Theme.TEXT2);
            legendX += 6.7f + g.width(Ui.BODY, label, 4.7f) + 9.0f;
        }
        g.wrapped(Ui.BODY, "La cage choisit au hasard, les familiers d'evenement plus rarement. Leur allure ne "
                + "change rien aux competences.", gridX, cursor + 11.0f, 4.8f, gridWidth, 7.0f, Ui.Theme.MUTED, 3);
    }

    private static String rarity(Familiar familiar) {
        return rarityLabel(familiar.weight());
    }

    private static String rarityLabel(int weight) {
        return switch (weight) {
            case 10 -> "Commun";
            case 5 -> "Peu commun";
            case 2 -> "Rare";
            default -> "Legendaire";
        };
    }

    private static int rarityColor(Familiar familiar) {
        return rarityColor(familiar.weight());
    }

    private static int rarityColor(int weight) {
        return switch (weight) {
            case 10 -> 0xFF5A5B62;
            case 5 -> 0xFF22B14C;
            case 2 -> 0xFF9B59D9;
            default -> 0xFFFFC533;
        };
    }

    static String duration(long millis) {
        long minutes = Math.max(1L, (millis + 59_999L) / 60_000L);
        if (minutes < 60L) {
            return minutes + " min";
        }
        return String.format(Locale.FRANCE, "%d h %02d", minutes / 60L, minutes % 60L);
    }

    // -- Les clics --------------------------------------------------------------------------------

    @Override
    protected boolean backgroundClicked(double mx, double my, int button) {
        if (button == 0 && shell.click(mx, my)) {
            return true;
        }
        if (!view.has()) {
            return false;
        }
        if (over(mx, my, left() + 8.0f, toggleY(), HERO - 16.0f, TOGGLE_H)) {
            click(1.0f);
            UiClient.send(PetView.ID, "visible", "", 0);
            return true;
        }
        for (int index = 0; index < view.slotsOpen() && index < 20; index++) {
            if (over(mx, my, slotX(index), slotY(index), SLOT, SLOT)) {
                click(1.1f);
                selected = selected == index ? -1 : index;
                return true;
            }
        }
        float tabY = tabsY() + 2.0f;
        if (over(mx, my, tabX(false), tabY, TAB_W, TAB_H - 4.0f)) {
            if (actives) {
                actives = false;
                scroll = 0.0f;
                click(1.0f);
            }
            return true;
        }
        if (over(mx, my, tabX(true), tabY, TAB_W, TAB_H - 4.0f)) {
            if (!actives) {
                actives = true;
                scroll = 0.0f;
                click(1.0f);
            }
            return true;
        }
        if (over(mx, my, skillsX(), buttonY(), BUTTON_W, BUTTON_H) && selected >= 0) {
            click(0.9f);
            UiClient.send(PetView.ID, "clear", String.valueOf(selected), 0);
            return true;
        }
        if (over(mx, my, skillsX(), listY(), listWidth(), listHeight())) {
            int index = (int) ((my - listY() + scroll) / ROW_STEP);
            List<PetView.Skill> rows = listed();
            if (index >= 0 && index < rows.size() && selected >= 0) {
                click(1.3f);
                UiClient.send(PetView.ID, "assign", selected + ":" + rows.get(index).id(), 0);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = designX(mouseX);
        double y = designY(mouseY);
        if (view.has() && over(x, y, skillsX(), listY(), skillsWidth(), listHeight())) {
            scroll -= (float) scrollY * ROW_STEP;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
