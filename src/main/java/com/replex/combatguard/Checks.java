package com.replex.combatguard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Les quatre contrôles : clics par seconde, clics réguliers, visée, multi-cibles.
 * Ils ne font qu'appeler {@link Alerts#flag} : rien n'est annulé.
 */
final class Checks implements Listener {
   /** Au-delà de cet écart (en ticks) entre deux clics, la série est finie. */
   private static final int MAX_SERIES_GAP = 5;

   private final CombatGuard plugin;
   private final Map<UUID, PlayerData> data = new HashMap<>();

   Checks(CombatGuard plugin) {
      this.plugin = plugin;
   }

   PlayerData data(Player player) {
      return this.data.computeIfAbsent(player.getUniqueId(), id -> new PlayerData());
   }

   PlayerData dataIfPresent(UUID id) {
      return this.data.get(id);
   }

   /** Appelé chaque seconde : clics par seconde. */
   void everySecond() {
      Settings settings = this.plugin.settings();
      int now = Bukkit.getCurrentTick();
      boolean lagging = this.lagging();
      for (Player player : Bukkit.getOnlinePlayers()) {
         PlayerData data = this.data.get(player.getUniqueId());
         if (data == null) {
            continue;
         }
         while (!data.clickTicks.isEmpty() && data.clickTicks.peekFirst() <= now - 20) {
            data.clickTicks.pollFirst();
         }
         int cps = data.clickTicks.size();
         if (!settings.cpsEnabled || lagging || this.plugin.isExempt(player) || cps <= settings.cpsMax) {
            data.highCpsSeconds = 0;
            data.peakCps = 0;
            continue;
         }
         data.highCpsSeconds++;
         data.peakCps = Math.max(data.peakCps, cps);
         if (data.highCpsSeconds >= settings.cpsSeconds) {
            this.plugin.alerts().flag(player, data, "cps",
                  "jusqu'à " + data.peakCps + " cps pendant " + data.highCpsSeconds + " s", settings.cpsPoints);
            data.highCpsSeconds = 0;
            data.peakCps = 0;
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onSwing(PlayerAnimationEvent event) {
      if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
         return;
      }
      Player player = event.getPlayer();
      if (!this.fighting(player) || this.targetsBlock(player)) {
         return;
      }
      PlayerData data = this.data(player);
      int now = Bukkit.getCurrentTick();
      data.clickTicks.addLast(now);

      int gap = data.lastClickTick == Integer.MIN_VALUE ? Integer.MAX_VALUE : now - data.lastClickTick;
      data.lastClickTick = now;
      if (gap > MAX_SERIES_GAP) {
         data.intervals.clear();
         return;
      }
      data.intervals.addLast(gap);
      Settings settings = this.plugin.settings();
      if (data.intervals.size() < settings.regularitySamples) {
         return;
      }
      while (data.intervals.size() > settings.regularitySamples) {
         data.intervals.pollFirst();
      }
      if (!settings.regularityEnabled || this.lagging() || this.plugin.isExempt(player)) {
         return;
      }
      int first = data.intervals.peekFirst();
      int sum = 0;
      boolean identical = true;
      for (int interval : data.intervals) {
         sum += interval;
         identical &= interval == first;
      }
      double cps = sum == 0 ? Double.MAX_VALUE : 20.0 * data.intervals.size() / sum;
      if (identical && cps >= settings.regularityMinCps) {
         this.plugin.alerts().flag(player, data, "regularity",
               data.intervals.size() + " clics espacés de " + first + " tick" + (first > 1 ? "s" : ""), settings.regularityPoints);
         data.intervals.clear();
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onHit(EntityDamageByEntityEvent event) {
      // Même un coup annulé (zone protégée, coéquipier) a été envoyé par le client : on le contrôle aussi.
      if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
            || !(event.getDamager() instanceof Player attacker)
            || !(event.getEntity() instanceof LivingEntity victim)
            || !this.fighting(attacker)
            || this.plugin.isExempt(attacker)
            || this.lagging()) {
         return;
      }
      Settings settings = this.plugin.settings();
      PlayerData data = this.data(attacker);
      int now = Bukkit.getCurrentTick();
      Location eye = attacker.getEyeLocation();
      Vector look = eye.getDirection();
      Vector origin = eye.toVector();
      Vector toCenter = victim.getBoundingBox().getCenter().subtract(origin);
      boolean aimable = now >= data.graceUntilTick && attacker.getPing() <= settings.maxPing;

      if (settings.aimEnabled && aimable) {
         Vector closest = closestPoint(victim.getBoundingBox().expand(0.1), origin);
         Vector toClosest = closest.subtract(origin);
         double distance = toClosest.length();
         if (distance >= settings.aimMinDistance) {
            double angle = Math.toDegrees(look.angle(toClosest));
            if (angle > settings.aimMaxAngle) {
               this.plugin.alerts().flag(attacker, data, "aim",
                     String.format(java.util.Locale.FRANCE, "%.0f° hors du regard à %.1f blocs, cible %s", angle, distance, name(victim)),
                     settings.aimPoints);
            }
         }
      }

      if (settings.multiEnabled && aimable && data.lastVictim != null && !data.lastVictim.equals(victim.getUniqueId())
            && now - data.lastHitTick <= settings.multiMaxTicks && data.lastHitDirection != null
            && toCenter.lengthSquared() > 0 && data.lastHitDirection.lengthSquared() > 0) {
         double angle = Math.toDegrees(data.lastHitDirection.angle(toCenter));
         if (angle >= settings.multiMinAngle) {
            this.plugin.alerts().flag(attacker, data, "multi",
                  String.format(java.util.Locale.FRANCE, "2 cibles à %.0f° d'écart en %d tick%s", angle, now - data.lastHitTick,
                        now - data.lastHitTick > 1 ? "s" : ""),
                  settings.multiPoints);
         }
      }
      data.lastHitTick = now;
      data.lastVictim = victim.getUniqueId();
      data.lastHitDirection = toCenter;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      this.grace(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onTeleport(PlayerTeleportEvent event) {
      this.grace(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onRespawn(PlayerRespawnEvent event) {
      this.grace(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onWorldChange(PlayerChangedWorldEvent event) {
      this.grace(event.getPlayer());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.data.remove(event.getPlayer().getUniqueId());
   }

   private void grace(Player player) {
      this.data(player).graceUntilTick = Bukkit.getCurrentTick() + this.plugin.settings().graceTicks;
   }

   /** Joueur qui peut se battre : survie ou aventure. */
   private boolean fighting(Player player) {
      GameMode mode = player.getGameMode();
      return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
   }

   /**
    * Le joueur vise un bloc à portée : il casse ou pose (pont du HikaBrain), ce n'est pas un clic de combat.
    * Une entité devant le bloc compte comme un clic de combat.
    */
   private boolean targetsBlock(Player player) {
      AttributeInstance rangeAttribute = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
      double range = (rangeAttribute != null ? rangeAttribute.getValue() : 4.5) + 0.5;
      Location eye = player.getEyeLocation();
      RayTraceResult result = player.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.0,
            entity -> entity != player && entity instanceof LivingEntity);
      return result != null && result.getHitBlock() != null && result.getHitEntity() == null;
   }

   private boolean lagging() {
      return Bukkit.getServer().getTPS()[0] < this.plugin.settings().minTps;
   }

   /** Point de la boîte le plus proche de l'œil (l'œil lui-même s'il est dedans). */
   private static Vector closestPoint(BoundingBox box, Vector point) {
      return new Vector(
            clamp(point.getX(), box.getMinX(), box.getMaxX()),
            clamp(point.getY(), box.getMinY(), box.getMaxY()),
            clamp(point.getZ(), box.getMinZ(), box.getMaxZ()));
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   private static String name(Entity entity) {
      return entity instanceof Player player ? player.getName() : entity.getType().name().toLowerCase(java.util.Locale.ROOT);
   }
}
