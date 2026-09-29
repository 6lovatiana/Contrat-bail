package mg.bailtech.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.StatutContrat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Requêtes de recherche sur la table {@code contrat_de_bail}.
 * <p>
 * Les méthodes marquées {@link EntityGraph} préchargent les associations
 * {@code logement}, {@code locataire} et {@code logement.proprietaire} : le rendu
 * Thymeleaf se fait hors transaction ({@code open-in-view=false}), toute
 * association paresseuse non préchargée y provoquerait une
 * {@code LazyInitializationException}.
 */
public interface ContratDeBailRepository extends JpaRepository<ContratDeBail, Integer> {

    // ------------------------------------------------------------------
    // Index partiel : un seul contrat EN_COURS par logement
    // ------------------------------------------------------------------

    /**
     * Contrat actif d'un logement, requété en SQL natif pour reproduire mot pour
     * mot le prédicat de l'index
     * {@code uq_un_seul_contrat_actif_par_logement} :
     * {@code WHERE statut_actuel = 'EN_COURS'}.
     * <p>
     * C'est la vérification application qui accompagne l'index : elle permet de
     * renvoyer un message d'erreur lisible au bailleur avant que PostgreSQL ne
     * rejette l'insertion.
     */
    @Query(value = "select * from contrat_de_bail "
            + "where id_logement = :logementId and statut_actuel = 'EN_COURS'", nativeQuery = true)
    Optional<ContratDeBail> findContratActifParLogement(@Param("logementId") Integer logementId);

    /** Nombre de contrats en cours sur un logement : 0 ou 1 si l'index est respecté. */
    @Query(value = "select count(*) from contrat_de_bail "
            + "where id_logement = :logementId and statut_actuel = 'EN_COURS'", nativeQuery = true)
    long countContratsEnCoursParLogement(@Param("logementId") Integer logementId);

    /** Vrai si le logement ne peut pas accueillir un nouveau bail actif. */
    @Query(value = "select case when count(*) > 0 then true else false end from contrat_de_bail "
            + "where id_logement = :logementId and statut_actuel = 'EN_COURS'", nativeQuery = true)
    boolean existsContratActifParLogement(@Param("logementId") Integer logementId);

    // ------------------------------------------------------------------
    // Listes par statut / par partie
    // ------------------------------------------------------------------

    /**
     * Charge un contrat par son identifiant, en préchargeant le propriétaire du
     * bien.
     *
     * <p>Nécessaire pour tout contrôle d'accès : savoir à qui appartient un
     * contrat impose de lire {@code logement.proprietaire}, or ces associations
     * sont paresseuses et la session est fermée avant le rendu
     * ({@code open-in-view=false}). {@code findById} laisserait un proxy non
     * initialisé, dont l'accès lèverait une {@code LazyInitializationException}.
     */
    @EntityGraph(attributePaths = {"logement", "logement.proprietaire", "locataire"})
    @Query("select c from ContratDeBail c where c.id = :id")
    Optional<ContratDeBail> findByIdAvecProprietaire(@Param("id") Integer id);

    @EntityGraph(attributePaths = {"logement", "locataire"})
    List<ContratDeBail> findByStatutActuelOrderByDateDebutDesc(StatutContrat statutActuel);

    @EntityGraph(attributePaths = {"logement", "locataire"})
    List<ContratDeBail> findByLogementIdOrderByDateDebutDesc(Integer logementId);

    @EntityGraph(attributePaths = {"logement", "locataire"})
    List<ContratDeBail> findByLogementIdAndStatutActuelOrderByDateDebutDesc(Integer logementId,
                                                                             StatutContrat statutActuel);

    @EntityGraph(attributePaths = {"logement", "locataire"})
    List<ContratDeBail> findByLocataireIdOrderByDateDebutDesc(Integer locataireId);

    @EntityGraph(attributePaths = {"logement", "locataire"})
    List<ContratDeBail> findByLocataireIdAndStatutActuel(Integer locataireId, StatutContrat statutActuel);

