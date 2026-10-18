package sn.afrierp.notarius.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injecte l'utilisateur qui agit, identifie par l'en-tete {@code X-Utilisateur}.
 *
 * <p>Environnement de demonstration : l'identite est choisie dans l'interface.
 * En production, elle proviendra du jeton d'authentification (OIDC / JWT) ;
 * les controles de role, eux, sont deja appliques cote serveur.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface UtilisateurCourant {
}
