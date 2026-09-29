package mg.bailtech.web;

import java.util.List;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.Utilisateur;
import mg.bailtech.repository.ContratDeBailRepository;
import mg.bailtech.repository.PaiementLoyerRepository;
import mg.bailtech.service.ContratPdfService;
import mg.bailtech.service.ContratService;
import mg.bailtech.service.RegleMetierException;
import mg.bailtech.web.dto.DocumentPdf;
import mg.bailtech.web.dto.LigneContrat;
import mg.bailtech.web.dto.LigneEcheancier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Visionneuse des contrats : consultation, téléchargement du PDF et passation du
 * bail au statut {@code EN_COURS}.
 *
 * <h2>Routage</h2>
 * <ul>
 *   <li>{@code GET /contrats} : liste des contrats du bailleur ;</li>
 *   <li>{@code GET /contrats/{id}} : fiche du contrat et de son échéancier ;</li>
 *   <li>{@code GET /contrats/{id}/pdf} : PDF affiché dans le navigateur ;</li>
 *   <li>{@code GET /contrats/{id}/pdf/telecharger} : PDF en pièce jointe ;</li>
 *   <li>{@code POST /contrats/{id}/signature} : le bail est signé, il passe en
 *       {@code EN_COURS} et ses échéances mensuelles sont générées.</li>
 * </ul>
 */
@Controller
public class VisionneuseController {

    private static final Logger LOG = LoggerFactory.getLogger(VisionneuseController.class);

    private static final String VUE_LISTE = "contrat/contrats";
    private static final String VUE_FICHE = "contrat/contrat-fiche";

    private final ContratDeBailRepository contrats;
    private final PaiementLoyerRepository paiements;
    private final ContratService contratService;
    private final ContratPdfService contratPdfService;

    public VisionneuseController(ContratDeBailRepository contrats,
                                 PaiementLoyerRepository paiements,
                                 ContratService contratService,
                                 ContratPdfService contratPdfService) {
        this.contrats = contrats;
        this.paiements = paiements;
        this.contratService = contratService;
        this.contratPdfService = contratPdfService;
    }

    // ==================================================================
    // Liste
    // ==================================================================

    @GetMapping("/contrats")
    public String lister(@RequestParam(name = "bailleurId", required = false) Integer bailleurId,
                         Model model) {
        Utilisateur bailleur = bailleur(bailleurId, model);
        if (bailleur == null) {
            return VUE_LISTE;
        }
        List<LigneContrat> lignes = contrats.findContratsDuBailleur(bailleur.getId())
                .stream()
                .map(LigneContrat::new)
                .toList();
        model.addAttribute("contrats", lignes);
        model.addAttribute("nbContrats", lignes.size());
        return VUE_LISTE;
    }

    // ==================================================================
    // Fiche d'un contrat
    // ==================================================================

    @GetMapping("/contrats/{id}")
    public String fiche(@PathVariable("id") Integer id,
                        @RequestParam(name = "bailleurId", required = false) Integer bailleurId,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        Utilisateur bailleur = bailleur(bailleurId, model);
        ContratDeBail contrat = contrats.findById(id).orElse(null);
        if (contrat == null) {
            redirectAttributes.addFlashAttribute("messageErreur", "Le contrat n° " + id + " est introuvable.");
            return "redirect:/contrats";
        }
        if (bailleur != null && !appartientA(contrat, bailleur)) {
            redirectAttributes.addFlashAttribute("messageErreur",
                    "Ce contrat n'appartient pas à votre parc immobilier.");
            return "redirect:/contrats";
        }

        model.addAttribute("contratId", id);
        model.addAttribute("statut", contrat.getStatutActuel() == null ? "" : contrat.getStatutActuel().getLibelle());
        model.addAttribute("statutCss", cssStatut(contrat));
        model.addAttribute("activable", contrat.getStatutActuel() != null
                && !contrat.getStatutActuel().isLocationActive());
        model.addAttribute("echeances", paiements
                .findByContratIdOrderByPeriodeAnneeDescPeriodeMoisDesc(id)
                .stream()
                .map(LigneEcheancier::new)
                .toList());
        return VUE_FICHE;
    }

    // ==================================================================
    // PDF
    // ==================================================================

    /** PDF affiché directement dans le navigateur (visionneuse). */
    @GetMapping("/contrats/{id}/pdf")
    public ResponseEntity<byte[]> afficherPdf(@PathVariable("id") Integer id) {
        return ContratController.reponse(contratPdfService.generer(id), false);
    }

    /** PDF proposé en pièce jointe. */
    @GetMapping("/contrats/{id}/pdf/telecharger")
    public ResponseEntity<byte[]> telechargerPdf(@PathVariable("id") Integer id) {
        return ContratController.reponse(contratPdfService.generer(id), true);
    }

    // ==================================================================
    // Signature : passage en EN_COURS et génération des échéances
    // ==================================================================

    /**
     * Le bail est signé et visé : le contrat passe en {@code EN_COURS}, ce qui
     * déclenche la génération de ses échéances mensuelles.
     */
    @PostMapping("/contrats/{id}/signature")
    public String signer(@PathVariable("id") Integer id,
                         @RequestParam(name = "bailleurId", required = false) Integer bailleurId,
                         RedirectAttributes redirectAttributes) {
        try {
            Utilisateur bailleur = contratService.utilisateurCourant(bailleurId);
            ContratDeBail contrat = contratService.activer(id, bailleur);
            redirectAttributes.addFlashAttribute("messageConfirmation",
                    "Contrat n° " + contrat.getId() + " signé : la location est effective et les échéances "
                            + "mensuelles ont été générées.");
        } catch (RegleMetierException e) {
            LOG.warn("Signature du contrat n° {} refusée : {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("messageErreur", e.getMessage());
        }
        return "redirect:/contrats/" + id;
    }

    // ==================================================================
    // Outils
    // ==================================================================

    private Utilisateur bailleur(Integer bailleurId, Model model) {
        try {
            Utilisateur bailleur = contratService.utilisateurCourant(bailleurId);
            model.addAttribute("bailleur", bailleur);
            return bailleur;
        } catch (RegleMetierException e) {
            model.addAttribute("bailleur", null);
            model.addAttribute("messageVide", e.getMessage());
            return null;
        }
    }

    private boolean appartientA(ContratDeBail contrat, Utilisateur bailleur) {
        return contrat.getLogement() != null
                && contrat.getLogement().getProprietaire() != null
                && contrat.getLogement().getProprietaire().getId().equals(bailleur.getId());
    }

    private String cssStatut(ContratDeBail contrat) {
        if (contrat.getStatutActuel() == null) {
            return "bg-slate-100 text-slate-600";
        }
        return switch (contrat.getStatutActuel().name()) {
            case "EN_COURS" -> "bg-emerald-100 text-emerald-700";
            case "EN_ATTENTE_SIGNATURE" -> "bg-amber-100 text-amber-700";
            case "RESILIE" -> "bg-red-100 text-red-700";
            default -> "bg-slate-100 text-slate-600";
        };
    }
}
