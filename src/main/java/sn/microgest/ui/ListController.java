package sn.microgest.ui;

import java.util.*;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import sn.microgest.App;
import sn.microgest.model.*;
import sn.microgest.model.Domain.*;
import sn.microgest.repository.Repository.*;
import sn.microgest.service.*;

public class ListController {
  @FXML private Label title, subtitle, total, pageLabel, notice;
  @FXML private TextField search, goTo;
  @FXML private ComboBox<String> status;
  @FXML private ComboBox<Agency> agency;
  @FXML private ComboBox<Integer> size;
  @FXML private DatePicker from, to;
  @FXML private TableView<Object> table;
  @FXML private Button addButton, previous, next;
  @FXML private FlowPane actions;
  private View view;
  private int page, pages = 1, request;
  private Role role;

  @FXML
  public void initialize() {
    size.getItems().setAll(10, 20, 50, 100);
    size.setValue(10);
    size.setOnAction(
        e -> {
          page = 0;
          load();
        });
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    table.setRowFactory(
        t -> {
          TableRow<Object> row = new TableRow<>();
          row.setOnMouseClicked(
              e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) details();
              });
          return row;
        });
    status.setConverter(
        new javafx.util.StringConverter<>() {
          public String toString(String s) {
            return s == null || s.isBlank() ? "Tous les statuts" : Format.label(s);
          }

          public String fromString(String s) {
            return s;
          }
        });
  }

  public void configure(View view) {
    this.view = view;
    role = App.service.current().role;
    String name =
        switch (view) {
          case MEMBERS -> "Adhérents";
          case OPERATIONS -> "Comptes et opérations";
          case LOANS -> "Prêts et échéances";
          case USERS -> "Utilisateurs";
          case AGENCIES -> "Agences";
          case EMAILS -> "Emails";
        };
    title.setText(name);
    subtitle.setText(
        switch (view) {
          case MEMBERS -> "Liste des adhérents.";
          case OPERATIONS -> "Dépôts, retraits et remboursements.";
          case LOANS -> "Demandes de prêts et suivi des remboursements.";
          case USERS -> "Comptes utilisateurs et rôles.";
          case AGENCIES -> "Liste des agences.";
          case EMAILS -> "Messages envoyés ou en attente.";
        });
    boolean member = view == View.MEMBERS, op = view == View.OPERATIONS || view == View.LOANS;
    status.getItems().add("");
    if (member) for (var s : AdherentStatus.values()) status.getItems().add(s.name());
    else if (op) for (var s : State.values()) status.getItems().add(s.name());
    else if (view == View.USERS) for (var r : Role.values()) status.getItems().add(r.name());
    else if (view == View.EMAILS) status.getItems().addAll("EN_ATTENTE", "ENVOYE", "ECHEC");
    visible(status, status.getItems().size() > 1);
    visible(agency, member || op);
    visible(from, member || op || view == View.EMAILS);
    visible(to, from.isVisible());
    if (member || op) Ui.run(() -> App.service.agencies(), list -> agency.getItems().setAll(list));
    boolean editable = role != Role.SUPERVISEUR;
    visible(addButton, editable && view != View.EMAILS);
    switch (view) {
      case MEMBERS -> {
        col("Numéro", o -> ((Adherent) o).number, 150);
        col("Nom complet", o -> ((Adherent) o).fullName, 180);
        col("Téléphone", o -> ((Adherent) o).phone, 125);
        col("Agence", o -> ((Adherent) o).agency.name, 140);
        col("Statut", o -> Format.label(((Adherent) o).status), 100);
        col("Adhésion", o -> Format.date(((Adherent) o).joined), 105);
        action("Voir le compte", this::details);
        if (editable) {
          action("Modifier", () -> edit());
          action("Supprimer", () -> remove());
          action(
              "Nouvelle opération",
              () -> {
                Adherent m = (Adherent) selected();
                if (m != null) Forms.operation(null, Kind.DEPOT, m, this::load);
              });
        }
        notice.setText("Double-cliquez sur un adhérent pour consulter son compte d'épargne.");
      }
      case OPERATIONS, LOANS -> {
        col("N°", o -> "#" + ((Operation) o).id, 60);
        col("Adhérent", o -> ((Operation) o).member.fullName, 170);
        col("Type", o -> Format.label(((Operation) o).kind), 120);
        col("Montant", o -> Format.money(((Operation) o).amount), 140);
        col("Statut", o -> Format.label(((Operation) o).state), 115);
        col(
            view == View.LOANS ? "Échéance" : "Date",
            o ->
                view == View.LOANS
                    ? Format.date(((Operation) o).dueDate)
                    : Format.date(((Operation) o).created),
            130);
        action("Détails", this::details);
        if (editable) {
          action("Modifier la demande", this::edit);
          action("Supprimer la demande", this::remove);
        }
        if (role != Role.AGENT) {
          action("Valider le prêt", () -> change(true));
          action("Annuler l'opération", () -> change(false));
        }
        notice.setText(
            "La modification et la suppression concernent les prêts en attente. Une opération"
                + " validée peut être annulée par un responsable.");
      }
      case USERS -> {
        col("Identifiant", o -> ((AccountUser) o).username, 150);
        col("Nom", o -> ((AccountUser) o).fullName, 200);
        col("Rôle", o -> Format.label(((AccountUser) o).role), 140);
        col("Accès", o -> ((AccountUser) o).active ? "Actif" : "Désactivé", 110);
        action("Modifier / désactiver", this::edit);
        notice.setText(
            "Désactivez un compte pour révoquer son accès tout en conservant son historique.");
      }
      case AGENCIES -> {
        col("Agence", o -> ((Agency) o).name, 220);
        col("Adresse", o -> ((Agency) o).address, 300);
        action("Modifier", this::edit);
        action("Supprimer", this::remove);
        notice.setText("Une agence peut être supprimée lorsqu'aucun adhérent n'y est rattaché.");
      }
      case EMAILS -> {
        col("Date", o -> Format.date(((Email) o).created), 140);
        col("Destinataire", o -> ((Email) o).recipient, 180);
        col("Objet", o -> ((Email) o).subject, 240);
        col("Statut", o -> Format.label(((Email) o).state), 100);
        col("Essais", o -> Integer.toString(((Email) o).attempts), 60);
        action("Lire le message", this::details);
        action(
            "Relancer",
            () -> {
              Email e = (Email) selected();
              if (e != null)
                Ui.run(
                    () -> {
                      App.service.retryEmail(e.id);
                      return null;
                    },
                    v -> load());
            });
        notice.setText("Jakarta Mail : envoi des emails de bienvenue et des notifications.");
      }
    }
    load();
  }

  private void visible(javafx.scene.Node n, boolean yes) {
    n.setVisible(yes);
    n.setManaged(yes);
  }

  private void col(String name, Function<Object, String> value, double width) {
    TableColumn<Object, String> c = new TableColumn<>(name);
    c.setCellValueFactory(v -> new SimpleStringProperty(value.apply(v.getValue())));
    c.setPrefWidth(width);
    c.setMinWidth(60);
    c.setSortable(false);
    table.getColumns().add(c);
  }

  private void action(String name, Runnable fn) {
    Button b = new Button(name);
    b.setOnAction(
        e -> {
          try {
            fn.run();
          } catch (Exception ex) {
            Ui.error(ex);
          }
        });
    b.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    actions.getChildren().add(b);
  }

  private Filter filters() {
    from.commitValue();
    to.commitValue();
    return new Filter(
        search.getText(),
        status.getValue(),
        agency.getValue() == null ? null : agency.getValue().id,
        from.getValue(),
        to.getValue(),
        page,
        size.getValue());
  }

  private void load() {
    if (view == null) return;
    try {
      Filter f = filters();
      int token = ++request;
      total.setText("Chargement…");
      Ui.run(
          () -> App.service.page(view, f),
          result -> {
            if (token != request) return;
            page = result.index();
            pages = result.pages();
            table.setItems(FXCollections.observableArrayList(result.rows()));
            total.setText(result.total() + " résultat(s)");
            pageLabel.setText((page + 1) + " / " + pages);
            previous.setDisable(page == 0);
            next.setDisable(page + 1 >= pages);
          });
    } catch (Exception e) {
      Ui.error(e);
    }
  }

  @FXML
  private void apply() {
    page = 0;
    load();
  }

  @FXML
  private void reset() {
    search.clear();
    status.setValue(null);
    agency.setValue(null);
    from.setValue(null);
    to.setValue(null);
    page = 0;
    load();
  }

  @FXML
  private void previous() {
    if (page > 0) {
      page--;
      load();
    }
  }

  @FXML
  private void next() {
    if (page + 1 < pages) {
      page++;
      load();
    }
  }

  @FXML
  private void go() {
    try {
      int n = Integer.parseInt(goTo.getText());
      if (n < 1 || n > pages) throw new NumberFormatException();
      page = n - 1;
      load();
    } catch (NumberFormatException e) {
      Ui.info("Choisissez une page entre 1 et " + pages + ".");
    }
  }

  private Object selected() {
    Object item = table.getSelectionModel().getSelectedItem();
    if (item == null) Ui.info("Sélectionnez une ligne.");
    return item;
  }

  @FXML
  private void add() {
    try {
      switch (view) {
        case MEMBERS -> Forms.member(null, this::load);
        case OPERATIONS -> Forms.operation(null, Kind.DEPOT, null, this::load);
        case LOANS -> Forms.operation(null, Kind.PRET, null, this::load);
        case USERS -> Forms.user(null, this::load);
        case AGENCIES -> Forms.agency(null, this::load);
        default -> {}
      }
    } catch (Exception e) {
      Ui.error(e);
    }
  }

  private void edit() {
    Object o = selected();
    if (o == null) return;
    switch (view) {
      case MEMBERS -> Forms.member((Adherent) o, this::load);
      case OPERATIONS, LOANS -> {
        Operation operation = (Operation) o;
        if (operation.state != State.EN_ATTENTE) {
          Ui.info("Seules les demandes de prêt en attente sont modifiables.");
          return;
        }
        Forms.operation(operation, Kind.PRET, null, this::load);
      }
      case USERS -> Forms.user((AccountUser) o, this::load);
      case AGENCIES -> Forms.agency((Agency) o, this::load);
      default -> {}
    }
  }

  private void remove() {
    Object o = selected();
    if (o == null || !Ui.confirm("Supprimer l'élément sélectionné ? Cette action est définitive."))
      return;
    Ui.run(
        () -> {
          switch (view) {
            case MEMBERS -> App.service.deleteMember(((Adherent) o).id);
            case OPERATIONS, LOANS -> App.service.deletePending(((Operation) o).id);
            case AGENCIES -> App.service.deleteAgency(((Agency) o).id);
            default -> {}
          }
          return null;
        },
        v -> load());
  }

  private void change(boolean approve) {
    Operation o = (Operation) selected();
    if (o == null
        || !Ui.confirm(
            approve
                ? "Valider ce prêt de " + Format.money(o.amount) + " ?"
                : "Annuler cette opération ? Son historique sera conservé.")) return;
    Ui.run(
        () -> {
          if (approve) App.service.approve(o.id);
          else App.service.cancel(o.id);
          return null;
        },
        v -> load());
  }

  private void details() {
    Object o = selected();
    if (o == null) return;
    if (o instanceof Adherent m) {
      Ui.run(
          () -> App.service.accountInfo(m.id),
          a ->
              Ui.info(
                  m.fullName
                      + "\n"
                      + m.number
                      + "\n"
                      + m.email
                      + " · "
                      + m.phone
                      + "\n"
                      + m.address
                      + "\n\nCompte d'épargne : "
                      + a.number()
                      + "\nSolde disponible : "
                      + Format.money(a.balance())));
    } else if (o instanceof Operation op) {
      Ui.run(
          () ->
              op.kind == Kind.PRET && op.state == State.VALIDEE
                  ? App.service.remaining(op.id)
                  : null,
          remaining ->
              Ui.info(
                  "Opération #"
                      + op.id
                      + " · "
                      + Format.label(op.kind)
                      + "\n"
                      + op.member
                      + "\nMontant : "
                      + Format.money(op.amount)
                      + "\nStatut : "
                      + Format.label(op.state)
                      + "\nDate : "
                      + Format.date(op.created)
                      + "\nAgent : "
                      + op.author
                      + (op.kind == Kind.PRET
                          ? "\nÉchéance : "
                              + Format.date(op.dueDate)
                              + "\nTaux total : "
                              + op.interest
                              + " %\nTotal contractuel : "
                              + Format.money(MicroGestService.loanTotal(op))
                          : "")
                      + (remaining == null ? "" : "\nRestant dû : " + Format.money(remaining))
                      + "\nNote : "
                      + op.note));
    } else if (o instanceof Email e)
      Ui.info(
          e.subject
              + "\nÀ : "
              + e.recipient
              + "\n\n"
              + e.body
              + (e.error == null ? "" : "\n\n" + e.error));
  }
}
