package com.replex.combatguard.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulation d'un joueur qui clique, de son réseau et du serveur, pour vérifier les contrôles de clics avec du lag.
 * - Client : traite les clics à chacun de ses ticks (50 ms) et envoie un paquet de mouvement par tick (il se bat, donc tourne).
 * - Réseau (TCP) : latence + gigue ; un paquet perdu est renvoyé après un délai et bloque les suivants (ils arrivent d'un coup).
 * - Serveur : traite les paquets arrivés au tick suivant, puis juge chaque seconde, comme CombatGuard.
 */
final class NetworkSimulation {
   /** Rythme de clic humain : écart moyen et écart-type, en ms. */
   record Clicker(double meanMs, double sdMs, boolean robot) {
   }

   /** Connexion : ping aller (ms), gigue max (ms), probabilité de perte d'un paquet, délai de renvoi (ms). */
   record Network(double oneWayMs, double jitterMs, double loss, double resendMs) {
   }

   record Result(int cpsAlerts, int regularityAlerts, int cpsAlertsWithoutLagCompensation) {
   }

   private record Packet(double sentMs, boolean click) {
   }

   static Result run(Clicker clicker, Network network, int seconds, long seed, int maxCps, int cpsSeconds, int samples, double minCps) {
      Random random = new Random(seed);
      double durationMs = seconds * 1000.0;

      // Clics humains ou robot, en temps continu, regroupés par tick client (le client les traite à son tick).
      List<Packet> packets = new ArrayList<>();
      double clientPhase = random.nextDouble() * 50;
      double nextClick = random.nextDouble() * clicker.meanMs();
      for (double tick = clientPhase; tick < durationMs; tick += 50) {
         while (nextClick <= tick) {
            packets.add(new Packet(tick, true));
            double gap = clicker.robot() ? clicker.meanMs() : Math.max(20, clicker.meanMs() + random.nextGaussian() * clicker.sdMs());
            nextClick += gap;
         }
         packets.add(new Packet(tick, false));
      }

      // Réseau TCP : dans l'ordre ; un paquet perdu retarde tous les suivants.
      double serverPhase = random.nextDouble() * 50;
      double lastArrival = 0;
      ClickWindow window = new ClickWindow();
      ClickSpeed speed = new ClickSpeed();
      ClickSpeed speedWithoutCompensation = new ClickSpeed();
      Regularity regularity = new Regularity();
      LagDetector lag = new LagDetector();
      int cpsAlerts = 0;
      int regularityAlerts = 0;
      int rawAlerts = 0;
      int judgedSecond = 0;
      for (Packet packet : packets) {
         double arrival = packet.sentMs() + network.oneWayMs() + random.nextDouble() * network.jitterMs();
         if (random.nextDouble() < network.loss()) {
            arrival += network.resendMs();
         }
         arrival = Math.max(arrival, lastArrival);
         lastArrival = arrival;
         int tick = (int) Math.ceil((arrival - serverPhase) / 50.0);

         // Jugement de chaque seconde écoulée avant ce tick (tâche du serveur, toutes les 20 ticks).
         while ((judgedSecond + 1) * 20 < tick) {
            judgedSecond++;
            int now = judgedSecond * 20;
            if (speed.judge(window, now, lag.laggedWithin(now, cpsSeconds * 20), lag.longStallWithin(now, ClickSpeed.LAG_WINDOW_SECONDS * 20), maxCps, cpsSeconds) > 0) {
               cpsAlerts++;
            }
            if (speedWithoutCompensation.judge(window, now, false, false, maxCps, cpsSeconds) > 0) {
               rawAlerts++;
            }
         }

         if (packet.click()) {
            window.add(tick);
            if (lag.laggedWithin(tick, 5)) {
               regularity.restart();
            } else if (regularity.add(tick, samples, minCps) > 0) {
               regularityAlerts++;
            }
         } else {
            lag.onMove(tick, 3, 25);
         }
      }
      return new Result(cpsAlerts, regularityAlerts, rawAlerts);
   }

   private NetworkSimulation() {
   }
}
