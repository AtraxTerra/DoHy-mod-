package fr.atraxterra.dohy.client.ui;

import fr.atraxterra.dohy.ui.view.UiView;

/** Un ecran qui affiche une vue du serveur et accepte ses mises a jour. */
public interface ViewScreen<V extends UiView> {

    /** La nouvelle vue, apres une action : l'ecran garde sa selection et son defilement. */
    void accept(V view);
}
