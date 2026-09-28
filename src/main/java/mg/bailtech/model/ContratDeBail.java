package mg.bailtech.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Entity
@Table(name = "contrat_de_bail")
public class ContratDeBail {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_contrat") private Integer id;
    @NotNull @Column(name = "date_debut") private LocalDate dateDebut;
    @NotNull @Column(name = "date_fin") private LocalDate dateFin;
    @NotNull @Positive @Digits(integer = 10, fraction = 2) @Column(name = "montant_loyer_mga", precision = 12, scale = 2) private BigDecimal montantLoyerMga;
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) @Column(name = "montant_caution_mga", precision = 12, scale = 2) private BigDecimal montantCautionMga;
    @NotNull @Min(1) @Max(31) @Column(name = "jour_paiement_mensuel") private Integer jourPaiementMensuel;
    @NotNull @Min(0) @Column(name = "duree_preavis_mois") private Integer dureePreavisMois = 3;
    @NotNull @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "statut_actuel", columnDefinition = "statut_contrat") private StatutContrat statutActuel = StatutContrat.EN_ATTENTE_SIGNATURE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_logement") private Logement logement;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_locataire") private Utilisateur locataire;
    @OneToMany(mappedBy = "contrat", cascade = CascadeType.ALL, orphanRemoval = true) private List<PaiementLoyer> paiements = new ArrayList<>();

    public Integer getId() { return id; }
    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate v) { dateDebut = v; }
    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate v) { dateFin = v; }
    public BigDecimal getMontantLoyerMga() { return montantLoyerMga; }
    public void setMontantLoyerMga(BigDecimal v) { montantLoyerMga = v; }
    public BigDecimal getMontantCautionMga() { return montantCautionMga; }
    public void setMontantCautionMga(BigDecimal v) { montantCautionMga = v; }
    public Integer getJourPaiementMensuel() { return jourPaiementMensuel; }
    public void setJourPaiementMensuel(Integer v) { jourPaiementMensuel = v; }
    public Integer getDureePreavisMois() { return dureePreavisMois; }
    public void setDureePreavisMois(Integer v) { dureePreavisMois = v; }
    public StatutContrat getStatutActuel() { return statutActuel; }
    public void setStatutActuel(StatutContrat v) { statutActuel = v; }
    public Logement getLogement() { return logement; }
    public void setLogement(Logement v) { logement = v; }
    public Utilisateur getLocataire() { return locataire; }
    public void setLocataire(Utilisateur v) { locataire = v; }
}