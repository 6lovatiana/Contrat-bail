package mg.bailtech.web;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;
import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.StatutContrat;
import mg.bailtech.repository.ContratDeBailRepository;
import mg.bailtech.repository.LogementRepository;
import mg.bailtech.repository.UtilisateurRepository;
import mg.bailtech.service.ContratService;
import mg.bailtech.service.PdfContratService;

@Controller
public class ContratController {
    private final ContratDeBailRepository contratRepository;
    private final LogementRepository logementRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ContratService contratService;
    private final PdfContratService pdfContratService;

    public ContratController(ContratDeBailRepository contratRepository,
                             LogementRepository logementRepository,
                             UtilisateurRepository utilisateurRepository,
                             ContratService contratService,
                             PdfContratService pdfContratService) {
        this.contratRepository = contratRepository;
        this.logementRepository = logementRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.contratService = contratService;
        this.pdfContratService = pdfContratService;
    }

    @GetMapping({"/", "/contrats/create", "/contrats/create-wizard"})
    public String afficherFormulaire(@RequestParam(defaultValue = "1") Integer proprietaireId, Model model) {
        model.addAttribute("contrat", new ContratDeBail());
        alimenterFormulaire(proprietaireId, model);
        return "contract/contract";
    }

    @GetMapping("/dashboard")
    public String tableauDeBord(Model model) {
        model.addAttribute("contrats", contratRepository.findAll());
        return "dashboard/dashboard";
    }

    @PostMapping("/contrats")
    public String enregistrerContrat(@Valid @ModelAttribute("contrat") ContratDeBail contrat,
                                     BindingResult result, @RequestParam Integer logementId,
                                     @RequestParam Integer locataireId, Model model) {
        return traiterEnregistrement(contrat, result, logementId, locataireId, model);
    }

    @PostMapping("/enregistrer")
    public String enregistrer(@Valid @ModelAttribute("contrat") ContratDeBail contrat,
                              BindingResult result, @RequestParam Integer logementId,
                              @RequestParam Integer locataireId, Model model) {
        return traiterEnregistrement(contrat, result, logementId, locataireId, model);
    }

    @GetMapping("/contrats/{id}")
    public String visionneuse(@PathVariable Integer id, Model model) {
        model.addAttribute("contrat", contratService.trouver(id));
        model.addAttribute("paiements", contratService.trouverPaiements(id));
        return "contract/viewer";
    }

    @GetMapping("/contrats/{id}/pdf")
    public ResponseEntity<ByteArrayResource> telechargerPdf(@PathVariable Integer id) {
        byte[] pdf = pdfContratService.genererPourContrat(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("contrat-bail-" + id + ".pdf")
                .build());
        return ResponseEntity.ok().headers(headers).contentLength(pdf.length)
                .body(new ByteArrayResource(pdf));
    }

    private String traiterEnregistrement(ContratDeBail contrat, BindingResult result,
                                         Integer logementId, Integer locataireId, Model model) {
        if (contrat.getDateDebut() != null && contrat.getDateFin() != null
                && !contrat.getDateFin().isAfter(contrat.getDateDebut())) {
            result.rejectValue("dateFin", "dateFin.invalide", "La date de fin doit suivre la date de début.");
        }
        logementRepository.findById(logementId).ifPresentOrElse(contrat::setLogement,
                () -> result.reject("logement.introuvable", "Le logement sélectionné est introuvable."));
        utilisateurRepository.findById(locataireId).ifPresentOrElse(contrat::setLocataire,
                () -> result.reject("locataire.introuvable", "Le locataire sélectionné est introuvable."));
        if (result.hasErrors()) {
            Integer proprietaireId = 1;
            if (contrat.getLogement() != null && contrat.getLogement().getProprietaire() != null
                && contrat.getLogement().getProprietaire().getId() != null) {
            proprietaireId = contrat.getLogement().getProprietaire().getId();
            }
            alimenterFormulaire(proprietaireId, model);
            return "contract/contract";
        }
        ContratDeBail saved = contratService.enregistrer(contrat);
        return "redirect:/contrats/" + saved.getId();
    }

    private void alimenterFormulaire(Integer proprietaireId, Model model) {
        model.addAttribute("logementsDisponibles", logementRepository.findDisponiblesPourProprietaire(
                proprietaireId, StatutContrat.EN_COURS));
        model.addAttribute("locatairesDisponibles", utilisateurRepository.findAll());
    }
}
