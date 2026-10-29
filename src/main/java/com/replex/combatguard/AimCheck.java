package com.replex.combatguard;

import com.replex.combatguard.math.AimMath;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Coup porté à une cible hors du regard. Pour ne jamais accuser un joueur qui lagge, l'angle retenu est le plus petit :
 * - sur toutes les positions de la cible que le joueur a pu voir (historique couvrant son ping et l'interpolation) ;
 * - sur tous les regards entre celui d'avant le coup et celui d'après (le client envoie son regard après le coup).
 */
final class AimCheck extends Check {
   private double maxAngle;

   AimCheck() {
      super("aim");
   }

   @Override
   protected void configure(ConfigurationSection section) {
      this.maxAngle = section == null ? 50 : section.getDouble("max-angle", 50);
   }

   @Override
   void onHitResolved(Player attacker, PlayerData data, PlayerData.PendingHit hit, float yawAfter, float pitchAfter) {
      double angle = AimMath.smallestAngle(hit.eye(), hit.yawBefore(), hit.pitchBefore(), yawAfter, pitchAfter,
            hit.targetBoxes(), this.plugin.settings().aimMinDistance);
      if (!Double.isNaN(angle) && angle > this.maxAngle) {
         this.flag(attacker, data, String.format(Locale.FRANCE, "%.0f° hors du regard, cible %s", angle, hit.victimName()));
      }
   }
}
