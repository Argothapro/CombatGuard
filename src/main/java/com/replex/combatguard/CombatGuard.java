package com.replex.combatguard;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
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
 * Mode observation par défaut : alertes et journal seulement. En mode actif, seules les réactions configurées sont lancées,
 * jamais de ban, et un garde-fou les suspend si elles s'emballent.
 */
public final class CombatGuard extends JavaPlugin implements TabExecutor {
   private Settings settings;
   private List<Check> checks = List.of();
   private CombatListener listener;
   private Violations violations;
   private AlertLog log;
   /** Joueurs exemptés pour un test (/cg exempt), jusqu'au redémarrage. */
   private final Set<UUID> exempt = new HashSet<>();

   @Override
   public void onEnable() {
      this.saveDefaultConfig();
      this.log = new AlertLog(this.getDataFolder().toPath().resolve("alerts.log"), this.getLogger());
      this.violations = new Violations(this, this.log);
      this.listener = new CombatListener(this);
      this.loadSettings();
      this.getServer().getPluginManager().registerEvents(this.listener, this);
      this.getServer().getScheduler().runTaskTimer(this, this.listener::everyTick, 1L, 1L);
      this.getServer().getScheduler().runTaskTimer(this, this.listener::everySecond, 20L, 20L);
      this.getServer().getScheduler().runTaskTimer(this, this.violations::prune, 1200L, 1200L);
      PluginCommand command = this.getCommand("combatguard");
      if (command != null) {
         command.setExecutor(this);
         command.setTabCompleter(this);
      }
   }

   @Override
   public void onDisable() {
      if (this.log != null) {
         this.log.close();
      }
   }

   /** Tous les contrôles. Pour en ajouter un : voir {@link Check}. */
   private static List<Check> createChecks() {
      return List.of(new ClickSpeedCheck(), new RegularityCheck(), new AimCheck(), new MultiTargetCheck());
   }

   private void loadSettings() {
      this.settings = new Settings(this.getConfig(), this.getLogger());
      this.log.setFile(this.alertsFile());
      List<Check> loaded = createChecks();
      for (Check check : loaded) {
         check.load(this, this.getConfig().getConfigurationSection(check.key()));
      }
      this.checks = loaded;
      if (this.settings.reactionsEnabled) {
         this.getLogger().warning("Mode actif : " + this.settings.reactions.size() + " réaction(s) configurée(s).");
      }
   }

   /** alerts-file de config.yml : vide = plugins/CombatGuard/alerts.log ; un chemin relatif part du dossier du serveur. */
   private Path alertsFile() {
      Path fallback = this.getDataFolder().toPath().resolve("alerts.log");
      String configured = this.getConfig().getString("alerts-file", "");
      if (configured == null || configured.isBlank()) {
         return fallback;
      }
      try {
         return Path.of(configured.trim());
      } catch (InvalidPathException e) {
         this.getLogger().warning("alerts-file invalide (" + configured + "), journal dans " + fallback);
         return fallback;
      }
   }

   Settings settings() {
      return this.settings;
   }

   List<Check> checks() {
      return this.checks;
   }

   CombatListener listener() {
      return this.listener;
   }

   Violations violations() {
      return this.violations;
   }

   boolean isExempt(Player player) {
      if (this.exempt.contains(player.getUniqueId()) || player.hasPermission("combatguard.bypass")) {
         return true;
      }
      // Joueurs Bedrock (Floodgate) : leur client vise et clique autrement (écran tactile, manette).
      return this.settings.exemptBedrock && player.getUniqueId().getMostSignificantBits() == 0L;
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
            this.send(sender, this.violations.toggle(player) ? "alerts-on" : "alerts-off", null);
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
         case "status" -> {
            if (!sender.hasPermission("combatguard.admin")) {
               this.send(sender, "no-permission", null);
               return true;
            }
            this.status(sender);
         }
         case "reload" -> {
            if (!sender.hasPermission("combatguard.admin")) {
               this.send(sender, "no-permission", null);
               return true;
            }
            this.reloadConfig();
            this.loadSettings();
            this.send(sender, "reloaded", null);
         }
         default -> {
            return false;
         }
      }
      return true;
   }

   private void info(CommandSender sender, Player target) {
      PlayerData data = this.listener.dataIfPresent(target.getUniqueId());
      double points = data == null ? 0 : this.violations.points(data);
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

   private void status(CommandSender sender) {
      StringBuilder active = new StringBuilder();
      for (Check check : this.checks) {
         active.append(active.isEmpty() ? "" : ", ").append(check.key()).append(check.enabled() ? "" : " (coupé)");
      }
      String mode = this.settings.reactionsEnabled
            ? "actif, " + this.settings.reactions.size() + " réaction(s)" + (this.violations.reactionsPaused() ? ", suspendues" : "")
            : "observation";
      sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<yellow>CombatGuard</yellow> <white>: mode <mode> ; contrôles : <checks> ; serveur <tps></white>",
            Placeholder.unparsed("mode", mode),
            Placeholder.unparsed("checks", active.toString()),
            Placeholder.unparsed("tps", this.listener.serverLagging() ? "en pause (TPS bas)" : "ok")));
   }

   private void send(CommandSender sender, String key, String player) {
      sender.sendMessage(MiniMessage.miniMessage().deserialize(this.settings.message(key),
            Placeholder.unparsed("player", player == null ? "" : player)));
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      List<String> options = new ArrayList<>();
      if (args.length == 1) {
         for (String sub : new String[] {"alerts", "info", "exempt", "status", "reload"}) {
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
