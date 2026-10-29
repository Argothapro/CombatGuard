package com.replex.combatguard;

import com.replex.combatguard.logic.ClickSpeed;
import com.replex.combatguard.logic.ClickWindow;
import com.replex.combatguard.logic.LagDetector;
import com.replex.combatguard.logic.Points;
import com.replex.combatguard.logic.PositionHistory;
import com.replex.combatguard.logic.Regularity;
import com.replex.combatguard.math.Box;
import com.replex.combatguard.math.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mesures en cours d'un joueur (en mémoire seulement, oubliées à la déconnexion). */
final class PlayerData {
   /** Historique des positions : 2 s, de quoi compenser 1 s de ping et l'interpolation du client. */
   static final int HISTORY_TICKS = 40;

   final ClickWindow clicks = new ClickWindow();
   final ClickSpeed clickSpeed = new ClickSpeed();
   final Regularity regularity = new Regularity();
   final LagDetector lag = new LagDetector();
   final PositionHistory history = new PositionHistory(HISTORY_TICKS);

   /** Coups en attente du contrôle de visée : on attend le regard envoyé après le coup. */
   final List<PendingHit> pendingHits = new ArrayList<>(2);

   /** Dernier coup porté, pour le contrôle multi-cibles. */
   int lastHitTick = Integer.MIN_VALUE;
   UUID lastVictim;
   Vec3 lastHitDirection;

   /** Pas de contrôle de visée avant ce tick (connexion, téléportation, réapparition). */
   int graceUntilTick;

   /** Tous les points, et ceux des contrôles qui peuvent déclencher une réaction (can-react). */
   final Points points;
   final Points reactionPoints;

   /** Dernières alertes, pour /cg info. */
   final Deque<String> recentAlerts = new ArrayDeque<>();
   /** Par contrôle : dernier message au staff et alertes regroupées depuis. */
   final Map<String, long[]> alertThrottle = new HashMap<>();

   PlayerData(long nowMillis) {
      this.points = new Points(nowMillis);
      this.reactionPoints = new Points(nowMillis);
   }

   /** Coup porté, à juger quand le regard d'après le coup arrive. */
   record PendingHit(int tick, String victimName, Vec3 eye, float yawBefore, float pitchBefore, List<Box> targetBoxes) {
   }
}
