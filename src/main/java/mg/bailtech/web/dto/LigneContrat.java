package mg.bailtech.web.dto;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.Logement;
import mg.bailtech.model.PaiementLoyer;

/**
 * Ligne du tableau « Contrats Récents » du tableau de bord.
 * <p>
 * Les associations sont résolues à la construction ({@code @EntityGraph} côté
 * repository) et les montants mis en forme une fois pour toutes : le gabarit
 * n'a plus qu'à faire du {@code th:text}.
 */
public class LigneContrat {

    private final Integer id;

    private final String locataire;

    private final String bien;

    private final String loyer;

    private final String statut;

    private final String statutCss;

    private final boolean actif;

    public LigneContrat(ContratDeBail contrat) {
        this.id = contrat.getId();
        String nomLocataire = contrat.getLocataire() == null
                ? "Locataire supprimé"
                : contrat.getLocataire().getNomComplet();
        this.locataire = nomLocataire;
        Logement logement = contrat.getLogement();
        this.bien = logement == null ? "Bien indisponible" : logement.getLibelle();
        this.loyer = Format.montantEntier(contrat.getMontantLoyerMga()) + " Ar";
        this.statut = contrat.getStatutActuel() == null ? "" : contrat.getStatutActuel().getLibelle();
        this.actif = contrat.getStatutActuel() != null && contrat.getStatutActuel().isLocationActive();
        this.statutCss = switch (contrat.getStatutActuel() == null ? "" : contrat.getStatutActuel().name()) {
            case "EN_COURS" -> "bg-emerald-100 text-emerald-700";
            case "EN_ATTENTE_SIGNATURE" -> "bg-amber-100 text-amber-700";
            case "RESILIE" -> "bg-red-100 text-red-700";
            default -> "bg-slate-100 text-slate-600";
        };
    }

    public Integer getId() {
        return id;
    }

    public String getLocataire() {
        return locataire;
    }

    public String getBien() {
        return bien;
    }

    public String getLoyer() {
        return loyer;
    }

    public String getStatut() {
        return statut;
    }

    public String getStatutCss() {
        return statutCss;
    }

    public boolean isActif() {
        return actif;
    }
}
