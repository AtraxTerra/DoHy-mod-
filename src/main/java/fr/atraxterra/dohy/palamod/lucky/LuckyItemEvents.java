package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.pvp.PalaPvp;
import fr.atraxterra.dohy.palamod.pvp.SpikeBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Les evenements du quatrieme lot : ceux qui demandaient un objet ou un bloc de PalaMod
 * absent du mod jusqu'ici ({@link LuckyItems}, le faux bloc de paladium vert), et les deux
 * pieges au slimepad.
 *
 * <p><b>Ecarts</b> : un objet donne dans l'inventaire tombe au sol si l'inventaire est
 * plein (PalaMod le perdait) ; deux noms et quatre images changent, parce qu'ils
 * reprenaient un personnage, un logo ou un objet d'une autre licence :
 * « Flash McQueen » devient « Coup de turbo », « Michael Jordans » devient « Air Min »
 * (le nom des bottes chez PalaMod), et « J'ai glisse chef » montre un panneau de sol
 * glissant.
 */
final class LuckyItemEvents {

    /** {@code spawnStructure(..., 7, 1, 7, SLIMEPAD_BLOCK, ...)} : la nappe de « J'ai glisse chef ». */
    private static final int SLIME_PAD_SIZE = 7;
    /** {@code y < pos.getY() + 10} : la hauteur de la tour, et le pic qui la coiffe. */
    private static final int TOWER_HEIGHT = 10;

    private LuckyItemEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.classic("slime_pad", "J'ai glisse chef", "sol_glissant", true, 100, LuckyItemEvents::slimePad);
        c.classic("slime_tower", "Tour de slime", "tour_de_slime", true, 20, LuckyItemEvents::slimeTower);

