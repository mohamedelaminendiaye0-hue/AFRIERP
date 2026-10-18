package sn.afrierp.notarius.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.afrierp.notarius.domain.PieceJointe;

public interface PieceJointeRepository extends JpaRepository<PieceJointe, Long> {

    List<PieceJointe> findByDossierIdOrderByDeposeLeDescIdDesc(String dossierId);

    List<PieceJointe> findByDossierIdIn(List<String> dossierIds);
}
