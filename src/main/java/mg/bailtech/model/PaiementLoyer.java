package mg.bailtech.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "paiement_loyer", uniqueConstraints = @UniqueConstraint(name = "uq_paiement_periode", columnNames = {"id_contrat", "periode_mois", "periode_annee"}))
public class PaiementLoyer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_paiement") private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_contrat") private ContratDeBail contrat;
    @NotNull @Min(1) @Max(12) @Column(name = "periode_mois") private Integer periodeMois;
    @NotNull @Min(2026) @Column(name = "periode_annee") private Integer periodeAnnee;
    @NotNull @DecimalMin("0.00") @Column(name = "montant_attendu", precision = 12, scale = 2) private BigDecimal montantAttendu;
    @NotNull @DecimalMin("0.00") @Column(name = "montant_paye", precision = 12, scale = 2) private BigDecimal montantPaye = BigDecimal.ZERO;
    @Column(name = "date_paiement_effectif") private LocalDate datePaiementEffectif;
    @Column(name = "mode_paiement", length = 50) private String modePaiement;
    @NotNull @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "statut", columnDefinition = "statut_paiement") private StatutPaiement statut = StatutPaiement.A_PAYER;
    @Column(name = "recu_quittance_genere") private boolean recuQuittanceGenere;

    public Integer getId() { return id; }
    public ContratDeBail getContrat() { return contrat; }
    public void setContrat(ContratDeBail v) { contrat = v; }
    public Integer getPeriodeMois() { return periodeMois; }
    public void setPeriodeMois(Integer v) { periodeMois = v; }
    public Integer getPeriodeAnnee() { return periodeAnnee; }
    public void setPeriodeAnnee(Integer v) { periodeAnnee = v; }
    public BigDecimal getMontantAttendu() { return montantAttendu; }
    public void setMontantAttendu(BigDecimal v) { montantAttendu = v; }
    public BigDecimal getMontantPaye() { return montantPaye; }
    public void setMontantPaye(BigDecimal v) { montantPaye = v; }
    public LocalDate getDatePaiementEffectif() { return datePaiementEffectif; }
    public void setDatePaiementEffectif(LocalDate v) { datePaiementEffectif = v; }
    public String getModePaiement() { return modePaiement; }
    public void setModePaiement(String v) { modePaiement = v; }
    public StatutPaiement getStatut() { return statut; }
    public void setStatut(StatutPaiement v) { statut = v; }
    public boolean isRecuQuittanceGenere() { return recuQuittanceGenere; }
    public void setRecuQuittanceGenere(boolean v) { recuQuittanceGenere = v; }
}