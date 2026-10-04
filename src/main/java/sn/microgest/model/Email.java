package sn.microgest.model;

import jakarta.persistence.*;
import java.time.*;
import sn.microgest.model.Domain.*;

@Entity(name = "Email")
@Table(name = "email_outbox")
public class Email {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 150)
  public String recipient;

  @Column(nullable = false, length = 200)
  public String subject;

  @Column(nullable = false, length = 4000)
  public String body;

  @Column(nullable = false)
  public LocalDateTime created = LocalDateTime.now();

  public LocalDateTime sent;

  @Column(nullable = false, length = 30)
  public String state = "EN_ATTENTE";

  public int attempts;

  @Column(length = 200)
  public String error;
}
