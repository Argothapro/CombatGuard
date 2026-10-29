package com.replex.combatguard.math;

import java.util.List;

/** Calculs de visée, sans dépendance au serveur. */
public final class AimMath {
   /** Nombre de directions testées entre le regard d'avant et celui d'après le coup. */
   private static final int LOOK_STEPS = 4;

   private AimMath() {
   }

   /**
    * Plus petit angle (degrés) entre le regard du joueur et la cible, sur toutes les positions possibles de la cible
    * (historique compensé du lag) et tous les regards possibles au moment du coup (entre avant et après).
    * Renvoie 0 si l'œil est dans une des boîtes ; NaN si toutes sont plus proches que minDistance (contrôle impossible).
    */
   public static double smallestAngle(Vec3 eye, float yawBefore, float pitchBefore, float yawAfter, float pitchAfter,
         List<Box> targetBoxes, double minDistance) {
      double best = Double.NaN;
      for (Box box : targetBoxes) {
         Vec3 toTarget = box.closestPoint(eye).subtract(eye);
         double distance = toTarget.length();
         if (distance < 1.0E-6) {
            return 0;
         }
         if (distance < minDistance) {
            continue;
         }
         for (int step = 0; step <= LOOK_STEPS; step++) {
            double t = step / (double) LOOK_STEPS;
            float yaw = (float) (yawBefore + wrapDegrees(yawAfter - yawBefore) * t);
            float pitch = (float) (pitchBefore + (pitchAfter - pitchBefore) * t);
            double angle = Vec3.direction(yaw, pitch).angleDegrees(toTarget);
            if (Double.isNaN(best) || angle < best) {
               best = angle;
            }
         }
      }
      return best;
   }

   /** Ramène un angle dans ]-180 ; 180]. */
   public static double wrapDegrees(double degrees) {
      double wrapped = degrees % 360;
      if (wrapped > 180) {
         wrapped -= 360;
      } else if (wrapped <= -180) {
         wrapped += 360;
      }
      return wrapped;
   }
}
