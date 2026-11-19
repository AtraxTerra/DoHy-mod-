package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Le Lucky Pass ({@code UILuckyPass}, {@code PacketGetLuckyPass} et
 * {@code PacketWinLuckyPass}).
 *
 * <p>Sur Paladium, le Lucky Pass s'achetait sur le site : un lancer par jour, et
 * les bons evenements 1,25 fois plus probables. Ici il est toujours actif et
 * gratuit. Le lancer donne trois cases ({@code PacketGetLuckyPass}) :
 * <ul>
 *   <li>6 fois sur 10, un lucky block, au milieu ;</li>
 *   <li>3 fois sur 10, deux ;</li>
 *   <li>1 fois sur 10, trois.</li>
 * </ul>
 * Chaque case tire son type au poids du Lucky Pass ({@link LuckyType#draw}). Le
 * jour change a l'heure des contrats du jour ({@code ContractApi.today}).
 */
@EventBusSubscriber
public final class LuckyPass {

    private LuckyPass() {
    }

    public static void open(ServerPlayer player) {
        UiNet.show(player, view(player, 0));
    }

    /** Le lancer du jour est-il encore a faire, ou reste-t-il un lancer offert ? */
    public static boolean available(ServerPlayer player) {
        LuckyData data = PalaLucky.data(player);
        return data.passDay() != ContractApi.today() || data.passBonus() > 0;
    }

    /**
     * Un lancer offert ({@code setHasLuckyEventBypassNow} puis
     * {@code GUI_LUCKY_PASS_BYPASS}) : il s'ajoute a celui du jour.
     */
    public static void giveBonus(ServerPlayer player) {
        PalaLucky.data(player).giveBonus();
    }

    public static void handle(ServerPlayer player, UiNet.Action action) {
        if ("spin".equals(action.verb())) {
            spin(player);
        }
    }

    private static void spin(ServerPlayer player) {
        LuckyData data = PalaLucky.data(player);
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!available(player)) {
            LuckyWorld.tell(player, "[LuckyPass] Reviens demain pour gagner tes lucky blocks !", ChatFormatting.AQUA);
            UiNet.show(player, view(player, 0));
            return;
        }
        List<Integer> wins = draw(player.getRandom());
        if (data.passDay() != ContractApi.today()) {
            data.spin(ContractApi.today(), wins);
        } else {
            data.bonusSpin(wins);
        }
        for (int win : wins) {
            if (win >= 0) {
                ItemStack stack = LuckyType.of(win).stack(1);
                if (!player.getInventory().add(stack)) {
                    LuckyWorld.drop(level, player.getX(), player.getY() + 0.5d, player.getZ(), stack);
                }
            }
        }
        LuckyWorld.sound(level, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, 3.0f, 1.0f);
        level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.0d, player.getZ(), 20,
                0.3d, 0.3d, 0.3d, 0.05d);
        UiNet.show(player, view(player, data.passSpins()));
    }

    /** Les trois cases d'un lancer : -1 vide, sinon le numero du type. */
    static List<Integer> draw(RandomSource random) {
        int roll = random.nextInt(100);
        int[] slots = roll < 10 ? new int[] {0, 0, 0} : roll < 40 ? new int[] {0, 0, -1} : new int[] {-1, 0, -1};
        List<Integer> wins = new ArrayList<>(3);
        for (int slot : slots) {
            wins.add(slot < 0 ? -1 : LuckyType.draw(random).ordinal());
        }
        return wins;
    }

    static LuckyPassView view(ServerPlayer player, int spin) {
        LuckyData data = PalaLucky.data(player);
        boolean claimed = !available(player);
        // Juste apres un lancer, les rouleaux s'arretent sur ses gains, meme s'il reste un lancer offert.
        return new LuckyPassView(claimed, claimed || spin > 0 ? data.passWins() : List.of(-1, -1, -1), spin,
                ContractApi.minutesUntilDailyReset(), data.passSpins());
    }

    /** {@code /luckypass} (et {@code /lucky}) : l'ecran du Lucky Pass. */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("luckypass").executes(context -> {
            open(context.getSource().getPlayerOrException());
            return 1;
        }));
        event.getDispatcher().register(Commands.literal("lucky").executes(context -> {
            open(context.getSource().getPlayerOrException());
            return 1;
        }));
    }
}
