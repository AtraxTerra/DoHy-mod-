package fr.atraxterra.dohy.casino;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Les panneaux de casino de NationsGlory, ecrits sur un panneau ordinaire :
 *
 * <pre>
 * [Casino]         [Casino2]
 * nom du casino    nom du casino
 * prix du ticket   somme gagnee
 * nom du jeu       nom du jeu (le meme)
 * </pre>
 *
 * <p>Le [Casino] s'accroche a un monnayeur : un clic droit paie le ticket et lance le jeu. Le
 * [Casino2] s'accroche a une caisse des gains : quand elle s'allume, il paie le gagnant. Le maitre
 * des blocs reecrit ses panneaux accroupi ; debout, son clic ouvre le jeu aux clients et lance une
 * partie d'essai, sans argent.
 */
@EventBusSubscriber
public final class CasinoSigns {

    /** Les quatre lignes d'un panneau de casino. */
    public record Lines(boolean payout, String casino, long amount, String game, String rawAmount) {
    }

    private CasinoSigns() {
    }

    /** Le bloc auquel le panneau est accroche, ou {@code null} (panneaux suspendus). */
    static BlockPos attached(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof WallSignBlock) {
            return pos.relative(state.getValue(WallSignBlock.FACING).getOpposite());
        }
        if (state.getBlock() instanceof StandingSignBlock) {
            return pos.below();
        }
        return null;
    }

    /** Les panneaux de casino du bon genre accroches a ce bloc. */
    static List<SignBlockEntity> signsOn(Level level, BlockPos block) {
        List<SignBlockEntity> signs = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) {
                continue;
            }
            BlockPos at = block.relative(direction);
            BlockState state = level.getBlockState(at);
            if (block.equals(attached(state, at)) && level.getBlockEntity(at) instanceof SignBlockEntity sign) {
                signs.add(sign);
            }
        }
        return signs;
    }

    /** Les lignes d'un panneau de casino (face avant, sinon arriere), ou {@code null}. */
    static Lines read(SignBlockEntity sign) {
        Lines front = read(sign.getFrontText());
        return front != null ? front : read(sign.getBackText());
    }

    private static Lines read(SignText text) {
        String head = line(text, 0).toLowerCase(Locale.ROOT);
        boolean payout;
        if (head.equals("[casino]")) {
            payout = false;
        } else if (head.equals("[casino2]")) {
            payout = true;
        } else {
            return null;
        }
        String amount = line(text, 2);
        return new Lines(payout, line(text, 1), amount(amount), line(text, 3), amount);
    }

    private static String line(SignText text, int index) {
        return text.getMessage(index, false).getString().trim();
    }

    /**
     * « 1000 », « 1 000 », « 1000$ », « 2,5k », « 1M » : la somme d'un panneau, ou -1 si elle ne
     * se lit pas (ou sort de 1 $ a 10 millions).
     */
    static long amount(String raw) {
        String text = raw.toLowerCase(Locale.ROOT).replace(" ", "").replace("$", "").replace(',', '.');
        if (text.isEmpty()) {
            return -1L;
        }
        double factor = 1.0d;
        if (text.endsWith("k")) {
            factor = 1_000.0d;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("m")) {
            factor = 1_000_000.0d;
            text = text.substring(0, text.length() - 1);
        }
        try {
            long value = Math.round(Double.parseDouble(text) * factor);
            return value >= 1L && value <= 10_000_000L ? value : -1L;
        } catch (NumberFormatException wrong) {
            return -1L;
        }
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            return;
        }
        Lines lines = read(sign);
        if (lines == null) {
            return;
        }
        BlockPos block = attached(state, pos);
        CasinoBlockEntity casino = block != null && level.getBlockEntity(block) instanceof CasinoBlockEntity found
                ? found : null;
        // Le maitre (ou n'importe qui, si le panneau n'est accroche a rien) le reecrit accroupi.
        if (player.isShiftKeyDown() && (casino == null || casino.ownedBy(player))) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        String refusal = lines.payout()
                ? Casinos.describePayout(player, level, block, casino, lines)
                : Casinos.play(player, level, pos, block, casino, lines);
        if (refusal != null) {
            player.sendSystemMessage(Component.literal("[Casino] " + refusal).withStyle(ChatFormatting.RED));
        }
    }
}
