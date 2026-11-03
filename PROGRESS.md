# État d'avancement

> Ce fichier est la **mémoire du projet**. Une session Claude ne garde rien d'une fois sur l'autre :
> lire `PROGRESS.md`, `docs/DESIGN.md` et `docs/BALANCE.md` suffit à reprendre le travail.
> **À mettre à jour à chaque fin de session.**

Dernière mise à jour : 2026-11-03

---

## Cible technique

| | |
|---|---|
| Minecraft | 26.2 (*Chaos Cubed*, sorti le 16 juin 2026) |
| Loader | NeoForge 26.2 (**beta** — épingler le build exact) |
| Mod optionnel | NTM NEXT 1.0.x (26.2, NeoForge) — phase 10 |
| Mode de jeu | Solo, monde normal |

---

## Fait

- [x] `docs/DESIGN.md` — spec des 8 systèmes, anti-exploits, ordre d'implémentation, décisions tranchées
- [x] `docs/BALANCE.md` — modèle de prix, courbes de skills, progression attendue, sinks
- [x] `data/rates.csv` — 152 items avec leur taux d'obtention horaire et leur tier
- [x] `tools/gen_prices.py` — générateur de prix + vérification de cohérence $/h par tier
- [x] `data/prices.csv` / `data/generated/prices.json` — générés, 152 items
- [x] `data/skills.json` — 8 skills, courbes, sources d'XP, 10 perks chacun
- [x] `data/collections.json` — 2 courbes de paliers, récompenses, exceptions
- [x] `data/contracts.json` — pool de 18 quotidiens + 10 hebdos, demande du jour, bonus de première vente

## À faire — phase 0 (socle)

- [ ] Projet Gradle NeoForge 26.2 (`build.gradle`, `gradle.properties`, `neoforge.mods.toml`)
- [ ] GitHub Actions : build à chaque push, c'est la boucle de validation de l'API
- [ ] `.gitignore` Gradle / IDEA

## À faire — phases 1 à 10

Ordre détaillé et justifié dans `docs/DESIGN.md` §11. Résumé :

1. [ ] Monnaie, persistance, HUD, **backups**
2. [ ] **Tracking des blocs posés** (chunk attachment) — bloquant pour 5 et 6
3. [ ] Shop statique, `/worth`, `/sellall`
4. [ ] Prix dynamiques et oubli
5. [ ] Collections
6. [ ] Skills et perks
7. [ ] Banque et pénalité de mort
8. [ ] Contrats et demande du jour
9. [ ] Boosters, caisses, autosell
10. [ ] NTM NEXT

---

## Bloqué

| Sujet | Blocage | Débloqué par |
|---|---|---|
| Accès au dépôt | GitHub n'est pas relié au compte Claude (`add_repo` → *link your GitHub account*) | Réglages → Connecteurs → connecter GitHub, puis autoriser `AtraxTerra/DoHy-mod-` |

---

## Points ouverts à trancher

- **IDs d'attributs 26.2** : les perks de `skills.json` utilisent des ids plausibles (`minecraft:mining_efficiency`, `minecraft:block_interaction_range`…) qui doivent être validés contre l'API réelle. À faire à la première compilation.
- **Version de Java** du toolchain 26.2 — à confirmer (NTM NEXT se construit avec JDK 25).
- **Plafonds villageois** : ~~à confirmer~~ → **tranché le 2026-11-03 : on garde les plafonds.** Ne pas relancer `gen_prices.py` avec `--no-villager-caps`. Audit encore à compléter sur : briques, argile, papier, verre teinté, charbon, fer.
- **Catégorie COMPONENT** : liste et prix d'achat à établir (phase 3).
- **Table de loot des caisses** : non écrite.
- **Barème d'XP du niveau global** par type de jalon : à fixer quand les contrats tournent.
- **`minecraft:potent_sulfur`** classé farmable sur la base des tags de geyser de 26.2 — **à vérifier en jeu** : si l'éruption ne produit pas de gain net, le reclasser en `FOUND`.

---

## Journal des sessions

### 2026-11-03
Première session. Cadrage complet et production du doc + data.
Décisions notables, toutes justifiées dans `DESIGN.md` §12 :
- Catégorie « trouvable » par ID d'item, pas par tag posé sur l'ItemStack
- Oubli des prix en temps de **jeu**, pas en temps réel (sinon les soirées de semaine sont stérilisées)
- Pénalité de mort indexée sur les **gains cumulés**, plafonnée
- **Tout item craftable est en achat seul**, jamais vendable → supprime l'audit de 2 000 recettes
- Prix **générés** depuis un modèle $/h, jamais écrits à la main
Aucun code écrit : le dépôt n'est pas encore accessible.
