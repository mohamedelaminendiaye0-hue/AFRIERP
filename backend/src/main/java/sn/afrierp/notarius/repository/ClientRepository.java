package sn.afrierp.notarius.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.afrierp.notarius.domain.Client;

public interface ClientRepository extends JpaRepository<Client, String> {

    Optional<Client> findFirstByIdentifiantIgnoreCase(String identifiant);

    Optional<Client> findFirstByNomIgnoreCase(String nom);

    List<Client> findAllByOrderByNom();
}
