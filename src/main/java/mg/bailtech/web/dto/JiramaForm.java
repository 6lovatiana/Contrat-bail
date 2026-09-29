package mg.bailtech.web.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import mg.bailtech.model.Logement;
import mg.bailtech.model.TypeCompteurJirama;
import mg.bailtech.service.MoteurRepartitionJirama.ModeEcart;

/**
 * Objet de formulaire du calculateur de charges JIRAMA.
 *
 * <p>Il regroupe les deux sources de données nécessaires à la répartition :
 * <ul>
 *   <li>les index du <strong>compteur principal</strong>, relevés par le bailleur
 *       sur la facture de l'immeuble&nbsp;;</li>
 *   <li>les index des <strong>sous-compteurs</strong>, relevés locataire par
 *       locataire depuis l'index de départ recorded à l'état des lieux.</li>
 * </ul>
 *
 * <p>Les tarifs JIRAMA ne sont pas figés dans le modèle : ils évoluent selon la
 * tranche de consommation. Ils sont donc saisis, avec des valeurs par défaut
 * correspondant aux tarifs de la maquette (420 Ar/kWh et 1 250 Ar/m³).
 */
public class JiramaForm {

    /** Tarif par défaut en Ariary par kWh, conforme à la maquette du calculateur. */
    public static final BigDecimal TARIF_ELEC_PAR_DEFAUT = new BigDecimal("420");

    /** Tarif par défaut en Ariary par m³ d'eau. */
    public static final BigDecimal TARIF_EAU_PAR_DEFAUT = new BigDecimal("1250");

    private Integer logementId;

    private BigDecimal indexElectricitePrincipal = BigDecimal.ZERO;

    private BigDecimal indexEauPrincipal = BigDecimal.ZERO;

    private BigDecimal indexDepartElectricitePrincipal = BigDecimal.ZERO;

    private BigDecimal indexDepartEauPrincipal = BigDecimal.ZERO;

    private String numeroCompteurElectricite = "";

    private String numeroCompteurEau = "";

    private TypeCompteurJirama typeGestion = TypeCompteurJirama.UNIQUE;

    @NotNull(message = "{jirama.tarif.notNull}")
    @DecimalMin(value = "0.00", message = "{jirama.tarif.min}")
    private BigDecimal prixUnitaireElectricite = TARIF_ELEC_PAR_DEFAUT;

    @NotNull(message = "{jirama.tarif.notNull}")
    @DecimalMin(value = "0.00", message = "{jirama.tarif.min}")
    private BigDecimal prixUnitaireEau = TARIF_EAU_PAR_DEFAUT;

    /**
     * Montant de la facture telle que JIRAMA l'a émise&nbsp;: taxes et frais
     * divers la rendent différente du seul produit consommation x tarif. Laissé
     * vide, le montant calculé fait foi.
     */
    @DecimalMin(value = "0.00", message = "{jirama.facture.min}")
    private BigDecimal montantFacture;

    private ModeEcart modeEcart = ModeEcart.PRORATA;

    private Integer annee = YearMonth.now().getYear();

    @Min(value = 1, message = "{jirama.mois.min}")
    @Max(value = 12, message = "{jirama.mois.max}")
    private Integer mois = YearMonth.now().getMonthValue();

    @Valid
    private List<ReleveSousCompteur> sousCompteurs = new ArrayList<>();

    public JiramaForm() {
        // requis par la liaison de formulaire
    }

    /** Prépare le formulaire à partir du compteur principal d'un bien. */
    public void initialiserDepuis(Logement logement) {
        if (logement == null) {
            return;
        }
        this.logementId = logement.getId();
        this.indexDepartElectricitePrincipal = decale(logement.getCompteurElectriciteIndexDepart());
        this.indexDepartEauPrincipal = decale(logement.getCompteurEauIndexDepart());
        this.numeroCompteurElectricite = videSiNull(logement.getCompteurElectriciteNumero());
        this.numeroCompteurEau = videSiNull(logement.getCompteurEauNumero());
        if (logement.getJiramaTypeGestion() != null) {
            this.typeGestion = logement.getJiramaTypeGestion();
        }
    }

    public Integer getLogementId() {
        return logementId;
    }

    public void setLogementId(Integer logementId) {
        this.logementId = logementId;
    }

    public BigDecimal getIndexElectricitePrincipal() {
        return indexElectricitePrincipal;
    }

    public void setIndexElectricitePrincipal(BigDecimal indexElectricitePrincipal) {
        this.indexElectricitePrincipal = indexElectricitePrincipal;
    }

    public BigDecimal getIndexEauPrincipal() {
        return indexEauPrincipal;
    }

