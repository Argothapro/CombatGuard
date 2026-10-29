package com.replex.combatguard;

import com.replex.combatguard.math.Vec3;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Deux cibles différentes frappées presque en même temps, dans des directions très éloignées (killaura).
 * Jamais juste après un lag : des coups donnés à 1 s d'écart peuvent alors arriver dans le même tick.
 */
final class MultiTargetCheck extends Check {
   private int maxTicks;
   private double minAngle;

   MultiTargetCheck() {
      super("multi");
   }

   @Override
   protected void configure(ConfigurationSection section) {
      this.maxTicks = section == null ? 1 : Math.max(0, section.getInt("max-ticks", 1));
      this.minAngle = section == null ? 100 : section.getDouble("min-angle", 100);
   }

   @Override
   void onHit(Player attacker, PlayerData data, LivingEntity victim, int tick) {
      Vec3 toVictim = CombatListener.box(victim).center().subtract(CombatListener.eye(attacker));
      if (data.lastVictim != null && !data.lastVictim.equals(victim.getUniqueId()) && data.lastHitDirection != null
            && tick - data.lastHitTick <= this.maxTicks && !data.lag.laggedWithin(tick, 20)) {
         double angle = data.lastHitDirection.angleDegrees(toVictim);
         if (angle >= this.minAngle) {
            int ticks = tick - data.lastHitTick;
            this.flag(attacker, data, String.format(Locale.FRANCE, "2 cibles à %.0f° d'écart en %d tick%s", angle, ticks, ticks > 1 ? "s" : ""));
         }
      }
      data.lastHitTick = tick;
      data.lastVictim = victim.getUniqueId();
      data.lastHitDirection = toVictim;
   }
}
