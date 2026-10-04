package sn.microgest.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Supplier;
import javafx.collections.FXCollections;
import javafx.fxml.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import sn.microgest.App;
import sn.microgest.model.*;
import sn.microgest.model.Domain.*;
import sn.microgest.service.MicroGestService.OperationInput;

public final class Forms {
  private Forms() {}

  public static final class Form {
    final Dialog<Void> dialog = new Dialog<>();
    final GridPane grid;
    int row;

    Form(String title) {
      try {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("/views/editor.fxml"));
        DialogPane pane = loader.load();
        grid = (GridPane) loader.getNamespace().get("fields");
        dialog.setDialogPane(pane);
        pane.getStylesheets().add(App.class.getResource("/styles/app.css").toExternalForm());
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      dialog.setTitle(title);
      dialog.setHeaderText(title);
      dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
      ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Enregistrer");
      dialog.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("primary");
    }

    <T extends Node> T field(String label, T control) {
      Label l = new Label(label);
      l.setWrapText(true);
      grid.add(l, 0, row);
      grid.add(control, 1, row++);
      if (control instanceof Control c) c.setMaxWidth(Double.MAX_VALUE);
      return control;
    }

    TextField text(String label, String value) {
      return field(label, new TextField(value == null ? "" : value));
    }

    PasswordField password(String label) {
      return field(label, new PasswordField());
    }

    <T> ComboBox<T> choice(String label, T[] values, T selected) {
      ComboBox<T> c = new ComboBox<>(FXCollections.observableArrayList(values));
      c.setConverter(
          new javafx.util.StringConverter<T>() {
            public String toString(T value) {
              return value == null ? "" : Format.label(value);
            }

            public T fromString(String value) {
              return null;
            }
          });
      c.setValue(selected);
      return field(label, c);
    }

    void hint(String text) {
      Label l = new Label(text);
      l.setWrapText(true);
      l.getStyleClass().add("small-muted");
      grid.add(l, 0, row++, 2, 1);
    }

