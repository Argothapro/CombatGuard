package com.replex.combatguard.logic;

/**
 * Clics par seconde trop élevés, en moyenne sur plusieurs secondes.
 * Le lag ne fait que décaler des clics dans le temps (retenus puis reçus d'un coup) : une moyenne sur une fenêtre longue
 * n'en est presque pas changée. Après un lag, la fenêtre est allongée pour diluer la rafale, au lieu d'ignorer la mesure :
 * un tricheur ne peut pas se cacher derrière un simple lag. Seul un blocage long (plus de 1,5 s) suspend la mesure,
 * le temps que sa rafale sorte de la fenêtre.
 */
public final class ClickSpeed {
   /** Fenêtre utilisée quand le joueur a laggé pendant la fenêtre normale. */
   public static final int LAG_WINDOW_SECONDS = 10;

   private int nextAllowedTick = Integer.MIN_VALUE;

   /** Appelé chaque seconde ; renvoie les clics par seconde moyens si l'alerte est due, sinon -1. */
   public double judge(ClickWindow clicks, int now, boolean lagged, boolean longStall, int max, int seconds) {
      if (longStall) {
         return -1;
      }
      int windowSeconds = lagged ? Math.max(seconds, LAG_WINDOW_SECONDS) : seconds;
      double cps = clicks.count(now, windowSeconds * 20) / (double) windowSeconds;
      if (cps <= max || now < this.nextAllowedTick) {
         return -1;
      }
      // Une alerte au plus par fenêtre : une longue série ne compte pas une alerte par seconde.
      this.nextAllowedTick = now + seconds * 20;
      return cps;
   }

   public void reset() {
      this.nextAllowedTick = Integer.MIN_VALUE;
   }
}
