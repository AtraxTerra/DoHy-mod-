package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Les evenements du deuxieme lot qui posent un effet ({@code CustomPotion} de
 * PLuckyBlock). Ce que fait chaque effet vit dans LuckyEffectEvents (serveur),
 * LuckyClientEvents et LuckyOverlays (client).
 *
 * <p>Les durees « 999 999 999 tics » de PalaMod sont des effets sans fin : le
 * lait les retire, comme chez PalaMod.
 */
final class EffectEvents {

    /** {@code Thread.sleep(180000)} entre les deux {@code PacketUseInversion}. */
    private static final int INVERSION_TICKS = 180 * 20;
    /** {@code currentTimeMillis() + 900000} : quinze minutes. */
    private static final int HALF_XP_TICKS = 15 * 60 * 20;
    /** {@code new PotionEffect(..., 1200, 0)}. */
    private static final int MINUTE = 1200;

    private EffectEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        // Paladium
        c.classic("rayman", "T'as un probleme nan ?", "rayman", true, 20, (player, level, pos) ->
                forever(player, PalaLucky.RAYMAN, 0));
        c.classic("inversion", "Inversion", "inversion", true, 20, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(PalaLucky.INVERSION, INVERSION_TICKS)));
        c.classic("rotate", "Le monde a l'envers", "reverse", true, 50, (player, level, pos) ->
                forever(player, PalaLucky.ROTATE, 0));
        c.classic("boom_mobs", "Boom les mobs", "boom_les_mobs", true, 100, (player, level, pos) -> {
            forever(player, PalaLucky.BOOM_MOBS, 0);
            LuckyWorld.bar(player, "Cet effet me semble bien amusant", ChatFormatting.YELLOW);
        });
        c.classic("ghost", "Fantome", "ghost", true, 100, (player, level, pos) ->
                forever(player, PalaLucky.GHOST, 0));

        // Findium
        c.findium("teamtree", "#TeamTree", "teamtree", false, 100, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(PalaLucky.TREE, MINUTE)));
        c.findium("flou", "Sans lunette", "flou", true, 100, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(PalaLucky.FLOU, MINUTE)));
        c.findium("explose", "Explosif", "explosif", true, 100, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(PalaLucky.EXPLOSE, MINUTE)));
        c.findium("steve_jobs", "Steve Jobs", "steve_jobs", true, 200, (player, level, pos) ->
                forever(player, PalaLucky.STEVE, 0));
        c.findium("ilovecats", "I love cats", "i_love_cats", true, 200, (player, level, pos) -> {
            forever(player, PalaLucky.CAT, 1);
            player.sendSystemMessage(Component.literal("I love ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("cats!").withStyle(ChatFormatting.YELLOW)), true);
        });
        // « Tous les memes » : le meme skin par defaut que Steve Jobs ({@code STEVE}).
        c.findium("allinone", "Tous les memes", "allinone", true, 200, (player, level, pos) ->
                forever(player, PalaLucky.STEVE, 0));
        c.findium("divisexp", "Divise XP", "divise_xp", true, 300, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(PalaLucky.HALF_JOB_XP, HALF_XP_TICKS));
            LuckyWorld.tell(player, "L'XP de vos metiers est divisee par 2 pendant 15 minutes !",
                    ChatFormatting.GREEN);
        });
        c.findium("night_vision", "Vision nocturne", "vision_nocturne", true, 500, (player, level, pos) ->
                forever(player, PalaLucky.GREEN, 0));
    }

    private static void forever(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier));
    }
}
