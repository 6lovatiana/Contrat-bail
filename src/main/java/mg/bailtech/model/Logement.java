package mg.bailtech.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "logement")
public class Logement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_logement") private Integer id;
    @NotBlank @Size(max = 50) @Column(name = "adresse_lot") private String adresseLot;
    @NotBlank @Size(max = 100) @Column(name = "quartier_fokontany") private String quartierFokontany;
    @NotBlank @Size(max = 100) private String ville;
    @NotNull @Positive @Column(name = "nombre_pieces_principales") private Integer nombrePiecesPrincipales;
    @Column(name = "description_consistance") private String descriptionConsistance;
    @Column(name = "compteur_eau_numero") private String compteurEauNumero;
    @Column(name = "compteur_eau_index_depart") private Integer compteurEauIndexDepart = 0;
    @Column(name = "compteur_electricite_numero") private String compteurElectriciteNumero;
    @Column(name = "compteur_electricite_index_depart") private Integer compteurElectriciteIndexDepart = 0;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "jirama_type_gestion", columnDefinition = "type_compteur_jirama")
    private TypeCompteurJirama jiramaTypeGestion = TypeCompteurJirama.UNIQUE;
    @Column(name = "jirama_methode_repartition") private String jiramaMethodeRepartition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_proprietaire") private Utilisateur proprietaire;
    @OneToMany(mappedBy = "logement") private List<ContratDeBail> contrats = new ArrayList<>();

    public Integer getId() { return id; }
    public String getAdresseLot() { return adresseLot; }
    public void setAdresseLot(String v) { adresseLot = v; }
    public String getQuartierFokontany() { return quartierFokontany; }
    public void setQuartierFokontany(String v) { quartierFokontany = v; }
    public String getVille() { return ville; }
    public void setVille(String v) { ville = v; }
    public Integer getNombrePiecesPrincipales() { return nombrePiecesPrincipales; }
    public void setNombrePiecesPrincipales(Integer v) { nombrePiecesPrincipales = v; }
    public String getDescriptionConsistance() { return descriptionConsistance; }
    public void setDescriptionConsistance(String v) { descriptionConsistance = v; }
    public TypeCompteurJirama getJiramaTypeGestion() { return jiramaTypeGestion; }
    public void setJiramaTypeGestion(TypeCompteurJirama v) { jiramaTypeGestion = v; }
    public Utilisateur getProprietaire() { return proprietaire; }
    public void setProprietaire(Utilisateur v) { proprietaire = v; }
}