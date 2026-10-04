package sn.microgest.ui;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import sn.microgest.App;

public final class Ui {
  private Ui() {}

  public static <T> void run(Callable<T> work, Consumer<T> success) {
    Task<T> task =
        new Task<>() {
          protected T call() throws Exception {
            return work.call();
          }
        };
    task.setOnSucceeded(e -> success.accept(task.getValue()));
    task.setOnFailed(e -> error(task.getException()));
    App.worker.submit(task);
  }

  public static void error(Throwable e) {
    Throwable cause = e;
    while (cause.getCause() != null
        && !(cause instanceof IllegalArgumentException)
        && !(cause instanceof SecurityException)) cause = cause.getCause();
    String message =
        cause instanceof IllegalArgumentException || cause instanceof SecurityException
            ? cause.getMessage()
            : "L'action n'a pas pu aboutir. Vérifiez les doublons ou les données liées et"
                  + " réessayez.";
    if (!(cause instanceof IllegalArgumentException) && !(cause instanceof SecurityException))
      e.printStackTrace();
    Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
    a.setHeaderText("Action impossible");
    a.showAndWait();
  }

  public static boolean confirm(String message) {
    Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
    a.setHeaderText("Confirmer l'action");
    return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
  }

  public static void info(String message) {
    Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
    a.setHeaderText("MicroGest");
    a.showAndWait();
  }
}
