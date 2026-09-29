package mg.bailtech.web.dto;

import mg.bailtech.model.PaiementLoyer;

/**
 * Ligne de l'échéancier affiché dans la fiche d'un contrat et dans le tableau de
 * bord des paiements.
 */
public class LigneEcheancier {

    private final String periode;
    private final String montantDu;
    private final String montantVerse;
    private final String resteDu;
    private final String statut;
    private final String statutCss;
    private final boolean soldee;
    private final String datePaiement;

    public LigneEcheancier(PaiementLoyer paiement) {
        this.periode = paiement.getPeriodeLisible();
        this.montantDu = Format.montant(paiement.getMontantAttendu());
        this.montantVerse = Format.montant(paiement.getMontantPaye());
        this.resteDu = Format.montant(paiement.getResteDu());
        this.soldee = paiement.isSoldee();
        this.datePaiement = Format.date(paiement.getDatePaiementEffectif());
        this.statut = paiement.getStatut() == null ? "" : paiement.getStatut().getLibelle();
        this.statutCss = switch (paiement.getStatut() == null ? "" : paiement.getStatut().name()) {
            case "PAYE" -> "bg-emerald-100 text-emerald-700";
            case "EN_RETARD" -> "bg-red-100 text-red-700";
            case "PARTIEL" -> "bg-amber-100 text-amber-700";
            default -> "bg-slate-100 text-slate-600";
        };
    }

    public String getPeriode() {
        return periode;
    }

    public String getMontantDu() {
        return montantDu;
    }

    public String getMontantVerse() {
        return montantVerse;
    }

    public String getResteDu() {
        return resteDu;
    }

    public String getStatut() {
        return statut;
    }

    public String getStatutCss() {
        return statutCss;
    }

    public boolean isSoldee() {
        return soldee;
    }

    public String getDatePaiement() {
        return datePaiement;
    }
}
