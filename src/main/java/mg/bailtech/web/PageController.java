package mg.bailtech.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.Logement;
import mg.bailtech.model.StatutContrat;
import mg.bailtech.model.StatutPaiement;
import mg.bailtech.model.Utilisateur;
import mg.bailtech.repository.ContratDeBailRepository;
import mg.bailtech.repository.LogementRepository;
import mg.bailtech.repository.PaiementLoyerRepository;
import mg.bailtech.repository.UtilisateurRepository;
import mg.bailtech.service.BailleurCourantService;
import mg.bailtech.service.ContratService;
import mg.bailtech.web.dto.LigneCompteur;
import mg.bailtech.web.dto.LigneContrat;
import mg.bailtech.web.dto.LigneDossier;
import mg.bailtech.web.dto.LigneDossier.UtilisateurVue;
import mg.bailtech.web.dto.SyntheseBailleur;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Contrôleur des pages de consultation : tableau de bord et coffre-fort
 * documentaire.
 * <p>
 * Ces deux écrans n'exposent pas de formulaire d'écriture : ils se contentent de
 * mettre à disposition des listes que les gabarits parcourent avec {@code th:each}
 * (contrats récents, dossiers des locataires).
 * <p>
 * Le calculateur JIRAMA, qui saisit et écrit, est confié à son propre
 * contrôleur ({@link JiramaController}).
 */
@Controller
public class PageController {

    private static final List<StatutPaiement> STATUTS_IMPAYES =
            List.of(StatutPaiement.A_PAYER, StatutPaiement.EN_RETARD, StatutPaiement.PARTIEL);

    private final ContratService contratService;
    private final UtilisateurRepository utilisateurs;
    private final LogementRepository logements;
    private final ContratDeBailRepository contrats;
    private final PaiementLoyerRepository paiements;
    private final BailleurCourantService bailleurCourant;

    public PageController(ContratService contratService,
                          UtilisateurRepository utilisateurs,
                          LogementRepository logements,
                          ContratDeBailRepository contrats,
                          PaiementLoyerRepository paiements,
                          BailleurCourantService bailleurCourant) {
        this.contratService = contratService;
        this.utilisateurs = utilisateurs;
        this.logements = logements;
        this.contrats = contrats;
        this.paiements = paiements;
        this.bailleurCourant = bailleurCourant;
    }

    // ==================================================================
    // Tableau de bord
    // ==================================================================

    @GetMapping("/dashboard")
    public String tableauDeBord(@RequestParam(name = "q", required = false) String recherche,
                                Model model) {
        // Valeurs par défaut : le gabarit reste rendu même sans bailleur résolu.
        LocalDate aujourdhui = LocalDate.now();
        model.addAttribute("contratsRecents", List.of());
        model.addAttribute("recherche", recherche == null ? "" : recherche);
        model.addAttribute("nbResultats", 0);
        model.addAttribute("moisCourant",
                aujourdhui.getMonth().getDisplayName(TextStyle.FULL, Locale.FRANCE) + " " + aujourdhui.getYear());

        Utilisateur bailleur = bailleurInterne(model);
        if (bailleur == null) {
            return "dashboard/dashboard";
        }
        Integer id = bailleur.getId();

        List<ContratDeBail> recents = (recherche == null || recherche.isBlank())
                ? contrats.findTop5ByLogement_Proprietaire_IdOrderByDateDebutDesc(id)
                : contrats.rechercherContratsDuBailleur(id, recherche.trim());

        Map<String, Long> statuts = contrats.findContratsDuBailleur(id).stream()
                .collect(Collectors.groupingBy(c -> c.getStatutActuel().getLibelle(), Collectors.counting()));

        SyntheseBailleur synthese = new SyntheseBailleur(
                bailleur.getNom(),
                bailleur.getPrenom(),
                ouZero(contrats.sumMontantLoyerDuBailleur(id, StatutContrat.EN_COURS)),
                logements.countByProprietaireId(id),
                contrats.countByLogement_Proprietaire_IdAndStatutActuel(id, StatutContrat.EN_COURS),
                paiements.countByContrat_Logement_Proprietaire_IdAndStatutIn(id, STATUTS_IMPAYES),
                ouZero(paiements.sumResteDuPeriode(id, aujourdhui.getYear(), aujourdhui.getMonthValue())),
                statuts);

        model.addAttribute("synthese", synthese);
        model.addAttribute("contratsRecents", recents.stream().map(LigneContrat::new).toList());
        model.addAttribute("nbResultats", recents.size());
        return "dashboard/dashboard";
    }

