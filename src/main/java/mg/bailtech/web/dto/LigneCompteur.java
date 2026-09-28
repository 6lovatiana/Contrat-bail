package mg.bailtech.web.dto;

import mg.bailtech.model.Logement;

/**
 * Ligne du tableau « Sous-compteurs &amp; Répartition » du calculateur JIRAMA.
 * <p>
 * Regroupe le compteur JIRAMA d'un bien ({@code logement.compteur_*}) et le bail
 * en cours associé, afin que le gabarit n'accède à aucune association paresseuse.
 */
public class LigneCompteur {

    private final Integer logementId;

    private final String bien;

    private final String locataire;

    private final String typeGestion;

    private final String numeroElectricite;

    private final Integer indexElectricite;

    private final String numeroEau;

    private final Integer indexEau;

    public LigneCompteur(Logement logement, String locataire) {
        this.logementId = logement.getId();
        this.bien = logement.getLibelle();
        this.locataire = locataire;
        this.typeGestion = logement.getJiramaTypeGestion() == null
                ? "" : logement.getJiramaTypeGestion().getLibelle();
        this.numeroElectricite = videSiNull(logement.getCompteurElectriciteNumero());
        this.indexElectricite = logement.getCompteurElectriciteIndexDepart();
        this.numeroEau = videSiNull(logement.getCompteurEauNumero());
        this.indexEau = logement.getCompteurEauIndexDepart();
    }

    public Integer getLogementId() {
        return logementId;
    }

    public String getBien() {
        return bien;
    }

    public String getLocataire() {
        return locataire;
    }

    public String getTypeGestion() {
        return typeGestion;
    }

    public String getNumeroElectricite() {
        return numeroElectricite;
    }

    public Integer getIndexElectricite() {
        return indexElectricite;
    }

    public String getNumeroEau() {
        return numeroEau;
    }

    public Integer getIndexEau() {
        return indexEau;
    }

    private static String videSiNull(String valeur) {
        return valeur == null ? "" : valeur;
    }
}
