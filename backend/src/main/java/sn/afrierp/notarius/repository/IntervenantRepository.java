package sn.afrierp.notarius.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.afrierp.notarius.domain.Intervenant;

public interface IntervenantRepository extends JpaRepository<Intervenant, Long> {

    List<Intervenant> findByClient_Id(String clientId);

    List<Intervenant> findByDossier_IdIn(List<String> dossierIds);
}
