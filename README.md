# CombatGuard

Anti-triche des clics et de la visée pour les serveurs de combat du réseau Replex (The Pit, HikaBrain).
Il complète Grim, qui ne contrôle ni l'autoclicker ni la visée. Plan complet : `Serveur Minecraft/ANTITRICHE-COMBAT.md`.

**Mode observation par défaut** : alertes au staff et journal seulement. Le plugin ne renvoie jamais un joueur en arrière et n'annule aucun coup.
En mode `active`, il lance en plus les réactions configurées (aucune par défaut), jamais de ban, derrière un garde-fou.

## Contrôles

| Contrôle | Alerte quand | Points | Par défaut |
|---|---|---|---|
| Clics | plus de 20 clics par seconde en moyenne sur 3 s | 1 | actif |
| Visée | coup porté à plus de 50° du regard, à plus de 1,5 bloc | 2 | actif |
| Multi-cibles | deux cibles frappées dans le même tick ou le suivant, à plus de 100° l'une de l'autre | 3 | actif |
| Clics réguliers | 100 écarts d'affilée identiques au tick près, à 8 clics par seconde ou plus | 2 | coupé (peu fiable) |

## Lag : ce qui protège les joueurs honnêtes

- **Rafales** : après un blocage réseau, les paquets retenus arrivent d'un coup. Le plugin le repère (3 paquets de mouvement dans le même tick, ou un silence puis une reprise).
- **Clics** : après un lag, la moyenne est prise sur 10 s au lieu de 3 s, ce qui dilue la rafale sans laisser passer un autoclicker. Après un blocage de plus de 1,5 s, la mesure attend que la rafale sorte de la fenêtre.
- **Visée** : le serveur garde 2 s de positions de chaque joueur. Il juge le coup sur toutes les positions que l'attaquant a pu voir (son ping + 150 ms d'interpolation), et sur tous les regards entre celui d'avant et celui d'après le coup. Le client envoie en effet son regard après le coup.
- **Multi-cibles** : jamais dans la seconde qui suit un lag.
- **Pauses** : serveur sous 18 TPS, ping au-dessus de 300 ms, 3 s après une connexion, une téléportation ou une réapparition. Les joueurs Bedrock sont exemptés.

Les tests (`src/test`) simulent un client, un réseau TCP (latence, gigue, pertes renvoyées) et le serveur. Sur 200 minutes de combat simulé :
- un humain à 17 cps avec 300 ms de ping, 120 ms de gigue et 5 % de pertes ne déclenche aucune alerte ;
- un humain à 17 cps avec des blocages d'une seconde ne déclenche aucune alerte (sans la compensation, il en déclencherait des centaines) ;
- un autoclicker à 25 cps est signalé chaque minute, même avec une mauvaise connexion.

## Réactions (mode `active`)

- Liste vide par défaut. Seuls les points des contrôles marqués `can-react: true` comptent.
- Les commandes de ban sont refusées au chargement.
- Pas de réaction pendant un lag du serveur ou du joueur.
- Une réaction au plus par joueur toutes les 10 min, même après une reconnexion.
- Au-delà de 3 réactions en 1 min sur le serveur, toutes sont suspendues 10 min et le staff est prévenu : une vague de réactions veut dire un lag général ou des fausses alertes.

## Ajouter un contrôle

Une classe qui étend `Check`, une section du même nom dans `config.yml`, une ligne dans `CombatGuard.createChecks()`. Les réglages `enabled`, `points`, `name` et `can-react` sont lus automatiquement. Le calcul pur va dans `logic/` ou `math/`, avec un test.

## Commandes

- `/cg alerts` : couper ou remettre les alertes (permission `combatguard.alerts`, op par défaut).
- `/cg info <joueur>` : points, ping et 10 dernières alertes.
- `/cg exempt <joueur>` : exempter un joueur pour un test, jusqu'au redémarrage (ou permission `combatguard.bypass`).
- `/cg status` : mode, contrôles actifs, réactions suspendues ou non.
- `/cg reload` : recharger `config.yml`.

Toutes les alertes sont aussi écrites dans le journal (`alerts-file`, par défaut `plugins/CombatGuard/alerts.log`), avec le nom de l'instance et le ping du joueur. Un chemin absolu le garde hors des instances recréées à chaque partie, et plusieurs instances peuvent le partager. Au plus un message par contrôle et par joueur toutes les 5 s : les suivants sont regroupés (« +N »).

## Compiler

`./gradlew build` (Windows : `gradlew.bat build`) → `build/libs/CombatGuard.jar`. Les tests sont lancés avec. Java 25, Paper 26.2.
À installer sur les serveurs The Pit et HikaBrain (pas sur Velocity).

GitHub compile aussi le plugin à chaque push : le jar est dans l'onglet **Actions**, artefact `CombatGuard`.

## Mise à jour depuis 0.1.0

Remplacer `plugins/CombatGuard/config.yml` par le nouveau (supprimer l'ancien, le plugin le recrée) : l'ancien garde des réglages moins sûrs (régularité active sur 30 clics, multi-cibles sur 2 ticks à 90°).