        c.findium("pig_helmet", "Grouik Grouik (Helmet)", "grouik_grouik_hat", false, 1000,
                (player, level, pos) -> pig(player, LuckyItems.PIG_HELMET));
        c.findium("pig_chestplate", "Grouik Grouik (Chestplate)", "grouik_grouik_armor", false, 1000,
                (player, level, pos) -> pig(player, LuckyItems.PIG_CHESTPLATE));
        c.findium("pig_leggings", "Grouik Grouik (Leggings)", "grouik_grouik_leggings", false, 1000,
                (player, level, pos) -> pig(player, LuckyItems.PIG_LEGGINGS));
        c.findium("pig_boots", "Grouik Grouik (Boots)", "grouik_grouik_boots", false, 1000,
                (player, level, pos) -> pig(player, LuckyItems.PIG_BOOTS));
        c.findium("flashmcqueen", "Coup de turbo", "coup_de_turbo", false, 3000,
                (player, level, pos) -> give(player, new ItemStack(LuckyItems.SPEED_APPLE.get())));
        c.findium("invisible_human", "Homme invisible", "homme_invisible", false, 800, LuckyItemEvents::invisible);
        c.findium("palarelou_green", "Ouaiche t'es tres relou", "ouaich_t_est_tres_relou", false, 7000,
                (player, level, pos) -> LuckyWorld.place(player, level, pos,
                        PalaLucky.GREEN_PALADIUM_FAKE_BLOCK.get().defaultBlockState()));
        c.findium("cavehelmet", "C'est cheat un peu", "cheat_un_peu", false, 1500,
                (player, level, pos) -> give(player, new ItemStack(LuckyItems.CAVE_HELMET.get())));
        c.findium("fakemoney", "Faux billets", "fakemoney", true, 100,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyItems.FAKE_MONEY.get(), 5)));
        c.findium("balaclava", "Cagoule", "cagoule", false, 200,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyItems.BALACLAVA_HELMET.get())));
        c.findium("calcium", "Dose de Calcium", "calcium", false, 100,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyItems.CALCIUM.get())));
        c.findium("airmin", "Air Min", "baskets_ailees", false, 200,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyItems.AIR_MIN_BOOTS.get())));
        c.findium("fakestuffendium", "Juste pour flex", "flex", false, 400, LuckyItemEvents::fakeEndium);
        c.findium("poulay", "Poulay", "poulay", false, 300,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyItems.COUAC_SWORD.get())));
    }

    /** {@code addItemStackToInventory} : dans l'inventaire, ou au sol s'il est plein. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Une piece de l'armure de cochon, et « Grouik Grouik! » en rose au-dessus de la barre d'objets. */
    private static void pig(ServerPlayer player, DeferredItem<Item> piece) {
        give(player, new ItemStack(piece.get()));
        LuckyWorld.bar(player, "Grouik Grouik!", ChatFormatting.LIGHT_PURPLE);
    }

    /**
     * J'ai glisse chef ({@code SlimePad}) : une nappe de 7 x 7 slimepads a hauteur des
     * pieds du joueur, lui compris.
     */
    private static void slimePad(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyWorld.box(player, level, player.blockPosition(), SLIME_PAD_SIZE, 1, SLIME_PAD_SIZE,
                PalaPvp.SLIMEPAD.get().defaultBlockState(), null);
        LuckyWorld.tell(player, "Il ne me semblait pas avoir vu de panneau sol glissant pourtant ?",
                ChatFormatting.WHITE);
    }

    /**
     * Tour de slime ({@code SlimeTower}) : un puits de dix slimepads cercle de pierre, un pic
     * en paladium au sommet, pointe en bas, et le joueur pose un bloc au-dessus de ses pieds.
     * Les slimepads le lancent vers le haut, droit dans le pic.
     */
    private static void slimeTower(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos base = player.blockPosition();
        BlockState pad = PalaPvp.SLIMEPAD.get().defaultBlockState();
        BlockState wall = Blocks.COBBLESTONE.defaultBlockState();
        for (int dy = 0; dy < TOWER_HEIGHT; dy++) {
            BlockPos at = base.above(dy);
            LuckyWorld.place(player, level, at, pad);
            LuckyWorld.place(player, level, at.east(), wall);
            LuckyWorld.place(player, level, at.west(), wall);
            LuckyWorld.place(player, level, at.south(), wall);
            LuckyWorld.place(player, level, at.north(), wall);
        }
        // La metadonnee 0 de BlockSpike : la pointe vers le bas, la face du haut pleine.
        LuckyWorld.place(player, level, base.above(TOWER_HEIGHT),
                PalaPvp.PALADIUM_SPIKE.get().defaultBlockState().setValue(SpikeBlock.FACING, Direction.DOWN));
        player.teleportTo(base.getX() + 0.5d, base.getY() + 1, base.getZ() + 0.5d);
    }

    /** Homme invisible ({@code InvisibleHuman}) : une piece au hasard, un quart de chance chacune. */
    private static void invisible(ServerPlayer player, ServerLevel level, BlockPos pos) {
        double roll = level.getRandom().nextDouble();
        DeferredItem<Item> piece = roll < 0.25d ? LuckyItems.INVISIBLE_HELMET
                : roll < 0.5d ? LuckyItems.INVISIBLE_CHESTPLATE
                : roll < 0.75d ? LuckyItems.INVISIBLE_LEGGINGS
                : LuckyItems.INVISIBLE_BOOTS;
        LuckyWorld.drop(level, pos, new ItemStack(piece.get()));
    }

    /**
     * Juste pour flex ({@code FakeStuffEndium}) : {@code nextInt(5)}, de 1 a 4 une piece
     * dans l'ordre, et 0 retombe sur les bottes — qui sortent donc deux fois sur cinq.
     */
    private static void fakeEndium(ServerPlayer player, ServerLevel level, BlockPos pos) {
        DeferredItem<Item> piece = switch (level.getRandom().nextInt(5)) {
            case 1 -> LuckyItems.FAKE_ENDIUM_HELMET;
            case 2 -> LuckyItems.FAKE_ENDIUM_CHESTPLATE;
            case 3 -> LuckyItems.FAKE_ENDIUM_LEGGINGS;
            default -> LuckyItems.FAKE_ENDIUM_BOOTS;
        };
        LuckyWorld.drop(level, pos, new ItemStack(piece.get()));
    }
}
