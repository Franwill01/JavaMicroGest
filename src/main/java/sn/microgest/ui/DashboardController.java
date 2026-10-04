package sn.microgest.ui;

import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import sn.microgest.App;
import sn.microgest.model.Operation;

public class DashboardController {
  @FXML private Label memberCount, savings, monthCount;
  @FXML private BarChart<String, Number> activity;
  @FXML private TableView<Operation> recent;

  @FXML
  public void initialize() {
    column("Référence", o -> "#" + o.id);
    column("Adhérent", o -> o.member.fullName);
    column("Type", o -> Format.label(o.kind));
    column("Montant", o -> Format.money(o.amount));
    column("Statut", o -> Format.label(o.state));
    recent.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    refresh();
  }

  private void column(String name, Function<Operation, String> value) {
    TableColumn<Operation, String> c = new TableColumn<>(name);
    c.setCellValueFactory(v -> new SimpleStringProperty(value.apply(v.getValue())));
    c.setSortable(false);
    recent.getColumns().add(c);
  }

  @FXML
  private void refresh() {
    Ui.run(
        () -> App.service.stats(),
        s -> {
          memberCount.setText(Long.toString(s.members()));
          savings.setText(Format.money(s.savings()).replace(" F CFA", ""));
          monthCount.setText(Long.toString(s.month()));
          XYChart.Series<String, Number> d = new XYChart.Series<>(), w = new XYChart.Series<>();
          d.setName("Dépôts");
          w.setName("Retraits");
          for (int i = 0; i < s.months().size(); i++) {
            d.getData().add(new XYChart.Data<>(s.months().get(i), s.deposits().get(i)));
            w.getData().add(new XYChart.Data<>(s.months().get(i), s.withdrawals().get(i)));
          }
          activity.getData().setAll(java.util.List.of(d, w));
          recent.setItems(FXCollections.observableArrayList(s.recent()));
        });
  }
}
