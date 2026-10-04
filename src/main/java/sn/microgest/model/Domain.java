package sn.microgest.model;

public final class Domain {
  private Domain() {}

  public enum Role {
    ADMIN,
    AGENT,
    SUPERVISEUR
  }

  public enum AdherentStatus {
    ACTIF,
    SUSPENDU
  }

  public enum Kind {
    DEPOT,
    RETRAIT,
    PRET,
    REMBOURSEMENT
  }

  public enum State {
    EN_ATTENTE,
    VALIDEE,
    ANNULEE
  }
}
