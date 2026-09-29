package mg.bailtech.web.dto;

import java.util.List;

/**
 * Modèle du document « Contrat de bail à usage d'habitation » présenté au
 * gabarit d'impression {@code contrat/contrat-pdf.html}.
 * <p>
 * Toutes les valeurs sont déjà résolues et mises en forme : le gabarit n'accède à
 * aucune association paresseuse, ce qui permet au rendu PDF de s'exécuter hors
 * transaction ({@code spring.jpa.open-in-view=false}).
 */
public class ContratPdfModel {

    /** Ligne d'annexe : une échéance mensuelle du bail. */
    public record LigneEcheance(String periode, String montant, String statut, String statutCss) {
    }

    private String reference = "";
    private String lieu = "";
    private String dateEdition = "";

    private String bailleurNom = "";
    private String bailleurCin = "";
    private String bailleurProfession = "";
    private String bailleurAdresse = "";
    private String bailleurTelephone = "";
    private String bailleurEmail = "";
    /** Lieu de délivrance de la CIN — modèle article « Délivrée le … à ». */
    private String bailleurCinLieu = "";

    private String locataireNom = "";
    private String locataireCin = "";
    private String locataireAdresse = "";
    private String locataireTelephone = "";
    private String locataireEmail = "";
    /** Lieu de délivrance de la CIN — modèle article « Délivrée le … à ». */
    private String locataireCinLieu = "";

    private String bienType = "";
    private String bienAdresse = "";
    private String bienQuartier = "";
    private String bienVille = "";
    private String bienPieces = "";
    private String bienDescription = "";
    private String bienModeComptage = "";
    private String bienMethodeRepartition = "";
    private String bienCompteurElectricite = "";
    private String bienCompteurEau = "";

    private String dateDebut = "";
    private String dateFin = "";
    private String loyer = "";
    private String caution = "";
    private String cautionMois = "";
    private String jourPaiement = "";
    private String dureePreavis = "";
    private String dureePreavisNombre = "";
    private String dureeBail = "";
    private String statut = "";
    private String statutCss = "";

    /**
     * Vrai lorsque le bien porte un compteur commun à répartir.
     *
     * <p>Le modèle distingue deux options à l'article 5 : si le locataire
     * souscrit lui-même ses abonnements, rien n'est à répartir ; sinon la
     * facture est répartie selon la règle inscrite sur la fiche du bien. Le
     * gabarit a besoin de cette bascule pour n'afficher que l'option qui
     * s'applique, plutôt que les deux — afficher « Option A » sur un
     * compteur commun afficherait un engagement que les parties n'ont pas
     * pris.
     */
    private boolean repartitionJirama;

    /**
     * Montant du loyer en toutes lettres. Non calculé à ce stade : la conversion
     * numérique → texte est portée par le module de calcul de montants ; le champ
     * est prévu pour l'accueillir sans modifier le gabarit.
     */
    private String loyerEnLettres = "";

    private List<LigneEcheance> echeances = List.of();

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getLieu() {
        return lieu;
    }

    public void setLieu(String lieu) {
        this.lieu = lieu;
    }

    public String getDateEdition() {
        return dateEdition;
    }

    public void setDateEdition(String dateEdition) {
        this.dateEdition = dateEdition;
    }

    public String getBailleurNom() {
        return bailleurNom;
    }

    public void setBailleurNom(String bailleurNom) {
        this.bailleurNom = bailleurNom;
    }

    public String getBailleurCin() {
        return bailleurCin;
    }

    public void setBailleurCin(String bailleurCin) {
        this.bailleurCin = bailleurCin;
    }

    public String getBailleurProfession() {
        return bailleurProfession;
    }

    public void setBailleurProfession(String bailleurProfession) {
        this.bailleurProfession = bailleurProfession;
    }

    public String getBailleurAdresse() {
        return bailleurAdresse;
    }

    public void setBailleurAdresse(String bailleurAdresse) {
        this.bailleurAdresse = bailleurAdresse;
    }

    public String getBailleurTelephone() {
        return bailleurTelephone;
    }

    public void setBailleurTelephone(String bailleurTelephone) {
        this.bailleurTelephone = bailleurTelephone;
    }

    public String getBailleurEmail() {
        return bailleurEmail;
    }

    public void setBailleurEmail(String bailleurEmail) {
        this.bailleurEmail = bailleurEmail;
    }

    public String getBailleurCinLieu() {
        return bailleurCinLieu;
    }

    public void setBailleurCinLieu(String bailleurCinLieu) {
        this.bailleurCinLieu = bailleurCinLieu;
    }

