package com.replex.combatguard;

import com.replex.combatguard.math.Box;
import com.replex.combatguard.math.Vec3;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;

/**
 * Reçoit les événements du serveur, tient les mesures communes (lag, historique des positions, TPS)
 * et les transmet aux contrôles actifs. Les contrôles eux-mêmes ne touchent pas aux événements.
 */
final class CombatListener implements Listener {
   /** Coup sans regard reçu après lui pendant ce nombre de ticks : jugé avec le dernier regard connu. */
   private static final int PENDING_TICKS = 2;

   private final CombatGuard plugin;
   private final Map<UUID, PlayerData> data = new HashMap<>();
   private double tps = 20;

   CombatListener(CombatGuard plugin) {
      this.plugin = plugin;
   }

   PlayerData data(Player player) {
      return this.data.computeIfAbsent(player.getUniqueId(), id -> new PlayerData(System.currentTimeMillis()));
   }

   PlayerData dataIfPresent(UUID id) {
      return this.data.get(id);
   }

   /** Chaque tick : historique des positions et coups en attente trop anciens. */
   void everyTick() {
      int now = Bukkit.getCurrentTick();
      for (Player player : Bukkit.getOnlinePlayers()) {
         PlayerData data = this.data(player);
         data.history.record(now, box(player));
         if (!data.pendingHits.isEmpty()) {
            Location location = player.getLocation();
            this.resolve(player, data, location.getYaw(), location.getPitch(), now - PENDING_TICKS);
         }
      }
   }

   /** Chaque seconde : TPS et contrôles périodiques. */
   void everySecond() {
      this.tps = Bukkit.getServer().getTPS()[0];
      if (this.serverLagging()) {
         return;
      }
      int now = Bukkit.getCurrentTick();
      for (Player player : Bukkit.getOnlinePlayers()) {
         PlayerData data = this.data.get(player.getUniqueId());
         if (data == null || !this.checkable(player)) {
            continue;
         }
         for (Check check : this.plugin.checks()) {
            if (check.enabled()) {
               check.everySecond(player, data, now);
            }
         }
      }
   }

   boolean serverLagging() {
      return this.tps < this.plugin.settings().minTps;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onMove(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      PlayerData data = this.data(player);
      Settings settings = this.plugin.settings();
      data.lag.onMove(Bukkit.getCurrentTick(), settings.burstPackets, settings.silenceTicks);
      if (!data.pendingHits.isEmpty()) {
         // Premier regard reçu après le coup : celui que le joueur avait en frappant est entre les deux.
         this.resolve(player, data, event.getTo().getYaw(), event.getTo().getPitch(), Integer.MAX_VALUE);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onSwing(PlayerAnimationEvent event) {
      if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
         return;
      }
      Player player = event.getPlayer();
      if (this.serverLagging() || !this.checkable(player) || this.targetsBlock(player)) {
         return;
      }
      PlayerData data = this.data(player);
      int now = Bukkit.getCurrentTick();
      data.clicks.add(now);
      for (Check check : this.plugin.checks()) {
         if (check.enabled()) {
            check.onClick(player, data, now);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onHit(EntityDamageByEntityEvent event) {
      // Même un coup annulé (zone protégée, coéquipier) a été envoyé par le client : on le contrôle aussi.
      if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
            || !(event.getDamager() instanceof Player attacker)
            || !(event.getEntity() instanceof LivingEntity victim)
            || this.serverLagging()
            || !this.checkable(attacker)) {
         return;
      }
      Settings settings = this.plugin.settings();
      PlayerData data = this.data(attacker);
      int now = Bukkit.getCurrentTick();
      // Ping trop haut, début de partie, ou paquets reçus en rafale à l'instant : la visée n'est pas mesurable.
      if (now < data.graceUntilTick || attacker.getPing() > settings.maxPing || data.lag.laggedWithin(now, 5)) {
         return;
      }
      for (Check check : this.plugin.checks()) {
         if (check.enabled()) {
            check.onHit(attacker, data, victim, now);
         }
      }
      // Le contrôle de visée attend le regard envoyé après le coup (voir onMove).
      Location eye = attacker.getEyeLocation();
      int back = (int) Math.ceil((attacker.getPing() + settings.interpolationMs) / 50.0) + 1;
      Box current = box(victim).expand(settings.hitboxMargin);
      List<Box> boxes;
      if (victim instanceof Player target) {
         boxes = this.data(target).history.between(now - back, now, current);
         boxes.replaceAll(box -> box == current ? box : box.expand(settings.hitboxMargin));
      } else {
         boxes = List.of(current);
      }
      data.pendingHits.add(new PlayerData.PendingHit(now, name(victim), vec(eye), eye.getYaw(), eye.getPitch(), boxes));
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

   /** Juge les coups en attente frappés au plus tard au tick maxTick, avec le regard d'après le coup. */
   private void resolve(Player player, PlayerData data, float yawAfter, float pitchAfter, int maxTick) {
      Iterator<PlayerData.PendingHit> iterator = data.pendingHits.iterator();
      while (iterator.hasNext()) {
         PlayerData.PendingHit hit = iterator.next();
         if (hit.tick() > maxTick) {
            continue;
         }
         iterator.remove();
         for (Check check : this.plugin.checks()) {
            if (check.enabled()) {
               check.onHitResolved(player, data, hit, yawAfter, pitchAfter);
            }
         }
      }
   }

   private void grace(Player player) {
      PlayerData data = this.data(player);
      data.graceUntilTick = Bukkit.getCurrentTick() + this.plugin.settings().graceTicks;
      data.pendingHits.clear();
   }

   /** Joueur contrôlé : survie ou aventure, pas exempté. */
   private boolean checkable(Player player) {
      GameMode mode = player.getGameMode();
      return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !this.plugin.isExempt(player);
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

   static Box box(Entity entity) {
      BoundingBox box = entity.getBoundingBox();
      return new Box(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
   }

   static Vec3 eye(Player player) {
      return vec(player.getEyeLocation());
   }

   private static Vec3 vec(Location location) {
      return new Vec3(location.getX(), location.getY(), location.getZ());
   }

   private static String name(Entity entity) {
      return entity instanceof Player player ? player.getName() : entity.getType().name().toLowerCase(java.util.Locale.ROOT);
   }
}
