package sn.microgest.model;

import jakarta.persistence.*;
import java.time.*;
import sn.microgest.model.Domain.*;

@Entity(name = "Adherent")
@Table(
    name = "members",
    indexes = {@Index(name = "idx_members_agency_status", columnList = "agency_id,status")})
public class Adherent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true, length = 30)
  public String number;

  @Column(nullable = false, length = 120)
  public String fullName;

  @Column(nullable = false, unique = true, length = 25)
  public String phone;

  @Column(nullable = false, length = 150)
  public String email;

  @Column(nullable = false, length = 250)
  public String address;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public AdherentStatus status = AdherentStatus.ACTIF;

  @ManyToOne(optional = false)
  @JoinColumn(name = "agency_id")
  public Agency agency;

  @Column(nullable = false)
  public LocalDate joined = LocalDate.now();

  @Version public long version;

  @Override
  public String toString() {
    return number + " · " + fullName;
  }
}
