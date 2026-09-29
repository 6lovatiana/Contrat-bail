package mg.bailtech.web;

import java.util.List;

import jakarta.validation.Valid;

import mg.bailtech.model.Logement;
import mg.bailtech.model.TypeCompteurJirama;
import mg.bailtech.model.Utilisateur;
import mg.bailtech.repository.LogementRepository;
import mg.bailtech.service.BailleurCourantService;
import mg.bailtech.service.JiramaService;
import mg.bailtech.service.JiramaService.ResultatJirama;
import mg.bailtech.service.MoteurRepartitionJirama.ModeEcart;
import mg.bailtech.service.RegleMetierException;
import mg.bailtech.web.dto.JiramaForm;
import mg.bailtech.web.dto.LigneCompteur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Calculateur de charges JIRAMA.
 *
 * <p>Trois gestes, du moins engageant au plus engageant :
 * <ol>
 *   <li>{@code GET /jirama} — relevés des sous-compteurs, compteurs principaux et
 *       tarifs du bailleur ;</li>
 *   <li>{@code POST /jirama/calculer} — répartition, sans aucune écriture&nbsp;:
 *       c'est une simulation que le bailleur contrôle avant de facturer&nbsp;;</li>
 *   <li>{@code POST /jirama/appliquer} — imputation de la part de chaque
 *       locataire sur son échéance de la période choisie.</li>
 * </ol>
 *
 * <p>Le calcul est donc volontairement séparé de l'écriture : une erreur de
 * relevé ne peut pas se traduire par un montant débité au locataire.
 *
 * <p>Les deux POST renvoient un {@code Object}. Comme pour l'assistant de
 * contrat, la valeur de retour porte le succès (nom de vue) comme l'échec (nom
 * de vue également) : renvoyer directement la chaîne d'un view name ferait
 * perdre au gabarit le {@link BindingResult}, et donc tous les messages
 * d'erreur saisis champ par champ.
 *
 * <h2>Identité et identifiants</h2>
 * Le bailleur vient de la session. Le {@code logementId} du formulaire, lui,
 * reste un identifiant choisi par le client : il est recoupé avec le parc du
 * bailleur avant d'être retenu, sans quoi il suffirait de demander le relevé
 * du compteur d'un immeuble voisin pour en connaître la configuration.
 */
@Controller
public class JiramaController {

    private static final Logger LOG = LoggerFactory.getLogger(JiramaController.class);

    private static final String VUE = "jirama/jirama";

    private final JiramaService jiramaService;

    private final LogementRepository logements;

    private final BailleurCourantService bailleurCourant;

    public JiramaController(JiramaService jiramaService,
                            LogementRepository logements,
                            BailleurCourantService bailleurCourant) {
        this.jiramaService = jiramaService;
        this.logements = logements;
        this.bailleurCourant = bailleurCourant;
    }

    // ==================================================================
    // Affichage
    // ==================================================================

    @GetMapping("/jirama")
    public String formulaire(@RequestParam(name = "logementId", required = false) Integer logementId,
                             Model model) {
        Utilisateur bailleur = bailleurCourant.exigerBailleurConnecte();
        JiramaForm formulaire = jiramaService.preparerFormulaire(bailleur, logementAppartenantAuParc(bailleur, logementId), null);
        return afficher(bailleur, formulaire, null, model);
    }

    // ==================================================================
    // Simulation
    // ==================================================================

    /** Répartit la facture sans écrire : le bailleur vérifie avant de facturer. */
    @PostMapping("/jirama/calculer")
    public Object calculer(@Valid @ModelAttribute("formulaireJirama") JiramaForm formulaire,
                           BindingResult erreurs,
                           Model model) {
        Utilisateur bailleur = bailleurCourant.exigerBailleurConnecte();
        if (erreurs.hasErrors()) {
            LOG.info("Calcul JIRAMA refusé : {} erreur(s) de saisie.", erreurs.getErrorCount());
            return afficher(bailleur, formulaire, null, model);
        }
        try {
            ResultatJirama resultat = jiramaService.calculer(formulaire);
            model.addAttribute("messageSucces",
                    "Répartition calculée : " + resultat.getMontantReparti().toPlainString()
                            + " Ar à imputer sur " + resultat.getPeriodeLisible()
                            + ". Aucune écriture n'a encore été faite.");
            return afficher(bailleur, formulaire, resultat, model);
        } catch (RegleMetierException e) {
            model.addAttribute("messageErreur", e.getMessage());
            return afficher(bailleur, formulaire, null, model);
        }
    }

