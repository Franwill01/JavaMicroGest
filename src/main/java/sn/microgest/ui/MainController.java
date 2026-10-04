package sn.microgest.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.fxml.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import sn.microgest.App;
import sn.microgest.model.Domain.Role;
import sn.microgest.repository.Repository.View;

public class MainController {
  @FXML private BorderPane root;
  @FXML private StackPane content;
  @FXML private Label today, identity;
  @FXML private Button usersNav, agenciesNav, emailsNav;

  @FXML
  public void initialize() {
    var u = App.service.current();
    identity.setText(u.fullName + " · " + Format.label(u.role));
    today.setText(
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)));
    for (Button b : new Button[] {usersNav, agenciesNav, emailsNav}) {
      b.setVisible(u.role == Role.ADMIN);
      b.setManaged(u.role == Role.ADMIN);
    }
    dashboard();
  }

  @FXML
  private void dashboard() {
    try {
      content
          .getChildren()
          .setAll((Parent) FXMLLoader.load(App.class.getResource("/views/dashboard.fxml")));
    } catch (Exception e) {
      Ui.error(e);
    }
  }

  private void list(View view) {
    try {
      FXMLLoader loader = new FXMLLoader(App.class.getResource("/views/list.fxml"));
      Parent p = loader.load();
      ListController c = loader.getController();
      c.configure(view);
      content.getChildren().setAll(p);
    } catch (Exception e) {
      Ui.error(e);
    }
  }

  @FXML
  private void members() {
    list(View.MEMBERS);
  }

  @FXML
  private void operations() {
    list(View.OPERATIONS);
  }

  @FXML
  private void loans() {
    list(View.LOANS);
  }

  @FXML
  private void users() {
    list(View.USERS);
  }

  @FXML
  private void agencies() {
    list(View.AGENCIES);
  }

  @FXML
  private void emails() {
    list(View.EMAILS);
  }

  @FXML
  private void changePassword() {
    Forms.password();
  }

  @FXML
  private void logout() {
    App.service.logout();
    try {
      App.show("login", 1024, 720);
    } catch (Exception e) {
      Ui.error(e);
    }
  }
}
