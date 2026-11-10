package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.ui.view.UiView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * L'ecran « Creation Faction » de Paladium, qui sert aussi a modifier la
 * faction : nom, description, MOTD (en modification), deux tags, blason.
 *
 * @param create    creation (cout en lingots) ou modification
 * @param canAfford le joueur a les 100 lingots de paladium sur lui
 * @param error     le dernier refus du serveur, vide sinon
 */
public record FactionEditView(boolean create, String name, String description, String motd, List<String> tags,
                              int banner, int cost, boolean canAfford, List<String> allTags, String error)
        implements UiView {

    public static final Identifier ID = UiNet.id("faction_edit");

    /** Separateur des champs dans l'argument de l'action « save ». */
    public static final String SEPARATOR = "\u001f";

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionEditView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                buffer.writeBoolean(view.create());
                buffer.writeUtf(view.name());
                buffer.writeUtf(view.description());
                buffer.writeUtf(view.motd());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buffer, view.tags());
                buffer.writeVarInt(view.banner());
                buffer.writeVarInt(view.cost());
                buffer.writeBoolean(view.canAfford());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buffer, view.allTags());
                buffer.writeUtf(view.error());
            },
            buffer -> new FactionEditView(buffer.readBoolean(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buffer), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readBoolean(),
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buffer), buffer.readUtf()));

    @Override
    public Identifier viewId() {
        return ID;
    }
}
