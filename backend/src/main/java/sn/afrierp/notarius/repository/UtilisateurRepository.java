package sn.afrierp.notarius.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.Utilisateur;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, String> {

    List<Utilisateur> findByRoleAndActifTrueOrderByNom(Role role);
}
