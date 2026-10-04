package sn.microgest.repository;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import sn.microgest.config.Database;
import sn.microgest.model.*;
import sn.microgest.model.Domain.*;

public final class Repository {
  public enum View {
    MEMBERS,
    OPERATIONS,
    LOANS,
    USERS,
    AGENCIES,
    EMAILS
  }

  public record Filter(
      String text, String status, Long agency, LocalDate from, LocalDate to, int page, int size) {
    public Filter {
      text = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
      if (page < 0 || size < 1 || size > 100)
        throw new IllegalArgumentException("Pagination invalide.");
      if (from != null && to != null && from.isAfter(to))
        throw new IllegalArgumentException("La date de début doit précéder la date de fin.");
    }

    public Filter page(int p) {
      return new Filter(text, status, agency, from, to, p, size);
    }
  }

  public record Page(List<?> rows, long total, int index, int size) {
    public int pages() {
      return Math.max(1, (int) ((total + size - 1) / size));
    }
  }

  private final Database db;

  public Repository(Database db) {
    this.db = db;
  }

  public Page page(View view, Filter f) {
    return db.tx(em -> page(em, view, f));
  }

  public Page page(EntityManager em, View view, Filter f) {
    String entity =
        switch (view) {
          case MEMBERS -> "Adherent";
          case OPERATIONS, LOANS -> "Operation";
          case USERS -> "AccountUser";
          case AGENCIES -> "Agency";
          case EMAILS -> "Email";
        };
    List<String> clauses = new ArrayList<>();
    Map<String, Object> params = new HashMap<>();
    boolean member = view == View.MEMBERS, op = view == View.OPERATIONS || view == View.LOANS;
    if (view == View.LOANS) {
      clauses.add("e.kind = :kind");
      params.put("kind", Kind.PRET);
    }
    if (!f.text().isBlank()) {
      String[] fields =
          switch (view) {
            case MEMBERS -> new String[] {"e.fullName", "e.number", "e.phone"};
            case OPERATIONS, LOANS ->
                new String[] {"e.member.fullName", "e.member.number", "e.note"};
            case USERS -> new String[] {"e.username", "e.fullName"};
            case AGENCIES -> new String[] {"e.name", "e.address"};
            case EMAILS -> new String[] {"e.recipient", "e.subject"};
          };
      clauses.add(
          "("
              + String.join(
                  " or ",
                  Arrays.stream(fields)
                      .map(s -> "lower(" + s + ") like :text escape '!' ")
                      .toList())
              + ")");
      params.put(
          "text", "%" + f.text().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }
    if (f.status() != null && !f.status().isBlank()) {
      if (member) {
        clauses.add("e.status = :status");
        params.put("status", AdherentStatus.valueOf(f.status()));
      } else if (op) {
        clauses.add("e.state = :status");
        params.put("status", State.valueOf(f.status()));
      } else if (view == View.EMAILS) {
        clauses.add("e.state = :status");
        params.put("status", f.status());
      } else if (view == View.USERS) {
        clauses.add("e.role = :status");
        params.put("status", Role.valueOf(f.status()));
      }
    }
    if (f.agency() != null && (member || op)) {
      clauses.add((member ? "e.agency.id" : "e.member.agency.id") + " = :agency");
      params.put("agency", f.agency());
    }
    boolean dated = member || op || view == View.EMAILS;
    if (dated && f.from() != null) {
      clauses.add((member ? "e.joined" : "e.created") + " >= :from");
      params.put("from", member ? f.from() : f.from().atStartOfDay());
    }
    if (dated && f.to() != null) {
      clauses.add((member ? "e.joined <= :to" : "e.created < :to"));
      params.put("to", member ? f.to() : f.to().plusDays(1).atStartOfDay());
    }
    String where = clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
    var count = em.createQuery("select count(e) from " + entity + " e" + where, Long.class);
    params.forEach(count::setParameter);
    long total = count.getSingleResult();
    int page = Math.min(f.page(), Math.max(0, (int) ((total + f.size() - 1) / f.size()) - 1));
    String join =
        member
            ? " join fetch e.agency"
            : op ? " join fetch e.member m join fetch m.agency left join fetch e.loan" : "";
    var query =
        em.createQuery("select e from " + entity + " e" + join + where + " order by e.id desc");
    params.forEach(query::setParameter);
    return new Page(
        query.setFirstResult(page * f.size()).setMaxResults(f.size()).getResultList(),
        total,
        page,
        f.size());
  }
}
