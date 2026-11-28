package fr.atraxterra.dohy.client.ui.vanilla;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Buttons;
import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.CyclingSlotBackground;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * La table d'enchantement, l'enclume et l'alambic au style du kit (les trois ecrans du
 * jeu que PalaVanillaGui, de Paladium, redessinait), puis la meule, la table de forgeron,
 * le tailleur de pierre, le metier a tisser et la table de cartographie, en plus de ceux que
 * le kit avait deja (etabli, fours, coffres, entonnoir, distributeur).
 *
 * <p>Les cases sont celles du jeu ({@code Slot.x} et {@code Slot.y} sont finals) ; les
 * clics, les bulles, le champ du nom et le livre anime restent ceux du jeu. Ces trois
 * ecrans calculent leurs zones depuis le centre de l'ecran : la fenetre n'est donc pas
 * recentree comme celle des autres ({@code topPos} reste celui du jeu), l'en-tete passe
 * au-dessus.
 *
 * <p>Les runes sous le livre, le losange XP, le trace de l'alambic, ses bulles et sa
 * fleche viennent du jar PalaVanillaGui ({@code tools/palavanillagui_assets.py}).
 */
public final class KitStations {

    private static final Identifier RUNES = piece("enchant_runes");
    private static final Identifier XP = piece("xp_badge");
    private static final Identifier LINE = piece("brewing_line");
    private static final Identifier BUBBLES = piece("bubbles");
    private static final Identifier BUBBLES_BACK = piece("bubbles_back");
    private static final Identifier ARROW = piece("brew_arrow");
    private static final Identifier ARROW_BACK = piece("brew_arrow_back");

    private static final int LAPIS = Ui.Theme.BLUE;
    /** Le trace de l'alambic : la couleur de l'image du jar (le fond clair de Paladium-UI). */
    private static final int LINE_COLOR = 0xFF40404E;
    private static final int POTION = 0xFFC9A2FF;

    private KitStations() {
    }

    private static Identifier piece(String name) {
        return Ui.texture("vanilla/" + name);
    }

    /** Le titre de l'en-tete : le nom du bloc, sauf si le joueur a renomme le sien. */
    static String headerTitle(Component title, String defaultKey, Block block) {
        String text = title.getString();
        return text.equals(Component.translatable(defaultKey).getString()) ? block.getName().getString() : text;
    }

