package sn.afrierp.notarius.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.afrierp.notarius.domain.RendezVous;

public interface RendezVousRepository extends JpaRepository<RendezVous, Long> {

    List<RendezVous> findByDebutBetweenOrderByDebut(LocalDateTime du, LocalDateTime au);

    List<RendezVous> findByDossier_IdOrderByDebutDesc(String dossierId);
}
