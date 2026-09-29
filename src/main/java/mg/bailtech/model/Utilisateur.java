package mg.bailtech.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.bailtech.web.validation.CinNational;

/**
 * Entité {@code utilisateur} — données d'identité d'une personne physique.
 * <p>
 * Le modèle v1 ne distingue pas explicitement bailleur et locataire (aucune colonne
 * de rôle dans {@code sql/240920260828-database_init.sql}) : un utilisateur est
 * bailleur s'il est référencé par {@code logement.id_proprietaire}, et locataire
 * s'il est référencé par {@code contrat_de_bail.id_locataire}. Les requêtes des
 * repositories exploitent justement cette distinction.
 */
@Entity
@Table(name = "utilisateur")
public class Utilisateur {

    /** Longueur maximale d'un numéro de CIN malgache : 12 chiffres. */
    public static final int CIN_LONGUEUR = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_utilisateur")
    private Integer id;

    @NotBlank(message = "{utilisateur.nom.notBlank}")
    @Size(max = 100, message = "{utilisateur.nom.taille}")
    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Size(max = 100, message = "{utilisateur.prenom.taille}")
    @Column(name = "prenom", length = 100)
    private String prenom;

    @NotBlank(message = "{utilisateur.cinNumero.notBlank}")
    @CinNational
    @Size(max = CIN_LONGUEUR, message = "{utilisateur.cinNumero.format}")
    @Column(name = "cin_numero", nullable = false, unique = true, length = CIN_LONGUEUR)
    private String cinNumero;

    @NotNull(message = "{utilisateur.cinDateDelivrance.notNull}")
    @Column(name = "cin_date_delivrance", nullable = false)
    private LocalDate cinDateDelivrance;

    @NotBlank(message = "{utilisateur.cinLieuDelivrance.notBlank}")
    @Size(max = 100, message = "{utilisateur.cinLieuDelivrance.taille}")
    @Column(name = "cin_lieu_delivrance", nullable = false, length = 100)
    private String cinLieuDelivrance;

    @Size(max = 100, message = "{utilisateur.profession.taille}")
    @Column(name = "profession", length = 100)
    private String profession;

    @NotBlank(message = "{utilisateur.adresseActuelle.notBlank}")
    @Column(name = "adresse_actuelle", nullable = false, columnDefinition = "text")
    private String adresseActuelle;

    @NotBlank(message = "{utilisateur.telephone.notBlank}")
    @Size(max = 20, message = "{utilisateur.telephone.taille}")
    @Column(name = "telephone", nullable = false, length = 20)
    private String telephone;

    @NotBlank(message = "{utilisateur.email.notBlank}")
    @Email(message = "{utilisateur.email.format}")
    @Size(max = 150, message = "{utilisateur.email.taille}")
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    /** Empreinte du mot de passe (hachage bcrypt, cf. Conception_base.md). */
    @NotBlank(message = "{utilisateur.motDePasse.notBlank}")
    @Column(name = "mot_de_passe", nullable = false, length = 255)
    private String motDePasse;

    @OneToMany(mappedBy = "proprietaire")
    private List<Logement> logements = new ArrayList<>();

    @OneToMany(mappedBy = "locataire")
    private List<ContratDeBail> contrats = new ArrayList<>();

    protected Utilisateur() {
        // constructeur requis par JPA
    }

    public Utilisateur(String nom, String prenom, String cinNumero) {
        this.nom = nom;
        this.prenom = prenom;
        this.cinNumero = cinNumero;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
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

    public String getMotDePasse() {
        return motDePasse;
    }

    public void setMotDePasse(String motDePasse) {
        this.motDePasse = motDePasse;
    }

    public List<Logement> getLogements() {
        return logements;
    }

    public void setLogements(List<Logement> logements) {
        this.logements = logements;
    }

    public List<ContratDeBail> getContrats() {
        return contrats;
    }

    public void setContrats(List<ContratDeBail> contrats) {
        this.contrats = contrats;
    }

    /** Nom et prénom concaténés, utilisé par les templates et l'aperçu du contrat. */
    public String getNomComplet() {
        if (prenom == null || prenom.isBlank()) {
            return nom;
        }
        return nom + " " + prenom;
    }

    /** Initiales utilisées comme avatar de repli dans les listes du tableau de bord. */
    public String getInitiales() {
        String initiales = nom == null || nom.isBlank() ? "?" : nom.substring(0, 1);
        if (prenom != null && !prenom.isBlank()) {
            initiales += prenom.substring(0, 1);
        }
        return initiales.toUpperCase();
    }

    /** True si cet utilisateur possède au moins un logement (rôle de bailleur). */
    public boolean isBailleur() {
        return !logements.isEmpty();
    }

    /** True si cet utilisateur est preneur d'au moins un bail (rôle de locataire). */
    public boolean isLocataire() {
        return !contrats.isEmpty();
    }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) {
            return true;
        }
        if (autre == null || getClass() != autre.getClass()) {
            return false;
        }
        Utilisateur that = (Utilisateur) autre;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Utilisateur.class.hashCode();
    }

    @Override
    public String toString() {
        return "Utilisateur{id=" + id + ", nom='" + nom + "', cinNumero='" + cinNumero + "'}";
    }
}
