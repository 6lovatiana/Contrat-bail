package mg.bailtech.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.PaiementLoyer;
import mg.bailtech.model.StatutPaiement;
import mg.bailtech.repository.PaiementLoyerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Génération automatique des échéances mensuelles de loyer.
 * <p>
 * Appelé au passage d'un contrat en {@link mg.bailtech.model.StatutContrat#EN_COURS} :
 * un bail signé doit immédiatement disposer de son plan de facturation.
 *
 * <h2>Règles appliquées</h2>
 * <ul>
 *   <li><strong>Étendue</strong> : une échéance par mois entamé, du mois de
 *       {@code date_debut} jusqu'au mois en cours. On ne facture pas d'avance :
 *       les mois à venir apparaîtront lors des activations suivantes du
 *       générateur, ou lors du passage de mois.</li>
 *   <li><strong>Plancher de la base</strong> : aucune période antérieure à
 *       {@link PaiementLoyer#PERIODE_MIN} n'est produite, la contrainte
 *       {@code CHECK (periode_annee >= 2026)} rejetant l'insertion.</li>
 *   <li><strong>Idempotence</strong> : une période déjà présente pour ce contrat est
 *       ignorée, conformément à {@code uq_paiement_periode}. L'opération peut donc
 *       être rejouée sans créer de doublon.</li>
 *   <li><strong>Statut initial</strong> : {@code EN_RETARD} pour un mois dont la date
 *       limite de paiement (article 3 du contrat) est dépassée, {@code A_PAYER} pour
 *       le mois en cours. Le passage à {@code PAYE} relève de la validation du
 *       règlement par le bailleur.</li>
 * </ul>
 */
@Service
public class EcheanceService {

    private static final Logger LOG = LoggerFactory.getLogger(EcheanceService.class);

    private final PaiementLoyerRepository paiements;

    public EcheanceService(PaiementLoyerRepository paiements) {
        this.paiements = paiements;
    }

    /**
     * Crée les échéances manquantes du contrat.
     *
     * @return les échéances effectivement insérées (vide si le plan était complet)
     */
    @Transactional
    public List<PaiementLoyer> genererEcheances(ContratDeBail contrat) {
        if (contrat == null || contrat.getId() == null) {
            throw new RegleMetierException(null, "contrat.absent",
                    "Le contrat doit être enregistré avant de générer ses échéances.");
        }

        YearMonth premiere = YearMonth.from(contrat.getDateDebut());
        YearMonth derniere = YearMonth.from(dernierePeriodeFacturable(contrat));
        int jourLimite = contrat.getJourPaiementMensuel() == null ? 5 : contrat.getJourPaiementMensuel();

        List<PaiementLoyer> creees = new ArrayList<>();
        int avantPlancher = 0;
        int dejaPresentes = 0;

        for (YearMonth periode = premiere; !periode.isAfter(derniere); periode = periode.plusMonths(1)) {
            if (periode.atDay(1).isBefore(PaiementLoyer.PERIODE_MIN)) {
                avantPlancher++;
                continue;
            }
            if (paiements.existsByContratIdAndPeriodeMoisAndPeriodeAnnee(
                    contrat.getId(), periode.getMonthValue(), periode.getYear())) {
                dejaPresentes++;
                continue;
            }
            creees.add(nouveauPaiement(contrat, periode, jourLimite));
        }

        if (!creees.isEmpty()) {
            paiements.saveAll(creees);
        }
        if (avantPlancher > 0) {
            LOG.warn("Contrat {} : {} période(s) antérieure(s) à {} ignorée(s), la base les refuse.",
                    contrat.getId(), avantPlancher, PaiementLoyer.PERIODE_MIN);
        }
        LOG.info("Contrat {} : {} échéance(s) créée(s), {} déjà présente(s), {} période(s) antérieure(s) "
                        + "à la période plancher ignorée(s).",
                contrat.getId(), creees.size(), dejaPresentes, avantPlancher);
        return creees;
    }

    /**
     * Dernière période facturable à la date du jour : le mois courant, plafonné à la
     * fin du bail. Un contrat déjà échu ne produit donc pas d'échéance future.
     */
    private LocalDate dernierePeriodeFacturable(ContratDeBail contrat) {
        LocalDate aujourdhui = LocalDate.now();
        if (aujourdhui.isAfter(contrat.getDateFin())) {
            return contrat.getDateFin();
        }
        return aujourdhui;
    }

    private PaiementLoyer nouveauPaiement(ContratDeBail contrat, YearMonth periode, int jourLimite) {
        PaiementLoyer paiement = new PaiementLoyer(periode.getMonthValue(), periode.getYear(),
                contrat.getMontantLoyerMga());
        paiement.setContrat(contrat);
        paiement.setMontantPaye(java.math.BigDecimal.ZERO);
        paiement.setStatut(statutInitial(periode, jourLimite));
        return paiement;
    }

    /** {@code EN_RETARD} dès que la date limite du mois est dépassée, sinon {@code A_PAYER}. */
    private StatutPaiement statutInitial(YearMonth periode, int jourLimite) {
        LocalDate limite;
        try {
            limite = periode.atDay(jourLimite);
        } catch (java.time.DateTimeException e) {
            // Ex. jour 31 sur un mois de 30 jours : on retient le dernier jour.
            limite = periode.atEndOfMonth();
        }
        return LocalDate.now().isAfter(limite) ? StatutPaiement.EN_RETARD : StatutPaiement.A_PAYER;
    }
}
