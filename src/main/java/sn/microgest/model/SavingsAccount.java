package sn.microgest.model;

import jakarta.persistence.*;
import java.time.*;
import sn.microgest.model.Domain.*;

@Entity(name = "SavingsAccount")
@Table(name = "savings_accounts")
public class SavingsAccount {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @OneToOne(optional = false)
  @JoinColumn(name = "member_id", unique = true)
  public Adherent member;

  @Column(nullable = false, unique = true, length = 40)
  public String number;

  @Column(nullable = false)
  public LocalDate opened = LocalDate.now();
}
