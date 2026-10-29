package com.replex.combatguard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Journal des alertes (alerts-file dans config.yml), écrit par un seul fil en arrière-plan, jamais sur le fil du serveur.
 * Chaque ligne commence par le nom de l'instance et part en une seule écriture en ajout : plusieurs serveurs peuvent
 * partager le même fichier sans que leurs lignes se mélangent.
 */
final class AlertLog {
   private final Logger logger;
   private final String instance;
   private volatile Path file;
   private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "CombatGuard-log");
      thread.setDaemon(true);
      return thread;
   });

   AlertLog(Path file, Logger logger) {
      this.file = file;
      this.logger = logger;
      this.instance = instanceName();
   }

   /** Change de fichier (rechargement de la config). */
   void setFile(Path file) {
      this.file = file;
   }

   void write(String line) {
      Path target = this.file;
      byte[] bytes = ("[" + this.instance + "] " + line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
      this.writer.execute(() -> {
         try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
               Files.createDirectories(parent);
            }
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
         } catch (IOException e) {
            this.logger.log(Level.WARNING, "Impossible d'écrire dans " + target, e);
         }
      });
   }

   /** À l'arrêt : finit d'écrire ce qui est en attente (5 s au plus). */
   void close() {
      this.writer.shutdown();
      try {
         this.writer.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   /** Nom de l'instance : -Dnetwork.instance=... (passé par Network), sinon le nom du dossier du serveur. */
   private static String instanceName() {
      String property = System.getProperty("network.instance");
      if (property != null && !property.isBlank()) {
         return property.trim();
      }
      Path folder = Path.of("").toAbsolutePath().getFileName();
      return folder == null ? "serveur" : folder.toString();
   }
}