    // ==================================================================
    // Coffre-fort documentaire
    // ==================================================================

    @GetMapping("/locataires")
    public String dossiersLocataires(@RequestParam(name = "q", required = false) String recherche,
                                     Model model) {
        model.addAttribute("dossiers", List.of());
        model.addAttribute("activites", List.of());
        model.addAttribute("recherche", recherche == null ? "" : recherche);
        model.addAttribute("nbDossiers", 0);
        model.addAttribute("nbDossiersComplets", 0L);
        model.addAttribute("nbPiecesManquantes", 0L);

        Utilisateur bailleur = bailleurInterne(model);
        if (bailleur == null) {
            return "documents/documents";
        }

        // Locataires rattachés à un bien du bailleur, et non tous les
        // locataires de la base : ces lignes exposent CIN, téléphone et email.
        List<Utilisateur> candidats = (recherche == null || recherche.isBlank())
                ? utilisateurs.findLocatairesDuParc(bailleur.getId())
                : utilisateurs.rechercherLocatairesDuParc(bailleur.getId(), recherche.trim());

        List<LigneDossier> dossiers = candidats.stream()
                .map(u -> new LigneDossier(
                        new UtilisateurVue(u.getId(), u.getNomComplet(), u.getInitiales(),
                                u.getCinNumero(), u.getTelephone(), u.getEmail()),
                        contratActifDe(u.getId())))
                .toList();

        long complets = dossiers.stream().filter(LigneDossier::isDossierComplet).count();
        model.addAttribute("dossiers", dossiers);
        // Le gabarit ne combine pas th:each et th:if sur un même élément (Thymeleaf
        // évalue th:if avant th:each) : la sélection des trois dernières activités
        // est donc faite ici.
        model.addAttribute("activites", dossiers.stream().limit(3).toList());
        model.addAttribute("recherche", recherche == null ? "" : recherche);
        model.addAttribute("nbDossiers", dossiers.size());
        model.addAttribute("nbDossiersComplets", complets);
        model.addAttribute("nbPiecesManquantes", dossiers.size() - complets);
        return "documents/documents";
    }

    // ==================================================================
    // Outils
    // ==================================================================

    /**
     * Résout le bailleur de la session. En cas de session absente ou expirée, on
     * rend quand même la page : {@code bailleur} vaut alors {@code null} et les
     * listes sont initialisées à vide, ce qui laisse les gabarits afficher un
     * état « aucune donnée » plutôt qu'une erreur 500.
     */
    private Utilisateur bailleurInterne(Model model) {
        try {
            Utilisateur bailleur = bailleurCourant.exigerBailleurConnecte();
            model.addAttribute("bailleur", bailleur);
            return bailleur;
        } catch (BailleurCourantService.UtilisateurIntrouvableException e) {
            model.addAttribute("bailleur", null);
            model.addAttribute("messageVide", e.getMessage());
            return null;
        }
    }

    private ContratDeBail contratActifDe(Integer locataireId) {
        List<ContratDeBail> actifs = contrats.findByLocataireIdAndStatutActuel(locataireId, StatutContrat.EN_COURS);
        return actifs.isEmpty() ? null : actifs.get(0);
    }

    private static BigDecimal ouZero(BigDecimal valeur) {
        return valeur == null ? BigDecimal.ZERO : valeur;
    }
}
