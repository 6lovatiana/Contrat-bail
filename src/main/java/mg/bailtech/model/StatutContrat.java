package mg.bailtech.model;

/**
 * Cycle de vie juridique d'un contrat de bail.
 * <p>
 * Le mapping est aligné sur l'enum PostgreSQL {@code statut_contrat} déclaré dans
 * {@code sql/240920260828-database_init.sql}. Les noms Java doivent donc rester
 * identiques aux libellés SQL : c'est cette égalité stricte qui permet à
 * l'index partiel {@code uq_un_seul_contrat_actif_par_logement} (créé avec
 * {@code WHERE statut_actuel = 'EN_COURS'}) de refléter {@link #EN_COURS}.
 */
public enum StatutContrat {

    EN_ATTENTE_SIGNATURE("En attente de signature"),
    EN_COURS("En cours"),
    TERMINE("Terminé"),
    RESILIE("Résilié");

    private final String libelle;

    StatutContrat(String libelle) {
        this.libelle = libelle;
    }

    /** Libellé français destiné à l'affichage (attributs {@code th:text} des templates). */
    public String getLibelle() {
        return libelle;
    }

    /**
     * Vrai pour le seul statut soumis à l'unicité par logement.
     * <p>
     * Règle métier critique : un logement ne peut pas avoir deux baux
     * {@code EN_COURS} simultanés (les contrats {@code TERMINE} et {@code RESILIE}
     * restent autorisés en nombre illimité pour conserver l'historique).
     */
    public boolean isLocationActive() {
        return this == EN_COURS;
    }
}
