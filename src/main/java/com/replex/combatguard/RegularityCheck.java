package com.replex.combatguard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** Une longue série de clics espacés exactement du même nombre de ticks (autoclicker à rythme fixe). */
final class RegularityCheck extends Check {
   private int samples;
   private double minCps;

   RegularityCheck() {
      super("regularity");
   }

   @Override
   protected void configure(ConfigurationSection section) {
      this.samples = section == null ? 100 : Math.max(10, section.getInt("samples", 100));
      this.minCps = section == null ? 8 : section.getDouble("min-cps", 8);
   }

   @Override
   void onClick(Player player, PlayerData data, int tick) {
      if (data.lag.laggedWithin(tick, 5)) {
         // Les écarts mesurés pendant un lag ne disent rien du rythme du joueur.
         data.regularity.restart();
         return;
      }
      int gap = data.regularity.add(tick, this.samples, this.minCps);
      if (gap > 0) {
         this.flag(player, data, this.samples + " clics espacés de " + gap + " tick" + (gap > 1 ? "s" : ""));
      }
   }
}
