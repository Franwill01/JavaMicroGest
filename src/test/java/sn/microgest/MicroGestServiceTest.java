package sn.microgest;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import sn.microgest.config.Database;
import sn.microgest.model.*;
import sn.microgest.model.Domain.*;
import sn.microgest.repository.Repository.*;
import sn.microgest.service.*;

class MicroGestServiceTest {
  Database db;
  MicroGestService service;
  long agency;
  Adherent member;
  @TempDir Path temp;

  @BeforeEach
  void setup() {
    db = new Database("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    service = new MicroGestService(db);
    service.setup("admin", "Administrateur", "Admin-test-2026", false);
    service.login("admin", "Admin-test-2026");
    agency = service.agencies().get(0).id;
    member = create("Awa Test", "771234567");
  }

  @AfterEach
  void close() {
    db.close();
  }

  Adherent create(String name, String phone) {
    return service.saveMember(
        null, 0, name, phone, "test@example.invalid", "Dakar", agency, AdherentStatus.ACTIF);
  }

  Operation operation(Kind kind, long amount, Long loan) {
    return service.saveOperation(
        new MicroGestService.OperationInput(
            null,
            0,
            member.id,
            kind,
            BigDecimal.valueOf(amount),
            new BigDecimal("10"),
            LocalDate.now().plusMonths(2),
            loan,
            "Test"));
  }

  Filter filter(
      String text, String state, Long agency, LocalDate from, LocalDate to, int page, int size) {
    return new Filter(text, state, agency, from, to, page, size);
  }

  @Test
  void passwordHashAndLogin() {
    AccountUser u = service.current();
    assertNotEquals("Admin-test-2026", u.passwordHash);
    assertTrue(Passwords.verify("Admin-test-2026", u.passwordHash));
    assertFalse(Passwords.verify("wrong", u.passwordHash));
    assertThrows(IllegalArgumentException.class, () -> service.login("admin", "wrong"));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.setup("another", "Another", "Password123!", false));
    service.changePassword("Admin-test-2026", "Another-test-2026");
    service.logout();
    assertThrows(SecurityException.class, service::stats);
    assertNotNull(service.login("admin", "Another-test-2026"));
  }

  @Test
  void combinedSearchFilterPagination() {
    for (int i = 0; i < 24; i++) create("Awa Pagination " + i, "78000" + String.format("%04d", i));
    Page first =
        service.page(
            View.MEMBERS, filter("awa", "ACTIF", agency, LocalDate.now(), LocalDate.now(), 0, 10));
    Page second =
        service.page(
            View.MEMBERS, filter("awa", "ACTIF", agency, LocalDate.now(), LocalDate.now(), 1, 10));
    assertEquals(25, first.total());
    assertEquals(10, first.rows().size());
    assertEquals(3, first.pages());
    assertNotEquals(((Adherent) first.rows().get(0)).id, ((Adherent) second.rows().get(0)).id);
    assertEquals(
        5,
        service
            .page(View.MEMBERS, filter("awa", "ACTIF", agency, null, null, 2, 10))
            .rows()
            .size());
    assertEquals(
        0,
        service.page(View.MEMBERS, filter("awa", "SUSPENDU", agency, null, null, 0, 10)).total());
    assertEquals(
        0,
        service.page(View.MEMBERS, filter("%' or 1=1 --", null, null, null, null, 0, 10)).total());
    assertEquals(0, service.page(View.MEMBERS, filter("%", null, null, null, null, 0, 10)).total());
    assertThrows(
        IllegalArgumentException.class,
        () -> filter("", null, null, LocalDate.now(), LocalDate.now().minusDays(1), 0, 10));
  }

  @Test
  void memberValidationVersionAndDeletion() {
    assertThrows(IllegalArgumentException.class, () -> create("Duplicate", member.phone));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.saveMember(
                null, 0, "Bad", "123", "bad", "Dakar", agency, AdherentStatus.ACTIF));
    Adherent edited =
        service.saveMember(
            member.id,
            member.version,
            "Awa Modifiée",
            member.phone,
            member.email,
            member.address,
            agency,
            AdherentStatus.SUSPENDU);
    assertEquals(AdherentStatus.SUSPENDU, edited.status);
    assertThrows(IllegalArgumentException.class, () -> operation(Kind.DEPOT, 1000, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.saveMember(
                member.id,
                member.version,
                "Stale",
                member.phone,
                member.email,
                member.address,
                agency,
                AdherentStatus.ACTIF));
    service.deleteMember(member.id);
    assertEquals(0, service.page(View.MEMBERS, filter("", null, null, null, null, 0, 10)).total());
  }

  @Test
  void depositsWithdrawalsAndReversals() {
    Operation deposit = operation(Kind.DEPOT, 10000, null);
    Operation withdrawal = operation(Kind.RETRAIT, 4000, null);
    assertEquals(new BigDecimal("6000"), service.balance(member.id));
    assertThrows(IllegalArgumentException.class, () -> operation(Kind.RETRAIT, 6001, null));
    assertThrows(IllegalArgumentException.class, () -> service.cancel(deposit.id));
    assertThrows(IllegalArgumentException.class, () -> service.deleteMember(member.id));
    service.cancel(withdrawal.id);
    assertEquals(new BigDecimal("10000"), service.balance(member.id));
    service.cancel(deposit.id);
    assertEquals(0, service.balance(member.id).signum());
    assertThrows(IllegalArgumentException.class, () -> operation(Kind.DEPOT, 0, null));
    assertThrows(IllegalArgumentException.class, () -> service.deletePending(deposit.id));
  }

