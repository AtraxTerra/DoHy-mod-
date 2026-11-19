package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.pvp.PalaPvp;
import fr.atraxterra.dohy.palamod.storage.ChestTier;
import fr.atraxterra.dohy.palamod.storage.PalaStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.Instruments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Les evenements du troisieme lot pour les lucky blocks en paladium et en endium :
 * ceux qui s'appuient sur les coffres du mod (paladium, endium) ou sur le jeu.
 *
 * <p><b>Ecarts</b> : la guitare et la trompette de Custom NPCs, deux accessoires sans
 * effet, deviennent un bloc musical (qui joue de la guitare pose sur de la laine) et une
 * corne de chevre ; le coffre piege, « Triforce » chez PalaMod, change de nom et d'image
 * (l'embleme est celui d'une autre licence).
 */
final class ContentEvents {

    /** {@code EventUtils.random(-20000, 20000)} : ou tombe la carte au tresor. */
    static final int TREASURE_RANGE = 20000;
    /** Expala-osion : 64 fois deux lingots, un lacher tous les deux tics ({@code Thread.sleep(100)}). */
    private static final int RAIN_DROPS = 64;
    private static final int RAIN_PERIOD = 2;
    /** Reflexe : cinq secondes de montee ({@code 2500 x 2 ms}), jusqu'a 500 blocs plus haut. */
    private static final int REFLEX_TICKS = 100;
    private static final double REFLEX_HEIGHT = 500.0d;

