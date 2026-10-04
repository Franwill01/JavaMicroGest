package sn.microgest.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import sn.microgest.model.Domain.*;

@Entity(name = "Operation")
@Table(
    name = "operations",
    indexes = {
      @Index(name = "idx_operations_member_state", columnList = "member_id,state"),
      @Index(name = "idx_operations_date", columnList = "created")
    })
public class Operation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "member_id")
  public Adherent member;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Kind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public State state;

  @Column(nullable = false, precision = 18, scale = 0)
  public BigDecimal amount;

  @Column(nullable = false, precision = 5, scale = 2)
  public BigDecimal interest = BigDecimal.ZERO;

  public LocalDate dueDate;

  @ManyToOne
  @JoinColumn(name = "loan_id")
  public Operation loan;

  @Column(nullable = false)
  public LocalDateTime created = LocalDateTime.now();

  @Column(length = 250)
  public String note = "";

  @Column(nullable = false, length = 60)
  public String author;

  @Version public long version;
}
