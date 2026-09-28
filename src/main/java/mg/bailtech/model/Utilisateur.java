package mg.bailtech.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "utilisateur")
public class Utilisateur {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_utilisateur")
    private Integer id;
    @NotBlank @Size(max = 100) private String nom;
    @Size(max = 100) private String prenom;
    @NotBlank @Pattern(regexp = "\\d{12}") @Size(max = 12)
    @Column(name = "cin_numero", unique = true, length = 12) private String cinNumero;
    @NotNull @Column(name = "cin_date_delivrance") private LocalDate cinDateDelivrance;
    @NotBlank @Size(max = 100) @Column(name = "cin_lieu_delivrance") private String cinLieuDelivrance;
    @Size(max = 100) private String profession;
    @NotBlank @Column(name = "adresse_actuelle") private String adresseActuelle;
    @NotBlank @Size(max = 20) private String telephone;
    @NotBlank @Email @Size(max = 150) @Column(unique = true) private String email;
    @NotBlank @Size(max = 255) @Column(name = "mot_de_passe") private String motDePasse;

    @OneToMany(mappedBy = "proprietaire") private List<Logement> logements = new ArrayList<>();
    @OneToMany(mappedBy = "locataire") private List<ContratDeBail> contrats = new ArrayList<>();

    public Integer getId() { return id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public String getCinNumero() { return cinNumero; }
    public void setCinNumero(String cinNumero) { this.cinNumero = cinNumero; }
    public LocalDate getCinDateDelivrance() { return cinDateDelivrance; }
    public void setCinDateDelivrance(LocalDate value) { this.cinDateDelivrance = value; }
    public String getCinLieuDelivrance() { return cinLieuDelivrance; }
    public void setCinLieuDelivrance(String value) { this.cinLieuDelivrance = value; }
    public String getProfession() { return profession; }
    public void setProfession(String value) { this.profession = value; }
    public String getAdresseActuelle() { return adresseActuelle; }
    public void setAdresseActuelle(String value) { this.adresseActuelle = value; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String value) { this.telephone = value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { this.email = value; }
    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String value) { this.motDePasse = value; }
}