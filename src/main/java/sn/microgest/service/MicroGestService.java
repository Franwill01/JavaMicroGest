package sn.microgest.service;

import jakarta.persistence.*;
import java.math.*;
import java.time.*;
import java.util.*;
import sn.microgest.config.Database;
import sn.microgest.model.*;
import sn.microgest.model.Domain.*;
import sn.microgest.repository.Repository;
import sn.microgest.repository.Repository.*;

public final class MicroGestService {
  private final Database db;
  private final Repository repository;
  private volatile Long userId;
  private int failedLogins;
  private long blockedUntil;

  public MicroGestService(Database db) {
    this.db = db;
    repository = new Repository(db);
  }

  public boolean needsSetup() {
    return db.tx(
        em ->
            em.createQuery("select count(u) from AccountUser u", Long.class).getSingleResult()
                == 0);
  }

  public synchronized void setup(String login, String name, String password, boolean demo) {
    String username = username(login),
        fullName = required(name, "Nom", 120),
        hash = Passwords.hash(password);
    db.tx(
        em -> {
          if (em.createQuery("select count(u) from AccountUser u", Long.class).getSingleResult()
              != 0) throw new IllegalArgumentException("Un administrateur existe déjà.");
          AccountUser u = new AccountUser();
          u.username = username;
          u.fullName = fullName;
          u.passwordHash = hash;
          u.role = Role.ADMIN;
          em.persist(u);
          Agency a = new Agency("Agence principale", "Dakar");
          em.persist(a);
          if (demo) {
            String[] names = {
              "Awa Exemple",
              "Moussa Exemple",
              "Fatou Exemple",
              "Ibrahima Exemple",
              "Mariama Exemple",
              "Ousmane Exemple",
              "Aminata Exemple",
              "Cheikh Exemple",
              "Sokhna Exemple",
              "Abdou Exemple",
              "Coumba Exemple",
              "Aliou Exemple",
              "Ndeye Exemple",
              "Babacar Exemple",
              "Khady Exemple",
              "Mamadou Exemple",
              "Adama Exemple",
              "Binta Exemple",
              "Mame Exemple",
              "Lamine Exemple",
              "Penda Exemple",
              "Samba Exemple",
              "Daba Exemple",
              "Issa Exemple",
              "Rama Exemple"
            };
            for (int i = 0; i < names.length; i++) {
              Adherent m = new Adherent();
              m.fullName = names[i];
              m.number = "DEMO-" + String.format("%03d", i + 1);
              m.phone = "77000" + String.format("%04d", i + 1);
              m.email = "demo" + (i + 1) + "@example.invalid";
              m.address = "Dakar · données fictives";
              m.agency = a;
              m.joined = LocalDate.now().minusDays(i * 4L);
              em.persist(m);
              account(em, m);
              Operation o = new Operation();
              o.member = m;
              o.kind = Kind.DEPOT;
              o.state = State.VALIDEE;
              o.amount = BigDecimal.valueOf(25000 + i * 5000L);
              o.author = username;
              o.note = "Données de démonstration";
              o.created = LocalDateTime.now().minusDays(i * 4L);
              em.persist(o);
            }
          }
          audit(
              em,
              username,
              "Initialisation de l'établissement" + (demo ? " avec données fictives" : ""));
          return null;
        });
  }

  public synchronized AccountUser login(String login, String password) {
    if (System.currentTimeMillis() < blockedUntil)
      throw new IllegalArgumentException("Trop de tentatives. Réessayez dans une minute.");
    AccountUser user =
        db.tx(
            em ->
                em.createQuery("from AccountUser where username=:u", AccountUser.class)
                    .setParameter("u", login == null ? "" : login.trim().toLowerCase(Locale.ROOT))
                    .getResultStream()
                    .findFirst()
                    .orElse(null));
    if (user == null
        || !Passwords.verify(password == null ? "" : password, user.passwordHash)
        || !user.active) {
      if (++failedLogins >= 5) {
        blockedUntil = System.currentTimeMillis() + 60_000;
        failedLogins = 0;
      }
      throw new IllegalArgumentException("Identifiant ou mot de passe incorrect.");
    }
    failedLogins = 0;
    userId = user.id;
    db.tx(
        em -> {
          audit(em, user.username, "Connexion");
          return null;
        });
    return user;
  }

