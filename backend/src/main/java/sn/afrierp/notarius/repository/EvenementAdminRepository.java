package sn.afrierp.notarius.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.afrierp.notarius.domain.EvenementAdmin;
import sn.afrierp.notarius.domain.TypeEvenementAdmin;

public interface EvenementAdminRepository extends JpaRepository<EvenementAdmin, Long> {

    List<EvenementAdmin> findAllByOrderByHorodatageDescIdDesc(Pageable page);

    List<EvenementAdmin> findByTypeOrderByHorodatageDescIdDesc(TypeEvenementAdmin type, Pageable page);

    long countByTypeAndHorodatageAfter(TypeEvenementAdmin type, LocalDateTime depuis);
}
