package mg.bailtech.web;

import jakarta.validation.Valid;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.Logement;
import mg.bailtech.model.Utilisateur;
import mg.bailtech.service.ContratService;
import mg.bailtech.service.RegleMetierException;
import mg.bailtech.web.dto.ContratForm;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Générateur de contrat de bail : seule page à exposer un formulaire de saisie
 * piloté par {@link ContratForm}.
 * <p>
 * Le gabarit {@code contract/contract.html} est relié à ce formulaire par
 * {@code th:object="${contratForm}"}, {@code th:field="*{…}"} et
 * {@code th:action="@{/contrats}"} ; les listes déroulantes sont alimentées par
 * {@code th:each} à partir des attributs {@code logementsDisponibles} et
 * {@code locatairesDisponibles}.
 */
@Controller
public class ContratController {

    private static final Logger LOG = LoggerFactory.getLogger(ContratController.class);

    private static final String VUE_CONTRAT = "contract/contract";

    private final ContratService contratService;

    public ContratController(ContratService contratService) {
        this.contratService = contratService;
    }

    @GetMapping({"/", "/contrats", "/contrats/create"})
    public String afficherFormulaire(@RequestParam(name = "bailleurId", required = false) Integer bailleurId,
                                     Model model) {
        ContratForm form = new ContratForm();
        try {
            Utilisateur bailleur = contratService.utilisateurCourant(bailleurId);
            contratService.preparerFormulaire(form, bailleur);
            alimenterModel(form, bailleur, model);
        } catch (RegleMetierException e) {
            // Base vide : on rend le formulaire vide avec le motif du blocage.
            model.addAttribute("contratForm", form);
            model.addAttribute("bailleur", null);
            model.addAttribute("logementsDisponibles", List.of());
            model.addAttribute("locatairesDisponibles", List.of());
            model.addAttribute("nombreLogementsDisponibles", 0);
            model.addAttribute("erreurInitiale", e.getMessage());
        }
        return VUE_CONTRAT;
    }

    @PostMapping("/contrats")
    public String enregistrer(@Valid @ModelAttribute("contratForm") ContratForm form,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        Utilisateur bailleur;
        try {
            bailleur = contratService.utilisateurCourant(form.getBailleurId());
        } catch (RegleMetierException e) {
            bindingResult.reject(e.getCode(), e.getMessage());
            alimenterModelVide(form, model, e.getMessage());
            return VUE_CONTRAT;
        }

        if (bindingResult.hasErrors()) {
            contratService.remplirApercu(form, bailleur);
            alimenterModel(form, bailleur, model);
            return VUE_CONTRAT;
        }

        try {
            ContratDeBail contrat = contratService.enregistrer(form, bailleur);
            redirectAttributes.addFlashAttribute("contratEnregistre", contrat);
            redirectAttributes.addFlashAttribute("messageConfirmation",
                    "Contrat n°" + contrat.getId() + " enregistré : il attend la signature des parties.");
            return "redirect:/contrats/create";
        } catch (RegleMetierException e) {
            if (e.getChamp() != null) {
                bindingResult.rejectValue(e.getChamp(), e.getCode(), e.getMessage());
            } else {
                bindingResult.reject(e.getCode(), e.getMessage());
            }
        } catch (DataIntegrityViolationException e) {
            // Filet de sécurité : index partiel uq_un_seul_contrat_actif_par_logement,
            // unicité de la CIN ou de l'email, contraintes de date.
            LOG.warn("Enregistrement du contrat refusé par une contrainte de la base", e);
            bindingResult.reject("enregistrement.refuse", "L'enregistrement a été refusé par la base de données : "
                    + "vérifiez le numéro de CIN, l'adresse email et les dates du bail.");
        }

        contratService.remplirApercu(form, bailleur);
        alimenterModel(form, bailleur, model);
        return VUE_CONTRAT;
    }

    /** Alimente les listes déroulantes et les messages contextuels du formulaire. */
    private void alimenterModel(ContratForm form, Utilisateur bailleur, Model model) {
        List<Logement> logements = contratService.logementsDisponibles(bailleur);
        model.addAttribute("contratForm", form);
        model.addAttribute("bailleur", bailleur);
        model.addAttribute("logementsDisponibles", logements);
        model.addAttribute("locatairesDisponibles", contratService.locatairesDisponibles(bailleur));
        model.addAttribute("nombreLogementsDisponibles", logements.size());
    }

    /** Variante utilisée quand aucun bailleur ne peut être résolu. */
    private void alimenterModelVide(ContratForm form, Model model, String message) {
        model.addAttribute("contratForm", form);
        model.addAttribute("bailleur", null);
        model.addAttribute("logementsDisponibles", List.of());
        model.addAttribute("locatairesDisponibles", List.of());
        model.addAttribute("nombreLogementsDisponibles", 0);
        model.addAttribute("erreurInitiale", message);
    }
}
