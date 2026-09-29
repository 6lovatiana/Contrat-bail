package mg.bailtech.model;

/**
 * État comptable d'une échéance mensuelle de loyer.
 * <p>
 * Le mapping est aligné sur l'enum PostgreSQL {@code statut_paiement}.
 */
public enum StatutPaiement {

    A_PAYER("À payer"),
    PAYE("Payé"),
    EN_RETARD("En retard"),
    PARTIEL("Partiel");

    private final String libelle;

    StatutPaiement(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Une échéance est soldée lorsque le montant versé couvre l'intégralité du dû. */
    public boolean isSoldee() {
        return this == PAYE;
    }
}
