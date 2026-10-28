package com.replex.combatguard;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import org.bukkit.util.Vector;

/** Mesures en cours d'un joueur (en mémoire seulement, remises à zéro à la déconnexion). */
final class PlayerData {
   /** Ticks des derniers clics comptés (dans le vide ou sur une entité), sur la dernière seconde. */
   final Deque<Integer> clickTicks = new ArrayDeque<>();
   /** Secondes d'affilée au-dessus de cps.max, et le plus haut relevé pendant ces secondes. */
   int highCpsSeconds;
   int peakCps;

   /** Écarts en ticks entre les clics de la série en cours. */
   final Deque<Integer> intervals = new ArrayDeque<>();
   int lastClickTick = Integer.MIN_VALUE;

   /** Dernier coup porté : tick, cible, et direction de la cible vue depuis l'attaquant. */
   int lastHitTick = Integer.MIN_VALUE;
   UUID lastVictim;
   Vector lastHitDirection;

   /** Pas de contrôle de visée avant ce tick (connexion, téléportation, réapparition). */
   int graceUntilTick;

   double points;
   long lastDecayMillis = System.currentTimeMillis();
   /** Dernières alertes, pour /cg info. */
   final Deque<String> recentAlerts = new ArrayDeque<>();
}
