package mg.bailtech.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Objet de formulaire du générateur de contrat (page {@code contract/contract.html}).
 * <p>
 * <strong>Pourquoi un DTO plutôt que l'entité {@code ContratDeBail} ?</strong>
 * <ul>
 *   <li>les listes déroulantes renvoient des identifiants ({@code logementId},
 *       {@code locataireId}) : les binder directement sur des associations
 *       {@code @ManyToOne} exigerait des convertisseurs et rouvrirait la porte à
 *       l'affectation massive de champs (statut, échéances…) ;</li>
 *   <li>la saisie peut créer un locataire inconnu, dont les champs sont à plat
 *       alors qu'ils vivent dans la table {@code utilisateur} ;</li>
 *   <li>l'aperçu du contrat a besoin de valeurs déjà résolues (nom du bailleur,
 *       adresse du bien, mode de comptage) : les calculer ici évite toute
 *       initialisation paresseuse pendant le rendu Thymeleaf.</li>
 * </ul>
 * Chaque champ porte un nom Lombok-free explicite, exploitable par
 * {@code th:object} / {@code th:field} / {@code th:errors}.
 */
public class ContratForm {

    // ------------------------------------------------------------------
    // Étape 1 — Les parties
    // ------------------------------------------------------------------

    /** Bailleur connecté ; reporté tel quel pour ne pas lier le formulaire à une session. */
    private Integer bailleurId;

    /** Locataire déjà enregistré dans la base (sélection prioritaire). */
    private Integer locataireId;

    // -- identité du locataire saisi à la volée ------------------------------

    @Size(max = 100, message = "{utilisateur.nom.taille}")
    private String nom;

    @Size(max = 100, message = "{utilisateur.prenom.taille}")
    private String prenom;

    @Pattern(regexp = "\\d{12}", message = "{utilisateur.cinNumero.format}")
    private String cinNumero;

    /** Rendu et lu au format ISO par les {@code <input type="date">} du formulaire. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate cinDateDelivrance;

    @Size(max = 100, message = "{utilisateur.cinLieuDelivrance.taille}")
    private String cinLieuDelivrance;

    private String adresseActuelle;

    @Size(max = 20, message = "{utilisateur.telephone.taille}")
    private String telephone;

    @Email(message = "{utilisateur.email.format}")
    @Size(max = 150, message = "{utilisateur.email.taille}")
    private String email;

    // ------------------------------------------------------------------
    // Étape 2 — Le bien
    // ------------------------------------------------------------------

    @NotNull(message = "{contrat.logement.notNull}")
    private Integer logementId;

    // ------------------------------------------------------------------
    // Étape 3 — Les conditions financières
    // ------------------------------------------------------------------

    @NotNull(message = "{contrat.dateDebut.notNull}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateDebut;

    @NotNull(message = "{contrat.dateFin.notNull}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFin;

    @NotNull(message = "{contrat.montantLoyerMga.notNull}")
    @DecimalMin(value = "0.01", message = "{contrat.montantLoyerMga.min}")
    @Digits(integer = 10, fraction = 2, message = "{contrat.montant.digits}")
    private BigDecimal montantLoyerMga;

    @NotNull(message = "{contrat.montantCautionMga.notNull}")
    @DecimalMin(value = "0.00", message = "{contrat.montantCautionMga.min}")
    @Digits(integer = 10, fraction = 2, message = "{contrat.montant.digits}")
    private BigDecimal montantCautionMga = BigDecimal.ZERO;

    @NotNull(message = "{contrat.jourPaiementMensuel.notNull}")
    @Min(value = 1, message = "{contrat.jourPaiementMensuel.min}")
    @Max(value = 31, message = "{contrat.jourPaiementMensuel.max}")
    private Integer jourPaiementMensuel = 5;

    @NotNull(message = "{contrat.dureePreavisMois.notNull}")
    @Min(value = 0, message = "{contrat.dureePreavisMois.min}")
    @Max(value = 36, message = "{contrat.dureePreavisMois.max}")
    private Integer dureePreavisMois = 3;

    // ------------------------------------------------------------------
    // Étape 4 — Aperçu : valeurs résolues côté serveur (lecture seule)
    // ------------------------------------------------------------------

    private String bailleurNomComplet = "";
    private String bailleurCin = "";
    private String bailleurAdresse = "";
    private String locataireNomComplet = "";
    private String locataireCin = "";
    private String locataireAdresse = "";
    private String bienType = "";
    private String bienAdresse = "";
    private String bienVille = "";
    private String bienQuartier = "";
    private String bienModeComptage = "";
    private Integer bienNombrePieces;

    // -- valeurs mises en forme pour l'aperçu du contrat (aucun binding) -------

    private String loyerLisible = "";
    private String cautionLisible = "";
    private String cautionMoisLisible = "";
    private String jourPaiementLisible = "05";
    private String dateDebutLisible = "";
    private String dateFinLisible = "";

    /**
     * Le locataire doit être identifiable : soit il est déjà en base
     * ({@code locataireId}), soit toutes les rubriques obligatoires de la table
     * {@code utilisateur} sont saisies ({@code cin_date_delivrance} et
     * {@code cin_lieu_delivrance} sont NOT NULL en base).
     */
    @AssertTrue(message = "{contrat.locataire.obligatoire}")
    public boolean isLocataireRenseigne() {
        if (locataireId != null) {
            return true;
        }
        return nonVide(nom)
                && nonVide(prenom)
                && nonVide(cinNumero)
                && cinDateDelivrance != null
                && nonVide(cinLieuDelivrance)
                && nonVide(adresseActuelle)
                && nonVide(telephone)
                && nonVide(email);
    }