    public void setIndexEauPrincipal(BigDecimal indexEauPrincipal) {
        this.indexEauPrincipal = indexEauPrincipal;
    }

    public BigDecimal getIndexDepartElectricitePrincipal() {
        return indexDepartElectricitePrincipal;
    }

    public void setIndexDepartElectricitePrincipal(BigDecimal indexDepartElectricitePrincipal) {
        this.indexDepartElectricitePrincipal = indexDepartElectricitePrincipal;
    }

    public BigDecimal getIndexDepartEauPrincipal() {
        return indexDepartEauPrincipal;
    }

    public void setIndexDepartEauPrincipal(BigDecimal indexDepartEauPrincipal) {
        this.indexDepartEauPrincipal = indexDepartEauPrincipal;
    }

    public String getNumeroCompteurElectricite() {
        return numeroCompteurElectricite;
    }

    public void setNumeroCompteurElectricite(String numeroCompteurElectricite) {
        this.numeroCompteurElectricite = numeroCompteurElectricite;
    }

    public String getNumeroCompteurEau() {
        return numeroCompteurEau;
    }

    public void setNumeroCompteurEau(String numeroCompteurEau) {
        this.numeroCompteurEau = numeroCompteurEau;
    }

    public TypeCompteurJirama getTypeGestion() {
        return typeGestion;
    }

    public void setTypeGestion(TypeCompteurJirama typeGestion) {
        this.typeGestion = typeGestion;
    }

    public BigDecimal getPrixUnitaireElectricite() {
        return prixUnitaireElectricite;
    }

    public void setPrixUnitaireElectricite(BigDecimal prixUnitaireElectricite) {
        this.prixUnitaireElectricite = prixUnitaireElectricite;
    }

    public BigDecimal getPrixUnitaireEau() {
        return prixUnitaireEau;
    }

    public void setPrixUnitaireEau(BigDecimal prixUnitaireEau) {
        this.prixUnitaireEau = prixUnitaireEau;
    }

    public BigDecimal getMontantFacture() {
        return montantFacture;
    }

    public void setMontantFacture(BigDecimal montantFacture) {
        this.montantFacture = montantFacture;
    }

    public ModeEcart getModeEcart() {
        return modeEcart;
    }

    public void setModeEcart(ModeEcart modeEcart) {
        this.modeEcart = modeEcart;
    }

    public Integer getAnnee() {
        return annee;
    }

    public void setAnnee(Integer annee) {
        this.annee = annee;
    }

    public Integer getMois() {
        return mois;
    }

    public void setMois(Integer mois) {
        this.mois = mois;
    }

    public List<ReleveSousCompteur> getSousCompteurs() {
        return sousCompteurs;
    }

    public void setSousCompteurs(List<ReleveSousCompteur> sousCompteurs) {
        this.sousCompteurs = sousCompteurs == null ? new ArrayList<>() : sousCompteurs;
    }

    // ------------------------------------------------------------------
    // Valeurs dérivées affichées dans le gabarit
    // ------------------------------------------------------------------

    /** Consommation du compteur principal depuis l'état des lieux, en kWh. */
    public BigDecimal getConsommationElectricitePrincipale() {
        return difference(indexElectricitePrincipal, indexDepartElectricitePrincipal);
    }

    /** Consommation du compteur principal depuis l'état des lieux, en m³. */
    public BigDecimal getConsommationEauPrincipale() {
        return difference(indexEauPrincipal, indexDepartEauPrincipal);
    }

    /** Période sur laquelle les charges seront imputées. */
    public YearMonth getPeriode() {
        return YearMonth.of(annee == null ? YearMonth.now().getYear() : annee,
                mois == null ? YearMonth.now().getMonthValue() : mois);
    }

    public void setPeriode(YearMonth periode) {
        if (periode == null) {
            return;
        }
        this.annee = periode.getYear();
        this.mois = periode.getMonthValue();
    }

    /** Vrai si ce bien ne dessert qu'un occupant : la répartition est sans objet. */
    public boolean isRepartitionRequise() {
        return typeGestion != null && typeGestion.necessiteRepartition();
    }

    private static BigDecimal difference(BigDecimal saisi, BigDecimal depart) {
        BigDecimal s = saisi == null ? BigDecimal.ZERO : saisi;
        BigDecimal d = depart == null ? BigDecimal.ZERO : depart;
        BigDecimal ecart = s.subtract(d);
        return ecart.signum() < 0 ? BigDecimal.ZERO : ecart;
    }

    private static BigDecimal decale(Integer valeur) {
        return valeur == null ? BigDecimal.ZERO : BigDecimal.valueOf(valeur);
    }

    private static String videSiNull(String valeur) {
        return valeur == null ? "" : valeur;
    }
}
