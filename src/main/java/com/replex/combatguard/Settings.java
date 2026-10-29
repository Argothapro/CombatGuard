package com.replex.combatguard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Réglages communs lus dans config.yml (voir ses commentaires). Les réglages de chaque contrôle sont lus par le contrôle. */
final class Settings {
   /** Mots refusés dans les commandes de réaction : jamais de ban automatique. */
   private static final List<String> FORBIDDEN = List.of("ban", "ipban", "tempban", "banip", "ban-ip", "tempipban");

   final boolean reactionsEnabled;
   final List<Reaction> reactions = new ArrayList<>();
   final long reactionPlayerCooldownMillis;
   final int reactionMaxPerMinute;
   final long reactionPauseMillis;

   final double decayPerMinute;
   final double alertFrom;
   final long alertCooldownMillis;

   final double minTps;
   final int maxPing;
   final int graceTicks;
   final int burstPackets;
   final int silenceTicks;
   final boolean exemptBedrock;

   final double aimMinDistance;
   final double interpolationMs;
   final double hitboxMargin;

   final String alertFormat;
   private final ConfigurationSection messages;

   /** Une réaction : commande lancée quand les points de réaction d'un joueur atteignent le seuil. */
   record Reaction(double points, String command) {
   }

   Settings(FileConfiguration config, Logger logger) {
      this.reactionsEnabled = "active".equalsIgnoreCase(config.getString("mode", "observation"));
      for (Map<?, ?> entry : config.getMapList("reactions.list")) {
         Object points = entry.get("points");
         Object command = entry.get("command");
         if (!(points instanceof Number number) || !(command instanceof String text) || text.isBlank()) {
            logger.warning("Réaction ignorée (points ou command manquant) : " + entry);
            continue;
         }
         String firstWord = text.trim().replaceFirst("^/", "").split("\\s+")[0].toLowerCase(Locale.ROOT);
         if (FORBIDDEN.contains(firstWord.substring(firstWord.indexOf(':') + 1))) {
            logger.warning("Réaction refusée, jamais de ban automatique : " + text);
            continue;
         }
         this.reactions.add(new Reaction(number.doubleValue(), text.trim().replaceFirst("^/", "")));
      }
      this.reactions.sort((a, b) -> Double.compare(a.points(), b.points()));
      this.reactionPlayerCooldownMillis = Math.max(1, config.getInt("reactions.per-player-cooldown-minutes", 10)) * 60000L;
      this.reactionMaxPerMinute = Math.max(1, config.getInt("reactions.max-per-minute", 3));
      this.reactionPauseMillis = Math.max(1, config.getInt("reactions.pause-minutes", 10)) * 60000L;

      this.decayPerMinute = Math.max(0, config.getDouble("points.decay-per-minute", 1));
      this.alertFrom = config.getDouble("points.alert-from", 0);
      this.alertCooldownMillis = Math.max(0, config.getInt("points.alert-cooldown-seconds", 5)) * 1000L;

      this.minTps = config.getDouble("pause.min-tps", 18);
      this.maxPing = config.getInt("pause.max-ping", 300);
      this.graceTicks = Math.max(0, config.getInt("pause.grace-seconds", 3)) * 20;
      this.burstPackets = Math.max(2, config.getInt("pause.burst-packets", 3));
      this.silenceTicks = Math.max(1, config.getInt("pause.silence-ticks", 25));
      this.exemptBedrock = config.getBoolean("pause.exempt-bedrock", true);

      this.aimMinDistance = config.getDouble("aim.min-distance", 1.5);
      this.interpolationMs = Math.max(0, config.getDouble("aim.interpolation-ms", 150));
      this.hitboxMargin = Math.max(0, config.getDouble("aim.hitbox-margin", 0.1));

      this.alertFormat = config.getString("messages.alert", "<red>[Anti-triche]</red> <player> : <check> (<detail>), <points> points");
      this.messages = config.getConfigurationSection("messages");
   }

   /** Message MiniMessage de la section messages, ou la clé s'il manque. */
   String message(String key) {
      return this.messages == null ? key : this.messages.getString(key, key);
   }
}
