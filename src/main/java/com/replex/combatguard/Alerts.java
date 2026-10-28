package com.replex.combatguard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Points de triche, alertes au staff et journal (plugins/CombatGuard/alerts.log). */
final class Alerts {
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
   private static final int RECENT = 10;

   private final CombatGuard plugin;
   private final Path logFile;
   /** Membres du staff qui ont coupé les alertes (/cg alerts), jusqu'au redémarrage. */
   private final Set<UUID> muted = new HashSet<>();

   Alerts(CombatGuard plugin) {
      this.plugin = plugin;
      this.logFile = plugin.getDataFolder().toPath().resolve("alerts.log");
   }

   /** Points actuels, après la baisse due au temps passé sans alerte. */
   double points(PlayerData data) {
      long now = System.currentTimeMillis();
      double minutes = (now - data.lastDecayMillis) / 60000.0;
      data.points = Math.max(0, data.points - minutes * this.plugin.settings().decayPerMinute);
      data.lastDecayMillis = now;
      return data.points;
   }

   void flag(Player player, PlayerData data, String check, String detail, double points) {
      Settings settings = this.plugin.settings();
      double total = this.points(data) + points;
      data.points = total;
      String pointsText = String.format(Locale.FRANCE, "%.1f", total);
      String line = TIME.format(LocalDateTime.now()) + " " + player.getName() + " " + check + " (" + detail + ") " + pointsText + " points";
      data.recentAlerts.addFirst(line);
      while (data.recentAlerts.size() > RECENT) {
         data.recentAlerts.pollLast();
      }
      this.log(line);
      if (total < settings.alertFrom) {
         return;
      }
      Component message = MiniMessage.miniMessage().deserialize(settings.alertFormat,
            Placeholder.unparsed("player", player.getName()),
            Placeholder.unparsed("check", settings.checkName(check)),
            Placeholder.unparsed("detail", detail),
            Placeholder.unparsed("points", pointsText));
      for (Player staff : Bukkit.getOnlinePlayers()) {
         if (staff.hasPermission("combatguard.alerts") && !this.muted.contains(staff.getUniqueId())) {
            staff.sendMessage(message);
         }
      }
      Bukkit.getConsoleSender().sendMessage(message);
   }

   /** Coupe ou remet les alertes ; renvoie true si elles sont maintenant actives. */
   boolean toggle(Player staff) {
      if (this.muted.remove(staff.getUniqueId())) {
         return true;
      }
      this.muted.add(staff.getUniqueId());
      return false;
   }

   private void log(String line) {
      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.append(line));
   }

   private synchronized void append(String line) {
      try {
         Files.createDirectories(this.logFile.getParent());
         Files.writeString(this.logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
               StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException e) {
         this.plugin.getLogger().log(Level.WARNING, "Impossible d'écrire dans alerts.log", e);
      }
   }
}