  public void logout() {
    userId = null;
  }

  public AccountUser current() {
    return db.tx(this::requireUser);
  }

  private AccountUser requireUser(EntityManager em, Role... roles) {
    AccountUser u = userId == null ? null : em.find(AccountUser.class, userId);
    if (u == null || !u.active) throw new SecurityException("Veuillez vous reconnecter.");
    if (roles.length > 0 && Arrays.stream(roles).noneMatch(r -> r == u.role))
      throw new SecurityException("Votre rôle ne permet pas cette action.");
    return u;
  }

  public Page page(View view, Filter f) {
    return db.tx(
        em -> {
          if (view == View.USERS || view == View.AGENCIES || view == View.EMAILS)
            requireUser(em, Role.ADMIN);
          else requireUser(em);
          return repository.page(em, view, f);
        });
  }

  public List<Agency> agencies() {
    return db.tx(
        em -> {
          requireUser(em);
          return em.createQuery("from Agency order by name", Agency.class).getResultList();
        });
  }

  public List<Adherent> findMembers(String text) {
    return db.tx(
        em -> {
          requireUser(em);
          return em.createQuery(
                  "from Adherent m join fetch m.agency where lower(m.fullName) like :q or"
                      + " lower(m.number) like :q order by m.fullName",
                  Adherent.class)
              .setParameter("q", "%" + (text == null ? "" : text.toLowerCase(Locale.ROOT)) + "%")
              .setMaxResults(30)
              .getResultList();
        });
  }

