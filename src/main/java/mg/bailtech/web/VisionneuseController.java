package mg.bailtech.web;

import java.util.List;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.Utilisateur;
import mg.bailtech.repository.ContratDeBailRepository;
import mg.bailtech.repository.PaiementLoyerRepository;
import mg.bailtech.service.BailleurCourantService;
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
 *
 * <h2>Contrôle de propriété</h2>
 * Aucun identifiant de contrat n'est lu dans l'URL sans être confronté au
 * bailleur de la session. Le paramètre {@code {id}} est choisi par le client :
 * sans la vérification faite par {@link #chargerContrat}, changer le numéro
 * dans la barre d'adresse suffirait à lire — et à télécharger — le contrat d'un
 * autre propriétaire. La vérification est faite <em>avant</em> toute lecture de
 * l'échéancier, et l'échec est tracé : une série de refus sur des identifiants
 * strangers est le signe d'une tentative d'énumération.
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
    private final BailleurCourantService bailleurCourant;

    public VisionneuseController(ContratDeBailRepository contrats,
                                 PaiementLoyerRepository paiements,
                                 ContratService contratService,
                                 ContratPdfService contratPdfService,
                                 BailleurCourantService bailleurCourant) {
        this.contrats = contrats;
        this.paiements = paiements;
        this.contratService = contratService;
        this.contratPdfService = contratPdfService;
        this.bailleurCourant = bailleurCourant;
    }

    // ==================================================================
    // Liste
    // ==================================================================

    @GetMapping("/contrats")
    public String lister(Model model) {
        Utilisateur bailleur = bailleurCourant.exigerBailleurConnecte();
        model.addAttribute("bailleur", bailleur);
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
                        Model model,
                        RedirectAttributes redirectAttributes) {
        Utilisateur bailleur = bailleurCourant.exigerBailleurConnecte();
        model.addAttribute("bailleur", bailleur);

        ContratDeBail contrat = chargerContrat(bailleur, id, redirectAttributes);
        if (contrat == null) {
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

    /**
     * PDF affiché directement dans le navigateur (visionneuse).
     *
     * <p>La propriété est contrôlée avant la génération : le PDF contient les
     * identités et les montants du bail, il ne doit jamais sortir du parc de
     * celui qui le demande.
     */
    @GetMapping("/contrats/{id}/pdf")
    public ResponseEntity<byte[]> afficherPdf(@PathVariable("id") Integer id) {
        return ContratController.reponse(genererPdfAutorise(id), false);
    }

    /** PDF proposé en pièce jointe. */
    @GetMapping("/contrats/{id}/pdf/telecharger")
    public ResponseEntity<byte[]> telechargerPdf(@PathVariable("id") Integer id) {
        return ContratController.reponse(genererPdfAutorise(id), true);
    }

    /** Génère le PDF du contrat, après avoir vérifié qu'il est au bailleur. */
    private DocumentPdf genererPdfAutorise(Integer id) {
        Utilisateur bailleur = bailleurCourant.exigerBailleurConnecte();
        if (chargerContrat(bailleur, id, null) == null) {
            // Aucun message à afficher : la réponse est un fichier, pas une page.
            throw new AccesRefuseException("Le contrat n° " + id + " ne fait pas partie de votre parc.");
        }
        return contratPdfService.generer(id);
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
                         RedirectAttributes redirectAttributes) {
        try {
            Utilisateur bailleur = bailleurCourant.exigerBailleurConnecte();
            ContratDeBail contrat = contratService.activer(id, bailleur);
            redirectAttributes.addFlashAttribute("messageConfirmation",
                    "Contrat n° " + contrat.getId() + " signé : la location est effective et les échéances "
                            + "mensuelles ont été générées.");
        } catch (BailleurCourantService.UtilisateurIntrouvableException
                 | RegleMetierException e) {
            LOG.warn("Signature du contrat n° {} refusée : {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("messageErreur", e.getMessage());
        }
        return "redirect:/contrats/" + id;
    }

    // ==================================================================
    // Outils
    // ==================================================================

    /**
     * Charge un contrat en vérifiant qu'il appartient au bailleur.
     *
     * @return le contrat, ou {@code null} s'il est introuvable ou étranger —
     *         auquel cas un message est ajouté si {@code redirectAttributes}
     *         est fourni
     */
    private ContratDeBail chargerContrat(Utilisateur bailleur, Integer id,
                                         RedirectAttributes redirectAttributes) {
        // findByIdAvecProprietaire et non findById : le contrôle d'accès doit
        // lire le propriétaire du bien, association paresseuse.
        ContratDeBail contrat = contrats.findByIdAvecProprietaire(id).orElse(null);
        if (contrat == null) {
            avertir(redirectAttributes, "Le contrat n° " + id + " est introuvable.", id);
            return null;
        }
        if (!appartientA(contrat, bailleur)) {
            // Le contrat existe mais n'est pas au demandeur : le message reste
            // identique à celui d'un contrat absent, pour ne pas confirmer
            // l'existence d'un contrat appartenant à autrui.
            LOG.warn("Accès refusé au contrat n° {} : il n'appartient pas au bailleur n° {}.",
                    id, bailleur.getId());
            avertir(redirectAttributes, "Ce contrat n'appartient pas à votre parc immobilier.", id);
            return null;
        }
        return contrat;
    }

    private void avertir(RedirectAttributes redirectAttributes, String message, Integer id) {
        if (redirectAttributes != null) {
            redirectAttributes.addFlashAttribute("messageErreur", message);
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
