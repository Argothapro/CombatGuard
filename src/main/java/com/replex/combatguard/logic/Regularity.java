package com.replex.combatguard.logic;

/**
 * Clics trop réguliers : une longue série d'écarts tous identiques au tick près.
 * Le serveur ne voit les clics qu'au tick (50 ms) : un humain très régulier peut tomber plusieurs fois dans le même tick,
 * d'où une série longue (100 par défaut). Même ainsi, ce contrôle reste peu fiable : coupé par défaut (voir config.yml).
 */
public final class Regularity {
   /** Au-delà de cet écart (en ticks) entre deux clics, la série est finie. */
   public static final int MAX_SERIES_GAP = 5;

   private int lastTick = Integer.MIN_VALUE;
   private int firstGap = -1;
   private int length;
   private int sum;

   /** Ajoute un clic ; renvoie l'écart commun (en ticks) si l'alerte est due, sinon -1. */
   public int add(int tick, int samples, double minCps) {
      int gap = this.lastTick == Integer.MIN_VALUE ? Integer.MAX_VALUE : tick - this.lastTick;
      if (gap == 0) {
         // Plusieurs clics dans le même tick : écart non mesurable (souvent du lag), la série ne bouge pas.
         return -1;
      }
      this.lastTick = tick;
      if (gap > MAX_SERIES_GAP) {
         this.restart();
         return -1;
      }
      if (gap != this.firstGap) {
         // Un écart différent : la série repart de cet écart.
         this.firstGap = gap;
         this.length = 0;
         this.sum = 0;
      }
      this.length++;
      this.sum += gap;
      if (this.length < samples) {
         return -1;
      }
      double cps = this.sum == 0 ? Double.MAX_VALUE : 20.0 * this.length / this.sum;
      int result = cps >= minCps ? gap : -1;
      this.restart();
      return result;
   }

   /** Remet la série à zéro (lag : les écarts mesurés ne valent plus rien). */
   public void restart() {
      this.firstGap = -1;
      this.length = 0;
      this.sum = 0;
   }
}
