package mg.bailtech.model;

/**
 * Politique de gestion du compteur JIRAMA d'un logement.
 * <p>
 * Le mapping est aligné sur l'enum PostgreSQL {@code type_compteur_jirama}.
 */
public enum TypeCompteurJirama {

    UNIQUE("Compteur unique"),
    PARTAGE("Compteur partagé"),
    SOUS_COMPTEUR("Sous-compteur");

    private final String libelle;

    TypeCompteurJirama(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /**
     * Vrai lorsque la facture du compteur principal doit être répartie entre
     * plusieurs occupants (donc lorsque des sous-compteurs sont attendus).
     */
    public boolean necessiteRepartition() {
        return this == PARTAGE || this == SOUS_COMPTEUR;
    }
}
