package mg.bailtech.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Relevé de sous-compteur saisi pour un locataire.
 *
 * <p>Objet de formulaire et non entité : les index saisis n'ont de valeur que
 * pour le calcul en cours, tant que le relevé n'est pas enregistré. Les index de
 * départ, eux, proviennent de la fiche du logement et sont reconstruits par
 * {@link JiramaService} à chaque affichage — ils ne sont donc pas acceptés du
 * client, sans quoi un relevé falsifierait sa propre référence.
 */
public class ReleveSousCompteur {

    private Integer contratId;

    private Integer logementId;

    private String locataire = "";

    private String bien = "";

    private String typeGestion = "";

    /** Index de départ issu de {@code logement.compteur_electricite_index_depart}. */
    private BigDecimal indexDepartElectricite = BigDecimal.ZERO;

    /** Index de départ issu de {@code logement.compteur_eau_index_depart}. */
    private BigDecimal indexDepartEau = BigDecimal.ZERO;

    @NotNull(message = "{jirama.index.notNull}")
    @DecimalMin(value = "0.00", message = "{jirama.index.min}")
    private BigDecimal indexElectricite = BigDecimal.ZERO;

    @NotNull(message = "{jirama.index.notNull}")
    @DecimalMin(value = "0.00", message = "{jirama.index.min}")
    private BigDecimal indexEau = BigDecimal.ZERO;

    public ReleveSousCompteur() {
        // requis par la liaison de formulaire
    }

    public ReleveSousCompteur(Integer contratId, Integer logementId, String locataire, String bien) {
        this.contratId = contratId;
        this.logementId = logementId;
        this.locataire = locataire;
        this.bien = bien;
    }

    public Integer getContratId() {
        return contratId;
    }

    public void setContratId(Integer contratId) {
        this.contratId = contratId;
    }

    public Integer getLogementId() {
        return logementId;
    }

    public void setLogementId(Integer logementId) {
        this.logementId = logementId;
    }

    public String getLocataire() {
        return locataire;
    }

    public void setLocataire(String locataire) {
        this.locataire = locataire;
    }

    public String getBien() {
        return bien;
    }

    public void setBien(String bien) {
        this.bien = bien;
    }

    public String getTypeGestion() {
        return typeGestion;
    }

    public void setTypeGestion(String typeGestion) {
        this.typeGestion = typeGestion;
    }

    public BigDecimal getIndexDepartElectricite() {
        return indexDepartElectricite;
    }

    public void setIndexDepartElectricite(BigDecimal indexDepartElectricite) {
        this.indexDepartElectricite = indexDepartElectricite;
    }

    public BigDecimal getIndexDepartEau() {
        return indexDepartEau;
    }

    public void setIndexDepartEau(BigDecimal indexDepartEau) {
        this.indexDepartEau = indexDepartEau;
    }

    public BigDecimal getIndexElectricite() {
        return indexElectricite;
    }

    public void setIndexElectricite(BigDecimal indexElectricite) {
        this.indexElectricite = indexElectricite;
    }

    public BigDecimal getIndexEau() {
        return indexEau;
    }

    public void setIndexEau(BigDecimal indexEau) {
        this.indexEau = indexEau;
    }

    /** Consommation d'électricité depuis l'état des lieux, jamais négative. */
    public BigDecimal getConsommationElectricite() {
        return difference(indexElectricite, indexDepartElectricite);
    }

    /** Consommation d'eau depuis l'état des lieux, jamais négative. */
    public BigDecimal getConsommationEau() {
        return difference(indexEau, indexDepartEau);
    }

    private static BigDecimal difference(BigDecimal saisi, BigDecimal depart) {
        BigDecimal s = saisi == null ? BigDecimal.ZERO : saisi;
        BigDecimal d = depart == null ? BigDecimal.ZERO : depart;
        BigDecimal ecart = s.subtract(d);
        // Un compteur remis à zéro ou mal saisi ne doit pas produire de crédit.
        return ecart.signum() < 0 ? BigDecimal.ZERO : ecart;
    }
}