    public String getLocataireEmail() {
        return locataireEmail;
    }

    public void setLocataireEmail(String locataireEmail) {
        this.locataireEmail = locataireEmail;
    }

    public String getLocataireCinLieu() {
        return locataireCinLieu;
    }

    public void setLocataireCinLieu(String locataireCinLieu) {
        this.locataireCinLieu = locataireCinLieu;
    }

    public String getLocataireNom() {
        return locataireNom;
    }

    public void setLocataireNom(String locataireNom) {
        this.locataireNom = locataireNom;
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

    public String getLocataireTelephone() {
        return locataireTelephone;
    }

    public void setLocataireTelephone(String locataireTelephone) {
        this.locataireTelephone = locataireTelephone;
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

    public String getBienQuartier() {
        return bienQuartier;
    }

    public void setBienQuartier(String bienQuartier) {
        this.bienQuartier = bienQuartier;
    }

    public String getBienVille() {
        return bienVille;
    }

    public void setBienVille(String bienVille) {
        this.bienVille = bienVille;
    }

    public String getBienPieces() {
        return bienPieces;
    }

    public void setBienPieces(String bienPieces) {
        this.bienPieces = bienPieces;
    }

    public String getBienDescription() {
        return bienDescription;
    }

    public void setBienDescription(String bienDescription) {
        this.bienDescription = bienDescription;
    }

    public String getBienModeComptage() {
        return bienModeComptage;
    }

    public void setBienModeComptage(String bienModeComptage) {
        this.bienModeComptage = bienModeComptage;
    }

    public String getBienMethodeRepartition() {
        return bienMethodeRepartition;
    }

    public void setBienMethodeRepartition(String bienMethodeRepartition) {
        this.bienMethodeRepartition = bienMethodeRepartition;
    }

    public String getBienCompteurElectricite() {
        return bienCompteurElectricite;
    }

    public void setBienCompteurElectricite(String bienCompteurElectricite) {
        this.bienCompteurElectricite = bienCompteurElectricite;
    }

    public String getBienCompteurEau() {
        return bienCompteurEau;
    }

    public void setBienCompteurEau(String bienCompteurEau) {
        this.bienCompteurEau = bienCompteurEau;
    }

    public String getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(String dateDebut) {
        this.dateDebut = dateDebut;
    }

    public String getDateFin() {
        return dateFin;
    }

    public void setDateFin(String dateFin) {
        this.dateFin = dateFin;
    }

    public String getLoyer() {
        return loyer;
    }

    public void setLoyer(String loyer) {
        this.loyer = loyer;
    }

    public String getCaution() {
        return caution;
    }

    public void setCaution(String caution) {
        this.caution = caution;
    }

    public String getCautionMois() {
        return cautionMois;
    }

    public void setCautionMois(String cautionMois) {
        this.cautionMois = cautionMois;
    }

    public String getJourPaiement() {
        return jourPaiement;
    }

    public void setJourPaiement(String jourPaiement) {
        this.jourPaiement = jourPaiement;
    }

    public String getDureePreavis() {
        return dureePreavis;
    }

    public void setDureePreavis(String dureePreavis) {
        this.dureePreavis = dureePreavis;
    }

    /** Préavis en toutes lettres : « trois (3) mois », forme du modèle. */
    public String getDureePreavisNombre() {
        return dureePreavisNombre;
    }

    public void setDureePreavisNombre(String dureePreavisNombre) {
        this.dureePreavisNombre = dureePreavisNombre;
    }

    /** Durée totale du bail, en mois : « vingt-quatre (24) mois ». */
    public String getDureeBail() {
        return dureeBail;
    }

    public void setDureeBail(String dureeBail) {
        this.dureeBail = dureeBail;
    }

    public boolean isRepartitionJirama() {
        return repartitionJirama;
    }

    public void setRepartitionJirama(boolean repartitionJirama) {
        this.repartitionJirama = repartitionJirama;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getStatutCss() {
        return statutCss;
    }

    public void setStatutCss(String statutCss) {
        this.statutCss = statutCss;
    }

    public String getLoyerEnLettres() {
        return loyerEnLettres;
    }

    public void setLoyerEnLettres(String loyerEnLettres) {
        this.loyerEnLettres = loyerEnLettres;
    }

    public List<LigneEcheance> getEcheances() {
        return echeances;
    }

    public void setEcheances(List<LigneEcheance> echeances) {
        this.echeances = echeances == null ? List.of() : echeances;
    }
}
