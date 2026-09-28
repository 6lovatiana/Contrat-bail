package mg.bailtech.repository;

import mg.bailtech.model.PaiementLoyer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaiementLoyerRepository extends JpaRepository<PaiementLoyer, Integer> {
    List<PaiementLoyer> findByContratIdOrderByPeriodeAnneeDescPeriodeMoisDesc(Integer contratId);
}