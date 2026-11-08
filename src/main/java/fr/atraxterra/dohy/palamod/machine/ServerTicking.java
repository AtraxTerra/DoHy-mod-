package fr.atraxterra.dohy.palamod.machine;

/**
 * Une block entity qui travaille a chaque tick du serveur.
 *
 * <p>Elle existe pour que {@link MachineBlock} puisse brancher son ticker sans
 * connaitre {@link MachineBlockEntity}. Le totem de fertilite travaille comme
 * une machine mais n'a pas d'interface : l'obliger a declarer un type de menu
 * et une disposition de cases pour obtenir un ticker aurait demande d'inventer
 * les deux.
 */
public interface ServerTicking {

    /** Le travail d'un tick. Appele par le ticker du serveur uniquement. */
    void serverTick();
}
