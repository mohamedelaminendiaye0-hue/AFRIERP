package sn.afrierp.notarius.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.afrierp.notarius.domain.MouvementComptable;

public interface MouvementComptableRepository extends JpaRepository<MouvementComptable, Long> {

    List<MouvementComptable> findByDossier_IdOrderByDateOperationAscIdAsc(String dossierId);

    List<MouvementComptable> findAllByOrderByDateOperationAscIdAsc();
}
