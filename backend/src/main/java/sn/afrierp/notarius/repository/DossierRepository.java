package sn.afrierp.notarius.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.afrierp.notarius.domain.Dossier;

public interface DossierRepository extends JpaRepository<Dossier, String> {

    List<Dossier> findAllByOrderByDateOuvertureDesc();

    long countByNumeroRepertoireStartingWith(String prefixe);

    List<Dossier> findByNumeroRepertoireNotNullOrderByNumeroRepertoireDesc();
}