    /**
     * Contrats actifs (EN_COURS) d'un bailleur, identifiés par le propriétaire du
     * bien. Cas d'usage demandé : « récupérer les contrats actifs d'un bailleur ».
     */
    @EntityGraph(attributePaths = {"logement", "locataire"})
    @Query("select c from ContratDeBail c "
            + "where c.logement.proprietaire.id = :proprietaireId and c.statutActuel = :statutActif "
            + "order by c.dateDebut desc")
    List<ContratDeBail> findContratsActifsDuBailleur(@Param("proprietaireId") Integer proprietaireId,
                                                     @Param("statutActif") StatutContrat statutActif);

    /** Même requête, sans passer le statut : la location active est la seule visée. */
    default List<ContratDeBail> findContratsActifsDuBailleur(Integer proprietaireId) {
        return findContratsActifsDuBailleur(proprietaireId, StatutContrat.EN_COURS);
    }

    /** Tous les contrats d'un bailleur, du plus récent au plus ancien. */
    @EntityGraph(attributePaths = {"logement", "locataire"})
    @Query("select c from ContratDeBail c "
            + "where c.logement.proprietaire.id = :proprietaireId order by c.dateDebut desc")
    List<ContratDeBail> findContratsDuBailleur(@Param("proprietaireId") Integer proprietaireId);

    /** Les N derniers contrats du bailleur : bloc « Contrats récents » du tableau de bord. */
    @EntityGraph(attributePaths = {"logement", "locataire"})
    List<ContratDeBail> findTop5ByLogement_Proprietaire_IdOrderByDateDebutDesc(Integer proprietaireId);

    @EntityGraph(attributePaths = {"logement", "locataire"})
    List<ContratDeBail> findByLogement_Proprietaire_IdAndStatutActuel(Integer proprietaireId,
                                                                      StatutContrat statutActuel);

    // ------------------------------------------------------------------
    // Filtres et research
    // ------------------------------------------------------------------

    @EntityGraph(attributePaths = {"logement", "locataire"})
    @Query("select c from ContratDeBail c "
            + "where c.logement.proprietaire.id = :proprietaireId and ("
            + "lower(c.locataire.nom) like lower(concat('%', :fragment, '%')) "
            + "or lower(coalesce(c.locataire.prenom, '')) like lower(concat('%', :fragment, '%')) "
            + "or c.locataire.cinNumero like concat('%', :fragment, '%') "
            + "or lower(c.logement.adresseLot) like lower(concat('%', :fragment, '%')) "
            + "or lower(c.logement.ville) like lower(concat('%', :fragment, '%'))) "
            + "order by c.dateDebut desc")
    List<ContratDeBail> rechercherContratsDuBailleur(@Param("proprietaireId") Integer proprietaireId,
                                                      @Param("fragment") String fragment);

    // ------------------------------------------------------------------
    // Agrégats du tableau de bord
    // ------------------------------------------------------------------

    long countByLogement_Proprietaire_IdAndStatutActuel(Integer proprietaireId, StatutContrat statutActuel);

    long countByStatutActuel(StatutContrat statutActuel);

    /** Somme des loyers des contrats actifs du bailleur (carte « Total des loyers »). */
    @Query("select sum(c.montantLoyerMga) from ContratDeBail c "
            + "where c.logement.proprietaire.id = :proprietaireId and c.statutActuel = :statutActif")
    BigDecimal sumMontantLoyerDuBailleur(@Param("proprietaireId") Integer proprietaireId,
                                         @Param("statutActif") StatutContrat statutActif);

    /** Contrats dont la période de validité est traversée par la date donnée. */
    @EntityGraph(attributePaths = {"logement", "locataire"})
    @Query("select c from ContratDeBail c where c.logement.proprietaire.id = :proprietaireId "
            + "and c.dateDebut <= :date and c.dateFin >= :date order by c.dateDebut desc")
    List<ContratDeBail> findContratsEnVigueur(@Param("proprietaireId") Integer proprietaireId,
                                              @Param("date") LocalDate date);
}
