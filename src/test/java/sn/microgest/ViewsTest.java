package sn.microgest;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import sn.microgest.config.Database;
import sn.microgest.model.Domain.Kind;
import sn.microgest.repository.Repository.View;
import sn.microgest.service.MicroGestService;
import sn.microgest.ui.Forms;
import sn.microgest.ui.ListController;


class ViewsTest {
  static Stage stage;
  static Path shots = Path.of("target/screenshots");

  @BeforeAll
  static void start() throws Exception {
    System.setProperty("headless.geometry", "1600x1200");
    System.setProperty("glass.platform", "Monocle");
    System.setProperty("monocle.platform", "Headless");
    System.setProperty("prism.order", "sw");
    CountDownLatch ready = new CountDownLatch(1);
    Platform.startup(
        () -> {
          Platform.setImplicitExit(false);
          ready.countDown();
        });
    assertTrue(ready.await(15, TimeUnit.SECONDS));
    App.db = new Database("jdbc:h2:mem:views;DB_CLOSE_DELAY=-1");
    App.service = new MicroGestService(App.db);
    Files.createDirectories(shots);
    fx(
        () -> {
          stage = new Stage();
          return null;
        });
  }

  @AfterAll
  static void stop() throws Exception {
    App.worker.submit(() -> {}).get(15, TimeUnit.SECONDS);
    fx(
        () -> {
          stage.close();
          return null;
        });
    App.db.close();
  }

  @Test
  void allFxmlViewsLoadAndRender() throws Exception {
    fx(
        () -> {
          show("login");
          snapshot("01-premier-lancement");
          return null;
        });
    App.service.setup("admin", "Équipe MicroGest", "Test-interface-2026", true);
    App.service.login("admin", "Test-interface-2026");
    fx(
        () -> {
          show("login");
          snapshot("02-connexion");
          show("main");
          return null;
        });
    settle();
    fx(
        () -> {
          snapshot("03-dashboard");
          return null;
        });
    for (View view : View.values()) {
      fx(
          () -> {
            FXMLLoader loader = new FXMLLoader(App.class.getResource("/views/list.fxml"));
            Parent root = loader.load();
            ListController controller = loader.getController();
            controller.configure(view);
            scene(root);
            return null;
          });
      settle();
      fx(
          () -> {
            snapshot("04-" + view.name().toLowerCase());
            return null;
          });
    }
    fx(
        () -> {
          assertNotNull(FXMLLoader.load(App.class.getResource("/views/editor.fxml")));
          return null;
        });
  
    fx(
        () -> {
          Platform.runLater(
              () -> {
                try {
                  var window =
                      javafx.stage.Window.getWindows().stream()
                          .filter(w -> w != stage && w.isShowing())
                          .findFirst()
                          .orElseThrow();
                  DialogPane pane = (DialogPane) window.getScene().getRoot();
                  GridPane grid = (GridPane) pane.lookup("#fields");
                  var inputs =
                      grid.getChildren().stream()
                          .filter(n -> n instanceof TextField)
                          .map(n -> (TextField) n)
                          .toList();
                  inputs.get(0).setText("Adhérente Interface");
                  inputs.get(1).setText("789876543");
                  inputs.get(2).setText("interface@example.invalid");
                  inputs.get(3).setText("Dakar");
                  snapshotRoot(pane, "05-formulaire-adherent");
                  ((Button) pane.lookupButton(ButtonType.OK)).fire();
                } catch (Exception ex) {
                  throw new RuntimeException(ex);
                }
              });
          Forms.member(null, () -> {});
          return null;
        });
    var member = App.service.findMembers("Adhérente Interface").get(0);
    fx(
        () -> {
          Platform.runLater(
              () -> {
                try {
                  var window =
                      javafx.stage.Window.getWindows().stream()
                          .filter(w -> w != stage && w.isShowing())
                          .findFirst()
                          .orElseThrow();
                  DialogPane pane = (DialogPane) window.getScene().getRoot();
                  GridPane grid = (GridPane) pane.lookup("#fields");
                  var inputs =
                      grid.getChildren().stream()
                          .filter(n -> n instanceof TextField)
                          .map(n -> (TextField) n)
                          .toList();
                  inputs.get(1).setText("50000");
                  snapshotRoot(pane, "06-formulaire-operation");
                  ((Button) pane.lookupButton(ButtonType.OK)).fire();
                } catch (Exception ex) {
                  throw new RuntimeException(ex);
                }
              });
          Forms.operation(null, Kind.DEPOT, member, () -> {});
          return null;
        });
    assertEquals(new java.math.BigDecimal("50000"), App.service.balance(member.id));
  }

  static void settle() throws Exception {
    App.worker.submit(() -> {}).get(25, TimeUnit.SECONDS);
    fx(() -> null);
  }

  static void show(String name) throws Exception {
    scene(FXMLLoader.load(App.class.getResource("/views/" + name + ".fxml")));
  }

  static void scene(Parent root) {
    Scene scene = new Scene(root, 1280, 820);
    scene.getStylesheets().add(App.class.getResource("/styles/app.css").toExternalForm());
    stage.setScene(scene);
    stage.show();
    root.applyCss();
    root.layout();
  }

  static void snapshot(String name) throws Exception {
    snapshotRoot(stage.getScene().getRoot(), name);
  }

  static void snapshotRoot(Parent root, String name) throws Exception {
    root.applyCss();
    root.layout();
    WritableImage image = root.snapshot(null, null);
    int w = (int) image.getWidth(), h = (int) image.getHeight();
    BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
    ImageIO.write(output, "png", shots.resolve(name + ".png").toFile());
  }

  static <T> T fx(Callable<T> work) throws Exception {
    CompletableFuture<T> result = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          try {
            result.complete(work.call());
          } catch (Throwable e) {
            result.completeExceptionally(e);
          }
        });
    return result.get(30, TimeUnit.SECONDS);
  }
}
