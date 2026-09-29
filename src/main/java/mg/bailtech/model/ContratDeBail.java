package mg.bailtech.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code contrat_de_bail} — liaison juridique entre un bien et un preneur.
 * <p>
 * <strong>Index partiel PostgreSQL :</strong> le script d'initialisation crée
 * <pre>
 * CREATE UNIQUE INDEX uq_un_seul_contrat_actif_par_logement
 *     ON contrat_de_bail (id_logement) WHERE statut_actuel = 'EN_COURS';
 * </pre>
 * Cette contrainte n'est <em>pas</em> exprimable en annotations JPA : elle est
 * reproduite par le prédicat {@link #isLocationActive()} utilisé côté service
 * (vérification avant enregistrement) et par le traitement de la
 * {@code DataIntegrityViolationException} côté contrôleur. Contraintes
 * vérifiables en Java, elles sont en revanche portées par la base
 * ({@code chk_dates_coherentes}).
 */
@Entity
@Table(name = "contrat_de_bail")
@Check(name = "chk_dates_coherentes", constraints = "date_fin > date_debut")
public class ContratDeBail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contrat")
    private Integer id;

    @NotNull(message = "{contrat.dateDebut.notNull}")
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @NotNull(message = "{contrat.dateFin.notNull}")
    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @NotNull(message = "{contrat.montantLoyerMga.notNull}")
    @DecimalMin(value = "0.01", message = "{contrat.montantLoyerMga.min}")
    @Digits(integer = 10, fraction = 2, message = "{contrat.montant.digits}")
    @Column(name = "montant_loyer_mga", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantLoyerMga;

    @NotNull(message = "{contrat.montantCautionMga.notNull}")
    @DecimalMin(value = "0.00", message = "{contrat.montantCautionMga.min}")
    @Digits(integer = 10, fraction = 2, message = "{contrat.montant.digits}")
    @Column(name = "montant_caution_mga", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantCautionMga = BigDecimal.ZERO;

    @NotNull(message = "{contrat.jourPaiementMensuel.notNull}")
    @Min(value = 1, message = "{contrat.jourPaiementMensuel.min}")
    @Max(value = 31, message = "{contrat.jourPaiementMensuel.max}")
    @Column(name = "jour_paiement_mensuel", nullable = false)
    private Integer jourPaiementMensuel = 5;

    @NotNull(message = "{contrat.dureePreavisMois.notNull}")
    @Min(value = 0, message = "{contrat.dureePreavisMois.min}")
    @Column(name = "duree_preavis_mois", nullable = false)
    private Integer dureePreavisMois = 3;

    @NotNull(message = "{contrat.statutActuel.notNull}")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "statut_actuel", nullable = false, columnDefinition = "statut_contrat")
    private StatutContrat statutActuel = StatutContrat.EN_ATTENTE_SIGNATURE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_logement", nullable = false)
    private Logement logement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_locataire", nullable = false)
    private Utilisateur locataire;

    /**
     * Échéances mensuelles générées à l'activation du bail.
     * {@code orphanRemoval} + {@code CascadeType.ALL} : la FK
     * {@code fk_paiement_contrat} est en {@code ON DELETE CASCADE}, la suppression
     * d'un contrat projet supprime donc aussi ses lignes prévisionnelles.
     */
    @OneToMany(mappedBy = "contrat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaiementLoyer> paiements = new ArrayList<>();

    /**
     * Constructeur public : la couche de service et le générateur de données de
     * démonstration créent un contrat vide puis le renseignent via les setters.
     */
    public ContratDeBail() {
        // bean JPA
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDate dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }

    public BigDecimal getMontantLoyerMga() {
        return montantLoyerMga;
    }

    public void setMontantLoyerMga(BigDecimal montantLoyerMga) {
        this.montantLoyerMga = montantLoyerMga;
    }

    public BigDecimal getMontantCautionMga() {
        return montantCautionMga;
    }

    public void setMontantCautionMga(BigDecimal montantCautionMga) {
        this.montantCautionMga = montantCautionMga;
    }

    public Integer getJourPaiementMensuel() {
        return jourPaiementMensuel;
    }

    public void setJourPaiementMensuel(Integer jourPaiementMensuel) {
        this.jourPaiementMensuel = jourPaiementMensuel;
    }

    public Integer getDureePreavisMois() {
        return dureePreavisMois;
    }

    public void setDureePreavisMois(Integer dureePreavisMois) {
        this.dureePreavisMois = dureePreavisMois;
    }

    public StatutContrat getStatutActuel() {
        return statutActuel;
    }

    public void setStatutActuel(StatutContrat statutActuel) {
        this.statutActuel = statutActuel;
    }

    public Logement getLogement() {
        return logement;
    }

    public void setLogement(Logement logement) {
        this.logement = logement;
    }

    public Utilisateur getLocataire() {
        return locataire;
    }

    public void setLocataire(Utilisateur locataire) {
        this.locataire = locataire;
    }

    public List<PaiementLoyer> getPaiements() {
        return paiements;
    }

    public void setPaiements(List<PaiementLoyer> paiements) {
        this.paiements = paiements;
    }

    /**
     * Duplication de la contrainte {@code chk_dates_coherentes} pour renvoyer un
     * message métier lisible avant même l'aller-retour en base.
     */
    @AssertTrue(message = "{contrat.dates.coherence}")
    public boolean isDatesCoherentes() {
        return dateDebut == null || dateFin == null || dateFin.isAfter(dateDebut);
    }

    /**
     * Prédicat de l'index partiel {@code uq_un_seul_contrat_actif_par_logement}.
     * Seuls les contrats dont le statut est exactement {@code EN_COURS} entrent
     * dans l'unicité ; un historique illimité de contrats {@code TERMINE} ou
     * {@code RESILIE} reste possible sur un même logement.
     */
    public boolean isLocationActive() {
        return statutActuel != null && statutActuel.isLocationActive();
    }

    /** Durée du bail exprimée en mois (arrondie à l'unité supérieure). */
    public long getDureeMois() {
        if (dateDebut == null || dateFin == null || !dateFin.isAfter(dateDebut)) {
            return 0;
        }
        return ChronoUnit.MONTHS.between(dateDebut, dateFin) + 1;
    }

    /** Nombre de mois de loyer couverts par la caution versée à la signature. */
    public BigDecimal getCautionEnMoisDeLoyer() {
        if (montantLoyerMga == null || montantLoyerMga.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (montantCautionMga == null) {
            return BigDecimal.ZERO;
        }
        return montantCautionMga.divide(montantLoyerMga, 2, RoundingMode.HALF_UP);
    }

    /** Ajoute une échéance mensuelle en conservant la cohérence des deux associations. */
    public void ajouterPaiement(PaiementLoyer paiement) {
        paiements.add(paiement);
        paiement.setContrat(this);
    }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) {
            return true;
        }
        if (autre == null || getClass() != autre.getClass()) {
            return false;
        }
        ContratDeBail that = (ContratDeBail) autre;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return ContratDeBail.class.hashCode();
    }

    @Override
    public String toString() {
        return "ContratDeBail{id=" + id + ", statut=" + statutActuel + ", dateDebut=" + dateDebut
                + ", dateFin=" + dateFin + "}";
    }
}
