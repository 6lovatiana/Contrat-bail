package mg.bailtech.service;

import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.PaiementLoyer;
import mg.bailtech.model.StatutContrat;
import mg.bailtech.model.StatutPaiement;
import mg.bailtech.repository.ContratDeBailRepository;
import mg.bailtech.repository.PaiementLoyerRepository;

@Service
public class ContratService {
    private final ContratDeBailRepository contratRepository;
    private final PaiementLoyerRepository paiementRepository;

    public ContratService(ContratDeBailRepository contratRepository,
                          PaiementLoyerRepository paiementRepository) {
        this.contratRepository = contratRepository;
        this.paiementRepository = paiementRepository;
    }

    @Transactional
    public ContratDeBail enregistrer(ContratDeBail contrat) {
        if (contrat.getDateDebut() == null || contrat.getDateFin() == null
                || !contrat.getDateFin().isAfter(contrat.getDateDebut())) {
            throw new IllegalArgumentException("La date de fin doit suivre la date de début.");
        }

        ContratDeBail saved = contratRepository.save(contrat);
        if (saved.getStatutActuel() == StatutContrat.EN_COURS) {
            genererEcheances(saved);
        }
        return saved;
    }

    @Transactional
    public ContratDeBail passerEnCours(Integer contratId) {
        ContratDeBail contrat = contratRepository.findById(contratId)
                .orElseThrow(() -> new IllegalArgumentException("Contrat introuvable : " + contratId));
        contrat.setStatutActuel(StatutContrat.EN_COURS);
        genererEcheances(contrat);
        return contratRepository.save(contrat);
    }

    @Transactional(readOnly = true)
    public ContratDeBail trouver(Integer contratId) {
        return contratRepository.findDetailsById(contratId)
                .orElseThrow(() -> new IllegalArgumentException("Contrat introuvable : " + contratId));
    }

    @Transactional(readOnly = true)
    public List<PaiementLoyer> trouverPaiements(Integer contratId) {
        return paiementRepository.findByContratIdOrderByPeriodeAnneeDescPeriodeMoisDesc(contratId);
    }

    private void genererEcheances(ContratDeBail contrat) {
        if (!paiementRepository.findByContratIdOrderByPeriodeAnneeDescPeriodeMoisDesc(contrat.getId()).isEmpty()) {
            return;
        }

        YearMonth mois = YearMonth.from(contrat.getDateDebut());
        YearMonth dernierMois = YearMonth.from(contrat.getDateFin());
        while (!mois.isAfter(dernierMois)) {
            PaiementLoyer paiement = new PaiementLoyer();
            paiement.setPeriodeMois(mois.getMonthValue());
            paiement.setPeriodeAnnee(mois.getYear());
            paiement.setMontantAttendu(contrat.getMontantLoyerMga());
            paiement.setStatut(StatutPaiement.A_PAYER);
            contrat.ajouterPaiement(paiement);
            mois = mois.plusMonths(1);
        }
        contratRepository.save(contrat);
    }
}