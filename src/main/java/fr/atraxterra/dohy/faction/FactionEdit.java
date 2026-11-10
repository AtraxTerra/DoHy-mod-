package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/** Le cote serveur de l'ecran de creation et de modification. */
public final class FactionEdit {

    private FactionEdit() {
    }

    public static void open(ServerPlayer player) {
        show(player, "");
    }

    private static void show(ServerPlayer player, String error) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        FactionData data = FactionApi.data(player);
        boolean create = !data.exists();
        UiNet.show(player, new FactionEditView(create, data.name(), data.description(), data.motd(),
                !create && FactionPerks.of(data).motd(), data.tags(),
                data.banner(), FactionApi.CREATION_COST, FactionApi.canAfford(player), FactionTags.ALL, error));
    }

    /** « save » : nom, description, MOTD, tags (separes par des virgules) et blason. */
    public static void handle(ServerPlayer player, UiNet.Action action) {
        if ("cancel".equals(action.verb())) {
            if (FactionApi.has(player)) {
                FactionMenus.Overview.INSTANCE.open(player);
            } else {
                UiNet.close(player);
            }
            return;
        }
        if (!"save".equals(action.verb())) {
            return;
        }
        String[] parts = action.argument().split(FactionEditView.SEPARATOR, -1);
        if (parts.length < 4) {
            return;
        }
        List<String> tags = new ArrayList<>();
        for (String tag : parts[3].split(",")) {
            if (!tag.isBlank()) {
                tags.add(tag.strip());
            }
        }
        boolean creating = !FactionApi.has(player);
        String refusal = creating
                ? FactionApi.create(player, parts[0], parts[1], tags, action.amount())
                : FactionApi.edit(player, parts[0], parts[1], parts[2], tags, action.amount());
        if (refusal != null) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.MASTER,
                    0.4f, 1.0f);
            show(player, refusal);
            return;
        }
        player.level().playSound(null, player.blockPosition(),
                creating ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.MASTER, 0.5f, 1.0f);
        if (!creating) {
            FactionApi.tell(player, "Faction modifiee.", ChatFormatting.GREEN);
        }
        FactionMenus.Overview.INSTANCE.open(player);
    }
}
