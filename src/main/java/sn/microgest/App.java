package sn.microgest;

import java.nio.file.*;
import java.util.concurrent.*;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import sn.microgest.config.Database;
import sn.microgest.service.*;

public class App extends Application {
  public static Database db;
  public static MicroGestService service;
  public static final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "microgest-work");
            t.setDaemon(true);
            return t;
          });
  private static Stage stage;
  private static ScheduledExecutorService mailWorker;

  @Override
  public void start(Stage window) throws Exception {
    stage = window;
    Path data = sn.microgest.config.Settings.home().resolve("data");
    Files.createDirectories(data);
    db =
        new Database(
            "jdbc:h2:file:"
                + data.resolve("microgest").toString().replace((char) 92, '/')
                + ";DB_CLOSE_ON_EXIT=FALSE");
    service = new MicroGestService(db);
    stage.setTitle("MicroGest · Gestion de microfinance");
    show("login", 1024, 720);
    stage.setMinWidth(1000);
    stage.setMinHeight(700);
    stage.show();
    if (getParameters().getRaw().contains("--verify-startup")) {
      javafx.application.Platform.runLater(
          () -> {
            try {
              var root = stage.getScene().getRoot();
              root.applyCss();
              root.layout();
              var image = root.snapshot(null, null);
              int width = (int) image.getWidth(), height = (int) image.getHeight();
              var output =
                  new java.awt.image.BufferedImage(
                      width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
              for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                  output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
              javax.imageio.ImageIO.write(
                  output, "png", data.resolve("startup-check.png").toFile());
              Files.writeString(
                  data.resolve("startup-check.txt"), "JavaFX, FXML et base H2 : démarrage réussi.");
            } catch (Exception ex) {
              ex.printStackTrace();
            } finally {
              javafx.application.Platform.exit();
            }
          });
      return;
    }
    mailWorker =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "microgest-mail");
              t.setDaemon(true);
              return t;
            });
    mailWorker.scheduleWithFixedDelay(
        () -> {
          try {
            new MailService(db).sendPending();
          } catch (Exception e) {
            System.err.println("Le service email est temporairement indisponible.");
          }
        },
        15,
        30,
        TimeUnit.SECONDS);
  }

  public static void show(String name, double width, double height) throws java.io.IOException {
    var root = FXMLLoader.load(App.class.getResource("/views/" + name + ".fxml"));
    Scene scene = new Scene((javafx.scene.Parent) root, width, height);
    scene.getStylesheets().add(App.class.getResource("/styles/app.css").toExternalForm());
    stage.setScene(scene);
  }

  @Override
  public void stop() {
    if (mailWorker != null) mailWorker.shutdownNow();
    worker.shutdownNow();
    if (db != null) db.close();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
