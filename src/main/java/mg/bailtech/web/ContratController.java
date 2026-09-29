package mg.bailtech.web;

import java.util.List;

import jakarta.validation.Valid;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.Logement;
import mg.bailtech.model.Utilisateur;
import mg.bailtech.service.ContratPdfService;
import mg.bailtech.service.ContratService;
import mg.bailtech.service.RegleMetierException;
import mg.bailtech.web.dto.ContratForm;
import mg.bailtech.web.dto.DocumentPdf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Générateur de contrat de bail.
 *
 * <h2>Routage</h2>
 * <ul>
 *   <li>{@code GET /}, {@code GET /contrats/create} : assistant de saisie ;</li>
 *   <li>{@code GET /contrats/create-wizard} : même assistant, alias imposé par le
 *       cahier des charges ;</li>
 *   <li>{@code POST /contrats} et {@code POST /enregistrer} : enregistrement de la
 *       saisie puis téléchargement du PDF ;</li>
 *   <li>tableau de bord : {@link PageController} ; visionneuse et cycles de vie :
 *       {@link VisionneuseController}.</li>
 * </ul>
 *
 * <p>Le formulaire est un objet de liaison dédié ({@link ContratForm}) et non
 * l'entité {@code ContratDeBail} : voir cette classe pour la justification. Les
 * listes déroulantes sont alimentées par {@code th:each} à partir des attributs
 * {@code logementsDisponibles} et {@code locatairesDisponibles}.
 */
@Controller
public class ContratController {

    private static final Logger LOG = LoggerFactory.getLogger(ContratController.class);

    private static final String VUE_CONTRAT = "contract/contract";

    private final ContratService contratService;
    private final ContratPdfService contratPdfService;

    public ContratController(ContratService contratService, ContratPdfService contratPdfService) {
        this.contratService = contratService;
        this.contratPdfService = contratPdfService;
    }

    // ==================================================================
    // Générateur
    // ==================================================================

    @GetMapping({"/", "/contrats/create", "/contrats/create-wizard"})
    public String afficherFormulaire(@RequestParam(name = "bailleurId", required = false) Integer bailleurId,
                                     Model model) {
        ContratForm form = new ContratForm();
        try {
            Utilisateur bailleur = contratService.utilisateurCourant(bailleurId);
            contratService.preparerFormulaire(form, bailleur);
            alimenterModel(form, bailleur, model);
        } catch (RegleMetierException e) {
            // Base vide : on rend le formulaire vide avec le motif du blocage.
            alimenterModelVide(form, model, e.getMessage());
        }
        return VUE_CONTRAT;
    }

    // ==================================================================
    // Enregistrement + téléchargement du PDF
    // ==================================================================

    /**
     * Intercepte la saisie du formulaire, l'enregistre et renvoie le PDF du contrat
     * en pièce jointe.
     *
     * <p>Trois issues possibles :
     * <ol>
     *   <li><strong>incohérence de dates</strong> : l'exception
     *       {@link RegleMetierException} est levée par
     *       {@link ContratService#verifierCoherenceDates} et rattachée au champ
     *       {@code dateFin} ; le formulaire est réaffiché avec les valeurs
     *       saisies et les messages de validation ;</li>
     *   <li><strong>autre règle métier</strong> (logement déjà loué, CIN ou email
     *       déjà utilisés) : message rattaché au champ concerné ;</li>
     *   <li><strong>succès</strong> : le contrat est enregistré au statut
     *       {@code EN_ATTENTE_SIGNATURE} et le PDF est téléchargé.</li>
     * </ol>
     *
     * <p>Le contrat n'est pas activé ici : la location ne devient effective qu'à la
     * signature ({@code POST /contrats/{id}/signature}), instant où les échéances
     * mensuelles sont générées.
     *
     * <p><strong>Type de retour {@code Object}</strong> : la même requête doit
     * pouvoir renvoyer soit le formulaire réaffiché (le nom de la vue, une
     * {@code String}), soit le fichier PDF (un {@code ResponseEntity}). Spring MVC
     * inspecte la valeur de retour à l'exécution, ce qui permet de conserver les
     * erreurs de validation champ par champ — que la validation de session
     * (@SessionAttributes) ne sait pas faire, et qu'un message flash obligerait à
     * ramener en un seul bloc.
     */
    @PostMapping({"/contrats", "/enregistrer"})
    public Object enregistrer(@Valid @ModelAttribute("contratForm") ContratForm form,
                              BindingResult bindingResult,
                              Model model) {
        Utilisateur bailleur;
        try {
            bailleur = contratService.utilisateurCourant(form.getBailleurId());
        } catch (RegleMetierException e) {
            bindingResult.reject(e.getCode(), e.getMessage());
            alimenterModelVide(form, model, e.getMessage());
            return VUE_CONTRAT;
        }

        // Levée explicite de l'exception métier en cas d'incohérence de dates.
        try {
            contratService.verifierCoherenceDates(form.getDateDebut(), form.getDateFin());
        } catch (RegleMetierException e) {
            rattacherErreur(bindingResult, e);
        }

        ContratDeBail contrat = null;
        if (!bindingResult.hasErrors()) {
            try {
                contrat = contratService.enregistrer(form, bailleur);
            } catch (RegleMetierException e) {
                rattacherErreur(bindingResult, e);
            } catch (DataIntegrityViolationException e) {
                // Filet de sécurité : index partiel uq_un_seul_contrat_actif_par_logement,
                // unicité de la CIN ou de l'email, contraintes de date.
                LOG.warn("Enregistrement du contrat refusé par une contrainte de la base", e);
                bindingResult.reject("enregistrement.refuse", "L'enregistrement a été refusé par la base de "
                        + "données : vérifiez le numéro de CIN, l'adresse email et les dates du bail.");
            }
        }

        if (contrat == null) {
            contratService.remplirApercu(form, bailleur);
            alimenterModel(form, bailleur, model);
            return VUE_CONTRAT;
        }

        DocumentPdf document = contratPdfService.generer(contrat.getId());
        LOG.info("Contrat n° {} enregistré, PDF renvoyé ({} page(s), {} octets).",
                contrat.getId(), document.nbPages(), document.taille());
        return reponse(document, true);
    }

    /** Rattache une erreur métier au bon champ du formulaire. */
    private void rattacherErreur(BindingResult bindingResult, RegleMetierException e) {
        if (e.getChamp() != null) {
            bindingResult.rejectValue(e.getChamp(), e.getCode(), e.getMessage());
        } else {
            bindingResult.reject(e.getCode(), e.getMessage());
        }
    }

    /**
     * Construit la réponse HTTP portant le PDF : en pièce jointe (téléchargement)
     * ou en affichage direct (visionneuse du navigateur).
     */
    static ResponseEntity<byte[]> reponse(DocumentPdf document, boolean telecharger) {
        HttpHeaders entetes = new HttpHeaders();
        entetes.setContentType(MediaType.parseMediaType(DocumentPdf.TYPE_MIME));
        entetes.set(HttpHeaders.CONTENT_DISPOSITION, (telecharger ? "attachment" : "inline")
                + "; filename=\"" + document.nomFichier() + "\"");
        entetes.setCacheControl("no-store");
        entetes.setContentLength(document.taille());
        return new ResponseEntity<>(document.contenu(), entetes, HttpStatus.OK);
    }

    // ==================================================================
    // Alimentation du modèle
    // ==================================================================

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
