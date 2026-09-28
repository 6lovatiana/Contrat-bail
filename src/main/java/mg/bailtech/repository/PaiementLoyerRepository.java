package mg.bailtech.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import mg.bailtech.model.PaiementLoyer;
import mg.bailtech.model.StatutPaiement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Requêtes de recherche sur la table {@code paiement_loyer}.
 * <p>
 * Comme pour les contrats, les méthodes destinées au rendu préchargent la
 * hiérarchie {@code contrat -> locataire / logement} via {@link EntityGraph}.
 */
public interface PaiementLoyerRepository extends JpaRepository<PaiementLoyer, Integer> {

    // ------------------------------------------------------------------
    // Historique d'un contrat (contrainte uq_paiement_periode)
    // ------------------------------------------------------------------

    @EntityGraph(attributePaths = {"contrat", "contrat.logement"})
    List<PaiementLoyer> findByContratIdOrderByPeriodeAnneeDescPeriodeMoisDesc(Integer contratId);

    /** Échéance d'une période donnée : clé fonctionnelle (contrat, mois, année). */
    Optional<PaiementLoyer> findByContratIdAndPeriodeMoisAndPeriodeAnnee(Integer contratId,
                                                                        Integer periodeMois,
                                                                        Integer periodeAnnee);

    /** Contrôle préalable à la génération des échéances (évite de violer l'index unique). */
    boolean existsByContratIdAndPeriodeMoisAndPeriodeAnnee(Integer contratId, Integer periodeMois,
                                                           Integer periodeAnnee);

    long countByContratId(Integer contratId);

    // ------------------------------------------------------------------
    // Listes par statut (tableau de bord, relances)
    // ------------------------------------------------------------------

    @EntityGraph(attributePaths = {"contrat", "contrat.locataire", "contrat.logement"})
    List<PaiementLoyer> findByStatutOrderByPeriodeAnneeDescPeriodeMoisDesc(StatutPaiement statut);

    @EntityGraph(attributePaths = {"contrat", "contrat.locataire", "contrat.logement"})
    List<PaiementLoyer> findByStatutInOrderByPeriodeAnneeDescPeriodeMoisDesc(Collection<StatutPaiement> statuts);

    /** Échéances impayées d'un bailleur, toutes dates confondues. */
    @EntityGraph(attributePaths = {"contrat", "contrat.locataire", "contrat.logement"})
    @Query("select p from PaiementLoyer p "
            + "where p.contrat.logement.proprietaire.id = :proprietaireId and p.statut in :statuts "
            + "order by p.periodeAnnee desc, p.periodeMois desc")
    List<PaiementLoyer> findPaiementsDuBailleur(@Param("proprietaireId") Integer proprietaireId,
                                                @Param("statuts") Collection<StatutPaiement> statuts);

    @EntityGraph(attributePaths = {"contrat", "contrat.locataire", "contrat.logement"})
    List<PaiementLoyer> findTop5ByContrat_Logement_Proprietaire_IdAndStatutInOrderByPeriodeAnneeDescPeriodeMoisDesc(
            Integer proprietaireId, Collection<StatutPaiement> statuts);

    /** Quittances de tous les contrats actifs d'un bailleur (bloc « Paiements en attente »). */
    @EntityGraph(attributePaths = {"contrat", "contrat.locataire", "contrat.logement"})
    @Query("select p from PaiementLoyer p "
            + "where p.contrat.logement.proprietaire.id = :proprietaireId "
            + "and p.contrat.statutActuel = mg.bailtech.model.StatutContrat.EN_COURS "
            + "order by p.periodeAnnee desc, p.periodeMois desc")
    List<PaiementLoyer> findEcheancesDesContratsActifs(@Param("proprietaireId") Integer proprietaireId);

    long countByStatut(StatutPaiement statut);

    long countByContrat_Logement_Proprietaire_IdAndStatutIn(Integer proprietaireId,
                                                            Collection<StatutPaiement> statuts);

    long countByStatutIn(Collection<StatutPaiement> statuts);

    // ------------------------------------------------------------------
    // Agrégats financiers
    // ------------------------------------------------------------------

    /** Total effectivement encaissé par un bailleur. */
    @Query("select sum(p.montantPaye) from PaiementLoyer p "
            + "where p.contrat.logement.proprietaire.id = :proprietaireId")
    BigDecimal sumMontantPayeParBailleur(@Param("proprietaireId") Integer proprietaireId);

    /** Reste à recouvrer pour une période donnée. */
    @Query("select sum(p.montantAttendu) - sum(p.montantPaye) from PaiementLoyer p "
            + "where p.contrat.logement.proprietaire.id = :proprietaireId "
            + "and p.periodeAnnee = :annee and p.periodeMois = :mois")
    BigDecimal sumResteDuPeriode(@Param("proprietaireId") Integer proprietaireId,
                                 @Param("annee") Integer annee,
                                 @Param("mois") Integer mois);
}
