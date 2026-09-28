package mg.bailtech.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.StatutContrat;

public interface ContratDeBailRepository extends JpaRepository<ContratDeBail, Integer> {
    List<ContratDeBail> findByStatutActuel(StatutContrat statut);

    @Query("select c from ContratDeBail c join fetch c.logement l join fetch l.proprietaire "
         + "join fetch c.locataire where c.id = :id")
    Optional<ContratDeBail> findDetailsById(@Param("id") Integer id);

    @Query("select c from ContratDeBail c where c.logement.proprietaire.id = :proprietaireId and c.statutActuel = :statut")
    List<ContratDeBail> findContratsActifsDuBailleur(@Param("proprietaireId") Integer proprietaireId,
                                                     @Param("statut") StatutContrat statut);
}