    private ContentEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.classic("jimmy_hendyx", "Jimmy Hendryx", "jimmy_hendryx", false, 50, ContentEvents::guitar);
        c.classic("trompette", "Nez en trompette", "nez_en_trompette", false, 50, ContentEvents::horn);
        c.classic("expalaosion", "Expala-osion", "expala-osion", false, 150, ContentEvents::expalaosion);
        c.classic("treasure_map_good", "Carte au tresor", "carte_au_tresor_good", false, 200, (player, level, pos) ->
                treasure(player, level, true));
        c.classic("treasure_map_bad", "Carte au tresor", "carte_au_tresor", true, 200, (player, level, pos) ->
                treasure(player, level, false));
        c.classic("reflex", "Reflexe", "reflexe", true, 200, ContentEvents::reflex);
        c.classic("labyrinthe", "Labyrinthe", "labyrinthe", true, 200, LuckyMaze::build);
        c.classic("triforce", "Coffre maudit", "coffre_maudit", true, 1000, ContentEvents::cursedChest);
    }

    /** {@code npcGuitar} : un bloc musical, qui joue de la guitare pose sur de la laine. */
    private static void guitar(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ItemStack guitar = new ItemStack(Blocks.NOTE_BLOCK);
        guitar.set(DataComponents.CUSTOM_NAME, Component.literal("Guitare de Jimmy").withStyle(ChatFormatting.GOLD));
        guitar.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Posee sur de la laine, elle joue de la guitare").withStyle(ChatFormatting.GRAY))));
        LuckyWorld.drop(level, pos, guitar);
    }

    /** {@code npcFrenchHorn} : une corne de chevre. */
    private static void horn(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var instrument = level.registryAccess().lookupOrThrow(Registries.INSTRUMENT)
                .getOrThrow(Instruments.PONDER_GOAT_HORN);
        ItemStack horn = InstrumentItem.create(Items.GOAT_HORN, instrument);
        horn.set(DataComponents.CUSTOM_NAME, Component.literal("Trompette").withStyle(ChatFormatting.GOLD));
        LuckyWorld.drop(level, pos, horn);
    }

    /**
     * Une petite explosion qui ne casse rien ({@code createExplosion(..., 1.0F, false)}),
     * puis 64 fois deux lingots de paladium qui tombent de six blocs plus haut.
     */
    private static void expalaosion(ServerPlayer player, ServerLevel level, BlockPos pos) {
        level.explode(player, pos.getX(), pos.getY(), pos.getZ(), 1.0f, Level.ExplosionInteraction.NONE);
        RandomSource random = level.getRandom();
        LuckyTasks.repeat(player, RAIN_PERIOD, RAIN_PERIOD, RAIN_DROPS, step -> {
            ItemEntity item = new ItemEntity(level, pos.getX() + random.nextInt(6) - 3, pos.getY() + 6,
                    pos.getZ() + random.nextInt(6) - 3, new ItemStack(PalaItems.PALADIUM_INGOT.get(), 2));
            item.setPickUpDelay(5);
            level.addFreshEntity(item);
        });
    }

    /** Le premier bloc libre en surface ({@code getHeightValue}), le chunk genere s'il le faut. */
    static int surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    /** Une colonne au hasard a {@code range} blocs au plus de 0, dans la bordure du monde. */
    static BlockPos faraway(ServerLevel level, int range) {
        RandomSource random = level.getRandom();
        WorldBorder border = level.getWorldBorder();
        int x = (int) Math.round(border.getCenterX());
        int z = (int) Math.round(border.getCenterZ());
        for (int attempt = 0; attempt < 20; attempt++) {
            int tryX = random.nextInt(range * 2) - range;
            int tryZ = random.nextInt(range * 2) - range;
            if (border.isWithinBounds(tryX + 0.5d, tryZ + 0.5d, 20.0d)) {
                x = tryX;
                z = tryZ;
                break;
            }
        }
        return new BlockPos(x, surface(level, x, z), z);
    }

    /**
     * La carte au tresor ({@code TreasureMapGood} / {@code TreasureMapBad}) : une feuille de
     * papier qui porte les coordonnees d'un point au hasard, loin. La bonne y pose un coffre
     * en paladium plein, un lingot par case ; la mauvaise, une pancarte.
     */
    private static void treasure(ServerPlayer player, ServerLevel level, boolean good) {
        BlockPos at = faraway(level, TREASURE_RANGE);
        ItemStack map = new ItemStack(Items.PAPER);
        map.set(DataComponents.CUSTOM_NAME, Component.literal("Carte au tresor : ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(at.getX() + ", " + at.getY() + ", " + at.getZ())
                        .withStyle(ChatFormatting.AQUA)));
        if (!player.getInventory().add(map)) {
            player.drop(map, false);
        }
        if (good) {
            BlockState chest = PalaStorage.CHESTS.get(ChestTier.PALADIUM).get().defaultBlockState();
            if (LuckyWorld.place(player, level, at, chest) && level.getBlockEntity(at) instanceof Container container) {
                int slots = Math.min(ChestTier.PALADIUM.size(), container.getContainerSize());
                for (int slot = 0; slot < slots; slot++) {
                    container.setItem(slot, new ItemStack(PalaItems.PALADIUM_INGOT.get()));
                }
                container.setChanged();
            }
            return;
        }
        if (LuckyWorld.place(player, level, at, Blocks.OAK_SIGN.defaultBlockState())
                && level.getBlockEntity(at) instanceof SignBlockEntity sign) {
            sign.setText(new SignText()
                    .setMessage(0, Component.literal("[LuckyBlock]"))
                    .setMessage(1, Component.literal("Cheh y'a rien")), true);
            sign.setWaxed(true);
        }
    }

    /**
     * Reflexe : un deltaplane de moins ({@code consumeInventoryItem(HANGGLIDER)}), un seau
     * d'eau, puis cinq secondes de montee jusqu'a 500 blocs plus haut, dans des etincelles.
     * Il faut poser l'eau avant de toucher le sol.
     */
    private static void reflex(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(PalaPvp.HANG_GLIDER.get())) {
                inventory.getItem(slot).shrink(1);
                break;
            }
        }
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        if (!player.getInventory().add(bucket)) {
            player.drop(bucket, false);
        }
        LuckyTasks.repeat(player, 0, 1, REFLEX_TICKS, step -> {
            double y = pos.getY() + REFLEX_HEIGHT * (step + 1) / REFLEX_TICKS;
            player.teleportTo(pos.getX() + 0.5d, y, pos.getZ() + 0.5d);
            level.sendParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5d, y, pos.getZ() + 0.5d, 50, 0.3d, 0.3d,
                    0.3d, 0.0d);
        });
    }

    /**
     * Le coffre maudit ({@code Triforce}) : un coffre en endium vide, piege. L'ouvrir tue
     * ({@code onTriforce}, voir {@link LuckyContentEvents}).
     */
    private static void cursedChest(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState chest = PalaStorage.CHESTS.get(ChestTier.ENDIUM).get().defaultBlockState();
        if (LuckyWorld.place(player, level, pos, chest)) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) {
                entity.setData(PalaLucky.CURSED, true);
                entity.setChanged();
            }
        }
    }
}
