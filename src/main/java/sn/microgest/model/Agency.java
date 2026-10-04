package sn.microgest.model;

import jakarta.persistence.*;
import java.time.*;
import sn.microgest.model.Domain.*;

@Entity(name = "Agency")
@Table(name = "agencies")
public class Agency {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true, length = 100)
  public String name;

  @Column(nullable = false, length = 150)
  public String address;

  public Agency() {}

  public Agency(String name, String address) {
    this.name = name;
    this.address = address;
  }

  @Override
  public String toString() {
    return name;
  }
}
