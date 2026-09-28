package mg.bailtech.model;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code paiement_loyer} — échéance mensuelle d'un contrat de bail.
 * <p>
 * L'unicité {@code uq_paiement_periode (id_contrat, periode_mois, periode_annee)}
 * interdit de générer deux quittances pour la même période : elle est déclarée
 * dans le mapping pour rester alignée sur le script d'initialisation, et contrôlée
 * en amont par {@code PaiementLoyerRepository#existsByContratIdAndPeriode}.
 */
@Entity
@Table(name = "paiement_loyer",
        uniqueConstraints = @UniqueConstraint(name = "uq_paiement_periode",
                columnNames = {"id_contrat", "periode_mois", "periode_annee"}))
public class PaiementLoyer {

    /** Année plancher de la contrainte {@code CHECK (periode_annee >= 2026)}. */
    public static final int ANNEE_MIN = 2026;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_paiement")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_contrat", nullable = false)
    private ContratDeBail contrat;

    @NotNull(message = "{paiement.periodeMois.notNull}")
    @Min(value = 1, message = "{paiement.periodeMois.min}")
    @Max(value = 12, message = "{paiement.periodeMois.max}")
    @Column(name = "periode_mois", nullable = false)
    private Integer periodeMois;

    @NotNull(message = "{paiement.periodeAnnee.notNull}")
    @Min(value = ANNEE_MIN, message = "{paiement.periodeAnnee.min}")
    @Column(name = "periode_annee", nullable = false)
    private Integer periodeAnnee;

    @NotNull(message = "{paiement.montantAttendu.notNull}")
    @DecimalMin(value = "0.00", message = "{paiement.montantAttendu.min}")
    @Digits(integer = 10, fraction = 2, message = "{paiement.montant.digits}")
    @Column(name = "montant_attendu", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantAttendu;

    @NotNull(message = "{paiement.montantPaye.notNull}")
    @DecimalMin(value = "0.00", message = "{paiement.montantPaye.min}")
    @Digits(integer = 10, fraction = 2, message = "{paiement.montant.digits}")
    @Column(name = "montant_paye", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantPaye = new BigDecimal("0.00");

    @Column(name = "date_paiement_effectif")
    private LocalDate datePaiementEffectif;

    @Size(max = 50, message = "{paiement.modePaiement.taille}")
    @Column(name = "mode_paiement", length = 50)
    private String modePaiement;

    @NotNull(message = "{paiement.statut.notNull}")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "statut", nullable = false, columnDefinition = "statut_paiement")
    private StatutPaiement statut = StatutPaiement.A_PAYER;

    @Column(name = "recu_quittance_genere", nullable = false)
    private boolean recuQuittanceGenere = false;

    protected PaiementLoyer() {
        // constructeur requis par JPA
    }

    public PaiementLoyer(Integer periodeMois, Integer periodeAnnee, BigDecimal montantAttendu) {
        this.periodeMois = periodeMois;
        this.periodeAnnee = periodeAnnee;
        this.montantAttendu = montantAttendu;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public ContratDeBail getContrat() {
        return contrat;
    }

    public void setContrat(ContratDeBail contrat) {
        this.contrat = contrat;
    }

    public Integer getPeriodeMois() {
        return periodeMois;
    }

    public void setPeriodeMois(Integer periodeMois) {
        this.periodeMois = periodeMois;
    }

    public Integer getPeriodeAnnee() {
        return periodeAnnee;
    }

    public void setPeriodeAnnee(Integer periodeAnnee) {
        this.periodeAnnee = periodeAnnee;
    }

    public BigDecimal getMontantAttendu() {
        return montantAttendu;
    }

    public void setMontantAttendu(BigDecimal montantAttendu) {
        this.montantAttendu = montantAttendu;
    }

    public BigDecimal getMontantPaye() {
        return montantPaye;
    }

    public void setMontantPaye(BigDecimal montantPaye) {
        this.montantPaye = montantPaye;
    }

    public LocalDate getDatePaiementEffectif() {
        return datePaiementEffectif;
    }

    public void setDatePaiementEffectif(LocalDate datePaiementEffectif) {
        this.datePaiementEffectif = datePaiementEffectif;
    }

    public String getModePaiement() {
        return modePaiement;
    }

    public void setModePaiement(String modePaiement) {
        this.modePaiement = modePaiement;
    }

    public StatutPaiement getStatut() {
        return statut;
    }

    public void setStatut(StatutPaiement statut) {
        this.statut = statut;
    }

    public boolean isRecuQuittanceGenere() {
        return recuQuittanceGenere;
    }

    public void setRecuQuittanceGenere(boolean recuQuittanceGenere) {
        this.recuQuittanceGenere = recuQuittanceGenere;
    }

    /** Solde restant dû sur l'échéance : {@code montant_attendu - montant_paye}. */
    public BigDecimal getResteDu() {
        if (montantAttendu == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal verse = montantPaye == null ? BigDecimal.ZERO : montantPaye;
        BigDecimal reste = montantAttendu.subtract(verse);
        return reste.max(BigDecimal.ZERO);
    }

    /** Vrai si l'échéance a été intégralement réglée. */
    public boolean isSoldee() {
        return getResteDu().compareTo(BigDecimal.ZERO) == 0;
    }

    /** Période affichée dans les listes : « 09/2026 ». */
    public String getPeriodeLisible() {
        return String.format("%02d/%d", periodeMois == null ? 0 : periodeMois,
                periodeAnnee == null ? 0 : periodeAnnee);
    }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) {
            return true;
        }
        if (autre == null || getClass() != autre.getClass()) {
            return false;
        }
        PaiementLoyer that = (PaiementLoyer) autre;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return PaiementLoyer.class.hashCode();
    }

    @Override
    public String toString() {
        return "PaiementLoyer{id=" + id + ", periode=" + getPeriodeLisible() + ", statut=" + statut + "}";
    }
}
