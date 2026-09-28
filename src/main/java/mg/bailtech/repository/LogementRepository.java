package mg.bailtech.repository;

import java.util.List;
import java.util.Optional;

import mg.bailtech.model.Logement;
import mg.bailtech.model.StatutContrat;
import mg.bailtech.model.TypeCompteurJirama;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Requêtes de recherche sur la table {@code logement}.
 * <p>
 * Les listes déroulantes du générateur de contrat n'offrent que des biens
 * « disponibles » : see {@link #findDisponiblesPourProprietaire}, qui reproduit
 * la condition de l'index partiel
 * {@code uq_un_seul_contrat_actif_par_logement} ({@code statut_actuel = 'EN_COURS'}).
 */
public interface LogementRepository extends JpaRepository<Logement, Integer> {

    List<Logement> findByProprietaireIdOrderByVilleAscQuartierFokontanyAsc(Integer proprietaireId);

    List<Logement> findByProprietaireIdAndJiramaTypeGestionOrderByVilleAsc(Integer proprietaireId,
                                                                         TypeCompteurJirama typeGestion);

    Optional<Logement> findByCompteurElectriciteNumeroIgnoreCase(String numeroCompteur);

    Optional<Logement> findByCompteurEauNumeroIgnoreCase(String numeroCompteur);

    long countByProprietaireId(Integer proprietaireId);

    long countByProprietaireIdAndJiramaTypeGestion(Integer proprietaireId, TypeCompteurJirama typeGestion);

    /** Recherche par quartier / ville / lot, utilisée par la barre de recherche du tableau de bord. */
    @Query("select l from Logement l where lower(l.adresseLot) like lower(concat('%', :fragment, '%')) "
            + "or lower(l.quartierFokontany) like lower(concat('%', :fragment, '%')) "
            + "or lower(l.ville) like lower(concat('%', :fragment, '%')) "
            + "order by l.ville, l.quartierFokontany, l.adresseLot")
    List<Logement> rechercher(@Param("fragment") String fragment);

    /**
     * Biens du bailleur dépourvus de contrat {@code EN_COURS} : ce sont les seuls
     * logements proposables à la sélection dans le formulaire de contrat.
     * <p>
     * Le sous-select évalue l'existence d'un contrat actif par logement ; il
     * s'appuie donc exactement sur la même prédicat que l'index unique partiel.
     */
    @Query("select l from Logement l where l.proprietaire.id = :proprietaireId "
            + "and not exists (select c.id from ContratDeBail c "
            + "where c.logement = l and c.statutActuel = :statutActif) "
            + "order by l.ville, l.quartierFokontany, l.adresseLot")
    List<Logement> findDisponiblesPourProprietaire(@Param("proprietaireId") Integer proprietaireId,
                                                   @Param("statutActif") StatutContrat statutActif);

    /** Raccourci : tous les biens du bailleur dont la location n'est pas en cours. */
    default List<Logement> findDisponiblesPourProprietaire(Integer proprietaireId) {
        return findDisponiblesPourProprietaire(proprietaireId, StatutContrat.EN_COURS);
    }

    /**
     * Charge le propriétaire dans la même requête (fetch join) : indispensable
     * pour afficher le nom du bailleur dans une vue, car
     * {@code spring.jpa.open-in-view=false} interdit d'ouvrir une session
     * paresseuse pendant le rendu Thymeleaf.
     */
    @Query("select l from Logement l join fetch l.proprietaire where l.id = :id")
    Optional<Logement> findByIdAvecProprietaire(@Param("id") Integer id);
}
