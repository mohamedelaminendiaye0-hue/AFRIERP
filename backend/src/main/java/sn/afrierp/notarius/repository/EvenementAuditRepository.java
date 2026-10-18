package sn.afrierp.notarius.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.afrierp.notarius.domain.EvenementAudit;

public interface EvenementAuditRepository extends JpaRepository<EvenementAudit, Long> {

    List<EvenementAudit> findByDossierIdOrderByHorodatageDescIdDesc(String dossierId);
}
