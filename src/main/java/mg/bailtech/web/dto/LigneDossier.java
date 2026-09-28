package mg.bailtech.web.dto;

import mg.bailtech.model.ContratDeBail;

/**
 * Ligne du tableau « Gestion des Documents Locataires » (coffre-fort numérique).
 * <p>
 * <strong>Périmètre v1 :</strong> le modèle physique ne comporte pas encore de table
 * de pièces justificatives. La ligne expose donc l'identité réelle du locataire
 * (nom, CIN masqué, Contacts) et l'état de son dossier déduit de la base — bail en
 * cours ou non — et non un inventaire de scans, qui relèvera du module de stockage
 * chiffré.
 */
public class LigneDossier {

    private final Integer locataireId;

    private final String nom;

    private final String initiales;

    private final String cin;

    private final String telephone;

    private final String email;

    private final String bien;

    private final String statut;

    private final String statutCss;

    private final boolean dossierComplet;

    public LigneDossier(UtilisateurVue utilisateur, ContratDeBail contratActif) {
        this.locataireId = utilisateur.id();
        this.nom = utilisateur.nomComplet();
        this.initiales = utilisateur.initiales();
        this.cin = Format.cinMasque(utilisateur.cin());
        this.telephone = utilisateur.telephone() == null ? "" : utilisateur.telephone();
        this.email = utilisateur.email() == null ? "" : utilisateur.email();
        this.dossierComplet = contratActif != null;
        this.bien = contratActif == null || contratActif.getLogement() == null
                ? "Aucun bail en cours"
                : contratActif.getLogement().getLibelle();
        this.statut = contratActif == null ? "Pièces manquantes" : "Bail en cours";
        this.statutCss = contratActif == null
                ? "bg-amber-100 text-amber-700"
                : "bg-emerald-100 text-emerald-700";
    }

    public Integer getLocataireId() {
        return locataireId;
    }

    public String getNom() {
        return nom;
    }

    public String getInitiales() {
        return initiales;
    }

    public String getCin() {
        return cin;
    }

    public String getTelephone() {
        return telephone;
    }

    public String getEmail() {
        return email;
    }

    public String getBien() {
        return bien;
    }

    public String getStatut() {
        return statut;
    }

    public String getStatutCss() {
        return statutCss;
    }

    public boolean isDossierComplet() {
        return dossierComplet;
    }

    /**
     * Vue allégée d'un utilisateur, construite par le contrôleur : évite d'exposer
     * l'entité {@code Utilisateur} (avec son mot de passe) au gabarit.
     */
    public record UtilisateurVue(Integer id, String nomComplet, String initiales, String cin,
                                  String telephone, String email) {
    }
}
