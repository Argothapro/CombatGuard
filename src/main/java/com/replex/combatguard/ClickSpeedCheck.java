package com.replex.combatguard;

import com.replex.combatguard.logic.ClickSpeed;
import com.replex.combatguard.logic.ClickWindow;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** Plus de max clics par seconde en moyenne sur seconds secondes (fenêtre allongée après un lag, voir ClickSpeed). */
final class ClickSpeedCheck extends Check {
   private int max;
   private int seconds;

   ClickSpeedCheck() {
      super("cps");
   }

   @Override
   protected void configure(ConfigurationSection section) {
      this.max = section == null ? 20 : Math.max(1, section.getInt("max", 20));
      this.seconds = section == null ? 3 : Math.max(1, Math.min(ClickWindow.SIZE / 20, section.getInt("seconds", 3)));
   }

   @Override
   void everySecond(Player player, PlayerData data, int tick) {
      boolean lagged = data.lag.laggedWithin(tick, this.seconds * 20);
      boolean longStall = data.lag.longStallWithin(tick, ClickSpeed.LAG_WINDOW_SECONDS * 20);
      double cps = data.clickSpeed.judge(data.clicks, tick, lagged, longStall, this.max, this.seconds);
      if (cps > 0) {
         int window = lagged ? Math.max(this.seconds, ClickSpeed.LAG_WINDOW_SECONDS) : this.seconds;
         this.flag(player, data, String.format(java.util.Locale.FRANCE, "%.1f cps en moyenne sur %d s%s", cps, window,
               lagged ? ", après un lag" : ""));
      }
   }
}
