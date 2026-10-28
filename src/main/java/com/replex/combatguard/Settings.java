package com.replex.combatguard;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Réglages lus dans config.yml (voir ses commentaires). */
public final class Settings {
   public final boolean cpsEnabled;
   public final int cpsMax;
   public final int cpsSeconds;
   public final double cpsPoints;

   public final boolean regularityEnabled;
   public final int regularitySamples;
   public final double regularityMinCps;
   public final double regularityPoints;

   public final boolean aimEnabled;
   public final double aimMaxAngle;
   public final double aimMinDistance;
   public final double aimPoints;

   public final boolean multiEnabled;
   public final int multiMaxTicks;
   public final double multiMinAngle;
   public final double multiPoints;

   public final double decayPerMinute;
   public final double alertFrom;

   public final double minTps;
   public final int maxPing;
   public final int graceTicks;

   public final String alertFormat;
   private final Map<String, String> checkNames = new HashMap<>();
   private final ConfigurationSection messages;

   public Settings(FileConfiguration config) {
      this.cpsEnabled = config.getBoolean("cps.enabled", true);
      this.cpsMax = Math.max(1, config.getInt("cps.max", 20));
      this.cpsSeconds = Math.max(1, config.getInt("cps.seconds", 3));
      this.cpsPoints = config.getDouble("cps.points", 1);

      this.regularityEnabled = config.getBoolean("regularity.enabled", true);
      this.regularitySamples = Math.max(5, config.getInt("regularity.samples", 30));
      this.regularityMinCps = config.getDouble("regularity.min-cps", 8);
      this.regularityPoints = config.getDouble("regularity.points", 2);

      this.aimEnabled = config.getBoolean("aim.enabled", true);
      this.aimMaxAngle = config.getDouble("aim.max-angle", 50);
      this.aimMinDistance = config.getDouble("aim.min-distance", 1.5);
      this.aimPoints = config.getDouble("aim.points", 2);

      this.multiEnabled = config.getBoolean("multi.enabled", true);
      this.multiMaxTicks = Math.max(0, config.getInt("multi.max-ticks", 2));
      this.multiMinAngle = config.getDouble("multi.min-angle", 90);
      this.multiPoints = config.getDouble("multi.points", 3);

      this.decayPerMinute = Math.max(0, config.getDouble("points.decay-per-minute", 1));
      this.alertFrom = config.getDouble("points.alert-from", 0);

      this.minTps = config.getDouble("pause.min-tps", 18);
      this.maxPing = config.getInt("pause.max-ping", 300);
      this.graceTicks = Math.max(0, config.getInt("pause.grace-seconds", 3)) * 20;

      this.alertFormat = config.getString("messages.alert", "<red>[Anti-triche]</red> <player> : <check> (<detail>), <points> points");
      ConfigurationSection checks = config.getConfigurationSection("messages.checks");
      if (checks != null) {
         for (String key : checks.getKeys(false)) {
            this.checkNames.put(key, checks.getString(key, key));
         }
      }
      this.messages = config.getConfigurationSection("messages");
   }

   /** Nom affiché d'un contrôle (cps, regularity, aim, multi). */
   public String checkName(String key) {
      return this.checkNames.getOrDefault(key, key);
   }

   /** Message MiniMessage de la section messages, ou la clé s'il manque. */
   public String message(String key) {
      return this.messages == null ? key : this.messages.getString(key, key);
   }
}
