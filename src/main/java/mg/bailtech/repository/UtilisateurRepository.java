package mg.bailtech.repository;

import java.util.List;
import java.util.Optional;

import mg.bailtech.model.StatutContrat;
import mg.bailtech.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Requêtes de recherche sur la table {@code utilisateur}.
 * <p>
 * Le modèle v1 n'a pas de colonne de rôle : un bailleur est un utilisateur
 * possédant au moins un logement ({@link #findBailleurs()}) et un locataire est
 * un utilisateur porteur d'au moins un contrat ({@link #findLocataires()}).
 */
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Integer> {

    // ------------------------------------------------------------------
    // Recherche par identité (cas d'usage principal : saisie de la CIN)
    // ------------------------------------------------------------------

    /** Recherche exacte par numéro de CIN (colonne unique en base). */
    Optional<Utilisateur> findByCinNumero(String cinNumero);

    /** Idem, insensible à la casse et aux espaces accidentels. */
    @Query("select u from Utilisateur u where upper(trim(u.cinNumero)) = upper(trim(:cinNumero))")
    Optional<Utilisateur> findByCinNumeroNormalise(@Param("cinNumero") String cinNumero);

    /** Recherche exacte par email (colonne unique en base). */
    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    boolean existsByCinNumero(String cinNumero);

    boolean existsByEmailIgnoreCase(String email);

    // ------------------------------------------------------------------
    // Listes et recherches libres
    // ------------------------------------------------------------------

    List<Utilisateur> findAllByOrderByNomAscPrenomAsc();

    List<Utilisateur> findByNomContainingIgnoreCaseOrderByNomAsc(String fragment);

    List<Utilisateur> findByPrenomContainingIgnoreCaseOrderByNomAsc(String fragment);

    /** Recherche plein texte simplifiée sur le nom ou le prénom (barre de recherche). */
    @Query("select u from Utilisateur u where lower(u.nom) like lower(concat('%', :fragment, '%')) "
            + "or lower(coalesce(u.prenom, '')) like lower(concat('%', :fragment, '%')) "
            + "or u.cinNumero like concat('%', :fragment, '%') "
            + "or lower(u.email) like lower(concat('%', :fragment, '%')) "
            + "order by u.nom, u.prenom")
    List<Utilisateur> rechercher(@Param("fragment") String fragment);

    /** Liste des propriétaires, dédupliquée depuis les logements enregistrés. */
    @Query("select distinct l.proprietaire from Logement l order by l.proprietaire.nom, l.proprietaire.prenom")
    List<Utilisateur> findBailleurs();

    /** Liste des preneurs de bail, dédupliquée depuis les contrats enregistrés. */
    @Query("select distinct c.locataire from ContratDeBail c order by c.locataire.nom, c.locataire.prenom")
    List<Utilisateur> findLocataires();

    Utilisateur findFirstByOrderByIdAsc();

    // ------------------------------------------------------------------
    // Sélections des formulaires de création de contrat
    // ------------------------------------------------------------------

    /**
     * Locataires « disponibles » pour un bailleur donné : ni le bailleur lui-même,
     * ni un preneur déjà associé à un contrat {@code EN_COURS}.
     * <p>
     * Le sous-select porte sur {@code c.statutActuel = :statutActif}, ce qui
     * reproduit la condition de l'index partiel
     * {@code uq_un_seul_contrat_actif_par_logement} : un locataire dont le bail
     * est terminé ou résilié redevient sélectionnable.
     */
    @Query("select u from Utilisateur u where u.id <> :bailleurId "
            + "and not exists (select c.id from ContratDeBail c "
            + "where c.locataire = u and c.statutActuel = :statutActif) "
            + "order by u.nom, u.prenom")
    List<Utilisateur> findLocatairesDisponibles(@Param("bailleurId") Integer bailleurId,
                                                @Param("statutActif") StatutContrat statutActif);

    /** Locataires disponibles, la location active étant gérée par l'index partiel. */
    default List<Utilisateur> findLocatairesDisponibles(Integer bailleurId) {
        return findLocatairesDisponibles(bailleurId, StatutContrat.EN_COURS);
    }
}
