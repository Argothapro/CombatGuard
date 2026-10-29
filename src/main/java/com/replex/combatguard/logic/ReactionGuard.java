package com.replex.combatguard.logic;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Garde-fou des réactions automatiques, pour qu'une vague de fausses alertes (lag général, bug, mise à jour du jeu)
 * ne puisse jamais toucher beaucoup de joueurs :
 * - une réaction au plus par joueur pendant le délai choisi ;
 * - au-delà d'un nombre de réactions par minute sur tout le serveur, toutes les réactions sont suspendues un moment.
 */
public final class ReactionGuard {
   private final Map<UUID, Long> lastByPlayer = new HashMap<>();
   private final Deque<Long> recent = new ArrayDeque<>();
   private long pausedUntil;

   public enum Verdict { ALLOWED, PLAYER_COOLDOWN, PAUSED, JUST_PAUSED }

   public Verdict request(UUID player, long nowMillis, long playerCooldownMillis, int maxPerMinute, long pauseMillis) {
      if (nowMillis < this.pausedUntil) {
         return Verdict.PAUSED;
      }
      Long last = this.lastByPlayer.get(player);
      if (last != null && nowMillis - last < playerCooldownMillis) {
         return Verdict.PLAYER_COOLDOWN;
      }
      while (!this.recent.isEmpty() && nowMillis - this.recent.peekFirst() >= 60000L) {
         this.recent.pollFirst();
      }
      if (this.recent.size() >= maxPerMinute) {
         this.pausedUntil = nowMillis + pauseMillis;
         this.recent.clear();
         return Verdict.JUST_PAUSED;
      }
      this.recent.addLast(nowMillis);
      this.lastByPlayer.put(player, nowMillis);
      return Verdict.ALLOWED;
   }

   public boolean paused(long nowMillis) {
      return nowMillis < this.pausedUntil;
   }

   /** Oublie les délais terminés (gardés après une déconnexion, pour qu'une reconnexion ne relance pas une réaction). */
   public void prune(long nowMillis, long playerCooldownMillis) {
      this.lastByPlayer.values().removeIf(last -> nowMillis - last >= playerCooldownMillis);
   }
}
