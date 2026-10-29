package com.replex.combatguard.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Contrôles de clics face à des joueurs honnêtes avec du lag, et face à des autoclickers. Réglages par défaut de config.yml. */
class ClickChecksLagTest {
   private static final int RUNS = 200;
   private static final int SECONDS = 60;

   private static final NetworkSimulation.Network GOOD = new NetworkSimulation.Network(20, 5, 0, 0);
   private static final NetworkSimulation.Network BAD = new NetworkSimulation.Network(150, 120, 0.05, 400);

   private static int[] total(NetworkSimulation.Clicker clicker, NetworkSimulation.Network network) {
      int cps = 0;
      int regularity = 0;
      int raw = 0;
      int runsWithRegularity = 0;
      for (long seed = 1; seed <= RUNS; seed++) {
         NetworkSimulation.Result result = NetworkSimulation.run(clicker, network, SECONDS, seed, 20, 3, 100, 8);
         cps += result.cpsAlerts();
         regularity += result.regularityAlerts();
         raw += result.cpsAlertsWithoutLagCompensation();
         runsWithRegularity += result.regularityAlerts() > 0 ? 1 : 0;
      }
      return new int[] {cps, regularity, raw, runsWithRegularity};
   }

   @Test
   void fastHumanOnBadConnectionIsNeverFlagged() {
      // 17 cps (jitter click) avec 300 ms de ping, 120 ms de gigue et 5 % de pertes : 200 minutes de combat.
      int[] result = total(new NetworkSimulation.Clicker(1000.0 / 17, 18, false), BAD);
      assertEquals(0, result[0], "alertes de clics pour un humain qui lagge");
      assertEquals(0, result[1], "alertes de régularité pour un humain qui lagge");
   }

   @Test
   void freezesDoNotFlagAFastHuman() {
      // 17 cps avec des blocages d'une seconde (1 % de paquets renvoyés après 1 s, soit un blocage toutes les 3 s environ) :
      // sans compensation, les rafales dépassent 20 cps ; avec la fenêtre allongée après un lag, aucune alerte.
      int[] result = total(new NetworkSimulation.Clicker(1000.0 / 17, 15, false), new NetworkSimulation.Network(60, 30, 0.01, 1000));
      assertEquals(0, result[0], "alertes de clics pour un humain qui se fige");
      assertTrue(result[2] > 0, "sans compensation, ce scénario donnerait de fausses alertes");
   }

   @Test
   void steadyHumanIsNeverFlaggedForRegularity() {
      // Humain régulier : 10 cps, écart-type 10 ms, bonne connexion. 200 minutes de clic continu.
      // (Un humain encore plus régulier, écart-type 5 ms, serait parfois signalé : d'où ce contrôle coupé par défaut.)
      int[] result = total(new NetworkSimulation.Clicker(100, 10, false), GOOD);
      assertEquals(0, result[1], "alertes de régularité pour un humain régulier");
      assertEquals(0, result[0]);
   }

   @Test
   void fastAutoclickerIsFlagged() {
      int[] result = total(new NetworkSimulation.Clicker(1000.0 / 25, 0, true), GOOD);
      assertTrue(result[0] >= RUNS, "chaque minute d'autoclicker à 25 cps doit donner au moins une alerte");
   }

   @Test
   void fastAutoclickerIsStillFlaggedOnBadConnection() {
      int[] result = total(new NetworkSimulation.Clicker(1000.0 / 25, 0, true), BAD);
      assertTrue(result[0] >= RUNS, "le lag ne doit pas cacher un autoclicker à 25 cps toute une minute");
   }

   @Test
   void fixedRateAutoclickerIsFlaggedForRegularity() {
      int[] result = total(new NetworkSimulation.Clicker(100, 0, true), GOOD);
      assertTrue(result[3] >= RUNS * 8 / 10, "un autoclicker à 10 cps fixes doit être repéré dans la plupart des minutes : " + result[3]);
   }
}
