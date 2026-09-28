package mg.bailtech.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mg.bailtech.model.Logement;
import mg.bailtech.model.StatutContrat;

public interface LogementRepository extends JpaRepository<Logement, Integer> {
    List<Logement> findByProprietaireId(Integer proprietaireId);

        @Query("select l from Logement l where l.proprietaire.id = :proprietaireId "
            + "and not exists (select c.id from ContratDeBail c where c.logement = l and c.statutActuel = :statut)")
        List<Logement> findDisponiblesPourProprietaire(@Param("proprietaireId") Integer proprietaireId,
                                             @Param("statut") StatutContrat statut);
}