package fr.atraxterra.dohy.client.pet;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.palamod.pet.Familiar;
import fr.atraxterra.dohy.palamod.pet.PetView;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Le menu du familier ({@code /pet}) : a gauche le familier, son niveau, son
 * bonheur et ses quetes du jour ; a droite ses vingt emplacements et la liste des
 * competences. Un clic sur un emplacement le choisit, un clic sur une competence
 * l'y pose ; RETIRER vide l'emplacement choisi.
 */
public class PetScreenOld extends UiScreen implements ViewScreen<PetView> {

    private static final int W = Shell.WIDTH;
    private static final int H = Shell.HEIGHT;

    private static final float LEFT = 22.0f;
    private static final float LEFT_W = 196.0f;
    private static final float RIGHT = 232.0f;
    private static final float RIGHT_W = 354.0f;
    private static final float SLOT = 30.0f;
    private static final float SLOT_STEP = 35.0f;
    private static final float SLOTS_Y = 46.0f;
    private static final float TAB_Y = 124.0f;
    private static final float TAB_H = 15.0f;
    private static final float LIST_Y = 144.0f;
    private static final float LIST_H = 148.0f;
    private static final float ROW_H = 17.0f;
    private static final float ROW_STEP = 19.0f;
    private static final float BUTTON_Y = 300.0f;

    private static final int PINK = 0xFFF3445D;
    private static final int GOLD = 0xFFFAC400;
    private static final int RED = 0xFFEF3926;
    private static final int GREEN = 0xFF30CB1F;

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

    private PetView view;
    private boolean actives;
    private int selected = -1;
    private float scroll;
    private final double opened = Anim.now();

    public PetScreenOld(PetView view) {
        super(Component.literal("Familier"), W, H);
        this.view = view;
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
    }

    @Override
    protected void build() {
    }

    @Override
    protected float lift() {
        return 6.0f * (1.0f - Anim.easeOut(Anim.progress(opened, 0.25d)));
    }

    static ItemStack icon(String skill) {
        return new ItemStack(ICONS.getOrDefault(skill, Items.BOOK));
    }

