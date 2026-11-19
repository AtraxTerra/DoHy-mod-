package fr.atraxterra.dohy.ui;

import fr.atraxterra.dohy.contract.ActiveContract;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * Les pastilles de la barre laterale : ce qui attend le joueur, entree par entree.
 *
 * <p>Calculees a chaque vue envoyee, et renvoyees seulement si elles changent. Une
 * source qui echoue ne donne pas de pastille, elle ne bloque pas l'ecran.
 */
public final class NavBadges {

    private NavBadges() {
    }

    public static UiNet.Nav of(ServerPlayer player) {
        Map<String, String> badges = new HashMap<>();
        try {
            int left = 0;
            for (ActiveContract contract : ContractApi.data(player).daily()) {
                if (!contract.done()) {
                    left++;
                }
            }
            if (left > 0) {
                badges.put("contrats", String.valueOf(left));
            }
        } catch (RuntimeException ignored) {
            // pas de pastille
        }
        try {
            int claimable = fr.atraxterra.dohy.pass.PassApi.claimable(player);
            if (claimable > 0) {
                badges.put("pass", "+" + claimable);
            }
        } catch (RuntimeException ignored) {
            // pas de pastille
        }
        try {
            for (Job job : Job.values()) {
                if (fr.atraxterra.dohy.jobboss.JobBossApi.state(player, job)
                        == fr.atraxterra.dohy.jobboss.JobBossApi.State.READY) {
                    badges.put("boss", "PRET");
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // pas de pastille
        }
        try {
            int points = fr.atraxterra.dohy.palamod.dungeon.Rpg.available(player);
            if (points > 0) {
                badges.put("rpg", "+" + points);
            }
        } catch (RuntimeException ignored) {
            // pas de pastille
        }
        try {
            int fresh = 0;
            for (String id : fr.atraxterra.dohy.guide.EncyclopediaEvents.data(player).freshIds()) {
                if (fr.atraxterra.dohy.guide.Encyclopedia.entry(id) != null) {
                    fresh++;
                }
            }
            if (fresh > 0) {
                badges.put("encyclopedie", "+" + fresh);
            }
        } catch (RuntimeException ignored) {
            // pas de pastille
        }
        try {
            int trixium = fr.atraxterra.dohy.palamod.trixium.TrixiumContest.claimable(player);
            if (trixium > 0) {
                badges.put("trixium", "+" + trixium);
            }
        } catch (RuntimeException ignored) {
            // pas de pastille
        }
        try {
            // Un invendu de l'hotel des ventes attend d'etre recupere.
            int waiting = fr.atraxterra.dohy.auction.AuctionSales.unsold(player);
            if (waiting > 0) {
                badges.put("commerce", String.valueOf(waiting));
            }
        } catch (RuntimeException ignored) {
            // pas de pastille
        }
        int level = 0;
        try {
            level = ContractApi.globalLevel(player);
        } catch (RuntimeException ignored) {
            // niveau inconnu
        }
        return new UiNet.Nav(badges, level);
    }
}