  @Test
  void loanApprovalRepaymentAndStatistics() {
    Operation loan = operation(Kind.PRET, 100000, null);
    assertEquals(State.EN_ATTENTE, loan.state);
    assertEquals(1, service.stats().pending());
    assertThrows(
        IllegalArgumentException.class, () -> operation(Kind.REMBOURSEMENT, 1000, loan.id));
    service.approve(loan.id);
    assertThrows(IllegalArgumentException.class, () -> service.approve(loan.id));
    assertEquals(new BigDecimal("110000"), service.remaining(loan.id));
    Operation repayment = operation(Kind.REMBOURSEMENT, 60000, loan.id);
    assertEquals(new BigDecimal("50000"), service.remaining(loan.id));
    assertThrows(
        IllegalArgumentException.class, () -> operation(Kind.REMBOURSEMENT, 50001, loan.id));
    assertThrows(IllegalArgumentException.class, () -> service.cancel(loan.id));
    assertEquals(0, service.balance(member.id).signum());
    assertEquals(0, service.stats().outstanding().compareTo(new BigDecimal("50000")));
    service.cancel(repayment.id);
    assertEquals(new BigDecimal("110000"), service.remaining(loan.id));
    service.cancel(loan.id);
    assertEquals(0, service.stats().outstanding().signum());
  }

  @Test
  void rolesAndRevocation() {
    service.saveUser(null, 0, "agent", "Agent Test", "Agent-test-2026", Role.AGENT, true);
    service.saveUser(
        null, 0, "supervisor", "Superviseur Test", "Super-test-2026", Role.SUPERVISEUR, true);
    Operation loan = operation(Kind.PRET, 5000, null);
    service.login("agent", "Agent-test-2026");
    assertThrows(SecurityException.class, () -> service.approve(loan.id));
    assertThrows(
        SecurityException.class,
        () -> service.page(View.USERS, filter("", null, null, null, null, 0, 10)));
    operation(Kind.DEPOT, 5000, null);
    service.login("supervisor", "Super-test-2026");
    service.approve(loan.id);
    assertThrows(SecurityException.class, () -> create("Forbidden", "779998888"));
    assertThrows(SecurityException.class, () -> operation(Kind.RETRAIT, 1, null));
    service.login("admin", "Admin-test-2026");
    AccountUser agent =
        (AccountUser)
            service.page(View.USERS, filter("agent", null, null, null, null, 0, 10)).rows().get(0);
    service.saveUser(
        agent.id, agent.version, agent.username, agent.fullName, "", agent.role, false);
    assertThrows(IllegalArgumentException.class, () -> service.login("agent", "Agent-test-2026"));
    AccountUser admin = service.current();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.saveUser(
                admin.id, admin.version, admin.username, admin.fullName, "", Role.AGENT, true));
  }

  @Test
  void emailsAreQueued() {
    operation(Kind.DEPOT, 12345, null);
    Page outbox = service.page(View.EMAILS, filter("", null, null, null, null, 0, 10));
    assertEquals(2, outbox.total());
  }

  @Test
  void allListQueriesAndAgencyIntegrity() {
    for (View view : View.values())
      assertNotNull(service.page(view, filter("", null, null, null, null, 0, 10)));
    assertThrows(IllegalArgumentException.class, () -> service.deleteAgency(agency));
    service.saveAgency(null, "Thiès", "Centre");
    assertEquals(2, service.agencies().size());
    assertTrue(service.accountInfo(member.id).number().startsWith("EP-"));
    assertEquals(1, service.stats().members());
    assertEquals(6, service.stats().months().size());
  }

  @Test
  void concurrentWithdrawalsCannotOverdraw() throws Exception {
    operation(Kind.DEPOT, 10000, null);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var work =
          (java.util.concurrent.Callable<Boolean>)
              () -> {
                try {
                  operation(Kind.RETRAIT, 7000, null);
                  return true;
                } catch (IllegalArgumentException ex) {
                  return false;
                }
              };
      var results = executor.invokeAll(List.of(work, work));
      assertEquals(
          1,
          results.stream()
              .filter(
                  f -> {
                    try {
                      return f.get();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  })
              .count());
      assertEquals(new BigDecimal("3000"), service.balance(member.id));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void persistenceSurvivesRestart() {
    String url = "jdbc:h2:file:" + temp.resolve("persistent").toString().replace('\\', '/');
    try (Database first = new Database(url)) {
      MicroGestService s = new MicroGestService(first);
      s.setup("persist", "Persist Test", "Persist-test-2026", false);
    }
    try (Database second = new Database(url)) {
      MicroGestService s = new MicroGestService(second);
      assertFalse(s.needsSetup());
      assertNotNull(s.login("persist", "Persist-test-2026"));
    }
  }

  @Test
  void overdueUsesRoundedContractTotal() {
    Operation loan =
        service.saveOperation(
            new MicroGestService.OperationInput(
                null,
                0,
                member.id,
                Kind.PRET,
                new BigDecimal("101"),
                new BigDecimal("0.49"),
                LocalDate.now().plusDays(2),
                null,
                "Arrondi"));
    service.approve(loan.id);
    operation(Kind.REMBOURSEMENT, 101, loan.id);
    db.tx(
        em -> {
          em.find(Operation.class, loan.id).dueDate = LocalDate.now().minusDays(1);
          return null;
        });
    assertEquals(BigDecimal.ZERO, service.remaining(loan.id));
    assertEquals(0, service.stats().overdue());
  }
}
