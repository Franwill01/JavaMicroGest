package sn.microgest.service;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import sn.microgest.config.Database;
import sn.microgest.model.Email;

public final class MailService {
  private final Database db;
  private final Properties config = new Properties();

  public MailService(Database db) {
    this.db = db;
    Path p = sn.microgest.config.Settings.home().resolve("config/application.properties");
    if (Files.exists(p))
      try (var in = Files.newInputStream(p)) {
        config.load(in);
      } catch (Exception e) {
        throw new IllegalStateException("Configuration email illisible.", e);
      }
  }

  private String setting(String key, String fallback) {
    String value = System.getenv("MICROGEST_" + key.toUpperCase(Locale.ROOT).replace('.', '_'));
    return value == null ? config.getProperty(key, fallback) : value;
  }

  public boolean configured() {
    return !setting("smtp.host", "").isBlank() && !setting("smtp.from", "").isBlank();
  }

  public synchronized int sendPending() {
    if (!configured()) return 0;
    List<Email> pending =
        db.tx(
            em ->
                em.createQuery(
                        "from Email e where e.state='EN_ATTENTE' and e.attempts<3 order by e.id",
                        Email.class)
                    .setMaxResults(10)
                    .getResultList());
    int count = 0;
    for (Email email : pending) {
      if (email.recipient.endsWith(".invalid")) {
        db.tx(
            em -> {
              Email e = em.find(Email.class, email.id);
              e.state = "ECHEC";
              e.error = "Adresse de démonstration : envoi désactivé.";
              e.attempts = 3;
              return null;
            });
        continue;
      }
      try {
        Properties p = new Properties();
        p.setProperty("mail.smtp.host", setting("smtp.host", ""));
        p.setProperty("mail.smtp.port", setting("smtp.port", "587"));
        p.setProperty("mail.smtp.starttls.enable", "true");
        p.setProperty("mail.smtp.starttls.required", "true");
        p.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        p.setProperty("mail.smtp.connectiontimeout", "8000");
        p.setProperty("mail.smtp.timeout", "10000");
        p.setProperty("mail.smtp.writetimeout", "10000");
        String user = setting("smtp.user", "");
        p.setProperty("mail.smtp.auth", Boolean.toString(!user.isBlank()));
        Session session =
            Session.getInstance(
                p,
                new Authenticator() {
                  protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, setting("smtp.password", ""));
                  }
                });
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(setting("smtp.from", "")));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email.recipient));
        msg.setSubject(email.subject, "UTF-8");
        msg.setText(email.body, "UTF-8");
        msg.setHeader("X-MicroGest-Reference", String.valueOf(email.id));
        Transport.send(msg);
        db.tx(
            em -> {
              Email e = em.find(Email.class, email.id);
              e.state = "ENVOYE";
              e.sent = LocalDateTime.now();
              e.attempts++;
              e.error = null;
              return null;
            });
        count++;
      } catch (Exception ex) {
        db.tx(
            em -> {
              Email e = em.find(Email.class, email.id);
              e.attempts++;
              e.state = e.attempts >= 3 ? "ECHEC" : "EN_ATTENTE";
              e.error = "Le message n’a pas pu être envoyé. Réessayez plus tard.";
              return null;
            });
      }
    }
    return count;
  }
}