    // ==================================================================
    // Imputation
    // ==================================================================

    /** Impute la part JIRAMA de chaque locataire sur son échéance. */
    @PostMapping("/jirama/appliquer")
    public Object appliquer(@Valid @ModelAttribute("formulaireJirama") JiramaForm formulaire,
                           BindingResult erreurs,
                           Model model) {
        Utilisateur bailleur = bailleurCourant.exigerBailleurConnecte();
        if (erreurs.hasErrors()) {
            LOG.info("Imputation JIRAMA refusée : {} erreur(s) de saisie.", erreurs.getErrorCount());
            return afficher(bailleur, formulaire, null, model);
        }
        try {
            ResultatJirama resultat = jiramaService.appliquer(formulaire, bailleur);
            long reportees = resultat.getNbReportees();
            String bilan = resultat.getLignes().size() - reportees + " écriture(s) effectuée(s) sur "
                    + resultat.getPeriodeLisible();
            model.addAttribute("messageSucces", reportees == 0
                    ? bilan + "."
                    : bilan + ", " + reportees + " ignorée(s) — voir le détail.");
            return afficher(bailleur, formulaire, resultat, model);
        } catch (RegleMetierException e) {
            model.addAttribute("messageErreur", e.getMessage());
            return afficher(bailleur, formulaire, null, model);
        }
    }

    // ==================================================================
    // Outils
    // ==================================================================

    /**
     * Alimente le modèle commun aux trois points d'entrée.
     *
     * <p>Le modèle est réinitialisé à chaque rendu : sans cela, un
     * {@code messageSucces} d'un calcul réussi réapparaîtrait après une saisie
     * invalide, et donnerait l'impression que la saisie a été prise en compte.
     */
    private String afficher(Utilisateur bailleur, JiramaForm formulaire,
                            ResultatJirama resultat, Model model) {
        List<Logement> parc =
                logements.findByProprietaireIdOrderByVilleAscQuartierFokontanyAsc(bailleur.getId());

        model.addAttribute("bailleur", bailleur);
        model.addAttribute("formulaireJirama", formulaire);
        model.addAttribute("parc", parc.stream().map(l -> new LigneCompteur(l, "")).toList());
        model.addAttribute("repartitionRequise", formulaire.isRepartitionRequise());
        model.addAttribute("modesEcart", ModeEcart.values());
        model.addAttribute("typesGestion", TypeCompteurJirama.values());
        model.addAttribute("tarifElectriciteParDefaut", JiramaForm.TARIF_ELEC_PAR_DEFAUT);
        model.addAttribute("tarifEauParDefaut", JiramaForm.TARIF_EAU_PAR_DEFAUT);

        if (resultat == null) {
            model.addAttribute("resultatJirama", null);
            model.addAttribute("ecrituresJirama", List.of());
        } else {
            model.addAttribute("resultatJirama", resultat);
            model.addAttribute("ecrituresJirama", resultat.getEcritures());
        }
        return VUE;
    }

    /**
     * Ne retient un {@code logementId} que s'il figure dans le parc du bailleur.
     *
     * <p>Le paramètre vient de la liste déroulante, donc d'une valeur légitime,
     * mais il est modifiable dans l'URL. Le service choisit alors le premier bien
     * du parc à défaut, et ne divulgue rien sur l'existence d'un bien inconnu.
     */
    private Integer logementAppartenantAuParc(Utilisateur bailleur, Integer logementId) {
        if (logementId == null) {
            return null;
        }
        boolean connu = logements.findByProprietaireIdOrderByVilleAscQuartierFokontanyAsc(bailleur.getId())
                .stream()
                .anyMatch(l -> logementId.equals(l.getId()));
        if (!connu) {
            LOG.warn("Bien n° {} demandé par le bailleur n° {} : hors de son parc, ignoré.",
                    logementId, bailleur.getId());
        }
        return connu ? logementId : null;
    }
}
