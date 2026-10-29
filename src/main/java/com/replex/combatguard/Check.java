package com.replex.combatguard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Un contrôle. Pour en ajouter un : une classe qui étend celle-ci, une section du même nom dans config.yml,
 * et une ligne dans {@link CombatGuard#createChecks()}. Les réglages communs (enabled, points, can-react, name) sont lus ici.
 */
abstract class Check {
   private final String key;
   private boolean enabled;
   private double points;
   private boolean canReact;
   private String displayName;
   protected CombatGuard plugin;

   protected Check(String key) {
      this.key = key;
   }

   final void load(CombatGuard plugin, ConfigurationSection section) {
      this.plugin = plugin;
      this.enabled = section != null && section.getBoolean("enabled", true);
      this.points = section == null ? 1 : section.getDouble("points", 1);
      this.canReact = section != null && section.getBoolean("can-react", false);
      // Ancien emplacement du nom (config 0.1.0) : messages.checks.<clé>.
      String legacyName = plugin.getConfig().getString("messages.checks." + this.key, this.key);
      this.displayName = section == null ? legacyName : section.getString("name", legacyName);
      this.configure(section);
   }

   /** Lit les réglages propres au contrôle (section null si absente de config.yml : valeurs par défaut). */
   protected abstract void configure(ConfigurationSection section);

   /** Un clic de combat (dans le vide ou sur une entité, pas sur un bloc). */
   void onClick(Player player, PlayerData data, int tick) {
   }

   /** Un coup porté au corps à corps. */
   void onHit(Player attacker, PlayerData data, LivingEntity victim, int tick) {
   }

   /** Un coup en attente, quand le regard d'après le coup est connu. */
   void onHitResolved(Player attacker, PlayerData data, PlayerData.PendingHit hit, float yawAfter, float pitchAfter) {
   }

   /** Chaque seconde, pour chaque joueur suivi. */
   void everySecond(Player player, PlayerData data, int tick) {
   }

   protected final void flag(Player player, PlayerData data, String detail) {
      this.plugin.violations().flag(this, player, data, detail);
   }

   String key() {
      return this.key;
   }

   boolean enabled() {
      return this.enabled;
   }

   double points() {
      return this.points;
   }

   boolean canReact() {
      return this.canReact;
   }

   String displayName() {
      return this.displayName;
   }
}
