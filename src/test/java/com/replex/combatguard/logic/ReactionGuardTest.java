package com.replex.combatguard.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReactionGuardTest {
   private static final long COOLDOWN = 600_000;
   private static final long PAUSE = 600_000;

   @Test
   void onePerPlayerPerCooldown() {
      ReactionGuard guard = new ReactionGuard();
      UUID player = UUID.randomUUID();
      assertEquals(ReactionGuard.Verdict.ALLOWED, guard.request(player, 0, COOLDOWN, 3, PAUSE));
      assertEquals(ReactionGuard.Verdict.PLAYER_COOLDOWN, guard.request(player, 1000, COOLDOWN, 3, PAUSE));
      assertEquals(ReactionGuard.Verdict.ALLOWED, guard.request(player, COOLDOWN, COOLDOWN, 3, PAUSE));
   }

   @Test
   void cooldownSurvivesReconnection() {
      ReactionGuard guard = new ReactionGuard();
      UUID player = UUID.randomUUID();
      guard.request(player, 0, COOLDOWN, 3, PAUSE);
      guard.prune(60_000, COOLDOWN);
      assertEquals(ReactionGuard.Verdict.PLAYER_COOLDOWN, guard.request(player, 60_000, COOLDOWN, 3, PAUSE));
   }

   @Test
   void stormOfFalseAlertsIsCapped() {
      // Lag général : 20 joueurs atteignent le seuil dans la même minute. Seuls 3 subissent une réaction, puis tout s'arrête.
      ReactionGuard guard = new ReactionGuard();
      int allowed = 0;
      int justPaused = 0;
      for (int i = 0; i < 20; i++) {
         switch (guard.request(UUID.randomUUID(), i * 1000L, COOLDOWN, 3, PAUSE)) {
            case ALLOWED -> allowed++;
            case JUST_PAUSED -> justPaused++;
            default -> {
            }
         }
      }
      assertEquals(3, allowed);
      assertEquals(1, justPaused);
      assertEquals(ReactionGuard.Verdict.PAUSED, guard.request(UUID.randomUUID(), 300_000, COOLDOWN, 3, PAUSE));
      assertEquals(ReactionGuard.Verdict.ALLOWED, guard.request(UUID.randomUUID(), 3000 + PAUSE, COOLDOWN, 3, PAUSE));
   }
}
