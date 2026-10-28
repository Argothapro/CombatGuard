package com.replex.combatguard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Anti-triche des clics et de la visée pour les serveurs de combat, en complément de Grim (qui ne contrôle ni l'un ni l'autre).
 * Mode observation : alertes et journal seulement, jamais de sanction automatique.
 */
public final class CombatGuard extends JavaPlugin implements TabExecutor {
   private Settings settings;
   private Alerts alerts;
   private Checks checks;
   /** Joueurs exemptés pour un test (/cg exempt), jusqu'au redémarrage. */
   private final Set<UUID> exempt = new HashSet<>();

   @Override
   public void onEnable() {
      this.saveDefaultConfig();
      this.settings = new Settings(this.getConfig());
      this.alerts = new Alerts(this);
      this.checks = new Checks(this);
      this.getServer().getPluginManager().registerEvents(this.checks, this);
      this.getServer().getScheduler().runTaskTimer(this, this.checks::everySecond, 20L, 20L);
      PluginCommand command = this.getCommand("combatguard");
      if (command != null) {
         command.setExecutor(this);
         command.setTabCompleter(this);
      }
   }

   Settings settings() {
      return this.settings;
   }

   Alerts alerts() {
      return this.alerts;
   }

   boolean isExempt(Player player) {
      return this.exempt.contains(player.getUniqueId());
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
      switch (sub) {
         case "alerts" -> {
            if (!(sender instanceof Player player) || !sender.hasPermission("combatguard.alerts")) {
               this.send(sender, "no-permission", null);
               return true;
            }
            this.send(sender, this.alerts.toggle(player) ? "alerts-on" : "alerts-off", null);
         }
         case "info", "exempt" -> {
            if (!sender.hasPermission("combatguard.admin")) {
               this.send(sender, "no-permission", null);
               return true;
            }
            if (args.length < 2) {
               return false;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
               this.send(sender, "unknown-player", args[1]);
               return true;
            }
            if (sub.equals("exempt")) {
               boolean now = this.exempt.add(target.getUniqueId());
               if (!now) {
                  this.exempt.remove(target.getUniqueId());
               }
               this.send(sender, now ? "exempt-on" : "exempt-off", target.getName());
            } else {
               this.info(sender, target);
            }
         }
         case "reload" -> {
            if (!sender.hasPermission("combatguard.admin")) {
               this.send(sender, "no-permission", null);
               return true;
            }
            this.reloadConfig();
            this.settings = new Settings(this.getConfig());
            this.send(sender, "reloaded", null);
         }
         default -> {
            return false;
         }
      }
      return true;
   }

   private void info(CommandSender sender, Player target) {
      PlayerData data = this.checks.dataIfPresent(target.getUniqueId());
      double points = data == null ? 0 : this.alerts.points(data);
      sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<yellow><player></yellow> : <white><points> points, ping <ping> ms<exempt></white>",
            Placeholder.unparsed("player", target.getName()),
            Placeholder.unparsed("points", String.format(Locale.FRANCE, "%.1f", points)),
            Placeholder.unparsed("ping", String.valueOf(target.getPing())),
            Placeholder.unparsed("exempt", this.isExempt(target) ? ", exempté" : "")));
      if (data != null) {
         for (String line : data.recentAlerts) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray><line></gray>", Placeholder.unparsed("line", line)));
         }
      }
   }

   private void send(CommandSender sender, String key, String player) {
      sender.sendMessage(MiniMessage.miniMessage().deserialize(this.settings.message(key),
            Placeholder.unparsed("player", player == null ? "" : player)));
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      List<String> options = new ArrayList<>();
      if (args.length == 1) {
         for (String sub : new String[] {"alerts", "info", "exempt", "reload"}) {
            if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
               options.add(sub);
            }
         }
      } else if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("exempt"))) {
         for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
               options.add(player.getName());
            }
         }
      }
      return options;
   }
}
