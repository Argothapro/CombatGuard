package com.replex.combatguard.logic;

/** Points de triche d'un joueur, qui baissent avec le temps sans alerte. */
public final class Points {
   private double value;
   private long lastUpdate;

   public Points(long nowMillis) {
      this.lastUpdate = nowMillis;
   }

   public double get(long nowMillis, double decayPerMinute) {
      double minutes = Math.max(0, nowMillis - this.lastUpdate) / 60000.0;
      this.value = Math.max(0, this.value - minutes * decayPerMinute);
      this.lastUpdate = nowMillis;
      return this.value;
   }

   public double add(long nowMillis, double decayPerMinute, double points) {
      this.value = this.get(nowMillis, decayPerMinute) + points;
      return this.value;
   }
}
