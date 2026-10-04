package sn.microgest.ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import sn.microgest.App;

public class LoginController {
  @FXML private Label heading, subtitle, error;
  @FXML private TextField username, fullName;
  @FXML private PasswordField password, confirmation;
  @FXML private Button submit;
  @FXML private CheckBox demo;
  private boolean setup;

  @FXML
  public void initialize() {
    setup = App.service.needsSetup();
    heading.setText(setup ? "Premier lancement" : "Connexion");
    subtitle.setText(
        setup
            ? "Créez votre premier compte administrateur."
            : "Saisissez votre identifiant et votre mot de passe.");
    submit.setText(setup ? "Créer le compte" : "Se connecter");
    fullName.setVisible(setup);
    fullName.setManaged(setup);
    confirmation.setVisible(setup);
    confirmation.setManaged(setup);
    demo.setVisible(setup);
    demo.setManaged(setup);
  }

  @FXML
  private void login() {
    error.setText("");
    String u = username.getText(), p = password.getText(), n = fullName.getText();
    boolean sample = demo.isSelected();
    if (setup && !p.equals(confirmation.getText())) {
      error.setText("Les mots de passe ne correspondent pas.");
      return;
    }
    submit.setDisable(true);
    javafx.concurrent.Task<Void> task =
        new javafx.concurrent.Task<>() {
          protected Void call() {
            if (setup) App.service.setup(u, n, p, sample);
            App.service.login(u, p);
            return null;
          }
        };
    task.setOnSucceeded(
        e -> {
          try {
            App.show("main", 1280, 820);
          } catch (Exception ex) {
            Ui.error(ex);
          } finally {
            submit.setDisable(false);
          }
        });
    task.setOnFailed(
        e -> {
          error.setText(task.getException().getMessage());
          submit.setDisable(false);
          password.clear();
          confirmation.clear();
        });
    App.worker.submit(task);
  }
}
