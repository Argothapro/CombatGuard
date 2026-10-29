package com.replex.combatguard;

import java.io.BufferedWriter;
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

/** Journal plugins/CombatGuard/alerts.log, écrit par un seul fil en arrière-plan (jamais sur le fil du serveur). */
final class AlertLog {
   private final Path file;
   private final Logger logger;
   private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "CombatGuard-log");
      thread.setDaemon(true);
      return thread;
   });

   AlertLog(Path file, Logger logger) {
      this.file = file;
      this.logger = logger;
   }

   void write(String line) {
      this.writer.execute(() -> {
         try {
            Files.createDirectories(this.file.getParent());
            try (BufferedWriter out = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8,
                  StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
               out.write(line);
               out.newLine();
            }
         } catch (IOException e) {
            this.logger.log(Level.WARNING, "Impossible d'écrire dans alerts.log", e);
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
}
