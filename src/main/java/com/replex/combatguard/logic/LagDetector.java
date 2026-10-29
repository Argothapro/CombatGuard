package com.replex.combatguard.logic;

/**
 * Repère le lag d'un joueur à partir des paquets de mouvement : en combat, le client en envoie un par tick.
 * Après un blocage (latence, perte de paquets retransmise), les paquets retenus arrivent d'un coup :
 * plusieurs dans le même tick du serveur. C'est le signe que les mesures de cette période sont faussées.
 */
public final class LagDetector {
   private int currentTick = Integer.MIN_VALUE;
   private int packetsThisTick;
   private int lastMoveTick = Integer.MIN_VALUE;
   private int lastLagTick = Integer.MIN_VALUE;
   private int lastLongStallTick = Integer.MIN_VALUE;

   /** Blocage long (1,5 s) : ses clics retenus déforment même une moyenne sur plusieurs secondes. */
   public static final int LONG_STALL_TICKS = 30;

   /** Un paquet de mouvement traité à ce tick. */
   public void onMove(int tick, int burstPackets, int silenceTicks) {
      if (tick != this.currentTick) {
         if (this.lastMoveTick != Integer.MIN_VALUE && tick - this.lastMoveTick > silenceTicks) {
            // Long silence puis reprise : on ne sait pas ce qui s'est passé entre-temps.
            this.lastLagTick = tick;
            if (tick - this.lastMoveTick > LONG_STALL_TICKS) {
               this.lastLongStallTick = tick;
            }
         }
         this.currentTick = tick;
         this.packetsThisTick = 0;
      }
      this.packetsThisTick++;
      this.lastMoveTick = tick;
      if (this.packetsThisTick >= burstPackets) {
         this.lastLagTick = tick;
      }
   }

   /** Le joueur s'est figé plus de 1,5 s puis a repris, pendant les ticks [now - window ; now]. */
   public boolean longStallWithin(int now, int window) {
      return this.lastLongStallTick != Integer.MIN_VALUE && now - this.lastLongStallTick <= window;
   }

   /** Le joueur a laggé pendant les ticks [now - window ; now]. */
   public boolean laggedWithin(int now, int window) {
      return this.lastLagTick != Integer.MIN_VALUE && now - this.lastLagTick <= window;
   }
}
