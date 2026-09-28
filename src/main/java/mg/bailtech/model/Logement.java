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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité {@code logement} — le bien immobilier mis en location.
 * <p>
 * Un logement appartient à un seul bailleur ({@code id_proprietaire}, FK
 * {@code ON DELETE RESTRICT}) et peut porter dans le temps plusieurs contrats,
 * dont un seul à la fois peut être {@code EN_COURS} (index partiel
 * {@code uq_un_seul_contrat_actif_par_logement}).
 */
@Entity
@Table(name = "logement")
public class Logement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_logement")
    private Integer id;

    @NotBlank(message = "{logement.adresseLot.notBlank}")
    @Size(max = 50, message = "{logement.adresseLot.taille}")
    @Column(name = "adresse_lot", nullable = false, length = 50)
    private String adresseLot;

    @NotBlank(message = "{logement.quartierFokontany.notBlank}")
    @Size(max = 100, message = "{logement.quartierFokontany.taille}")
    @Column(name = "quartier_fokontany", nullable = false, length = 100)
    private String quartierFokontany;

    @NotBlank(message = "{logement.ville.notBlank}")
    @Size(max = 100, message = "{logement.ville.taille}")
    @Column(name = "ville", nullable = false, length = 100)
    private String ville;

    @NotNull(message = "{logement.nombrePiecesPrincipales.notNull}")
    @Min(value = 1, message = "{logement.nombrePiecesPrincipales.min}")
    @Column(name = "nombre_pieces_principales", nullable = false)
    private Integer nombrePiecesPrincipales;

    @Column(name = "description_consistance", columnDefinition = "text")
    private String descriptionConsistance;

    @Size(max = 50, message = "{logement.compteurEauNumero.taille}")
    @Column(name = "compteur_eau_numero", length = 50)
    private String compteurEauNumero;

    @Min(value = 0, message = "{logement.indexDepart.min}")
    @Column(name = "compteur_eau_index_depart")
    private Integer compteurEauIndexDepart = 0;

    @Size(max = 50, message = "{logement.compteurEauNumero.taille}")
    @Column(name = "compteur_electricite_numero", length = 50)
    private String compteurElectriciteNumero;

    @Min(value = 0, message = "{logement.indexDepart.min}")
    @Column(name = "compteur_electricite_index_depart")
    private Integer compteurElectriciteIndexDepart = 0;

    @NotNull(message = "{logement.jiramaTypeGestion.notNull}")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "jirama_type_gestion", nullable = false, columnDefinition = "type_compteur_jirama")
    private TypeCompteurJirama jiramaTypeGestion = TypeCompteurJirama.UNIQUE;

    @Column(name = "jirama_methode_repartition", columnDefinition = "text")
    private String jiramaMethodeRepartition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proprietaire", nullable = false)
    private Utilisateur proprietaire;

    @OneToMany(mappedBy = "logement")
    private List<ContratDeBail> contrats = new ArrayList<>();

    protected Logement() {
        // constructeur requis par JPA
    }

    public Logement(String adresseLot, String quartierFokontany, String ville, Integer nombrePiecesPrincipales) {
        this.adresseLot = adresseLot;
        this.quartierFokontany = quartierFokontany;
        this.ville = ville;
        this.nombrePiecesPrincipales = nombrePiecesPrincipales;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getAdresseLot() {
        return adresseLot;
    }

    public void setAdresseLot(String adresseLot) {
        this.adresseLot = adresseLot;
    }

    public String getQuartierFokontany() {
        return quartierFokontany;
    }

    public void setQuartierFokontany(String quartierFokontany) {
        this.quartierFokontany = quartierFokontany;
    }

    public String getVille() {
        return ville;
    }

    public void setVille(String ville) {
        this.ville = ville;
    }

    public Integer getNombrePiecesPrincipales() {
        return nombrePiecesPrincipales;
    }

    public void setNombrePiecesPrincipales(Integer nombrePiecesPrincipales) {
        this.nombrePiecesPrincipales = nombrePiecesPrincipales;
    }

    public String getDescriptionConsistance() {
        return descriptionConsistance;
    }

    public void setDescriptionConsistance(String descriptionConsistance) {
        this.descriptionConsistance = descriptionConsistance;
    }

    public String getCompteurEauNumero() {
        return compteurEauNumero;
    }

    public void setCompteurEauNumero(String compteurEauNumero) {
        this.compteurEauNumero = compteurEauNumero;
    }

    public Integer getCompteurEauIndexDepart() {
        return compteurEauIndexDepart;
    }

    public void setCompteurEauIndexDepart(Integer compteurEauIndexDepart) {
        this.compteurEauIndexDepart = compteurEauIndexDepart;
    }

    public String getCompteurElectriciteNumero() {
        return compteurElectriciteNumero;
    }

    public void setCompteurElectriciteNumero(String compteurElectriciteNumero) {
        this.compteurElectriciteNumero = compteurElectriciteNumero;
    }

    public Integer getCompteurElectriciteIndexDepart() {
        return compteurElectriciteIndexDepart;
    }

    public void setCompteurElectriciteIndexDepart(Integer compteurElectriciteIndexDepart) {
        this.compteurElectriciteIndexDepart = compteurElectriciteIndexDepart;
    }

    public TypeCompteurJirama getJiramaTypeGestion() {
        return jiramaTypeGestion;
    }

    public void setJiramaTypeGestion(TypeCompteurJirama jiramaTypeGestion) {
        this.jiramaTypeGestion = jiramaTypeGestion;
    }

    public String getJiramaMethodeRepartition() {
        return jiramaMethodeRepartition;
    }

    public void setJiramaMethodeRepartition(String jiramaMethodeRepartition) {
        this.jiramaMethodeRepartition = jiramaMethodeRepartition;
    }

    public Utilisateur getProprietaire() {
        return proprietaire;
    }

    public void setProprietaire(Utilisateur proprietaire) {
        this.proprietaire = proprietaire;
    }

    public List<ContratDeBail> getContrats() {
        return contrats;
    }

    public void setContrats(List<ContratDeBail> contrats) {
        this.contrats = contrats;
    }

    /** Adresse postale complète du bien, telle qu'elle apparaît à l'article 1 du contrat. */
    public String getAdresseComplete() {
        return "Lot " + adresseLot + ", " + quartierFokontany + ", " + ville;
    }

    /** Libellé court pour les listes déroulantes : « Lot IVG 22 – Ivandry, Antananarivo ». */
    public String getLibelle() {
        return "Lot " + adresseLot + " – " + quartierFokontany + ", " + ville;
    }

    /** Type de bien déduit du nombre de pièces principales (dénominations du design). */
    public String getTypeDeBien() {
        if (nombrePiecesPrincipales == null) {
            return "logement";
        }
        return switch (nombrePiecesPrincipales) {
            case 1 -> "studio";
            case 2 -> "appartement T2";
            case 3 -> "appartement T3";
            case 4 -> "appartement T4";
            default -> "appartement T" + nombrePiecesPrincipales;
        };
    }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) {
            return true;
        }
        if (autre == null || getClass() != autre.getClass()) {
            return false;
        }
        Logement that = (Logement) autre;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Logement.class.hashCode();
    }

    @Override
    public String toString() {
        return "Logement{id=" + id + ", libelle='" + getLibelle() + "'}";
    }
}