    void save(Supplier<Runnable> action, Runnable done) {
      Button button = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
      button.addEventFilter(
          javafx.event.ActionEvent.ACTION,
          e -> {
            e.consume();
            try {
              Runnable work = action.get();
              button.setDisable(true);
              dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
              javafx.concurrent.Task<Void> task =
                  new javafx.concurrent.Task<>() {
                    protected Void call() {
                      work.run();
                      return null;
                    }
                  };
              task.setOnSucceeded(
                  ev -> {
                    dialog.close();
                    done.run();
                  });
              task.setOnFailed(
                  ev -> {
                    button.setDisable(false);
                    dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);
                    Ui.error(task.getException());
                  });
              App.worker.submit(task);
            } catch (Exception ex) {
              Ui.error(ex);
            }
          });
      dialog.showAndWait();
    }
  }

  public static void member(Adherent m, Runnable done) {
    Form f = new Form(m == null ? "Nouvel adhérent" : "Modifier l'adhérent");
    f.hint(
        "Tous les champs sont obligatoires. Un compte d'épargne est ouvert automatiquement à"
            + " l'adhésion.");
    TextField name = f.text("Nom complet", m == null ? "" : m.fullName),
        phone = f.text("Téléphone", m == null ? "" : m.phone),
        email = f.text("Email", m == null ? "" : m.email),
        address = f.text("Adresse", m == null ? "" : m.address);
    var agencyList = App.service.agencies();
    ComboBox<Agency> agency =
        f.choice(
            "Agence",
            agencyList.toArray(Agency[]::new),
            agencyList.stream()
                .filter(a -> m != null && a.id.equals(m.agency.id))
                .findFirst()
                .orElse(agencyList.isEmpty() ? null : agencyList.get(0)));
    ComboBox<AdherentStatus> state =
        f.choice("Statut", AdherentStatus.values(), m == null ? AdherentStatus.ACTIF : m.status);
    f.save(
        () -> {
          String n = name.getText(),
              p = phone.getText(),
              e = email.getText(),
              a = address.getText();
          Long ag = agency.getValue() == null ? null : agency.getValue().id;
          AdherentStatus s = state.getValue();
          return () ->
              App.service.saveMember(
                  m == null ? null : m.id, m == null ? 0 : m.version, n, p, e, a, ag, s);
        },
        done);
  }

  public static void operation(Operation o, Kind defaultKind, Adherent preselected, Runnable done) {
    Form f = new Form(o == null ? "Nouvelle opération" : "Modifier la demande de prêt");
    f.hint(
        "Montants en F CFA, sans décimales. Les prêts nécessitent une validation. L'intérêt est"
            + " simple, calculé une fois sur le capital.");
    TextField lookup = f.text("Chercher l'adhérent", "");
    lookup.setPromptText("Nom ou numéro, puis Entrée");
    ComboBox<Adherent> member = f.choice("Adhérent", new Adherent[] {}, null);
    Runnable search =
        () -> {
          String query = lookup.getText();
          Ui.run(
              () -> App.service.findMembers(query),
              items -> {
                member.setItems(FXCollections.observableArrayList(items));
                if (!items.isEmpty()) member.getSelectionModel().selectFirst();
              });
        };
    lookup.setOnAction(e -> search.run());
    Button find = f.field("", new Button("Rechercher l'adhérent"));
    find.setOnAction(e -> search.run());
    ComboBox<Kind> kind = f.choice("Type", Kind.values(), o == null ? defaultKind : o.kind);
    TextField amount = f.text("Montant F CFA", o == null ? "" : o.amount.toPlainString());
    TextField interest = f.text("Intérêt total (%)", o == null ? "0" : o.interest.toPlainString());
    DatePicker due =
        f.field(
            "Date d'échéance",
            new DatePicker(o == null ? LocalDate.now().plusMonths(1) : o.dueDate));
    ComboBox<Operation> loan = f.choice("Prêt à rembourser", new Operation[] {}, null);
    loan.setConverter(
        new javafx.util.StringConverter<>() {
          public String toString(Operation l) {
            return l == null ? "" : "#" + l.id + " · " + Format.money(l.amount) + " · " + l.dueDate;
          }

          public Operation fromString(String s) {
            return null;
          }
        });
    TextField note = f.text("Note (facultative)", o == null ? "" : o.note);
    Runnable mode =
        () -> {
          interest.setDisable(kind.getValue() != Kind.PRET);
          due.setDisable(kind.getValue() != Kind.PRET);
          loan.setDisable(kind.getValue() != Kind.REMBOURSEMENT);
        };
    kind.valueProperty().addListener((a, b, c) -> mode.run());
    mode.run();
    member
        .valueProperty()
        .addListener(
            (a, b, c) -> {
              loan.getItems().clear();
              if (c != null)
                Ui.run(
                    () -> App.service.loans(c.id),
                    loans -> {
                      if (member.getValue() != null && member.getValue().id.equals(c.id))
                        loan.setItems(FXCollections.observableArrayList(loans));
                    });
            });
    Adherent selected = o != null ? o.member : preselected;
    if (selected != null) {
      member.getItems().add(selected);
      member.setValue(selected);
    } else search.run();
    if (o != null) {
      lookup.setDisable(true);
      find.setDisable(true);
      member.setDisable(true);
      kind.setDisable(true);
    }
    f.save(
        () -> {
          BigDecimal value = decimal(amount.getText(), "Montant"),
              rate =
                  kind.getValue() == Kind.PRET
                      ? decimal(interest.getText(), "Intérêt")
                      : BigDecimal.ZERO;
          if (due.isEditable()) due.commitValue();
          OperationInput input =
              new OperationInput(
                  o == null ? null : o.id,
                  o == null ? 0 : o.version,
                  member.getValue() == null ? null : member.getValue().id,
                  kind.getValue(),
                  value,
                  rate,
                  due.getValue(),
                  loan.getValue() == null ? null : loan.getValue().id,
                  note.getText());
          return () -> App.service.saveOperation(input);
        },
        done);
  }

  public static void user(AccountUser u, Runnable done) {
    Form f = new Form(u == null ? "Nouvel utilisateur" : "Modifier l'utilisateur");
    TextField login = f.text("Identifiant", u == null ? "" : u.username),
        name = f.text("Nom complet", u == null ? "" : u.fullName);
    PasswordField password = f.password("Mot de passe");
    f.hint(
        u == null ? "10 caractères minimum." : "Laissez le mot de passe vide pour le conserver.");
    ComboBox<Role> role = f.choice("Rôle", Role.values(), u == null ? Role.AGENT : u.role);
    CheckBox active = f.field("Compte actif", new CheckBox());
    active.setSelected(u == null || u.active);
    f.save(
        () -> {
          String l = login.getText(), n = name.getText(), p = password.getText();
          Role r = role.getValue();
          boolean a = active.isSelected();
          return () ->
              App.service.saveUser(
                  u == null ? null : u.id, u == null ? 0 : u.version, l, n, p, r, a);
        },
        done);
  }

  public static void agency(Agency a, Runnable done) {
    Form f = new Form(a == null ? "Nouvelle agence" : "Modifier l'agence");
    TextField name = f.text("Nom", a == null ? "" : a.name),
        address = f.text("Adresse", a == null ? "" : a.address);
    f.save(
        () -> {
          String n = name.getText(), ad = address.getText();
          return () -> App.service.saveAgency(a == null ? null : a.id, n, ad);
        },
        done);
  }

  public static void password() {
    Form f = new Form("Changer mon mot de passe");
    PasswordField old = f.password("Mot de passe actuel"),
        password = f.password("Nouveau mot de passe"),
        confirm = f.password("Confirmation");
    f.hint("10 caractères minimum.");
    f.save(
        () -> {
          String o = old.getText(), p = password.getText();
          if (!p.equals(confirm.getText()))
            throw new IllegalArgumentException("Les mots de passe ne correspondent pas.");
          return () -> App.service.changePassword(o, p);
        },
        () -> Ui.info("Mot de passe modifié."));
  }

  private static BigDecimal decimal(String value, String label) {
    try {
      return new BigDecimal(value.trim().replace(" ", "").replace(",", "."));
    } catch (Exception e) {
      throw new IllegalArgumentException(label + " : nombre invalide.");
    }
  }
}
