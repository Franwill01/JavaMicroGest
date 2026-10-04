package sn.microgest.config;

import jakarta.persistence.*;
import java.util.function.Function;
import org.hibernate.cfg.Configuration;
import sn.microgest.model.*;
import sn.microgest.model.Domain.*;

public final class Database implements AutoCloseable {
  private final EntityManagerFactory factory;

  public Database(String url) {
    Configuration c = new Configuration();
    for (Class<?> type :
        new Class<?>[] {
          Agency.class,
          AccountUser.class,
          Adherent.class,
          SavingsAccount.class,
          Operation.class,
          Audit.class,
          Email.class
        }) c.addAnnotatedClass(type);
    c.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
    c.setProperty("hibernate.connection.url", url);
    c.setProperty("hibernate.connection.username", "sa");
    c.setProperty("hibernate.connection.password", "");
    c.setProperty("hibernate.hbm2ddl.auto", "validate");
    c.setProperty("hibernate.show_sql", "false");
    c.setProperty("hibernate.connection.pool_size", "6");
    migrate(url);
    factory = c.buildSessionFactory();
  }

  private static void migrate(String url) {
    try (var conn = java.sql.DriverManager.getConnection(url, "sa", "");
        var reader =
            new java.io.InputStreamReader(
                java.util.Objects.requireNonNull(
                    Database.class.getResourceAsStream("/db/schema.sql")),
                java.nio.charset.StandardCharsets.UTF_8)) {
      org.h2.tools.RunScript.execute(conn, reader);
    } catch (Exception e) {
      throw new IllegalStateException("Impossible d'initialiser la base de données.", e);
    }
  }

  public <T> T tx(Function<EntityManager, T> work) {
    try (EntityManager em = factory.createEntityManager()) {
      var tx = em.getTransaction();
      tx.begin();
      try {
        T result = work.apply(em);
        tx.commit();
        return result;
      } catch (RuntimeException e) {
        if (tx.isActive()) tx.rollback();
        throw e;
      }
    }
  }

  @Override
  public void close() {
    factory.close();
  }
}