    private static boolean over(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
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

    private boolean equipped(String id) {
        for (PetView.Slot slot : view.slots()) {
            if (slot.skill().equals(id)) {
                return true;
            }
        }
        return false;
    }

    // -- Le dessin ---------------------------------------------------------------------------------

    @Override
    protected void drawBackground(UiGraphics g, double mx, double my) {
        Paint p = g.paint();
        p.rect(0, 0, W, H, 0xFF0A0A0B);
        frame(p);
        List<Component> tip = new ArrayList<>();
        if (view.has()) {
            drawFamiliar(g, mx, my, tip);
            drawSkills(g, mx, my, tip);
        } else {
            drawNone(g, mx, my, tip);
        }
        if (!tip.isEmpty()) {
            g.tooltip(tip);
        }
    }

    private static void frame(Paint p) {
        int line = 0xFF3A3B40;
        p.rect(5, 3, W - 10, 1, line);
        p.rect(5, H - 4, W - 10, 1, line);
        p.rect(3, 5, 1, H - 10, line);
        p.rect(W - 4, 5, 1, H - 10, line);
    }

    private void drawFamiliar(UiGraphics g, double mx, double my, List<Component> tip) {
        Paint p = g.paint();
        g.textShaded(Ui.TITLE, "FAMILIER", LEFT, 16.0f, 20.0f, 0xFFFFFFFF, 0xFF5A5A60, 1.3f);
        p.framed(LEFT, 44.0f, 92.0f, 92.0f, 3.0f, 0xFF15161A, 0xFF2C2E33, 1);
        p.glow(LEFT + 46.0f, 90.0f, 40.0f, Paint.alpha(PINK, 0.12f));
        g.texture(FamiliarModels.portrait(view.familiar()), (int) LEFT + 2, 46, 88, 88, 96, 96);

        float x = LEFT + 100.0f;
        float w = LEFT_W - 100.0f;
        g.text(Ui.WIDE, g.fit(Ui.WIDE, view.label().toUpperCase(Locale.ROOT), 7.0f, w), x, 48.0f, 7.0f, 0xFFFFFFFF);
        g.text(Ui.WIDE, "NIV. " + view.level(), x, 60.0f, 5.5f, GOLD);
        // l'XP
        p.rect(x, 70.0f, w, 6.0f, 0xFF222327);
        float done = view.xpNext() > 0 ? Math.min(1.0f, view.xp() / (float) view.xpNext()) : 1.0f;
        if (done > 0.0f) {
            p.rect(x, 70.0f, Math.max(1.0f, w * done), 6.0f, view.xpNext() > 0 ? RED : GOLD);
        }
        g.text(Ui.BODY, view.xpNext() > 0 ? view.xp() + " / " + view.xpNext() + " XP" : "Niveau maximum", x, 78.5f,
                4.4f, Ui.Theme.MUTED);
        // le bonheur
        g.text(Ui.WIDE, "BONHEUR", x, 90.0f, 4.6f, 0xFFFFFFFF);
        p.rect(x, 98.0f, w, 6.0f, 0xFF222327);
        float joy = view.maxHappiness() > 0 ? Math.min(1.0f, view.happiness() / (float) view.maxHappiness()) : 0.0f;
        if (joy > 0.0f) {
            p.rect(x, 98.0f, Math.max(1.0f, w * joy), 6.0f, PINK);
        }
        String multiplier = String.format(Locale.FRANCE, "x%.2f", view.happiness() / 100.0f);
        g.text(Ui.BODY, view.happiness() + " / " + view.maxHappiness() + "  ·  competences " + multiplier, x, 106.5f,
                4.4f, Ui.Theme.MUTED);
        if (over(mx, my, x, 88.0f, w, 24.0f)) {
            tip.add(Component.literal("Le bonheur multiplie tes competences : 100 = x1, 200 = x2."));
            tip.add(Component.literal("-25 si les quatre quetes du jour manquent a minuit."));
        }
        // visible / cache
        boolean hover = over(mx, my, x, 118.0f, w, 14.0f);
        p.framed(x, 118.0f, w, 14.0f, 2.0f, hover ? 0xFF2C2E33 : 0xFF202125, 0xFF3A3C42, 1);
        g.centered(Ui.WIDE, view.visible() ? "VISIBLE SUR L'EPAULE" : "CACHE", x + w / 2.0f, 122.5f, 4.4f,
                view.visible() ? GREEN : Ui.Theme.MUTED);

        // les quetes du jour
        float y = 146.0f;
        p.framed(LEFT, y, LEFT_W, 168.0f, 3.0f, 0xFF111214, 0xFF26272B, 1);
        g.text(Ui.WIDE, "QUETES DU JOUR", LEFT + 8.0f, y + 8.0f, 5.5f, 0xFFFFFFFF);
        g.right(Ui.BODY, "remise a zero dans " + duration(view.resetMs()), LEFT + LEFT_W - 8.0f, y + 9.0f, 4.2f,
                Ui.Theme.MUTED);
        float row = y + 22.0f;
        for (PetView.Mission mission : view.missions()) {
            int color = mission.done() ? GREEN : 0xFFE9E8EE;
            g.text(Ui.BODY, g.fit(Ui.BODY, mission.label(), 4.6f, LEFT_W - 60.0f), LEFT + 8.0f, row, 4.6f, color);
            g.right(Ui.BODY, "+" + mission.xp() + " XP", LEFT + LEFT_W - 8.0f, row, 4.4f, GOLD);
            p.rect(LEFT + 8.0f, row + 9.0f, LEFT_W - 16.0f, 4.0f, 0xFF222327);
            float part = Math.min(1.0f, mission.progress() / (float) Math.max(1, mission.goal()));
            if (part > 0.0f) {
                p.rect(LEFT + 8.0f, row + 9.0f, Math.max(1.0f, (LEFT_W - 16.0f) * part), 4.0f,
                        mission.done() ? GREEN : 0xFF3A86FF);
            }
            g.text(Ui.BODY, mission.progress() + " / " + mission.goal() + " " + mission.unit(), LEFT + 8.0f,
                    row + 15.0f, 4.0f, Ui.Theme.MUTED);
            row += 34.0f;
        }
    }

    private void drawSkills(UiGraphics g, double mx, double my, List<Component> tip) {
        Paint p = g.paint();
        g.textShaded(Ui.TITLE, "COMPETENCES", RIGHT, 16.0f, 20.0f, 0xFFFFFFFF, 0xFF5A5A60, 1.3f);
        g.right(Ui.WIDE, "EMPLACEMENTS " + view.slotsOpen() + "/20", RIGHT + RIGHT_W, 24.0f, 5.0f, Ui.Theme.MUTED);
        // les vingt emplacements, deux rangees de dix
        for (int index = 0; index < 20; index++) {
            float x = RIGHT + (index % 10) * SLOT_STEP;
            float y = SLOTS_Y + (index / 10) * SLOT_STEP;
            boolean open = index < view.slotsOpen();
            PetView.Slot slot = index < view.slots().size() ? view.slots().get(index) : new PetView.Slot("", 0L);
            boolean hover = over(mx, my, x, y, SLOT, SLOT);
            int border = index == selected ? GOLD : hover && open ? 0xFF6E7076 : 0xFF2C2E33;
            p.framed(x, y, SLOT, SLOT, 2.0f, open ? 0xFF18191D : 0xFF0E0F11, border, index == selected ? 2 : 1);
            if (!open) {
                g.centered(Ui.WIDE, "N" + ((index + 1) * 5), x + SLOT / 2.0f, y + 12.5f, 4.4f, Ui.Theme.DIM);
                if (hover) {
                    tip.add(Component.literal("S'ouvre au niveau " + ((index + 1) * 5)));
                }
                continue;
            }
            if (!slot.skill().isEmpty()) {
                PetView.Skill skill = skill(slot.skill());
                g.item(icon(slot.skill()), x + 7.0f, y + 7.0f, 16.0f, false);
                if (skill != null && skill.active()) {
                    p.rect(x + 2.0f, y + 2.0f, 4.0f, 4.0f, 0xFF3A86FF);
                }
                if (slot.lockMs() > 0) {
                    p.rect(x + SLOT - 6.0f, y + 2.0f, 4.0f, 4.0f, RED);
                }
                if (hover && skill != null) {
                    describe(skill, tip);
                    tip.add(Component.literal(slot.lockMs() > 0 ? "Verrouille encore " + duration(slot.lockMs())
                            : "Changeable"));
                }
            } else if (hover) {
                tip.add(Component.literal("Emplacement libre : choisis-le, puis une competence"));
            }
        }

        // les onglets et la liste
        tab(g, "PASSIVES", RIGHT, !actives, mx, my);
        tab(g, "ACTIVES (ROUE : R)", RIGHT + 110.0f, actives, mx, my);
        List<PetView.Skill> rows = listed();
        float max = Math.max(0.0f, rows.size() * ROW_STEP - 2.0f - LIST_H);
        scroll = Math.max(0.0f, Math.min(max, scroll));
        g.scissor((int) RIGHT, (int) LIST_Y, (int) RIGHT_W, (int) LIST_H);
        for (int index = 0; index < rows.size(); index++) {
            float y = LIST_Y + index * ROW_STEP - scroll;
            if (y + ROW_H < LIST_Y || y > LIST_Y + LIST_H) {
                continue;
            }
            PetView.Skill skill = rows.get(index);
            boolean unlocked = view.level() >= skill.unlock();
            boolean used = equipped(skill.id());
            boolean hover = over(mx, my, RIGHT, y, RIGHT_W - 8.0f, ROW_H) && my >= LIST_Y && my < LIST_Y + LIST_H;
            p.framed(RIGHT, y, RIGHT_W - 8.0f, ROW_H, 1.5f, hover ? 0xFF26282C : 0xFF1C1D21,
                    used ? 0xFF8C6A26 : 0xFF2A2B2F, 1);
            g.item(icon(skill.id()), RIGHT + 3.0f, y + 2.5f, 12.0f, false);
            int text = unlocked ? 0xFFE9E8EE : Ui.Theme.DIM;
            g.text(Ui.WIDE, g.fit(Ui.WIDE, skill.label().toUpperCase(Locale.ROOT), 4.6f, 150.0f), RIGHT + 19.0f,
                    y + 6.0f, 4.6f, text);
            String right = !unlocked ? "NIVEAU " + skill.unlock() : used ? "POSEE  " + skill.now() : skill.now();
            g.right(Ui.WIDE, right, RIGHT + RIGHT_W - 14.0f, y + 6.0f, 4.4f,
                    !unlocked ? Ui.Theme.DIM : used ? GOLD : GREEN);
            if (hover) {
                describe(skill, tip);
                tip.add(Component.literal(selected >= 0 ? "Clic : poser dans l'emplacement " + (selected + 1)
                        : "Choisis d'abord un emplacement"));
            }
        }
        g.endScissor();
        float trackX = RIGHT + RIGHT_W - 4.0f;
        p.rect(trackX, LIST_Y, 3.0f, LIST_H, 0xFF26272B);
        float content = rows.size() * ROW_STEP - 2.0f;
        float thumb = content <= LIST_H ? LIST_H : Math.max(16.0f, LIST_H * LIST_H / content);
        float thumbY = LIST_Y + (max <= 0.0f ? 0.0f : (LIST_H - thumb) * scroll / max);
        p.rect(trackX, thumbY, 3.0f, thumb, 0xFF6E7076);

        // RETIRER et le message
        boolean canClear = selected >= 0 && selected < view.slots().size()
                && !view.slots().get(selected).skill().isEmpty();
        boolean hover = canClear && over(mx, my, RIGHT, BUTTON_Y, 70.0f, 15.0f);
        p.rect(RIGHT, BUTTON_Y, 70.0f, 15.0f, canClear ? hover ? 0xFFF2492F : RED : 0xFF2B2C30);
        g.centered(Ui.WIDE, "RETIRER", RIGHT + 35.0f, BUTTON_Y + 5.2f, 5.0f, canClear ? 0xFFFFFFFF : Ui.Theme.DIM);
        if (!view.notice().isEmpty()) {
            g.text(Ui.BODY, g.fit(Ui.BODY, view.notice(), 4.8f, RIGHT_W - 84.0f), RIGHT + 80.0f, BUTTON_Y + 4.5f, 4.8f,
                    view.noticeError() ? RED : GREEN);
        }
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

    private void tab(UiGraphics g, String label, float x, boolean active, double mx, double my) {
        Paint p = g.paint();
        float w = 106.0f;
        boolean hover = !active && over(mx, my, x, TAB_Y, w, TAB_H);
        p.rect(x, TAB_Y, w, TAB_H, active ? 0xFFE8321F : hover ? 0xFF35363B : 0xFF2B2C30);
        g.centered(Ui.WIDE, label, x + w / 2.0f, TAB_Y + 5.4f, 4.6f, 0xFFFFFFFF);
    }

    /** Sans familier : la cage, l'appat et l'apprivoisement, et les vingt et un familiers possibles. */
    private void drawNone(UiGraphics g, double mx, double my, List<Component> tip) {
        Paint p = g.paint();
        g.textShaded(Ui.TITLE, "FAMILIER", LEFT, 16.0f, 20.0f, 0xFFFFFFFF, 0xFF5A5A60, 1.3f);
        g.text(Ui.WIDE, "TU N'AS PAS ENCORE DE FAMILIER", LEFT, 50.0f, 7.0f, PINK);
        String[] steps = {
            "1. Fabrique une cage a familier (Chasseur niveau 15) : 4 batons et 4 lingots de titane, "
                    + "1 amethyste compressee.",
            "2. Tue des serpents (75 %) ou des escargots (25 %) pour un appat.",
            "3. Clic droit sur ta cage avec l'appat : elle est chargee 5 minutes, et toutes les 2 minutes un "
                    + "familier a 5 % de chances d'y entrer.",
            "4. Tu as alors 5 minutes pour venir l'apprivoiser : trois paliers (100, 200, 400 points) ; arrete "
                    + "le curseur sur le vert (+50) ou le jaune (+25), evite le rouge (-50) et le noir (-150).",
            "Un seul familier par joueur, pour toujours. Il gagne des niveaux (1 a 100), ouvre vingt "
                    + "emplacements de competences et suit ton bonheur."};
        float y = 68.0f;
        for (String step : steps) {
            y += g.wrapped(Ui.BODY, step, LEFT, y, 5.2f, 300.0f, 8.0f, 0xFFE9E8EE, 4) * 8.0f + 6.0f;
        }
        // Les familiers de PalaPet, sept par rangee, cadres a la couleur de leur rarete.
        Familiar[] all = Familiar.values();
        for (int index = 0; index < all.length; index++) {
            Familiar familiar = all[index];
            float x = 344.0f + (index % 7) * 35.0f;
            float top = 60.0f + (index / 7) * 36.0f;
            boolean hover = over(mx, my, x, top, 33.0f, 33.0f);
            p.framed(x, top, 33.0f, 33.0f, 3.0f, hover ? 0xFF22232A : 0xFF15161A, rarityColor(familiar), 1);
            g.texture(FamiliarModels.portrait(familiar.id()), (int) x + 1, (int) top + 1, 31, 31, 96, 96);
            if (hover) {
                tip.add(Component.literal(familiar.label()).withStyle(ChatFormatting.WHITE));
                tip.add(Component.literal(rarity(familiar)).withStyle(
                        net.minecraft.network.chat.Style.EMPTY.withColor(rarityColor(familiar) & 0xFFFFFF)));
            }
        }
        g.wrapped(Ui.BODY, "La cage choisit au hasard, les familiers d'evenement plus rarement. Leur allure ne "
                + "change rien aux competences.", 344.0f, 176.0f, 4.8f, 246.0f, 7.0f, Ui.Theme.MUTED, 3);
    }

    private static String rarity(Familiar familiar) {
        return switch (familiar.weight()) {
            case 10 -> "Commun";
            case 5 -> "Peu commun";
            case 2 -> "Rare";
            default -> "Legendaire";
        };
    }

    private static int rarityColor(Familiar familiar) {
        return switch (familiar.weight()) {
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
        if (!view.has()) {
            return false;
        }
        float x = LEFT + 100.0f;
        if (over(mx, my, x, 118.0f, LEFT_W - 100.0f, 14.0f)) {
            click(1.0f);
            UiClient.send(PetView.ID, "visible", "", 0);
            return true;
        }
        for (int index = 0; index < view.slotsOpen() && index < 20; index++) {
            float sx = RIGHT + (index % 10) * SLOT_STEP;
            float sy = SLOTS_Y + (index / 10) * SLOT_STEP;
            if (over(mx, my, sx, sy, SLOT, SLOT)) {
                click(1.1f);
                selected = selected == index ? -1 : index;
                return true;
            }
        }
        if (over(mx, my, RIGHT, TAB_Y, 106.0f, TAB_H)) {
            actives = false;
            scroll = 0.0f;
            click(1.0f);
            return true;
        }
        if (over(mx, my, RIGHT + 110.0f, TAB_Y, 106.0f, TAB_H)) {
            actives = true;
            scroll = 0.0f;
            click(1.0f);
            return true;
        }
        if (over(mx, my, RIGHT, BUTTON_Y, 70.0f, 15.0f) && selected >= 0) {
            click(0.9f);
            UiClient.send(PetView.ID, "clear", String.valueOf(selected), 0);
            return true;
        }
        if (over(mx, my, RIGHT, LIST_Y, RIGHT_W - 8.0f, LIST_H)) {
            int index = (int) ((my - LIST_Y + scroll) / ROW_STEP);
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
        if (over(x, y, RIGHT, LIST_Y, RIGHT_W, LIST_H)) {
            scroll -= (float) scrollY * ROW_STEP;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
