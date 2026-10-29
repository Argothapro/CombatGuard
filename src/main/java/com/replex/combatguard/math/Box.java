package com.replex.combatguard.math;

/** Boîte de collision alignée sur les axes. */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
   public Box expand(double amount) {
      return new Box(this.minX - amount, this.minY - amount, this.minZ - amount,
            this.maxX + amount, this.maxY + amount, this.maxZ + amount);
   }

   public Vec3 center() {
      return new Vec3((this.minX + this.maxX) / 2, (this.minY + this.maxY) / 2, (this.minZ + this.maxZ) / 2);
   }

   /** Point de la boîte le plus proche (le point lui-même s'il est dedans). */
   public Vec3 closestPoint(Vec3 point) {
      return new Vec3(
            Math.max(this.minX, Math.min(this.maxX, point.x())),
            Math.max(this.minY, Math.min(this.maxY, point.y())),
            Math.max(this.minZ, Math.min(this.maxZ, point.z())));
   }
}
