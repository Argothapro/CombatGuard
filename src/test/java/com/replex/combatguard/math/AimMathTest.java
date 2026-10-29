package com.replex.combatguard.math;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AimMathTest {
   private static final Vec3 EYE = new Vec3(0, 1.62, 0);

   private static Box playerAt(double x, double z) {
      return new Box(x - 0.3, 0, z - 0.3, x + 0.3, 1.8, z + 0.3).expand(0.1);
   }

   /** Yaw pour regarder un point (convention Minecraft). */
   private static float yawTo(double x, double z) {
      return (float) Math.toDegrees(Math.atan2(-x, z));
   }

   @Test
   void laggingAttackerAimingWellIsNotFlagged() {
      // La cible court en travers à 5,6 blocs/s, à 3 blocs. L'attaquant a 250 ms de ping et 150 ms d'interpolation :
      // il voit et vise la cible là où elle était il y a 400 ms (8 ticks). Au moment du coup, le serveur l'a déjà 2,2 blocs plus loin.
      List<Box> history = new ArrayList<>();
      for (int tick = 0; tick <= 10; tick++) {
         history.add(playerAt(-tick * 0.28, 3));
      }
      Box now = history.get(0);
      Box seen = history.get(8);
      float yaw = yawTo(seen.center().x(), seen.center().z());
      double withoutHistory = AimMath.smallestAngle(EYE, yaw, 0, yaw, 0, List.of(now), 1.5);
      double withHistory = AimMath.smallestAngle(EYE, yaw, 0, yaw, 0, history, 1.5);
      assertTrue(withoutHistory > 30, "sans historique, l'angle serait grand : " + withoutHistory);
      assertTrue(withHistory < 5, "avec l'historique, l'angle doit être presque nul : " + withHistory);
   }

   @Test
   void flickReceivedAfterTheHitIsNotFlagged() {
      // Le client frappe puis envoie son nouveau regard : le serveur a encore le regard d'avant (90° à côté).
      Box target = playerAt(0, 3);
      float after = yawTo(0, 3);
      double angle = AimMath.smallestAngle(EYE, after - 90, 0, after, 0, List.of(target), 1.5);
      assertTrue(angle < 1, "le regard d'après le coup doit être pris en compte : " + angle);
   }

   @Test
   void hitBehindTheBackIsFlagged() {
      Box target = playerAt(0, -3);
      float yaw = yawTo(0, 3);
      double angle = AimMath.smallestAngle(EYE, yaw, 0, yaw, 0, List.of(target), 1.5);
      assertTrue(angle > 150, "coup dans le dos : " + angle);
   }

   @Test
   void tooCloseIsNotMeasured() {
      Box target = playerAt(0, 1);
      double angle = AimMath.smallestAngle(EYE, 180, 0, 180, 0, List.of(target), 1.5);
      assertTrue(Double.isNaN(angle));
   }
}