    /**
     * Équivalent applicatif de la contrainte {@code chk_dates_coherentes}
     * ({@code date_fin > date_debut}) : le message est rendu sous les deux champs
     * de date via {@code th:errors="*{datesCoherentes}"}.
     */
    @AssertTrue(message = "{contrat.dates.coherence}")
    public boolean isDatesCoherentes() {
        return dateDebut == null || dateFin == null || dateFin.isAfter(dateDebut);
    }

    /** Vrai lorsque le formulaire décrit la création d'un locataire inconnu. */
    public boolean isNouveauLocataire() {
        return locataireId == null;
    }

    private static boolean nonVide(String valeur) {
        return valeur != null && !valeur.isBlank();
    }

    // ------------------------------------------------------------------
    // Accesseurs (nécessaires au data binding de Spring MVC)
    // ------------------------------------------------------------------

    public Integer getBailleurId() {
        return bailleurId;
    }

    public void setBailleurId(Integer bailleurId) {
        this.bailleurId = bailleurId;
    }

    public Integer getLocataireId() {
        return locataireId;
    }

    public void setLocataireId(Integer locataireId) {
        this.locataireId = locataireId;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getCinNumero() {
        return cinNumero;
    }

    public void setCinNumero(String cinNumero) {
        this.cinNumero = cinNumero;
    }

    public LocalDate getCinDateDelivrance() {
        return cinDateDelivrance;
    }

    public void setCinDateDelivrance(LocalDate cinDateDelivrance) {
        this.cinDateDelivrance = cinDateDelivrance;
    }

    public String getCinLieuDelivrance() {
        return cinLieuDelivrance;
    }

    public void setCinLieuDelivrance(String cinLieuDelivrance) {
        this.cinLieuDelivrance = cinLieuDelivrance;
    }

    public String getAdresseActuelle() {
        return adresseActuelle;
    }

    public void setAdresseActuelle(String adresseActuelle) {
        this.adresseActuelle = adresseActuelle;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getLogementId() {
        return logementId;
    }

    public void setLogementId(Integer logementId) {
        this.logementId = logementId;
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

    public String getBailleurNomComplet() {
        return bailleurNomComplet;
    }

    public void setBailleurNomComplet(String bailleurNomComplet) {
        this.bailleurNomComplet = bailleurNomComplet;
    }

    public String getBailleurCin() {
        return bailleurCin;
    }

    public void setBailleurCin(String bailleurCin) {
        this.bailleurCin = bailleurCin;
    }

    public String getBailleurAdresse() {
        return bailleurAdresse;
    }

    public void setBailleurAdresse(String bailleurAdresse) {
        this.bailleurAdresse = bailleurAdresse;
    }

    public String getLocataireNomComplet() {
        return locataireNomComplet;
    }

    public void setLocataireNomComplet(String locataireNomComplet) {
        this.locataireNomComplet = locataireNomComplet;
    }

    public String getLocataireCin() {
        return locataireCin;
    }

    public void setLocataireCin(String locataireCin) {
        this.locataireCin = locataireCin;
    }

    public String getLocataireAdresse() {
        return locataireAdresse;
    }

    public void setLocataireAdresse(String locataireAdresse) {
        this.locataireAdresse = locataireAdresse;
    }

    public String getBienType() {
        return bienType;
    }

    public void setBienType(String bienType) {
        this.bienType = bienType;
    }

    public String getBienAdresse() {
        return bienAdresse;
    }

    public void setBienAdresse(String bienAdresse) {
        this.bienAdresse = bienAdresse;
    }

    public String getBienVille() {
        return bienVille;
    }

    public void setBienVille(String bienVille) {
        this.bienVille = bienVille;
    }

    public String getBienQuartier() {
        return bienQuartier;
    }

    public void setBienQuartier(String bienQuartier) {
        this.bienQuartier = bienQuartier;
    }

    public String getBienModeComptage() {
        return bienModeComptage;
    }

    public void setBienModeComptage(String bienModeComptage) {
        this.bienModeComptage = bienModeComptage;
    }

    public Integer getBienNombrePieces() {
        return bienNombrePieces;
    }

    public void setBienNombrePieces(Integer bienNombrePieces) {
        this.bienNombrePieces = bienNombrePieces;
    }

    public String getLoyerLisible() {
        return loyerLisible;
    }

    public void setLoyerLisible(String loyerLisible) {
        this.loyerLisible = loyerLisible;
    }

    public String getCautionLisible() {
        return cautionLisible;
    }

    public void setCautionLisible(String cautionLisible) {
        this.cautionLisible = cautionLisible;
    }

    public String getCautionMoisLisible() {
        return cautionMoisLisible;
    }

    public void setCautionMoisLisible(String cautionMoisLisible) {
        this.cautionMoisLisible = cautionMoisLisible;
    }

    public String getJourPaiementLisible() {
        return jourPaiementLisible;
    }

    public void setJourPaiementLisible(String jourPaiementLisible) {
        this.jourPaiementLisible = jourPaiementLisible;
    }

    public String getDateDebutLisible() {
        return dateDebutLisible;
    }

    public void setDateDebutLisible(String dateDebutLisible) {
        this.dateDebutLisible = dateDebutLisible;
    }

    public String getDateFinLisible() {
        return dateFinLisible;
    }

    public void setDateFinLisible(String dateFinLisible) {
        this.dateFinLisible = dateFinLisible;
    }
}
