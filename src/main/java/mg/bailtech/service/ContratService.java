package mg.bailtech.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.Logement;
import mg.bailtech.model.StatutContrat;
import mg.bailtech.model.Utilisateur;
import mg.bailtech.repository.ContratDeBailRepository;
import mg.bailtech.repository.LogementRepository;
import mg.bailtech.repository.UtilisateurRepository;
import mg.bailtech.web.dto.ContratForm;
import mg.bailtech.web.dto.Format;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Règles métier de la création d'un contrat de bail.
 * <p>
 * Point d'application de la règle critique du modèle : <strong>un logement ne peut
 * avoir qu'un seul contrat {@code EN_COURS}</strong>. L'index unique partiel
 * {@code uq_un_seul_contrat_actif_par_logement} reste l'arbitre final (il bloque
 * toute insertion concurrente), mais la vérification applicative
 * {@link ContratDeBailRepository#existsContratActifParLogement} permet de renvoyer
 * un message exploitable au bailleur.
 */
@Service
public class ContratService {

    private final ContratDeBailRepository contrats;
    private final LogementRepository logements;
    private final UtilisateurRepository utilisateurs;

    public ContratService(ContratDeBailRepository contrats,
                          LogementRepository logements,
                          UtilisateurRepository utilisateurs) {
        this.contrats = contrats;
        this.logements = logements;
        this.utilisateurs = utilisateurs;
    }

    // ==================================================================
    // Résolution du bailleur courant
    // ==================================================================

    /**
     * Détermine le bailleur « connecté ».
     * <p>
     * Le module d'authentification (Tohavina) n'étant pas encore en place, on
     * accepte un identifiant transmis par l'URL et, à défaut, on retient le
     * premier propriétaire enregistré. Cette méthode sera remplacée par la lecture
     * de la session dès l'introduction de Spring Security.
     */
    @Transactional(readOnly = true)
    public Utilisateur utilisateurCourant(Integer bailleurId) {
        if (bailleurId != null) {
            Optional<Utilisateur> demande = utilisateurs.findById(bailleurId);
            if (demande.isPresent()) {
                return demande.get();
            }
        }
        return utilisateurs.findBailleurs().stream()
                .findFirst()
                .or(() -> Optional.ofNullable(utilisateurs.findFirstByOrderByIdAsc()))
                .orElseThrow(() -> new RegleMetierException(null, "bailleur.absent",
                        "Aucun bailleur n'est enregistré : créez d'abord un propriétaire et un logement."));
    }

    // ==================================================================
    // Alimentation du formulaire
    // ==================================================================

    /** Biens du bailleur sans contrat en cours : seules valeurs de la liste déroulante. */
    @Transactional(readOnly = true)
    public List<Logement> logementsDisponibles(Utilisateur bailleur) {
        return logements.findDisponiblesPourProprietaire(bailleur.getId(), StatutContrat.EN_COURS);
    }

    /** Locataires connus de la base et sans bail en cours : valeurs de la liste déroulante. */
    @Transactional(readOnly = true)
    public List<Utilisateur> locatairesDisponibles(Utilisateur bailleur) {
        return utilisateurs.findLocatairesDisponibles(bailleur.getId(), StatutContrat.EN_COURS);
    }

    /**
     * Prépare un formulaire vierge : valeurs par défaut raisonnables et aperçu du
     * contrat résolu. Appelée au GET et après chaque POST invalide, afin que la
     * saisie de l'utilisateur soit conservée.
     */
    @Transactional(readOnly = true)
    public void preparerFormulaire(ContratForm form, Utilisateur bailleur) {
        form.setBailleurId(bailleur.getId());

        List<Logement> disponibles = logementsDisponibles(bailleur);
        boolean logementToujoursDisponible = form.getLogementId() != null
                && disponibles.stream().anyMatch(l -> l.getId().equals(form.getLogementId()));
        if (!logementToujoursDisponible) {
            form.setLogementId(disponibles.isEmpty() ? null : disponibles.get(0).getId());
        }

        if (form.getDateDebut() == null) {
            form.setDateDebut(LocalDate.now());
        }
        if (form.getDateFin() == null) {
            // Échéance au premier jour du mois situé 12 mois après la prise d'effet.
            form.setDateFin(LocalDate.now().plusMonths(12).withDayOfMonth(1));
        }
        if (form.getMontantCautionMga() == null) {
            form.setMontantCautionMga(BigDecimal.ZERO);
        }
        if (form.getJourPaiementMensuel() == null) {
            form.setJourPaiementMensuel(5);
        }
        if (form.getDureePreavisMois() == null) {
            form.setDureePreavisMois(3);
        }

        remplirApercu(form, bailleur);
    }

    /**
     * Résout les valeurs affichées dans l'aperçu du contrat (colonne de droite).
     * <p>
     * Aucune association paresseuse n'est touchée : toutes les lectures passent par
     * des méthodes de repository, ce qui autorise le rendu de la page avec
     * {@code spring.jpa.open-in-view=false}.
     */
    @Transactional(readOnly = true)
    public void remplirApercu(ContratForm form, Utilisateur bailleur) {
        form.setBailleurNomComplet(videSiNull(bailleur.getNomComplet()));
        form.setBailleurCin(videSiNull(bailleur.getCinNumero()));
        form.setBailleurAdresse(videSiNull(bailleur.getAdresseActuelle()));

        form.setLoyerLisible(Format.montant(form.getMontantLoyerMga()));
        form.setCautionLisible(Format.montant(form.getMontantCautionMga()));
        form.setCautionMoisLisible(cautionEnMois(form));
        form.setJourPaiementLisible(Format.jour(form.getJourPaiementMensuel()));
        form.setDateDebutLisible(Format.date(form.getDateDebut()));
        form.setDateFinLisible(Format.date(form.getDateFin()));

        Utilisateur locataire = resoudreLocataire(form);
        if (locataire != null) {
            form.setLocataireNomComplet(videSiNull(locataire.getNomComplet()));
            form.setLocataireCin(videSiNull(locataire.getCinNumero()));
            form.setLocataireAdresse(videSiNull(locataire.getAdresseActuelle()));
        } else {
            form.setLocataireNomComplet(composerNom(form.getPrenom(), form.getNom()));
            form.setLocataireCin(videSiNull(form.getCinNumero()));
            form.setLocataireAdresse(videSiNull(form.getAdresseActuelle()));
        }

        Logement bien = form.getLogementId() == null
                ? null
                : logements.findById(form.getLogementId()).orElse(null);
        if (bien != null) {
            form.setBienType(bien.getTypeDeBien());
            form.setBienAdresse(bien.getAdresseLot());
            form.setBienQuartier(bien.getQuartierFokontany());
            form.setBienVille(bien.getVille());
            form.setBienNombrePieces(bien.getNombrePiecesPrincipales());
            form.setBienModeComptage(bien.getJiramaTypeGestion() == null
                    ? "" : bien.getJiramaTypeGestion().getLibelle());
        } else {
            form.setBienType("");
            form.setBienAdresse("");
            form.setBienQuartier("");
            form.setBienVille("");
            form.setBienNombrePieces(null);
            form.setBienModeComptage("");
        }
    }

    /** Locataire déjà en base correspondant à la saisie (sélection ou CIN reconnue). */
    private Utilisateur resoudreLocataire(ContratForm form) {
        if (form.getLocataireId() != null) {
            return utilisateurs.findById(form.getLocataireId()).orElse(null);
        }
        if (form.getCinNumero() != null && !form.getCinNumero().isBlank()) {
            return utilisateurs.findByCinNumeroNormalise(form.getCinNumero()).orElse(null);
        }
        return null;
    }

    // ==================================================================
    // Enregistrement
    // ==================================================================

    /**
     * Transforme le formulaire en contrat et l'enregistre.
     * <p>
     * Le contrat naît en {@code EN_ATTENTE_SIGNATURE} : l'index unique partiel ne
     * concerne donc que les baux effectivement signés, ce qui laisse au bailleur le
     * temps d'obtenir le visa du Fokontany avant qu'une location ne démarre.
     *
     * @throws RegleMetierException si une règle métier ou d'intégrité est violée
     */
    @Transactional
    public ContratDeBail enregistrer(ContratForm form, Utilisateur bailleur) {
        Logement bien = logements.findById(form.getLogementId())
                .orElseThrow(() -> new RegleMetierException("logementId", "contrat.logement.introuvable",
                        "Le logement sélectionné est introuvable."));

        if (bien.getProprietaire() == null || !bien.getProprietaire().getId().equals(bailleur.getId())) {
            throw new RegleMetierException("logementId", "contrat.logement.autorisation",
                    "Ce logement n'appartient pas à votre parc immobilier.");
        }

        // Règle critique : un seul contrat EN_COURS par logement.
        if (contrats.existsContratActifParLogement(bien.getId())) {
            throw new RegleMetierException("logementId", "contrat.logement.dejaLoue",
                    "Ce logement fait déjà l'objet d'un contrat en cours. "
                            + "Terminez ou résiliez le contrat existant avant d'en créer un nouveau.");
        }

        Utilisateur locataire = form.getLocataireId() != null
                ? utilisateurs.findById(form.getLocataireId())
                        .orElseThrow(() -> new RegleMetierException("locataireId", "contrat.locataire.introuvable",
                                "Le locataire sélectionné est introuvable."))
                : rechercherOuCreerLocataire(form);

        ContratDeBail contrat = new ContratDeBail();
        contrat.setLogement(bien);
        contrat.setLocataire(locataire);
        contrat.setDateDebut(form.getDateDebut());
        contrat.setDateFin(form.getDateFin());
        contrat.setMontantLoyerMga(form.getMontantLoyerMga());
        contrat.setMontantCautionMga(form.getMontantCautionMga());
        contrat.setJourPaiementMensuel(form.getJourPaiementMensuel());
        contrat.setDureePreavisMois(form.getDureePreavisMois());
        contrat.setStatutActuel(StatutContrat.EN_ATTENTE_SIGNATURE);

        return contrats.save(contrat);
    }

    /**
     * Retrouve le locataire par sa CIN (recherche d'identité du cahier des charges)
     * et, à défaut, crée sa fiche.
     */
    private Utilisateur rechercherOuCreerLocataire(ContratForm form) {
        Optional<Utilisateur> dejaEnBase = utilisateurs.findByCinNumeroNormalise(form.getCinNumero());
        if (dejaEnBase.isPresent()) {
            // La CIN est unique en base : on réutilise la fiche existante
            // plutôt que de faire échouer l'enregistrement.
            return dejaEnBase.get();
        }
        if (utilisateurs.findByEmailIgnoreCase(form.getEmail()).isPresent()) {
            throw new RegleMetierException("email", "utilisateur.email.duplique",
                    "Cette adresse email est déjà associée à un autre utilisateur.");
        }

        Utilisateur nouveau = new Utilisateur(form.getNom(), form.getPrenom(), form.getCinNumero());
        nouveau.setCinDateDelivrance(form.getCinDateDelivrance());
        nouveau.setCinLieuDelivrance(form.getCinLieuDelivrance());
        nouveau.setAdresseActuelle(form.getAdresseActuelle());
        nouveau.setTelephone(form.getTelephone());
        nouveau.setEmail(form.getEmail());
        // Aucun mot de passe n'est saisissable depuis l'assistant : on pose une
        // empreinte non devinable. Le module de sécurité (BCrypt) remplacera ce
        // placeholder à l'ouverture d'un compte locataire.
        nouveau.setMotDePasse("{compte-sans-authentification}" + UUID.randomUUID());
        return utilisateurs.save(nouveau);
    }

    // ==================================================================
    // Utilitaires
    // ==================================================================

    /** Caution exprimée en mois de loyer, pour l'article 4 du contrat. */
    private static String cautionEnMois(ContratForm form) {
        if (form.getMontantLoyerMga() == null || form.getMontantLoyerMga().compareTo(BigDecimal.ZERO) == 0
                || form.getMontantCautionMga() == null) {
            return "0";
        }
        return form.getMontantCautionMga()
                .divide(form.getMontantLoyerMga(), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String composerNom(String prenom, String nom) {
        if (prenom == null || prenom.isBlank()) {
            return nom == null ? "" : nom;
        }
        return nom == null || nom.isBlank() ? prenom : prenom + " " + nom;
    }

    private static String videSiNull(String valeur) {
        return valeur == null ? "" : valeur;
    }
}
