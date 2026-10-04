package sn.microgest.model;

import jakarta.persistence.*;
import java.time.*;
import sn.microgest.model.Domain.*;

@Entity(name = "Audit")
@Table(name = "audit_events")
public class Audit {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public LocalDateTime created = LocalDateTime.now();

  @Column(nullable = false, length = 60)
  public String actor;

  @Column(nullable = false, length = 500)
  public String action;
}
