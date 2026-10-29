package com.replex.combatguard.logic;

import com.replex.combatguard.math.Box;
import java.util.ArrayList;
import java.util.List;

/** Boîtes de collision d'un joueur aux derniers ticks, pour compenser le lag du joueur qui le frappe. */
public final class PositionHistory {
   private final Box[] boxes;
   private final int[] ticks;

   public PositionHistory(int capacity) {
      this.boxes = new Box[capacity];
      this.ticks = new int[capacity];
      java.util.Arrays.fill(this.ticks, Integer.MIN_VALUE);
   }

   public void record(int tick, Box box) {
      int slot = Math.floorMod(tick, this.boxes.length);
      this.boxes[slot] = box;
      this.ticks[slot] = tick;
   }

   /** Boîtes connues pendant les ticks [from ; to], plus la position actuelle. */
   public List<Box> between(int from, int to, Box current) {
      List<Box> result = new ArrayList<>();
      result.add(current);
      for (int i = 0; i < this.boxes.length; i++) {
         if (this.boxes[i] != null && this.ticks[i] >= from && this.ticks[i] <= to) {
            result.add(this.boxes[i]);
         }
      }
      return result;
   }

   public int capacity() {
      return this.boxes.length;
   }
}
