package fr.atraxterra.dohy.ui.view;

import net.minecraft.resources.Identifier;

/**
 * Ce qu'un ecran du kit affiche, calcule par le serveur.
 *
 * <p>Le serveur envoie une vue ; le client la dessine et renvoie des actions
 * ({@link UiNet.Action}). Toute regle de jeu reste au serveur : le client ne
 * fait que montrer et demander.
 */
public interface UiView {

    Identifier viewId();
}
