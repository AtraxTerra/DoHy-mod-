package fr.atraxterra.dohy.palamod.unlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import com.mojang.serialization.MapCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * L'etiquette en endium ({@code ItemEndiumNametag}, Chasseur 90).
 *
 * <p>Releve dans le code : une etiquette renommee a l'enclume, posee sur une
 * creature ({@code EntityLiving}), lui donne son nom <b>en bleu</b> ({@code §b})
 * et la rend persistante : elle ne disparait plus (le wiki : « invulnerable au
 * clear lag »). Le nom d'avant est garde sur la creature ({@code defaultName}).
 * Un joueur tient <b>trois</b> creatures ainsi nommees a la fois
 * ({@code PlayerEndiumNameTagProperties}) : la quatrieme rend son ancien nom a
 * la premiere. Une creature nommee qui meurt sort de la liste
 * ({@code onEndiumNameTagEntityDeath}).
 */
public class EndiumNametagItem extends Item {

    /** Le nombre de creatures nommees a la fois ({@code iconst_3}). */
    public static final int MAX_NAMED = 3;

    /** Sur la creature : le nom qu'elle portait avant (vide : aucun). */
    static final String DEFAULT_NAME = "palamod_default_name";
    /** Sur la creature : le joueur qui l'a nommee. */
    static final String OWNER = "palamod_endium_nametag_owner";

    /** La liste du joueur, de la plus ancienne a la plus recente. */
    public record Named(List<UUID> entities) {
        public static final Named EMPTY = new Named(List.of());
        public static final MapCodec<Named> MAP_CODEC = UUIDUtil.STRING_CODEC.listOf()
                .fieldOf("entities").xmap(Named::new, Named::entities);
    }

    public EndiumNametagItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name == null || !(target instanceof Mob mob) || !target.isAlive()) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer owner)) {
            return InteractionResult.SUCCESS;
        }
        if (!mob.getPersistentData().contains(DEFAULT_NAME)) {
            Component before = mob.getCustomName();
            mob.getPersistentData().putString(DEFAULT_NAME, before == null ? "" : before.getString());
        }
        mob.setCustomName(name.copy().withStyle(ChatFormatting.AQUA));
        mob.setPersistenceRequired();
        mob.getPersistentData().putString(OWNER, owner.getStringUUID());
        remember(owner, mob);
        if (!owner.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.palamod.endium_nametag.limit", MAX_NAMED)
                .withStyle(ChatFormatting.YELLOW));
        if (!stack.has(DataComponents.CUSTOM_NAME)) {
            tooltip.accept(Component.translatable("item.palamod.endium_nametag.anvil")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Inscrit la creature ; au-dela de trois, la plus ancienne reprend son nom. */
    private static void remember(ServerPlayer owner, Mob mob) {
        List<UUID> list = new ArrayList<>(owner.getData(PalaUnlocks.NAMED.get()).entities());
        list.remove(mob.getUUID());
        list.add(mob.getUUID());
        while (list.size() > MAX_NAMED) {
            UUID oldest = list.removeFirst();
            Entity entity = find(owner.level().getServer(), oldest);
            if (entity instanceof Mob old) {
                restore(old);
            }
        }
        owner.setData(PalaUnlocks.NAMED.get(), new Named(List.copyOf(list)));
    }

    /** Rend son ancien nom a une creature ({@code setCustomNameTag(defaultName)}). */
    static void restore(Mob mob) {
        String before = mob.getPersistentData().getStringOr(DEFAULT_NAME, "");
        mob.setCustomName(before.isEmpty() ? null : Component.literal(before));
        mob.getPersistentData().remove(DEFAULT_NAME);
        mob.getPersistentData().remove(OWNER);
    }

    /** Une creature nommee meurt : elle quitte la liste de son joueur, s'il est la. */
    static void forget(Mob mob) {
        String owner = mob.getPersistentData().getStringOr(OWNER, "");
        if (owner.isEmpty() || mob.level().getServer() == null) {
            return;
        }
        ServerPlayer player;
        try {
            player = mob.level().getServer().getPlayerList().getPlayer(UUID.fromString(owner));
        } catch (IllegalArgumentException invalid) {
            return;
        }
        if (player == null) {
            return;
        }
        List<UUID> list = new ArrayList<>(player.getData(PalaUnlocks.NAMED.get()).entities());
        if (list.remove(mob.getUUID())) {
            player.setData(PalaUnlocks.NAMED.get(), new Named(List.copyOf(list)));
        }
    }

    private static Entity find(MinecraftServer server, UUID id) {
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }
}