    /**
     * L'icone de l'en-tete pour un bloc sans entite de bloc (une enclume, ebrechee ou
     * abimee) : le bloc vise s'il est de ce genre, sinon {@code fallback}.
     */
    static ItemStack aimed(Class<? extends Block> kind, ItemStack fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.hitResult instanceof BlockHitResult hit
                && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            Block block = minecraft.level.getBlockState(hit.getBlockPos()).getBlock();
            if (kind.isInstance(block)) {
                ItemStack stack = new ItemStack(block.asItem());
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return fallback;
    }

    /** La fenetre, l'en-tete et la croix, dans le repere du rectangle du jeu. */
    static void frame(UiGraphics g, Paint p, ItemStack icon, String title, int width, int height, double mouseX,
                      double mouseY) {
        ContainerStyle.window(p, width, height);
        ContainerStyle.header(g, p, icon, title, width, 22);
        ContainerStyle.close(g, p, width, ContainerStyle.overClose(mouseX, mouseY, width));
    }

    // -- Table d'enchantement ---------------------------------------------------------------

    /**
     * La table d'enchantement : a gauche le livre du jeu, sur les runes de PalaVanillaGui,
     * les deux cases et le niveau du joueur ; a droite les trois offres. Une offre montre le
     * lapis qu'elle demande, son nom en runes (celui du jeu, tire de la meme graine) et son
     * cout en niveaux ; grisee si le lapis ou les niveaux manquent. La bulle du jeu dit
     * toujours l'enchantement, le lapis et les niveaux.
     */
    public static final class Enchanting extends EnchantmentScreen {
        private static final Identifier BOOK =
                Identifier.withDefaultNamespace("textures/entity/enchantment/enchanting_table_book.png");
        private static final float OPTION_X = 60.0f;
        private static final float OPTION_W = 108.0f;
        private final ItemStack icon;
        private final Anim.Value[] hovers = {new Anim.Value(0.0f, 16.0f), new Anim.Value(0.0f, 16.0f),
            new Anim.Value(0.0f, 16.0f)};
        private BookModel book;

        public Enchanting(EnchantmentMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            this.icon = ContainerStyle.targetIcon(new ItemStack(Items.ENCHANTING_TABLE));
        }

        @Override
        protected void init() {
            super.init();
            book = new BookModel(minecraft.getEntityModels().bakeLayer(ModelLayers.BOOK));
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Paint p = g.paint();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            double mx = mouseX - leftPos;
            double my = mouseY - topPos;
            frame(g, p, icon, headerTitle(title, "container.enchant", Blocks.ENCHANTING_TABLE), imageWidth,
                    imageHeight, mx, my);

            // Le grimoire : les runes de PalaVanillaGui, les deux cases, le niveau du joueur.
            ContainerStyle.inset(p, 8, 9, 48, 66);
            p.image(RUNES, 11, 12, 42, 60, 37.5f, 0.0f, 133, 190, 208, 190, 0x80FFFFFF);
            for (Slot slot : menu.slots) {
                ContainerStyle.slot(p, slot);
            }
            p.image(XP, 12.5f, 65.7f, 8.0f, 8.0f, 0.0f, 0.0f, 40, 40, 40, 40, 0xFFFFFFFF);
            g.text(Ui.WIDE, "NIV. " + minecraft.player.experienceLevel, 22.5f, 67.4f, 4.4f, Ui.Theme.GREEN_L);

            options(graphics, g, p, mx, my);
            pose.popMatrix();

            // Le livre du jeu, qui s'ouvre et feuillette, en coordonnees d'ecran comme le jeu.
            if (book != null) {
                float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                graphics.book(book, BOOK, 40.0f, Mth.lerp(partial, oOpen, open), Mth.lerp(partial, oFlip, flip),
                        leftPos + 14, topPos + 14, leftPos + 52, topPos + 45);
            }
        }

        /** Les trois offres, aux zones de clic du jeu (60, 14 + 19 n, 108 x 19). */
        private void options(GuiGraphicsExtractor graphics, UiGraphics g, Paint p, double mx, double my) {
            // La meme graine et le meme ordre de tirage que le jeu : les memes noms en runes.
            EnchantmentNames.getInstance().initSeed(menu.getEnchantmentSeed());
            int gold = menu.getGoldCount();
            boolean creative = minecraft.player.getAbilities().instabuild;
            for (int index = 0; index < 3; index++) {
                float y = 14.0f + 19.0f * index;
                int cost = menu.costs[index];
                if (cost == 0) {
                    hovers[index].toward(0.0f);
                    p.framed(OPTION_X, y, OPTION_W, 18.0f, 3.5f, 0xFF16171A, 0xFF222428, 1);
                    continue;
                }
                String number = Integer.toString(cost);
                int room = 86 - font.width(number);
                FormattedText name = EnchantmentNames.getInstance().getRandomName(font, room);
                boolean enabled = ((gold >= index + 1 && minecraft.player.experienceLevel >= cost) || creative)
                        && menu.enchantClue[index] != -1;
                boolean over = enabled && mx >= OPTION_X && my >= y && mx < OPTION_X + OPTION_W && my < y + 19.0f;
                float hover = hovers[index].toward(over ? 1.0f : 0.0f);
                if (enabled && hover > 0.01f) {
                    p.glow(OPTION_X + OPTION_W / 2.0f, y + 9.0f, 40.0f, Paint.alpha(Ui.Theme.RED, 0.18f * hover));
                }
                int fill = enabled ? Paint.mix(Ui.Theme.CARD, Ui.Theme.CARD_HOVER, hover) : 0xFF1A1B1E;
                int edge = enabled ? Paint.mix(Ui.Theme.EDGE, Ui.Theme.RED, 0.8f * hover) : 0xFF26282C;
                p.framed(OPTION_X, y, OPTION_W, 18.0f, 3.5f, fill, edge, 1);

                // Le lapis demande : une, deux ou trois pierres.
                int tile = Paint.mix(0xFF1A1B1E, LAPIS, enabled ? 0.28f : 0.08f);
                p.framed(OPTION_X + 1.5f, y + 1.5f, 15.0f, 15.0f, 3.0f, tile,
                        Paint.mix(tile, LAPIS, enabled ? 0.55f : 0.2f), 1);
                g.centered(Ui.WIDE, Integer.toString(index + 1), OPTION_X + 9.0f, y + 6.0f, 6.0f,
                        enabled ? Ui.Theme.TEXT : Ui.Theme.DIM);

                // Le nom en runes, comme le jeu le pose, puis le cout en niveaux.
                int runes = !enabled ? 0xFF4D485C : Paint.mix(0xFF9187B0, 0xFFE6DDFF, hover);
                graphics.textWithWordWrap(font, name, (int) OPTION_X + 20, (int) y + 1, room, runes, false);
                g.right(Ui.WIDE, number, OPTION_X + OPTION_W - 2.5f, y + 9.8f, 5.7f,
                        enabled ? Ui.Theme.GREEN_L : 0xFF2F6B3B);
                if (over) {
                    graphics.requestCursor(CursorTypes.POINTING_HAND);
                }
            }
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            if (menu.slots.size() > 2) {
                ContainerStyle.inventoryLabel(g, menu.slots.get(2));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
                onClose();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
            return super.hasClickedOutside(mouseX, mouseY, left, top)
                    && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
        }
    }

    // -- Enclume -------------------------------------------------------------------------------

    /**
     * L'enclume : le champ du nouveau nom (celui du jeu, sur un champ du kit), les deux
     * objets, la fleche et le resultat ; une croix rouge quand rien ne sort ; le cout
     * en niveaux sous le resultat, en rouge s'il est trop cher ou que les niveaux manquent.
     */
    public static final class Anvil extends AnvilScreen {
        private final ItemStack icon;
        private EditBox name;

        public Anvil(AnvilMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            this.icon = aimed(AnvilBlock.class, new ItemStack(Items.ANVIL));
        }

        @Override
        protected void init() {
            super.init();
            name = null;
            for (GuiEventListener child : children()) {
                if (child instanceof EditBox box) {
                    name = box;
                }
            }
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Paint p = g.paint();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            frame(g, p, icon, headerTitle(title, "container.repair", Blocks.ANVIL), imageWidth, imageHeight,
                    mouseX - leftPos, mouseY - topPos);
            ContainerStyle.inset(p, 8, 9, imageWidth - 16, 66);

            // Le nouveau nom : le champ du jeu passe dessus.
            boolean editable = menu.getSlot(0).hasItem();
            g.text(Ui.PIXEL, "NOUVEAU NOM", 60.0f, 13.5f, 3.3f, editable ? Ui.Theme.MUTED : Ui.Theme.DIM);
            if (editable) {
                Buttons.field(p, 59, 20, 110, 16, name != null && name.isFocused());
            } else {
                p.framed(59, 20, 110, 16, 4.0f, 0xFF141518, 0xFF202226, 1);
            }

            // Les deux objets, le plus, la fleche et le resultat.
            Slot result = menu.getSlot(menu.getResultSlot());
            boolean takeable = result.hasItem() && result.mayPickup(minecraft.player);
            for (int index = 0; index < menu.slots.size(); index++) {
                Slot slot = menu.slots.get(index);
                if (index == menu.getResultSlot()) {
                    ContainerStyle.bigSlot(p, slot, result.hasItem() && !takeable ? Ui.Theme.RED : Ui.Theme.GREEN);
                } else {
                    ContainerStyle.slot(p, slot);
                }
            }
            int plus = menu.getSlot(1).hasItem() ? Ui.Theme.TEXT2 : 0xFF3A3C42;
            p.rect(56.0f, 54.35f, 7.0f, 1.3f, plus);
            p.rect(58.85f, 51.5f, 1.3f, 7.0f, plus);
            // Rien ne sort : la fleche s'eteint en rouge sous une croix, la ou le jeu met la sienne.
            boolean failed = (menu.getSlot(0).hasItem() || menu.getSlot(1).hasItem()) && !result.hasItem();
            p.arrow(99.0f, 50.5f, 24.0f, 9.0f, failed ? 0xFF4A2A2A : result.hasItem() ? Ui.Theme.TEXT2 : 0xFF3A3C42);
            if (failed) {
                p.glow(111.0f, 55.0f, 11.0f, Paint.alpha(Ui.Theme.RED, 0.35f));
                p.cross(111.0f, 55.0f, 12.0f, Ui.Theme.RED_LIGHT);
            }
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            if (menu.slots.size() > 3) {
                ContainerStyle.inventoryLabel(g, menu.slots.get(3));
            }
            // Le cout, comme le jeu : trop cher a 40 niveaux (sauf en creatif), en rouge si
            // le resultat ne peut pas etre pris.
            int cost = menu.getCost();
            if (cost <= 0) {
                return;
            }
            String text;
            int color = Ui.Theme.GREEN_L;
            if (cost >= 40 && !minecraft.player.hasInfiniteMaterials()) {
                text = Component.translatable("container.repair.expensive").getString();
                color = Ui.Theme.RED_LIGHT;
            } else if (!menu.getSlot(2).hasItem()) {
                return;
            } else {
                text = Component.translatable("container.repair.cost", cost).getString();
                if (!menu.getSlot(2).mayPickup(minecraft.player)) {
                    color = Ui.Theme.RED_LIGHT;
                }
            }
            // Une pastille sous le resultat, entre son cadre (67) et le bas de l'encart (75).
            text = ContainerStyle.plain(text).toUpperCase(Locale.ROOT);
            float right = imageWidth - 13.0f;
            float textWidth = g.width(Ui.WIDE, text, 3.8f);
            g.paint().framed(right - textWidth - 3.5f, 68.3f, textWidth + 7.0f, 6.0f, 3.0f,
                    Paint.mix(0xFF141518, color, 0.12f), Paint.mix(0xFF24262A, color, 0.35f), 1);
            g.right(Ui.WIDE, text, right, 69.4f, 3.8f, color);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
                onClose();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
            return super.hasClickedOutside(mouseX, mouseY, left, top)
                    && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
        }
    }

    // -- Alambic ------------------------------------------------------------------------------

    /**
     * L'alambic : le carburant (poudre de blaze) et sa jauge, l'ingredient entre les bulles et
     * la fleche de PalaVanillaGui, le trace qui descend vers les trois fioles. Les bulles
     * montent et la fleche se remplit comme celles du jeu ; le temps qui reste a cote.
     */
    public static final class Brewing extends BrewingStandScreen {
        /** Les hauteurs des bulles du jeu, image par image (BUBBLELENGTHS). */
        private static final int[] BUBBLE_LENGTHS = {29, 24, 20, 16, 11, 6, 0};
        private final ItemStack icon;

        public Brewing(BrewingStandMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            this.icon = ContainerStyle.targetIcon(new ItemStack(Items.BREWING_STAND));
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Paint p = g.paint();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            frame(g, p, icon, headerTitle(title, "container.brewing", Blocks.BREWING_STAND), imageWidth, imageHeight,
                    mouseX - leftPos, mouseY - topPos);
            ContainerStyle.inset(p, 8, 9, imageWidth - 16, 66.5f);

            // Le trace de l'ingredient aux fioles (l'image du jar relie les deux du bord ; le
            // trait du milieu descend jusqu'a la fiole centrale, plus basse).
            p.image(LINE, 62.6f, 35.3f, 49.0f, 15.2f, 0.0f, 0.0f, 196, 61, 196, 61, 0xFFFFFFFF);
            p.rect(85.6f, 43.5f, 3.0f, 14.0f, LINE_COLOR);
            for (Slot slot : menu.slots) {
                ContainerStyle.slot(p, slot);
            }

            // Le carburant : vingt distillations par poudre de blaze.
            int fuel = Mth.clamp(menu.getFuel(), 0, 20);
            ContainerStyle.bar(p, 13.0f, 37.0f, 24.0f, 3.0f, fuel / 20.0f, Ui.Theme.ORANGE);
            g.centered(Ui.PIXEL, "CARBURANT", 25.0f, 43.0f, 3.3f, Ui.Theme.DIM);
            g.centered(Ui.WIDE, fuel + " / 20", 25.0f, 48.5f, 4.2f, fuel > 0 ? Ui.Theme.ORANGE : Ui.Theme.MUTED);

            // Les bulles et la fleche, aux places du jeu.
            int ticks = menu.getBrewingTicks();
            p.image(BUBBLES_BACK, 63.0f, 14.0f, 12.0f, 29.0f, 0.0f, 0.0f, 48, 120, 48, 120, 0xFFFFFFFF);
            p.image(ARROW_BACK, 97.0f, 16.0f, 9.8f, 28.0f, 0.0f, 0.0f, 39, 112, 39, 112, 0xFFFFFFFF);
            if (ticks > 0) {
                float done = Math.max(0.0f, Math.min(1.0f, 1.0f - ticks / 400.0f));
                if (done > 0.0f) {
                    int region = Math.max(1, Math.round(112 * done));
                    p.image(ARROW, 97.0f, 16.0f, 9.8f, 28.0f * done, 0.0f, 0.0f, 39, region, 39, 112, POTION);
                }
                int bubbles = BUBBLE_LENGTHS[ticks / 2 % 7];
                if (bubbles > 0) {
                    float part = bubbles / 29.0f;
                    int region = Math.max(1, Math.round(120 * part));
                    p.image(BUBBLES, 63.0f, 14.0f + 29.0f - bubbles, 12.0f, bubbles, 0.0f, 120 - region, 48, region,
                            48, 120, 0xFFE7D8FF);
                }
                g.text(Ui.PIXEL, String.format(Locale.FRANCE, "%.1f S", ticks / 20.0f), 110.0f, 27.0f, 3.3f,
                        Ui.Theme.TEXT2);
            }
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            if (menu.slots.size() > 5) {
                ContainerStyle.inventoryLabel(g, menu.slots.get(5));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
                onClose();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
            return super.hasClickedOutside(mouseX, mouseY, left, top)
                    && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
        }
    }

    // -- Champs prives des ecrans du jeu ------------------------------------------------------

    /**
     * Un champ prive d'un ecran du jeu, lu par son nom : NeoForge garde a l'execution les noms
     * de Mojang, ceux du jeu en developpement. {@code null} si le jeu l'a renomme : l'ecran
     * reprend alors son dessin du jeu, rien ne se perd.
     */
    static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException missing) {
            return null;
        }
    }

    static Object read(Field field, Object owner) {
        try {
            return field == null ? null : field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException missing) {
            return null;
        }
    }

    // -- Meule -------------------------------------------------------------------------------

    /**
     * La meule : les deux objets l'un sur l'autre, la fleche, le resultat cercle ; une croix
     * rouge quand rien ne sort (la ou le jeu met la sienne).
     */
    public static final class Grindstone extends GrindstoneScreen {
        private final ItemStack icon = new ItemStack(Items.GRINDSTONE);

        public Grindstone(GrindstoneMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Paint p = g.paint();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            frame(g, p, icon, headerTitle(title, "container.grindstone_title", Blocks.GRINDSTONE), imageWidth,
                    imageHeight, mouseX - leftPos, mouseY - topPos);
            ContainerStyle.inset(p, 8, 9, imageWidth - 16, 66);
            Slot result = menu.getSlot(2);
            for (int index = 0; index < menu.slots.size(); index++) {
                Slot slot = menu.slots.get(index);
                if (index == 2) {
                    ContainerStyle.bigSlot(p, slot, Ui.Theme.GREEN);
                } else {
                    ContainerStyle.slot(p, slot);
                }
            }
            // Les deux entrees se rejoignent, puis la fleche vers le resultat.
            int line = menu.getSlot(0).hasItem() || menu.getSlot(1).hasItem() ? 0xFF4A4D55 : 0xFF2C2E33;
            p.rect(70.0f, 27.0f, 1.3f, 22.0f, line);
            p.rect(66.0f, 27.0f, 5.3f, 1.3f, line);
            p.rect(66.0f, 47.7f, 5.3f, 1.3f, line);
            p.rect(70.0f, 41.4f, 18.0f, 1.3f, line);
            boolean failed = (menu.getSlot(0).hasItem() || menu.getSlot(1).hasItem()) && !result.hasItem();
            p.arrow(92.0f, 37.5f, 26.0f, 9.0f, failed ? 0xFF4A2A2A : result.hasItem() ? Ui.Theme.TEXT2 : 0xFF3A3C42);
            if (failed) {
                p.glow(105.0f, 42.0f, 11.0f, Paint.alpha(Ui.Theme.RED, 0.35f));
                p.cross(105.0f, 42.0f, 12.0f, Ui.Theme.RED_LIGHT);
            }
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            if (menu.slots.size() > 3) {
                ContainerStyle.inventoryLabel(g, menu.slots.get(3));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
                onClose();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
            return super.hasClickedOutside(mouseX, mouseY, left, top)
                    && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
        }
    }

    // -- Table de forgeron ------------------------------------------------------------------

    /**
     * La table de forgeron : le modele, l'objet et le materiau, la fleche et le resultat a
     * gauche ; l'armure posee sur son support a droite, comme le jeu la montre. Les silhouettes
     * qui defilent dans les cases vides, l'apercu et les bulles d'aide sont ceux du jeu.
     */
    public static final class Smithing extends SmithingScreen {
        private static final Field TEMPLATE = field(SmithingScreen.class, "templateIcon");
        private static final Field BASE = field(SmithingScreen.class, "baseIcon");
        private static final Field ADDITION = field(SmithingScreen.class, "additionalIcon");
        private static final Field PREVIEW = field(SmithingScreen.class, "armorStandPreview");
        private static final Field TRANSLATION = field(SmithingScreen.class, "ARMOR_STAND_TRANSLATION");
        private static final Field ANGLE = field(SmithingScreen.class, "ARMOR_STAND_ANGLE");
        private final ItemStack icon = new ItemStack(Items.SMITHING_TABLE);

        public Smithing(SmithingMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            Object template = read(TEMPLATE, this);
            Object base = read(BASE, this);
            Object addition = read(ADDITION, this);
            Object preview = read(PREVIEW, this);
            Object translation = read(TRANSLATION, null);
            Object angle = read(ANGLE, null);
            if (!(template instanceof CyclingSlotBackground templateIcon)
                    || !(base instanceof CyclingSlotBackground baseIcon)
                    || !(addition instanceof CyclingSlotBackground additionIcon)
                    || !(preview instanceof ArmorStandRenderState stand)
                    || !(translation instanceof Vector3fc offset) || !(angle instanceof Quaternionfc rotation)) {
                super.extractBackground(graphics, mouseX, mouseY, partialTick);
                return;
            }
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Paint p = g.paint();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            frame(g, p, icon, headerTitle(title, "container.upgrade", Blocks.SMITHING_TABLE), imageWidth,
                    imageHeight, mouseX - leftPos, mouseY - topPos);
            ContainerStyle.inset(p, 8, 9, 110.5f, 66);
            ContainerStyle.inset(p, 120.5f, 9, 47.5f, 73);
            Slot result = menu.getSlot(menu.getResultSlot());
            for (int index = 0; index < menu.slots.size(); index++) {
                Slot slot = menu.slots.get(index);
                if (index == menu.getResultSlot()) {
                    ContainerStyle.bigSlot(p, slot, Ui.Theme.GREEN);
                } else {
                    ContainerStyle.slot(p, slot);
                }
            }
            // Au-dessus des cases, ce que la table attend (le titre du jeu, puis l'ordre des cases).
            ContainerStyle.label(g, 14.0f, 16.0f, "AMELIORER L'EQUIPEMENT", "");
            g.wrapped(Ui.BODY, "Un modele, l'objet, puis son materiau : le resultat sort a droite.", 14.0f, 25.0f,
                    4.6f, 98.0f, 6.6f, Ui.Theme.MUTED, 2);
            boolean failed = hasError();
            p.arrow(66.0f, 51.5f, 24.0f, 9.0f, failed ? 0xFF4A2A2A : result.hasItem() ? Ui.Theme.TEXT2 : 0xFF3A3C42);
            if (failed) {
                p.glow(78.0f, 56.0f, 11.0f, Paint.alpha(Ui.Theme.RED, 0.35f));
                p.cross(78.0f, 56.0f, 12.0f, Ui.Theme.RED_LIGHT);
            }
            pose.popMatrix();
            // Les silhouettes des cases vides et l'armure du jeu, en coordonnees d'ecran.
            templateIcon.extractRenderState(menu, graphics, partialTick, leftPos, topPos);
            baseIcon.extractRenderState(menu, graphics, partialTick, leftPos, topPos);
            additionIcon.extractRenderState(menu, graphics, partialTick, leftPos, topPos);
            graphics.entity(stand, 25.0f, offset, rotation, null, leftPos + 121, topPos + 20, leftPos + 161,
                    topPos + 80);
        }

        /** L'erreur du jeu : les trois cases remplies, et rien en sortie. */
        private boolean hasError() {
            return menu.hasRecipeError();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            if (menu.slots.size() > 4) {
                ContainerStyle.inventoryLabel(g, menu.slots.get(4));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
                onClose();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
            return super.hasClickedOutside(mouseX, mouseY, left, top)
                    && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
        }
    }

    // -- Tailleur de pierre ----------------------------------------------------------------

    /**
     * Le tailleur de pierre : la pierre a gauche, la grille des coupes possibles (quatre par
     * rangee, la choisie en rouge), sa barre de defilement, le resultat cercle a droite. Les
     * clics, la molette, la barre qu'on tire et la bulle de chaque coupe restent ceux du jeu.
     */
    public static final class Stonecutter extends StonecutterScreen {
        private static final Field START = field(StonecutterScreen.class, "startIndex");
        private static final Field SCROLL = field(StonecutterScreen.class, "scrollOffs");
        private static final Field SHOWN = field(StonecutterScreen.class, "displayRecipes");
        private static final int COLUMNS = 4;
        private static final int VISIBLE = 12;
        private final ItemStack icon = new ItemStack(Items.STONECUTTER);

        public Stonecutter(StonecutterMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            Object start = read(START, this);
            Object scroll = read(SCROLL, this);
            Object shown = read(SHOWN, this);
            if (!(start instanceof Integer first) || !(scroll instanceof Float offset)
                    || !(shown instanceof Boolean display)) {
                super.extractBackground(graphics, mouseX, mouseY, partialTick);
                return;
            }
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Paint p = g.paint();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            double mx = mouseX - leftPos;
            double my = mouseY - topPos;
            frame(g, p, icon, headerTitle(title, "container.stonecutter", Blocks.STONECUTTER), imageWidth,
                    imageHeight, mx, my);
            ContainerStyle.inset(p, 8, 9, imageWidth - 16, 66);
            for (int index = 0; index < menu.slots.size(); index++) {
                Slot slot = menu.slots.get(index);
                if (index == StonecutterMenu.RESULT_SLOT) {
                    ContainerStyle.bigSlot(p, slot, Ui.Theme.GREEN);
                } else {
                    ContainerStyle.slot(p, slot);
                }
            }

            // La grille des coupes, dans son creux.
            p.framed(50.5f, 13.5f, 67.0f, 59.0f, 3.5f, 0xFF101113, 0xFF24262A, 1);
            int count = display ? menu.getNumberOfVisibleRecipes() : 0;
            int selected = menu.getSelectedRecipeIndex();
            for (int index = first; index < first + VISIBLE && index < count; index++) {
                int place = index - first;
                float x = 52.0f + place % COLUMNS * 16.0f;
                float y = 15.0f + place / COLUMNS * 18.0f;
                boolean over = mx >= x && my >= y && mx < x + 16.0f && my < y + 18.0f;
                int fill = index == selected ? Paint.mix(0xFF1A1B1E, Ui.Theme.RED, 0.3f)
                        : over ? 0xFF2A2C31 : 0xFF1C1D21;
                int edge = index == selected ? Ui.Theme.RED : over ? 0xFF5A5D65 : 0xFF2A2C31;
                p.framed(x + 0.5f, y + 0.5f, 15.0f, 17.0f, 2.5f, fill, edge, 1);
                if (over) {
                    graphics.requestCursor(CursorTypes.POINTING_HAND);
                }
            }

            // La barre de defilement : le jeu la tire et la fait rouler, on la dessine.
            boolean active = display && count > VISIBLE;
            p.round(121.0f, 15.0f, 8.0f, 56.0f, 4.0f, 0xFF16171A);
            float thumbY = 15.0f + 41.0f * Math.max(0.0f, Math.min(1.0f, offset));
            boolean overBar = active && mx >= 119.0f && my >= 15.0f && mx < 131.0f && my < 71.0f;
            p.round(121.0f, thumbY, 8.0f, 15.0f, 4.0f, !active ? 0xFF2A2C31 : overBar ? 0xFF8C8F97 : 0xFF5A5D65);
            pose.popMatrix();

            // Les coupes elles-memes, comme le jeu les pose.
            if (count > 0 && minecraft.level != null) {
                ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
                SelectableRecipe.SingleInputSet<StonecutterRecipe> recipes = menu.getVisibleRecipes();
                for (int index = first; index < first + VISIBLE && index < recipes.size(); index++) {
                    int place = index - first;
                    int x = leftPos + 52 + place % COLUMNS * 16;
                    int y = topPos + 16 + place / COLUMNS * 18;
                    ItemStack stack = recipes.entries().get(index).recipe().optionDisplay().resolveForFirstStack(context);
                    graphics.item(stack, x, y);
                }
            }
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            if (menu.slots.size() > 2) {
                ContainerStyle.inventoryLabel(g, menu.slots.get(2));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
                onClose();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
            return super.hasClickedOutside(mouseX, mouseY, left, top)
                    && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
        }
    }

    // -- Metier a tisser -------------------------------------------------------------------

    /**
     * Le metier a tisser : la banniere, la teinture et le motif a gauche (leurs silhouettes du
     * jeu dans les cases vides) ; au milieu la grille des motifs, quatre par rangee aux places du
     * jeu, le choisi en rouge, curseur main et bulle du jeu (le motif dans la couleur de la
     * teinture) au survol, et sa barre de defilement ; a droite la banniere telle qu'elle sortira
     * (l'apercu du jeu) au-dessus du resultat cercle. Une croix rouge sur le resultat quand la
     * banniere porte deja tous ses motifs. Clics, molette et barre tiree restent ceux du jeu.
     */
    public static final class Loom extends LoomScreen {
        private static final Field FLAG = field(LoomScreen.class, "flag");
        private static final Field RESULT = field(LoomScreen.class, "resultBannerPatterns");
        private static final Field FULL = field(LoomScreen.class, "hasMaxPatterns");
        private static final Field SHOWN = field(LoomScreen.class, "displayPatterns");
        private static final Field SCROLL = field(LoomScreen.class, "scrollOffs");
        private static final Field SCROLLING = field(LoomScreen.class, "scrolling");
        private static final Field START = field(LoomScreen.class, "startRow");
        private static final Field DYE = field(LoomScreen.class, "dyeStack");
        private static final Identifier BANNER_SLOT = Identifier.withDefaultNamespace("container/slot/banner");
        private static final Identifier DYE_SLOT = Identifier.withDefaultNamespace("container/slot/dye");
        private static final Identifier PATTERN_SLOT = Identifier.withDefaultNamespace("container/slot/banner_pattern");
        /** La grille du jeu : 4 x 4 motifs de 14, en (60, 13) ; la barre en (119, 13), 56 de haut. */
        private static final int COLUMNS = 4;
        private static final int VISIBLE = 16;
        private static final float CELL = 14.0f;
        private final ItemStack icon = new ItemStack(Items.LOOM);

        public Loom(LoomMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            Object flag = read(FLAG, this);
            Object full = read(FULL, this);
            Object shown = read(SHOWN, this);
            Object scroll = read(SCROLL, this);
            Object scrolling = read(SCROLLING, this);
            Object start = read(START, this);
            Object dye = read(DYE, this);
            if (!(flag instanceof BannerFlagModel model) || !(full instanceof Boolean max)
                    || !(shown instanceof Boolean display) || !(scroll instanceof Float offset)
                    || !(scrolling instanceof Boolean dragging) || !(start instanceof Integer row)
                    || !(dye instanceof ItemStack dyeStack) || RESULT == null) {
                super.extractBackground(graphics, mouseX, mouseY, partialTick);
                return;
            }
            Object layers = read(RESULT, this);
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Paint p = g.paint();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            double mx = mouseX - leftPos;
            double my = mouseY - topPos;
            frame(g, p, icon, headerTitle(title, "container.loom", Blocks.LOOM), imageWidth, imageHeight, mx, my);
            // Trois encarts : ce qu'on tisse, les motifs, ce qui sort (le resultat descend plus bas).
            ContainerStyle.inset(p, 8, 9, 46, 66);
            ContainerStyle.inset(p, 56, 9, 79, 66);
            ContainerStyle.inset(p, 137, 9, 31, 70);
            Slot result = menu.getResultSlot();
            for (Slot slot : menu.slots) {
                if (slot == result) {
                    ContainerStyle.bigSlot(p, slot, Ui.Theme.GREEN);
                } else {
                    ContainerStyle.slot(p, slot);
                }
            }

            // La grille des motifs, dans son creux.
            p.framed(58.5f, 11.5f, 59.0f, 59.0f, 3.5f, 0xFF101113, 0xFF24262A, 1);
            List<Holder<BannerPattern>> patterns = menu.getSelectablePatterns();
            int selected = menu.getSelectedBannerPatternIndex();
            Holder<BannerPattern> hovered = null;
            if (display) {
                for (int place = 0; place < VISIBLE; place++) {
                    int index = (row + place / COLUMNS) * COLUMNS + place % COLUMNS;
                    if (index >= patterns.size()) {
                        break;
                    }
                    float x = 60.0f + place % COLUMNS * CELL;
                    float y = 13.0f + place / COLUMNS * CELL;
                    boolean over = mx >= x && my >= y && mx < x + CELL && my < y + CELL;
                    boolean chosen = index == selected;
                    int fill = chosen ? Paint.mix(0xFF1A1B1E, Ui.Theme.RED, 0.3f) : over ? 0xFF2A2C31 : 0xFF1C1D21;
                    int edge = chosen ? Ui.Theme.RED : over ? 0xFF5A5D65 : 0xFF2A2C31;
                    p.framed(x + 0.5f, y + 0.5f, CELL - 1.0f, CELL - 1.0f, 2.5f, fill, edge, 1);
                    if (over && !chosen) {
                        hovered = patterns.get(index);
                    }
                }
            } else {
                // Le jeu cache la grille sans banniere ou sans teinture, et sur une banniere pleine.
                String hint = max ? "Cette banniere porte deja ses six motifs : rien de plus ne s'y tisse."
                        : "Pose une banniere et une teinture : ses motifs viennent ici.";
                g.wrapped(Ui.BODY, hint, 62.0f, 30.0f, 4.6f, 52.0f, 6.6f, Ui.Theme.MUTED, 5);
            }

            // La barre de defilement : le jeu la tire et la fait rouler, on la dessine.
            boolean active = display && patterns.size() > VISIBLE;
            p.round(121.0f, 13.0f, 8.0f, 56.0f, 4.0f, 0xFF16171A);
            boolean overBar = mx >= 119.0f && my >= 13.0f && mx < 131.0f && my < 69.0f;
            float thumbY = 13.0f + (int) (41.0f * offset);
            p.round(121.0f, thumbY, 8.0f, 15.0f, 4.0f,
                    !active ? 0xFF2A2C31 : overBar || dragging ? 0xFF8C8F97 : 0xFF5A5D65);
            if (overBar) {
                graphics.requestCursor(!active ? CursorTypes.NOT_ALLOWED
                        : dragging ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
            }

            // L'apercu dans son creux ; la croix quand la banniere est pleine.
            p.framed(139.5f, 10.5f, 23.0f, 39.0f, 3.0f, 0xFF101113, 0xFF24262A, 1);
            if (max) {
                p.glow(result.x + 8.0f, result.y + 8.0f, 11.0f, Paint.alpha(Ui.Theme.RED, 0.35f));
                p.cross(result.x + 8.0f, result.y + 8.0f, 12.0f, Ui.Theme.RED_LIGHT);
            }
            pose.popMatrix();

            // Les silhouettes des cases vides, comme le jeu.
            silhouette(graphics, menu.getBannerSlot(), BANNER_SLOT);
            silhouette(graphics, menu.getDyeSlot(), DYE_SLOT);
            silhouette(graphics, menu.getPatternSlot(), PATTERN_SLOT);
            // Les motifs en petites bannieres, comme le jeu les pose.
            if (display) {
                for (int place = 0; place < VISIBLE; place++) {
                    int index = (row + place / COLUMNS) * COLUMNS + place % COLUMNS;
                    if (index >= patterns.size()) {
                        break;
                    }
                    TextureAtlasSprite sprite = graphics.getSprite(Sheets.getBannerSprite(patterns.get(index)));
                    bannerOnButton(graphics, leftPos + 60 + place % COLUMNS * 14, topPos + 13 + place / COLUMNS * 14,
                            sprite);
                }
            }
            if (hovered != null) {
                DyeColor color = dyeStack.getOrDefault(DataComponents.DYE, DyeColor.WHITE);
                graphics.setTooltipForNextFrame(
                        Component.translatable(hovered.value().translationKey() + "." + color.getName()), mouseX, mouseY);
                graphics.requestCursor(CursorTypes.POINTING_HAND);
            }
            // La banniere qui sortira : l'apercu du jeu, un peu plus petit, dans son creux.
            if (layers instanceof BannerPatternLayers pattern && !max
                    && result.getItem().getItem() instanceof BannerItem banner) {
                graphics.bannerPattern(model, banner.getColor(), pattern, leftPos + 142, topPos + 12, leftPos + 160,
                        topPos + 48);
            }
            minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        }

        private void silhouette(GuiGraphicsExtractor graphics, Slot slot, Identifier sprite) {
            if (!slot.hasItem()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, leftPos + slot.x, topPos + slot.y, 16, 16);
            }
        }

        /** Le motif sur une petite banniere grise de 5 x 10, au centre de sa case (le dessin du jeu). */
        private static void bannerOnButton(GuiGraphicsExtractor graphics, int x, int y, TextureAtlasSprite sprite) {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x + 4, y + 2);
            float u0 = sprite.getU0();
            float u1 = u0 + (sprite.getU1() - sprite.getU0()) * 21.0f / 64.0f;
            float height = sprite.getV1() - sprite.getV0();
            float v0 = sprite.getV0() + height / 64.0f;
            float v1 = v0 + height * 40.0f / 64.0f;
            graphics.fill(0, 0, 5, 10, DyeColor.GRAY.getTextureDiffuseColor());
            graphics.blit(sprite.atlasLocation(), 0, 0, 5, 10, u0, u1, v0, v1);
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            if (menu.slots.size() > 4) {
                ContainerStyle.inventoryLabel(g, menu.slots.get(4));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
                onClose();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
            return super.hasClickedOutside(mouseX, mouseY, left, top)
                    && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
        }
    }

    // -- Table de cartographie ---------------------------------------------------------------

    /**
     * La table de cartographie : la carte et ce qu'on lui ajoute a gauche (papier pour l'agrandir,
     * carte vierge pour la copier, vitre pour la verrouiller), la fleche, l'apercu au milieu, le
     * resultat cercle a droite. L'apercu est celui du jeu, a ses places, un peu reduit pour tenir
     * dans son creux : la carte agrandie, ses deux exemplaires, ou son cadenas. Une croix rouge sur
     * la fleche quand l'ajout ne se peut pas (carte verrouillee, ou deja a sa plus grande taille).
     * Sans carte, l'apercu dit ce que fait chaque ajout.
     */
    public static final class Cartography extends CartographyTableScreen {
        private static final Identifier SCALED = Identifier.withDefaultNamespace("container/cartography_table/scaled_map");
        private static final Identifier DUPLICATED =
                Identifier.withDefaultNamespace("container/cartography_table/duplicated_map");
        private static final Identifier SHEET = Identifier.withDefaultNamespace("container/cartography_table/map");
        private static final Identifier LOCKED = Identifier.withDefaultNamespace("container/cartography_table/locked");
        /** L'apercu du jeu tient dans (67, 13) - (133, 79) : on le reduit autour de son centre. */
        private static final float PREVIEW_SCALE = 0.86f;
        private final MapRenderState preview = new MapRenderState();
        private final ItemStack icon = new ItemStack(Items.CARTOGRAPHY_TABLE);

        public Cartography(CartographyTableMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ItemStack extra = menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem();
            boolean copy = extra.is(Items.MAP);
            boolean zoom = extra.is(Items.PAPER);
            boolean lock = extra.is(Items.GLASS_PANE);
            ItemStack map = menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem();
            MapId id = map.get(DataComponents.MAP_ID);
            MapItemSavedData data = id == null || minecraft.level == null ? null : MapItem.getSavedData(id, minecraft.level);
            // Les deux refus du jeu : une carte verrouillee ne change plus, une carte a l'echelle 4 ne grandit plus.
            boolean blocked = data != null && (data.locked || zoom && data.scale >= 4);
            boolean failed = data != null && (data.locked && (zoom || lock) || zoom && data.scale >= 4);

            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Paint p = g.paint();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            frame(g, p, icon, headerTitle(title, "container.cartography_table", Blocks.CARTOGRAPHY_TABLE),
                    imageWidth, imageHeight, mouseX - leftPos, mouseY - topPos);
            ContainerStyle.inset(p, 8, 9, imageWidth - 16, 66);
            Slot result = menu.getSlot(CartographyTableMenu.RESULT_SLOT);
            for (int index = 0; index < menu.slots.size(); index++) {
                Slot slot = menu.slots.get(index);
                if (index == CartographyTableMenu.RESULT_SLOT) {
                    ContainerStyle.bigSlot(p, slot, Ui.Theme.GREEN);
                } else {
                    ContainerStyle.slot(p, slot);
                }
            }
            // La carte, plus ce qu'on lui ajoute, puis la fleche vers l'apercu.
            Slot first = menu.getSlot(CartographyTableMenu.MAP_SLOT);
            Slot second = menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT);
            float plusX = first.x + 8.0f;
            float plusY = (first.y + 16 + second.y) / 2.0f;
            int plus = second.hasItem() ? Ui.Theme.TEXT2 : 0xFF3A3C42;
            p.rect(plusX - 3.5f, plusY - 0.65f, 7.0f, 1.3f, plus);
            p.rect(plusX - 0.65f, plusY - 3.5f, 1.3f, 7.0f, plus);
            p.arrow(39.0f, 37.5f, 20.0f, 9.0f, failed ? 0xFF4A2A2A : result.hasItem() ? Ui.Theme.TEXT2 : 0xFF3A3C42);
            if (failed) {
                p.glow(49.0f, 42.0f, 11.0f, Paint.alpha(Ui.Theme.RED, 0.35f));
                p.cross(49.0f, 42.0f, 12.0f, Ui.Theme.RED_LIGHT);
            }
            // L'apercu dans son creux.
            p.framed(63.5f, 11.5f, 73.0f, 61.0f, 3.5f, 0xFF101113, 0xFF24262A, 1);
            if (map.isEmpty()) {
                g.wrapped(Ui.BODY, "Pose une carte, puis du papier pour l'agrandir, une carte vierge pour la copier "
                        + "ou une vitre pour la verrouiller.", 68.0f, 21.0f, 4.6f, 64.0f, 6.6f, Ui.Theme.MUTED, 6);
            }
            pose.popMatrix();

            if (!map.isEmpty()) {
                pose.pushMatrix();
                pose.translate(leftPos + 100.0f, topPos + 42.0f);
                pose.scale(PREVIEW_SCALE, PREVIEW_SCALE);
                pose.translate(-100.0f, -46.0f);
                resulting(graphics, id, data, copy, zoom, lock, blocked);
                pose.popMatrix();
            }
        }

        /** L'apercu du jeu, a ses places (dans le repere de la fenetre). */
        private void resulting(GuiGraphicsExtractor graphics, MapId id, MapItemSavedData data, boolean copy,
                               boolean zoom, boolean lock, boolean blocked) {
            if (zoom && !blocked) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCALED, 67, 13, 66, 66);
                drawMap(graphics, id, data, 85, 31, 0.226f);
            } else if (copy) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DUPLICATED, 83, 13, 50, 66);
                drawMap(graphics, id, data, 86, 16, 0.34f);
                graphics.nextStratum();
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DUPLICATED, 67, 29, 50, 66);
                drawMap(graphics, id, data, 70, 32, 0.34f);
            } else if (lock) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SHEET, 67, 13, 66, 66);
                drawMap(graphics, id, data, 71, 17, 0.45f);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCKED, 118, 60, 10, 14);
            } else {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SHEET, 67, 13, 66, 66);
                drawMap(graphics, id, data, 71, 17, 0.45f);
            }
        }

        private void drawMap(GuiGraphicsExtractor graphics, MapId id, MapItemSavedData data, int x, int y, float scale) {
            if (id == null || data == null) {
                return;
            }
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(scale, scale);
            minecraft.getMapRenderer().extractRenderState(id, data, preview);
            graphics.map(preview);
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            if (menu.slots.size() > 3) {
                ContainerStyle.inventoryLabel(g, menu.slots.get(3));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
                onClose();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
            return super.hasClickedOutside(mouseX, mouseY, left, top)
                    && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
        }
    }
}