  public Adherent saveMember(
      Long id,
      long version,
      String name,
      String phone,
      String email,
      String address,
      Long agency,
      AdherentStatus status) {
    String n = required(name, "Nom", 120),
        p = required(phone, "Téléphone", 25).replaceAll("[\\s().-]", ""),
        e = required(email, "Email", 150).toLowerCase(Locale.ROOT),
        a = required(address, "Adresse", 250);
    if (!p.matches("\\+?[0-9]{9,15}"))
      throw new IllegalArgumentException("Téléphone : 9 à 15 chiffres, avec + facultatif.");
    if (!e.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new IllegalArgumentException("Adresse email invalide.");
    if (agency == null || status == null)
      throw new IllegalArgumentException("Choisissez une agence et un statut.");
    return db.tx(
        em -> {
          AccountUser actor = requireUser(em, Role.ADMIN, Role.AGENT);
          Adherent m =
              id == null
                  ? new Adherent()
                  : em.find(Adherent.class, id, LockModeType.PESSIMISTIC_WRITE);
          if (m == null) throw new IllegalArgumentException("Cet adhérent n'existe plus.");
          if (id != null && m.version != version)
            throw new IllegalArgumentException("L'adhérent a été modifié. Actualisez la liste.");
          if (em.createQuery(
                      "select count(m) from Adherent m where m.phone=:p and (:id is null or"
                          + " m.id<>:id)",
                      Long.class)
                  .setParameter("p", p)
                  .setParameter("id", id)
                  .getSingleResult()
              > 0)
            throw new IllegalArgumentException("Ce téléphone appartient déjà à un adhérent.");
          m.fullName = n;
          m.phone = p;
          m.email = e;
          m.address = a;
          m.agency = em.find(Agency.class, agency);
          m.status = status;
          if (m.agency == null) throw new IllegalArgumentException("Agence introuvable.");
          if (id == null) {
            m.number =
                "ADH-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
            em.persist(m);
            account(em, m);
            email(
                em,
                m.email,
                "Bienvenue chez MicroGest",
                "Bonjour "
                    + m.fullName
                    + ",\nVotre adhésion est enregistrée sous le numéro "
                    + m.number
                    + ".\nBienvenue dans votre établissement.");
          }
          audit(
              em,
              actor.username,
              (id == null ? "Création" : "Modification") + " adhérent " + m.number);
          return m;
        });
  }

  public void deleteMember(long id) {
    db.tx(
        em -> {
          AccountUser actor = requireUser(em, Role.ADMIN, Role.AGENT);
          Adherent m = em.find(Adherent.class, id, LockModeType.PESSIMISTIC_WRITE);
          if (m == null) throw new IllegalArgumentException("Adhérent introuvable.");
          if (em.createQuery("select count(o) from Operation o where o.member.id=:id", Long.class)
                  .setParameter("id", id)
                  .getSingleResult()
              > 0)
            throw new IllegalArgumentException(
                "Cet adhérent possède des opérations. Suspendez-le pour conserver son historique.");
          em.createQuery("delete from SavingsAccount a where a.member.id=:id")
              .setParameter("id", id)
              .executeUpdate();
          audit(em, actor.username, "Suppression adhérent " + m.number);
          em.remove(m);
          return null;
        });
  }

  public BigDecimal balance(long id) {
    return db.tx(
        em -> {
          requireUser(em);
          return balance(em, id);
        });
  }

  private BigDecimal balance(EntityManager em, long id) {
    return sum(
            em,
            "o.member.id=:id and o.state=:s and o.kind=:k",
            Map.of("id", id, "s", State.VALIDEE, "k", Kind.DEPOT))
        .subtract(
            sum(
                em,
                "o.member.id=:id and o.state=:s and o.kind=:k",
                Map.of("id", id, "s", State.VALIDEE, "k", Kind.RETRAIT)));
  }

  public record AccountInfo(String number, BigDecimal balance) {}

  public AccountInfo accountInfo(long id) {
    return db.tx(
        em -> {
          requireUser(em);
          SavingsAccount a =
              em.createQuery("from SavingsAccount where member.id=:id", SavingsAccount.class)
                  .setParameter("id", id)
                  .getSingleResult();
          return new AccountInfo(a.number, balance(em, id));
        });
  }

  private static void account(EntityManager em, Adherent m) {
    SavingsAccount a = new SavingsAccount();
    a.member = m;
    a.number = "EP-" + m.number;
    em.persist(a);
  }

  public record OperationInput(
      Long id,
      long version,
      Long member,
      Kind kind,
      BigDecimal amount,
      BigDecimal interest,
      LocalDate due,
      Long loan,
      String note) {}

  public Operation saveOperation(OperationInput input) {
    BigDecimal amount = money(input.amount());
    Kind kind = Objects.requireNonNull(input.kind(), "Type requis");
    BigDecimal rate = input.interest() == null ? BigDecimal.ZERO : input.interest();
    if (kind == Kind.PRET && (input.due() == null || !input.due().isAfter(LocalDate.now())))
      throw new IllegalArgumentException("L'échéance du prêt doit être dans le futur.");
    if (rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0 || rate.scale() > 2)
      throw new IllegalArgumentException("Taux : de 0 à 100, avec 2 décimales maximum.");
    String note = input.note() == null ? "" : input.note().trim();
    if (note.length() > 250) throw new IllegalArgumentException("Note : 250 caractères maximum.");
    return db.tx(
        em -> {
          AccountUser actor = requireUser(em, Role.ADMIN, Role.AGENT);
          if (input.member() == null) throw new IllegalArgumentException("Choisissez un adhérent.");
          Adherent m = em.find(Adherent.class, input.member(), LockModeType.PESSIMISTIC_WRITE);
          if (m == null || m.status != AdherentStatus.ACTIF)
            throw new IllegalArgumentException("L'adhérent doit être actif.");
          Operation o =
              input.id() == null
                  ? new Operation()
                  : em.find(Operation.class, input.id(), LockModeType.PESSIMISTIC_WRITE);
          if (o == null) throw new IllegalArgumentException("Opération introuvable.");
          if (input.id() != null
              && (o.state != State.EN_ATTENTE
                  || o.version != input.version()
                  || !o.member.id.equals(m.id)
                  || kind != Kind.PRET))
            throw new IllegalArgumentException(
                "Seul un prêt en attente, non modifié depuis l'ouverture, peut être édité.");
          if (kind == Kind.RETRAIT && balance(em, m.id).compareTo(amount) < 0)
            throw new IllegalArgumentException("Solde insuffisant pour ce retrait.");
          Operation loan = null;
          if (kind == Kind.REMBOURSEMENT) {
            loan =
                input.loan() == null
                    ? null
                    : em.find(Operation.class, input.loan(), LockModeType.PESSIMISTIC_WRITE);
            if (loan == null
                || loan.kind != Kind.PRET
                || loan.state != State.VALIDEE
                || !loan.member.id.equals(m.id))
              throw new IllegalArgumentException("Sélectionnez un prêt validé de cet adhérent.");
            if (remaining(em, loan).compareTo(amount) < 0)
              throw new IllegalArgumentException("Le remboursement dépasse le montant restant dû.");
          }
          o.member = m;
          o.kind = kind;
          o.amount = amount;
          o.interest = kind == Kind.PRET ? rate : BigDecimal.ZERO;
          o.dueDate = kind == Kind.PRET ? input.due() : null;
          o.loan = loan;
          o.note = note;
          o.author = actor.username;
          o.state = kind == Kind.PRET ? State.EN_ATTENTE : State.VALIDEE;
          if (input.id() == null) em.persist(o);
          em.flush();
          audit(
              em,
              actor.username,
              (input.id() == null ? "Création" : "Modification")
                  + " opération #"
                  + o.id
                  + " "
                  + kind
                  + " "
                  + amount
                  + " F CFA");
          if (kind != Kind.PRET)
            email(
                em,
                m.email,
                "Opération enregistrée · " + m.number,
                "Bonjour "
                    + m.fullName
                    + ",\nUne opération "
                    + kind
                    + " de "
                    + amount
                    + " F CFA a été enregistrée.\nRéférence : "
                    + o.id
                    + ".");
          return o;
        });
  }

  public void approve(long id) {
    db.tx(
        em -> {
          AccountUser actor = requireUser(em, Role.ADMIN, Role.SUPERVISEUR);
          Operation ref = em.find(Operation.class, id);
          if (ref == null) throw new IllegalArgumentException("Opération introuvable.");
          em.lock(ref.member, LockModeType.PESSIMISTIC_WRITE);
          em.refresh(ref, LockModeType.PESSIMISTIC_WRITE);
          if (ref.kind != Kind.PRET || ref.state != State.EN_ATTENTE)
            throw new IllegalArgumentException("Ce prêt n'est plus en attente.");
          if (ref.member.status != AdherentStatus.ACTIF || !ref.dueDate.isAfter(LocalDate.now()))
            throw new IllegalArgumentException(
                "Vérifiez le statut de l'adhérent et l'échéance du prêt.");
          ref.state = State.VALIDEE;
          audit(em, actor.username, "Validation prêt #" + id);
          email(
              em,
              ref.member.email,
              "Votre prêt est accordé",
              "Bonjour "
                  + ref.member.fullName
                  + ",\nVotre prêt de "
                  + ref.amount
                  + " F CFA est accordé.\nÉchéance : "
                  + ref.dueDate
                  + ". Total à rembourser : "
                  + loanTotal(ref)
                  + " F CFA.");
          return null;
        });
  }

  public void cancel(long id) {
    db.tx(
        em -> {
          AccountUser actor = requireUser(em, Role.ADMIN, Role.SUPERVISEUR);
          Operation o = em.find(Operation.class, id);
          if (o == null) throw new IllegalArgumentException("Opération introuvable.");
          em.lock(o.member, LockModeType.PESSIMISTIC_WRITE);
          em.refresh(o, LockModeType.PESSIMISTIC_WRITE);
          if (o.state == State.ANNULEE)
            throw new IllegalArgumentException("Opération déjà annulée.");
          if (o.kind == Kind.DEPOT
              && o.state == State.VALIDEE
              && balance(em, o.member.id).compareTo(o.amount) < 0)
            throw new IllegalArgumentException("L'annulation rendrait le solde négatif.");
          if (o.kind == Kind.PRET
              && em.createQuery(
                          "select count(o) from Operation o where o.loan.id=:id and o.state=:s",
                          Long.class)
                      .setParameter("id", id)
                      .setParameter("s", State.VALIDEE)
                      .getSingleResult()
                  > 0)
            throw new IllegalArgumentException("Ce prêt possède des remboursements validés.");
          o.state = State.ANNULEE;
          audit(em, actor.username, "Annulation opération #" + id);
          email(
              em,
              o.member.email,
              "Annulation d'une opération",
              "L'opération #"
                  + id
                  + " ("
                  + o.kind
                  + ", "
                  + o.amount
                  + " F CFA) a été annulée. Contactez votre agence pour toute question.");
          return null;
        });
  }

  public void deletePending(long id) {
    db.tx(
        em -> {
          AccountUser actor = requireUser(em, Role.ADMIN, Role.AGENT);
          Operation o = em.find(Operation.class, id, LockModeType.PESSIMISTIC_WRITE);
          if (o == null || o.state != State.EN_ATTENTE)
            throw new IllegalArgumentException(
                "Seules les demandes de prêt en attente peuvent être supprimées.");
          audit(em, actor.username, "Suppression demande de prêt #" + id);
          em.remove(o);
          return null;
        });
  }

  public List<Operation> loans(long member) {
    return db.tx(
        em -> {
          requireUser(em);
          return em.createQuery(
                  "from Operation o where o.member.id=:id and o.kind=:k and o.state=:s order by"
                      + " o.id desc",
                  Operation.class)
              .setParameter("id", member)
              .setParameter("k", Kind.PRET)
              .setParameter("s", State.VALIDEE)
              .getResultList();
        });
  }

  public BigDecimal remaining(long id) {
    return db.tx(
        em -> {
          requireUser(em);
          Operation o = em.find(Operation.class, id);
          if (o == null || o.kind != Kind.PRET)
            throw new IllegalArgumentException("Prêt introuvable.");
          return remaining(em, o);
        });
  }

  private BigDecimal remaining(EntityManager em, Operation loan) {
    return loanTotal(loan)
        .subtract(
            sum(em, "o.loan.id=:id and o.state=:s", Map.of("id", loan.id, "s", State.VALIDEE)));
  }

  public static BigDecimal loanTotal(Operation loan) {
    return loan.amount
        .multiply(BigDecimal.ONE.add(loan.interest.divide(new BigDecimal("100"))))
        .setScale(0, RoundingMode.HALF_UP);
  }

  public void saveUser(
      Long id,
      long version,
      String login,
      String name,
      String password,
      Role role,
      boolean active) {
    String un = username(login), n = required(name, "Nom", 120);
    if (role == null) throw new IllegalArgumentException("Choisissez un rôle.");
    String hash = password == null || password.isEmpty() ? null : Passwords.hash(password);
    if (id == null && hash == null) throw new IllegalArgumentException("Mot de passe requis.");
    db.tx(
        em -> {
          AccountUser actor = requireUser(em, Role.ADMIN);
          AccountUser u =
              id == null
                  ? new AccountUser()
                  : em.find(AccountUser.class, id, LockModeType.PESSIMISTIC_WRITE);
          if (u == null || id != null && u.version != version)
            throw new IllegalArgumentException("Ce compte a changé. Actualisez la liste.");
          if (id != null && id.equals(actor.id) && (!active || role != Role.ADMIN))
            throw new IllegalArgumentException(
                "Vous ne pouvez pas retirer vos propres droits administrateur.");
          if (em.createQuery(
                      "select count(u) from AccountUser u where u.username=:name and (:id is null"
                          + " or u.id<>:id)",
                      Long.class)
                  .setParameter("name", un)
                  .setParameter("id", id)
                  .getSingleResult()
              > 0) throw new IllegalArgumentException("Identifiant déjà utilisé.");
          u.username = un;
          u.fullName = n;
          u.role = role;
          u.active = active;
          if (hash != null) u.passwordHash = hash;
          if (id == null) em.persist(u);
          audit(em, actor.username, "Enregistrement utilisateur " + un);
          return null;
        });
  }

  public void changePassword(String oldPassword, String password) {
    String hash = Passwords.hash(password);
    db.tx(
        em -> {
          AccountUser u = requireUser(em);
          if (!Passwords.verify(oldPassword, u.passwordHash))
            throw new IllegalArgumentException("Le mot de passe actuel est incorrect.");
          u.passwordHash = hash;
          audit(em, u.username, "Changement de mot de passe");
          return null;
        });
  }

  public void saveAgency(Long id, String name, String address) {
    String n = required(name, "Agence", 100), a = required(address, "Adresse", 150);
    db.tx(
        em -> {
          AccountUser u = requireUser(em, Role.ADMIN);
          Agency agency = id == null ? new Agency() : em.find(Agency.class, id);
          if (agency == null) throw new IllegalArgumentException("Agence introuvable.");
          agency.name = n;
          agency.address = a;
          if (id == null) em.persist(agency);
          audit(em, u.username, "Enregistrement agence " + n);
          return null;
        });
  }

  public void deleteAgency(long id) {
    db.tx(
        em -> {
          AccountUser u = requireUser(em, Role.ADMIN);
          if (em.createQuery("select count(m) from Adherent m where m.agency.id=:id", Long.class)
                  .setParameter("id", id)
                  .getSingleResult()
              > 0)
            throw new IllegalArgumentException(
                "Transférez les adhérents avant de supprimer cette agence.");
          Agency a = em.find(Agency.class, id);
          if (a != null) {
            audit(em, u.username, "Suppression agence " + a.name);
            em.remove(a);
          }
          return null;
        });
  }

  public void retryEmail(long id) {
    db.tx(
        em -> {
          AccountUser u = requireUser(em, Role.ADMIN);
          Email e = em.find(Email.class, id, LockModeType.PESSIMISTIC_WRITE);
          if (e == null || e.state.equals("ENVOYE"))
            throw new IllegalArgumentException("Cet email ne peut pas être relancé.");
          e.state = "EN_ATTENTE";
          e.attempts = 0;
          e.error = null;
          audit(em, u.username, "Relance email #" + id);
          return null;
        });
  }

  public record Stats(
      long members,
      BigDecimal savings,
      long month,
      long pending,
      long overdue,
      BigDecimal outstanding,
      List<BigDecimal> deposits,
      List<BigDecimal> withdrawals,
      List<String> months,
      List<Operation> recent) {}

  public Stats stats() {
    return db.tx(
        em -> {
          requireUser(em);
          long members =
              em.createQuery("select count(m) from Adherent m", Long.class).getSingleResult();
          BigDecimal savings =
              sum(em, "o.state=:s and o.kind=:k", Map.of("s", State.VALIDEE, "k", Kind.DEPOT))
                  .subtract(
                      sum(
                          em,
                          "o.state=:s and o.kind=:k",
                          Map.of("s", State.VALIDEE, "k", Kind.RETRAIT)));
          long month =
              em.createQuery(
                      "select count(o) from Operation o where o.state=:s and o.created>=:d",
                      Long.class)
                  .setParameter("s", State.VALIDEE)
                  .setParameter("d", LocalDate.now().withDayOfMonth(1).atStartOfDay())
                  .getSingleResult();
          long pending =
              em.createQuery("select count(o) from Operation o where o.state=:s", Long.class)
                  .setParameter("s", State.EN_ATTENTE)
                  .getSingleResult();
          // Agrégats corrélés : ne charge pas tous les prêts en mémoire.
          String unpaid =
              "round(o.amount * (1 + o.interest / 100.0),0) - coalesce((select sum(r.amount) from"
                  + " Operation r where r.loan.id=o.id and r.state=:s),0)";
          long overdue =
              em.createQuery(
                      "select count(o) from Operation o where o.kind=:k and o.state=:s and"
                          + " o.dueDate<:d and "
                          + unpaid
                          + ">0",
                      Long.class)
                  .setParameter("k", Kind.PRET)
                  .setParameter("s", State.VALIDEE)
                  .setParameter("d", LocalDate.now())
                  .getSingleResult();
          BigDecimal principal =
              sum(em, "o.kind=:k and o.state=:s", Map.of("k", Kind.PRET, "s", State.VALIDEE));
          BigDecimal interest =
              em.createQuery(
                      "select coalesce(sum(round(o.amount*o.interest/100,0)),0) from Operation o"
                          + " where o.kind=:k and o.state=:s",
                      BigDecimal.class)
                  .setParameter("k", Kind.PRET)
                  .setParameter("s", State.VALIDEE)
                  .getSingleResult();
          BigDecimal outstanding =
              principal
                  .add(interest)
                  .subtract(
                      sum(
                          em,
                          "o.kind=:k and o.state=:s",
                          Map.of("k", Kind.REMBOURSEMENT, "s", State.VALIDEE)));
          List<BigDecimal> deposits = new ArrayList<>(), withdrawals = new ArrayList<>();
          List<String> months = new ArrayList<>();
          for (int i = 5; i >= 0; i--) {
            LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(i);
            months.add(
                start.format(
                    java.time.format.DateTimeFormatter.ofPattern("MMM yy", Locale.FRENCH)));
            for (Kind k : new Kind[] {Kind.DEPOT, Kind.RETRAIT}) {
              BigDecimal amount =
                  sum(
                      em,
                      "o.state=:s and o.kind=:k and o.created>=:a and o.created<:b",
                      Map.of(
                          "s",
                          State.VALIDEE,
                          "k",
                          k,
                          "a",
                          start.atStartOfDay(),
                          "b",
                          start.plusMonths(1).atStartOfDay()));
              (k == Kind.DEPOT ? deposits : withdrawals).add(amount);
            }
          }
          List<Operation> recent =
              em.createQuery(
                      "from Operation o join fetch o.member order by o.id desc", Operation.class)
                  .setMaxResults(6)
                  .getResultList();
          return new Stats(
              members,
              savings,
              month,
              pending,
              overdue,
              outstanding,
              deposits,
              withdrawals,
              months,
              recent);
        });
  }

  private static BigDecimal sum(EntityManager em, String where, Map<String, Object> parameters) {
    var q =
        em.createQuery(
            "select coalesce(sum(o.amount),0) from Operation o where " + where, BigDecimal.class);
    parameters.forEach(q::setParameter);
    return q.getSingleResult();
  }

  private static String username(String s) {
    String result = required(s, "Identifiant", 60).toLowerCase(Locale.ROOT);
    if (!result.matches("[a-z0-9._-]{3,60}"))
      throw new IllegalArgumentException(
          "Identifiant : 3 à 60 lettres sans accent, chiffres, points, tirets ou underscores.");
    return result;
  }

  private static String required(String s, String field, int max) {
    if (s == null || s.trim().isBlank() || s.trim().length() > max)
      throw new IllegalArgumentException(
          field + " : champ obligatoire, " + max + " caractères maximum.");
    return s.trim();
  }

  private static BigDecimal money(BigDecimal n) {
    if (n == null
        || n.signum() <= 0
        || n.compareTo(new BigDecimal("999999999999")) > 0
        || n.stripTrailingZeros().scale() > 0)
      throw new IllegalArgumentException(
          "Le montant doit être un entier positif, inférieur à 1 000 milliards F CFA.");
    return n.setScale(0);
  }

  private static void audit(EntityManager em, String actor, String action) {
    Audit a = new Audit();
    a.actor = actor;
    a.action = action;
    em.persist(a);
  }

  private static void email(EntityManager em, String to, String subject, String body) {
    Email e = new Email();
    e.recipient = to;
    e.subject = subject;
    e.body = body;
    em.persist(e);
  }
}
