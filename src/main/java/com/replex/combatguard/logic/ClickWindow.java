package com.replex.combatguard.logic;

/** Clics d'un joueur sur les derniers ticks (jusqu'à 10 s), sans allocation. */
public final class ClickWindow {
   public static final int SIZE = 200;
   private final int[] ticks = new int[SIZE];
   private final int[] counts = new int[SIZE];

   public ClickWindow() {
      java.util.Arrays.fill(this.ticks, Integer.MIN_VALUE);
   }

   public void add(int tick) {
      int slot = Math.floorMod(tick, SIZE);
      if (this.ticks[slot] != tick) {
         this.ticks[slot] = tick;
         this.counts[slot] = 0;
      }
      this.counts[slot]++;
   }

   /** Clics reçus pendant les ticks ]now - length ; now] (length au plus SIZE). */
   public int count(int now, int length) {
      int total = 0;
      for (int i = 0; i < SIZE; i++) {
         if (this.ticks[i] > now - length && this.ticks[i] <= now) {
            total += this.counts[i];
         }
      }
      return total;
   }
}
