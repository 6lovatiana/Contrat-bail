package mg.bailtech.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;
import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.StatutContrat;
import mg.bailtech.repository.ContratDeBailRepository;
import mg.bailtech.repository.LogementRepository;
import mg.bailtech.repository.UtilisateurRepository;

@Controller
public class ContratController {
    private final ContratDeBailRepository contratRepository;
    private final LogementRepository logementRepository;
    private final UtilisateurRepository utilisateurRepository;

    public ContratController(ContratDeBailRepository contratRepository, LogementRepository logementRepository,
                             UtilisateurRepository utilisateurRepository) {
        this.contratRepository = contratRepository;
        this.logementRepository = logementRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    @GetMapping({"/", "/contrats/create"})
    public String afficherFormulaire(@RequestParam(defaultValue = "1") Integer proprietaireId, Model model) {
        model.addAttribute("contrat", new ContratDeBail());
        alimenterFormulaire(proprietaireId, model);
        return "contract/contract";
    }

    @PostMapping("/contrats")
    public String enregistrer(@Valid @ModelAttribute("contrat") ContratDeBail contrat,
                              BindingResult result, @RequestParam Integer logementId,
                              @RequestParam Integer locataireId, Model model) {
        if (contrat.getDateDebut() != null && contrat.getDateFin() != null
                && !contrat.getDateFin().isAfter(contrat.getDateDebut())) {
            result.rejectValue("dateFin", "dateFin.invalide", "La date de fin doit suivre la date de début.");
        }
        logementRepository.findById(logementId).ifPresentOrElse(contrat::setLogement,
                () -> result.reject("logement.introuvable", "Le logement sélectionné est introuvable."));
        utilisateurRepository.findById(locataireId).ifPresentOrElse(contrat::setLocataire,
                () -> result.reject("locataire.introuvable", "Le locataire sélectionné est introuvable."));
        if (result.hasErrors()) {
            Integer proprietaireId = contrat.getLogement() == null || contrat.getLogement().getProprietaire() == null
                ? 1 : contrat.getLogement().getProprietaire().getId();
            alimenterFormulaire(proprietaireId == null ? 1 : proprietaireId, model);
            return "contract/contract";
        }
        contratRepository.save(contrat);
        return "redirect:/contrats/create?success";
    }

    private void alimenterFormulaire(Integer proprietaireId, Model model) {
        model.addAttribute("logementsDisponibles", logementRepository.findDisponiblesPourProprietaire(
            proprietaireId, StatutContrat.EN_COURS));
        model.addAttribute("locatairesDisponibles", utilisateurRepository.findAll());
    }
}