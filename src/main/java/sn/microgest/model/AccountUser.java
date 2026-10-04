package sn.microgest.model;

import jakarta.persistence.*;
import java.time.*;
import sn.microgest.model.Domain.*;

@Entity(name = "AccountUser")
@Table(name = "account_users")
public class AccountUser {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true, length = 60)
  public String username;

  @Column(nullable = false, length = 120)
  public String fullName;

  @Column(nullable = false, length = 300)
  public String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Role role;

  @Column(nullable = false)
  public boolean active = true;

  @Version public long version;
}
