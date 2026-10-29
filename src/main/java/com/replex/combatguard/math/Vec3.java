package com.replex.combatguard.math;

/** Vecteur 3D immuable, sans dépendance au serveur (testable seul). */
public record Vec3(double x, double y, double z) {
   public Vec3 subtract(Vec3 other) {
      return new Vec3(this.x - other.x, this.y - other.y, this.z - other.z);
   }

   public double length() {
      return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
   }

   public double dot(Vec3 other) {
      return this.x * other.x + this.y * other.y + this.z * other.z;
   }

   /** Angle en degrés avec un autre vecteur ; 0 si l'un des deux est nul. */
   public double angleDegrees(Vec3 other) {
      double lengths = this.length() * other.length();
      if (lengths < 1.0E-9) {
         return 0;
      }
      return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, this.dot(other) / lengths))));
   }

   /** Direction du regard (convention Minecraft : yaw 0 = sud +Z, pitch positif = vers le bas). */
   public static Vec3 direction(float yaw, float pitch) {
      double yawRad = Math.toRadians(yaw);
      double pitchRad = Math.toRadians(pitch);
      double xz = Math.cos(pitchRad);
      return new Vec3(-xz * Math.sin(yawRad), -Math.sin(pitchRad), xz * Math.cos(yawRad));
   }
}
