package fr.atraxterra.dohy.job;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * L'application des verrous de metier.
 *
 * <p><b>Ce que le verrou bloque, et pourquoi pas le craft.</b> Paladium
 * refusait aussi la fabrication — {@code JobsConstants} porte bien un
 * {@code CRAFTING_NOT_ALLOWED}. Ce n'est pas reproduit ici, et c'est un choix,
 * pas un oubli : la sonde d'API a etabli que {@code PlayerEvent.ItemCraftedEvent}
 * <b>n'implemente pas {@code ICancellableEvent}</b> en 26.2. Il ne reste donc
 * que des contournements, et tous violent le principe 4 de {@code DESIGN.md} —
 * rien d'irreversible pour le joueur : vider la pile fabriquee apres coup
 * detruirait les ingredients deja consommes, et les rendre ouvrirait une
 * duplication.
 *
 * <p>On verrouille donc <b>la pose et l'usage</b>, ce qui suffit : une machine
 * qu'on ne peut ni poser ni actionner est verrouillee pour de bon. L'objet reste
 * fabricable et se garde dans un coffre en attendant le niveau — ce qui est
 * meme plus doux que l'original, sans etre plus permissif sur ce qui compte.
 */
@EventBusSubscriber
public final class JobGateEvents {

    /**
     * Anti-spam des refus.
     *
     * <p>Un clic droit maintenu part plusieurs fois par seconde : sans ce
     * verrou, le refus noierait le chat et le joueur ne lirait jamais la ligne
     * qui lui dit quoi faire. Deux secondes par joueur, sur le tick du serveur.
     *
     * <p>Indexe par UUID et non par reference de joueur, pour la meme raison que
     * {@code SkillBossBar} : aucune reference a une entite morte ne traine.
     */
    private static final Map<UUID, Long> lastRefusal = new HashMap<>();
    private static final long REFUSAL_COOLDOWN_TICKS = 40L;

    private JobGateEvents() {
    }

    /**
     * Pose d'un bloc verrouille.
     *
     * <p>{@code BlockEvent.EntityPlaceEvent} est annulable (verifie a la sonde)
     * et l'annulation restitue l'item au joueur : rien n'est perdu.
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        JobConfig.Unlock unlock = JobGate.of(event.getPlacedBlock());
        if (JobGate.allows(player, unlock)) {
            return;
        }
        event.setCanceled(true);
        refuse(player, unlock);
    }

    /**
     * Clic droit sur un bloc verrouille, ou avec un item verrouille en main.
     *
     * <p>Les deux cas sont traites ensemble parce qu'actionner une machine et
     * utiliser un outil passent par le meme evenement. L'item est teste en
     * premier : s'il est verrouille, le joueur doit l'apprendre meme s'il visait
     * un bloc permis.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        JobConfig.Unlock held = JobGate.of(event.getItemStack());
        if (!JobGate.allows(player, held)) {
            event.setCanceled(true);
            refuse(player, held);
            return;
        }

        JobConfig.Unlock target = JobGate.of(event.getLevel().getBlockState(event.getPos()));
        if (!JobGate.allows(player, target)) {
            event.setCanceled(true);
            refuse(player, target);
        }
    }

    /** Clic droit dans le vide avec un item verrouille. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        JobConfig.Unlock unlock = JobGate.of(stack);
        if (JobGate.allows(player, unlock)) {
            return;
        }
        event.setCanceled(true);
        refuse(player, unlock);
    }

    private static void refuse(ServerPlayer player, JobConfig.Unlock unlock) {
        if (unlock == null) {
            return;
        }
        long now = player.level().getGameTime();
        Long previous = lastRefusal.get(player.getUUID());
        if (previous != null && now - previous < REFUSAL_COOLDOWN_TICKS) {
            return;
        }
        // La table ne porte que de l'anti-spam : la vider quand elle grossit
        // coute un refus affiche deux fois, et evite une fuite memoire si des
        // milliers de joueurs passent dans la meme session.
        if (lastRefusal.size() > 64) {
            lastRefusal.clear();
        }
        lastRefusal.put(player.getUUID(), now);
        player.sendSystemMessage(JobApi.refusal(player, unlock));
    }
}
