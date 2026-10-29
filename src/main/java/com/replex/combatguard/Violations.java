package com.replex.combatguard;

import com.replex.combatguard.logic.ReactionGuard;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Ce qui suit une alerte : points, journal, message au staff (regroupé pour ne pas inonder le chat),
 * et en mode actif seulement, les réactions configurées, toujours derrière le garde-fou {@link ReactionGuard}.
 */
final class Violations {
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
   private static final int RECENT = 10;
   /** Pas de réaction si le joueur a laggé pendant ces derniers ticks : ses mesures peuvent être faussées. */
   private static final int REACTION_LAG_TICKS = 40;

   private final CombatGuard plugin;
   private final AlertLog log;
   private final ReactionGuard guard = new ReactionGuard();
   /** Membres du staff qui ont coupé les alertes (/cg alerts), jusqu'au redémarrage. */
   private final Set<UUID> muted = new HashSet<>();

   Violations(CombatGuard plugin, AlertLog log) {
      this.plugin = plugin;
      this.log = log;
   }

   void flag(Check check, Player player, PlayerData data, String detail) {
      Settings settings = this.plugin.settings();
      long now = System.currentTimeMillis();
      double total = data.points.add(now, settings.decayPerMinute, check.points());
      String pointsText = String.format(Locale.FRANCE, "%.1f", total);
      String line = TIME.format(LocalDateTime.now()) + " " + player.getName() + " " + check.key() + " (" + detail + ") "
            + pointsText + " points, ping " + player.getPing() + " ms";
      data.recentAlerts.addFirst(line);
      while (data.recentAlerts.size() > RECENT) {
         data.recentAlerts.pollLast();
      }
      this.log.write(line);

      if (total >= settings.alertFrom) {
         this.notifyStaff(check, player, data, detail, pointsText, now);
      }
      if (check.canReact()) {
         double before = data.reactionPoints.get(now, settings.decayPerMinute);
         double after = data.reactionPoints.add(now, settings.decayPerMinute, check.points());
         this.react(player, data, before, after, now);
      }
   }

   /** Message au staff, au plus un par contrôle et par joueur toutes les quelques secondes ; les autres sont comptés. */
   private void notifyStaff(Check check, Player player, PlayerData data, String detail, String pointsText, long now) {
      Settings settings = this.plugin.settings();
      long[] throttle = data.alertThrottle.computeIfAbsent(check.key(), key -> new long[] {Long.MIN_VALUE / 2, 0});
      if (now - throttle[0] < settings.alertCooldownMillis) {
         throttle[1]++;
         return;
      }
      String grouped = throttle[1] > 0 ? " +" + throttle[1] : "";
      throttle[0] = now;
      throttle[1] = 0;
      this.broadcast(MiniMessage.miniMessage().deserialize(settings.alertFormat,
            Placeholder.unparsed("player", player.getName()),
            Placeholder.unparsed("check", check.displayName()),
            Placeholder.unparsed("detail", detail + grouped),
            Placeholder.unparsed("points", pointsText),
            Placeholder.unparsed("mode", settings.reactionsEnabled ? "actif" : "observation")));
   }

   private void react(Player player, PlayerData data, double before, double after, long now) {
      Settings settings = this.plugin.settings();
      if (!settings.reactionsEnabled) {
         return;
      }
      Settings.Reaction crossed = null;
      for (Settings.Reaction reaction : settings.reactions) {
         if (before < reaction.points() && after >= reaction.points()) {
            crossed = reaction;
         }
      }
      if (crossed == null) {
         return;
      }
      if (this.plugin.listener().serverLagging() || data.lag.laggedWithin(Bukkit.getCurrentTick(), REACTION_LAG_TICKS)) {
         this.log.write(TIME.format(LocalDateTime.now()) + " " + player.getName() + " réaction retenue (lag) : " + crossed.command());
         return;
      }
      String command = crossed.command().replace("<player>", player.getName());
      switch (this.guard.request(player.getUniqueId(), now, settings.reactionPlayerCooldownMillis,
            settings.reactionMaxPerMinute, settings.reactionPauseMillis)) {
         case ALLOWED -> {
            this.log.write(TIME.format(LocalDateTime.now()) + " réaction : " + command);
            this.broadcast(this.message("reaction", player.getName(), command));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
         }
         case JUST_PAUSED -> {
            String minutes = String.valueOf(settings.reactionPauseMillis / 60000L);
            this.log.write(TIME.format(LocalDateTime.now()) + " réactions suspendues " + minutes + " min (trop de réactions en 1 min)");
            this.broadcast(this.message("reactions-paused", player.getName(), minutes));
         }
         case PAUSED, PLAYER_COOLDOWN -> this.log.write(TIME.format(LocalDateTime.now()) + " " + player.getName()
               + " réaction non lancée (garde-fou) : " + command);
      }
   }

   /** Appelé chaque minute : oublie les délais de réaction terminés. */
   void prune() {
      this.guard.prune(System.currentTimeMillis(), this.plugin.settings().reactionPlayerCooldownMillis);
   }

   boolean reactionsPaused() {
      return this.guard.paused(System.currentTimeMillis());
   }

   double points(PlayerData data) {
      return data.points.get(System.currentTimeMillis(), this.plugin.settings().decayPerMinute);
   }

   /** Coupe ou remet les alertes ; renvoie true si elles sont maintenant actives. */
   boolean toggle(Player staff) {
      if (this.muted.remove(staff.getUniqueId())) {
         return true;
      }
      this.muted.add(staff.getUniqueId());
      return false;
   }

   private Component message(String key, String player, String value) {
      return MiniMessage.miniMessage().deserialize(this.plugin.settings().message(key),
            Placeholder.unparsed("player", player),
            Placeholder.unparsed("value", value));
   }

   private void broadcast(Component message) {
      for (Player staff : Bukkit.getOnlinePlayers()) {
         if (staff.hasPermission("combatguard.alerts") && !this.muted.contains(staff.getUniqueId())) {
            staff.sendMessage(message);
         }
      }
      Bukkit.getConsoleSender().sendMessage(message);
   }
}
