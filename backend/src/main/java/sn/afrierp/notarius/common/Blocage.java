package sn.afrierp.notarius.common;

/** Motif metier ayant refuse une operation, restitue tel quel a l'interface. */
public record Blocage(String code, String message) {
}
