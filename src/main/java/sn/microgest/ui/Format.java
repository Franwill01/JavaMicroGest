package sn.microgest.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Format {
  private Format() {}

  public static String money(BigDecimal n) {
    return NumberFormat.getIntegerInstance(Locale.FRANCE).format(n) + " F CFA";
  }

  public static String date(LocalDateTime d) {
    return d == null ? "—" : d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
  }

  public static String date(LocalDate d) {
    return d == null ? "—" : d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
  }

  public static String label(Object o) {
    if (o == null) return "—";
    return switch (o.toString()) {
      case "EN_ATTENTE" -> "En attente";
      case "VALIDEE" -> "Validée";
      case "ANNULEE" -> "Annulée";
      case "ACTIF" -> "Actif";
      case "SUSPENDU" -> "Suspendu";
      case "DEPOT" -> "Dépôt";
      case "RETRAIT" -> "Retrait";
      case "PRET" -> "Prêt";
      case "REMBOURSEMENT" -> "Remboursement";
      case "ENVOYE" -> "Envoyé";
      case "ECHEC" -> "Échec";
      case "ADMIN" -> "Administrateur";
      case "AGENT" -> "Agent";
      case "SUPERVISEUR" -> "Superviseur";
      default -> o.toString();
    };
  }
}
