# CombatGuard

Anti-triche des clics et de la visée pour les serveurs de combat du réseau Replex (The Pit, HikaBrain).
Il complète Grim, qui ne contrôle ni l'autoclicker ni la visée. Plan complet : `Serveur Minecraft/ANTITRICHE-COMBAT.md`.

**Mode observation** : alertes au staff et journal seulement. Rien n'est annulé, personne n'est expulsé ni banni.

## Contrôles

| Contrôle | Alerte quand | Points |
|---|---|---|
| Clics | plus de 20 clics par seconde pendant 3 s | 1 |
| Clics réguliers | 30 écarts d'affilée identiques au tick près, à 8 clics par seconde ou plus | 2 |
| Visée | coup porté à plus de 50° du regard, à plus de 1,5 bloc | 2 |
| Multi-cibles | deux cibles frappées en 2 ticks, à plus de 90° l'une de l'autre | 3 |

- Seuls les clics dans le vide ou sur une entité comptent : casser ou poser des blocs (pont du HikaBrain) n'en fait pas partie.
- Les points baissent de 1 par minute sans alerte.
- Pauses : serveur sous 18 TPS (tous les contrôles) ; ping au-dessus de 300 ms et 3 s après une connexion, téléportation ou réapparition (visée et multi-cibles).
- Tout se règle et se coupe dans `config.yml`.

## Commandes

- `/cg alerts` : couper ou remettre les alertes (permission `combatguard.alerts`, op par défaut).
- `/cg info <joueur>` : points, ping et 10 dernières alertes.
- `/cg exempt <joueur>` : exempter un joueur pour un test, jusqu'au redémarrage.
- `/cg reload` : recharger `config.yml`.

Toutes les alertes sont aussi écrites dans `plugins/CombatGuard/alerts.log`.

## Compiler

`./gradlew build` (Windows : `gradlew.bat build`) → `build/libs/CombatGuard.jar`. Java 25, Paper 26.2.
À installer sur les serveurs The Pit et HikaBrain (pas sur Velocity).

GitHub compile aussi le plugin à chaque push : le jar est dans l'onglet **Actions**, artefact `CombatGuard`.
