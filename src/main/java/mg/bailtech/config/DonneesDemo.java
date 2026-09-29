package mg.bailtech.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.Logement;
import mg.bailtech.model.PaiementLoyer;
import mg.bailtech.model.StatutContrat;
import mg.bailtech.model.StatutPaiement;
import mg.bailtech.model.TypeCompteurJirama;
import mg.bailtech.model.Utilisateur;
import mg.bailtech.repository.ContratDeBailRepository;
import mg.bailtech.repository.LogementRepository;
import mg.bailtech.repository.PaiementLoyerRepository;
import mg.bailtech.repository.UtilisateurRepository;
import mg.bailtech.service.AuthentificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jeu de données de démonstration.
 * <p>
 * Le schéma étant créé par le script {@code sql/240920260828-database_init.sql}
 * ({@code ddl-auto=validate}), une base neuve est vide : sans ce remplissage, les
 * pages reliées par {@code th:each} s'afficheraient sans aucune ligne. Le
 * remplissage n'a lieu que si la table {@code utilisateur} est vide et se
 * neutralise avec {@code bailtech.demo.desactiver=true}.
 */
@Service
@ConditionalOnProperty(name = "bailtech.demo.desactiver", havingValue = "false", matchIfMissing = true)
public class DonneesDemo implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DonneesDemo.class);

    /**
     * Mot de passe des comptes de démonstration, en clair. Il n'est jamais stocké
     * tel quel&nbsp;: chaque fiche reçoit son empreinte BCrypt, calculée avec le
     * sel aléatoire propre à cette empreinte. La valeur en clair ne figure donc
     * dans la base que sous forme de hachage à sel variable, et sert uniquement
     * à allow l'équipe de se connecter en développement.
     */
    private static final String MOT_DE_PASSE_DEMO = "Bailtech2026!";

    /**
     * Première période admissible par la contrainte
     * {@code CHECK (periode_annee >= 2026)} du script d'initialisation.
     */
    private static final LocalDate PERIODE_MIN = LocalDate.of(PaiementLoyer.ANNEE_MIN, 1, 1);

    private final UtilisateurRepository utilisateurs;
    private final LogementRepository logements;
    private final ContratDeBailRepository contrats;
    private final PaiementLoyerRepository paiements;
    private final AuthentificationService authentification;

    @Value("${bailtech.demo:false}")
    private boolean active;

    public DonneesDemo(UtilisateurRepository utilisateurs,
                       LogementRepository logements,
                       ContratDeBailRepository contrats,
                       PaiementLoyerRepository paiements,
                       AuthentificationService authentification) {
        this.utilisateurs = utilisateurs;
        this.logements = logements;
        this.contrats = contrats;
        this.paiements = paiements;
        this.authentification = authentification;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!active) {
            LOG.info("Jeu de données de démonstration désactivé (bailtech.demo=false).");
            return;
        }
        if (utilisateurs.count() > 0) {
            LOG.info("Base déjà peuplée : jeu de démonstration ignoré.");
            return;
        }

        LocalDate aujourdhui = LocalDate.now();
        // Exemple pour le 28/09/2026 : [2026-09, 2026-08, 2026-07].
        // Toutes les périodes restent >= PERIODE_MIN et sont distinctes les unes
        // des autres, sinon la contrainte uq_paiement_periode le rejette.
        List<LocalDate> periodes = periodesRecentes(aujourdhui, 3);

        Utilisateur bailleur = nouvelUtilisateur("RAZAFINDRAKOTO", "Jean-Luc", "101234567890",
                aujourdhui.minusYears(12), "Antananarivo", "Ingénieur informaticien",
                "Lot IVX 45 bis, Ivandry, Antananarivo", "+261 34 12 345 67", "razafindrakoto@bailtech.mg");
        Utilisateur locataire1 = nouvelUtilisateur("SAHONDRA", "Rakotomalala", "201211334455",
                aujourdhui.minusYears(5), "Fianarantsoa", "Comptable", "Villa Soafaniry, Ambatobe",
                "+261 32 45 678 90", "sahondra.rakotomalala@email.mg");
        Utilisateur locataire2 = nouvelUtilisateur("ANDRY", "Tiana Rakoto", "201054776688",
                aujourdhui.minusYears(8), "Toamasina", "Enseignant", "Immeuble Fiaro, Ankorondrano",
                "+261 33 11 222 33", "andry.tiana@email.mg");
        Utilisateur locataire3 = nouvelUtilisateur("LOVA", "Heriniaina", "115842009977",
                aujourdhui.minusYears(3), "Mahajanga", "Infirmière", "Rue Rainandriamampandry, 67 ha",
                "+261 38 44 555 66", "lova.heriniaina@email.mg");
        utilisateurs.saveAll(List.of(bailleur, locataire1, locataire2, locataire3));

        Logement t3 = nouveauLogement("IVG 22", "Ivandry", "Antananarivo", 3,
                "Appartement T3 avec salon, 2 chambres, cuisine et salle d'eau. Cour commune clôturée.",
                "JIRAMA-ELEC-4411", 1250, "JIRAMA-EAU-7781", 450, TypeCompteurJirama.UNIQUE, bailleur);
        Logement studio = nouveauLogement("A12", "Ankorondrano", "Antananarivo", 1,
                "Studio meublé, eau courante, accès à l'escalier B.",
                "JIRAMA-ELEC-9022", 640, "JIRAMA-EAU-3311", 210, TypeCompteurJirama.SOUS_COMPTEUR, bailleur);
        Logement villa = nouveauLogement("B 15", "Ambatobe", "Antananarivo", 5,
                "Villa Bassine avec jardin clôturé, dépendance et garage.",
                "JIRAMA-ELEC-1187", 3120, "JIRAMA-EAU-5540", 860, TypeCompteurJirama.PARTAGE, bailleur);
        Logement bureau = nouveauLogement("BC 3", "Antaninarenina", "Antananarivo", 2,
                "Local commercial en rez-de-chaussée, vitrine sur rue.",
                "JIRAMA-ELEC-7712", 2450, "JIRAMA-EAU-2210", 540, TypeCompteurJirama.UNIQUE, bailleur);
        logements.saveAll(List.of(t3, studio, villa, bureau));

        // Règle de l'index partiel : un seul contrat EN_COURS par logement. La villa
        // reste volontairement sans bail actif pour que la liste « biens disponibles »
        // du formulaire ne soit pas vide.
        ContratDeBail actif1 = nouveauContrat(t3, locataire1, aujourdhui.minusMonths(7),
                aujourdhui.plusMonths(5), "1200000.00", "2400000.00", 5, StatutContrat.EN_COURS);
        ContratDeBail actif2 = nouveauContrat(studio, locataire2, aujourdhui.minusMonths(4),
                aujourdhui.plusMonths(8), "650000.00", "650000.00", 10, StatutContrat.EN_COURS);
        ContratDeBail actif3 = nouveauContrat(bureau, locataire3, aujourdhui.minusMonths(2),
                aujourdhui.plusMonths(10), "800000.00", "1600000.00", 5, StatutContrat.EN_COURS);
        ContratDeBail enAttente = nouveauContrat(villa, locataire1, aujourdhui.plusWeeks(2),
                aujourdhui.plusMonths(14), "3000000.00", "6000000.00", 5, StatutContrat.EN_ATTENTE_SIGNATURE);
        contrats.saveAll(List.of(actif1, actif2, actif3, enAttente));

        List<PaiementLoyer> echeances = new ArrayList<>();

        // Contrat 1 : les mois les plus anciens sont soldés, le mois en cours est
        // partiellement réglé. La boucle garantit une période distincte par ligne.
        for (int i = periodes.size() - 1; i >= 1; i--) {
            boolean lePlusAncien = i > 1;
            echeances.add(nouveauPaiement(actif1, periodes.get(i), "1200000.00", "1200000.00",
                    lePlusAncien ? aujourdhui.minusMonths(2).withDayOfMonth(3)
                            : aujourdhui.withDayOfMonth(4),
                    lePlusAncien ? "MOBILE_MONEY" : "ESPECES", StatutPaiement.PAYE, true));
        }
        echeances.add(nouveauPaiement(actif1, periodes.get(0), "1200000.00", "400000.00",
                null, "MOBILE_MONEY", StatutPaiement.PARTIEL, false));

        // Contrat 2 : un impayé sur le mois précédent, puis l'échéance du mois en cours.
        if (periodes.size() > 1) {
            echeances.add(nouveauPaiement(actif2, periodes.get(1), "650000.00", "0.00",
                    null, null, StatutPaiement.EN_RETARD, false));
        }
        echeances.add(nouveauPaiement(actif2, periodes.get(0), "650000.00", "0.00",
                null, null, StatutPaiement.A_PAYER, false));

        // Contrat 3 : règlement du mois précédent.
        if (periodes.size() > 1) {
            echeances.add(nouveauPaiement(actif3, periodes.get(1), "800000.00", "800000.00",
                    aujourdhui.withDayOfMonth(2), "VIREMENT", StatutPaiement.PAYE, true));
        }
        paiements.saveAll(echeances);

        LOG.info("Jeu de démonstration chargé : 4 utilisateurs, 4 logements, 4 contrats, "
                + "{} échéances (périodes {} à {}).", echeances.size(),
                periodes.get(periodes.size() - 1), periodes.get(0));
    }

    /**
     * Premier jour du mois de la date fournie.
     * <p>
     * L'année <em>et</em> le mois sont préservés : seule l'année est ramenée au
     * plancher de la contrainte {@code CHECK (periode_annee >= 2026)}, le cas
     * échéant. Écrire ici {@code LocalDate.of(annee, 1, 1)} ferait retomber tous
     * les relevés sur janvier et ferait glisser le mois précédent sur l'année
     * précédente, ce qui interromp le démarrage sur la contrainte SQL.
     */
    private static LocalDate debutDeMois(LocalDate date) {
        int annee = Math.max(date.getYear(), PaiementLoyer.ANNEE_MIN);
        return LocalDate.of(annee, date.getMonthValue(), 1);
    }

    /**
     * Les {@code nombre} dernières périodes mensuelles, de la plus récente à la
     * plus ancienne, sans jamais descendre avant {@link #PERIODE_MIN}.
     * <p>
     * Exemple pour le 28/09/2026 : {@code [2026-09, 2026-08, 2026-07]}.
     * <p>
     * La liste retournée peut être plus courte que {@code nombre} — c'est le cas
     * en janvier de l'année plancher, où aucune période antérieure n'est
     * admissible. Les périodes retournées sont toujours strictement distinctes,
     * ce qu'exige la contrainte {@code uq_paiement_periode} sur
     * {@code (id_contrat, periode_mois, periode_annee)}.
     */
    private static List<LocalDate> periodesRecentes(LocalDate date, int nombre) {
        List<LocalDate> periodes = new ArrayList<>(nombre);
        LocalDate periode = debutDeMois(date);
        periodes.add(periode);
        while (periodes.size() < nombre) {
            LocalDate precedente = periode.minusMonths(1);
            if (precedente.isBefore(PERIODE_MIN)) {
                break;
            }
            periodes.add(precedente);
            periode = precedente;
        }
        return periodes;
    }

    private Utilisateur nouvelUtilisateur(String nom, String prenom, String cin,
                                           LocalDate cinDate, String cinLieu, String profession,
                                           String adresse, String telephone, String email) {
        Utilisateur utilisateur = new Utilisateur(nom, prenom, cin);
        utilisateur.setCinDateDelivrance(cinDate);
        utilisateur.setCinLieuDelivrance(cinLieu);
        utilisateur.setProfession(profession);
        utilisateur.setAdresseActuelle(adresse);
        utilisateur.setTelephone(telephone);
        utilisateur.setEmail(email);
        // Empreinte BCrypt, et non le mot de passe en clair : la colonne
        // utilisateur.mot_de_passe ne doit jamais contenir un secret lisible,
        // même dans un jeu de démonstration.
        utilisateur.setMotDePasse(authentification.hacherDemonstration(MOT_DE_PASSE_DEMO));
        return utilisateur;
    }

    private static Logement nouveauLogement(String lot, String quartier, String ville, int pieces,
                                            String description, String numeroElectricite, int indexElectricite,
                                            String numeroEau, int indexEau, TypeCompteurJirama type,
                                            Utilisateur proprietaire) {
        Logement logement = new Logement(lot, quartier, ville, pieces);
        logement.setDescriptionConsistance(description);
        logement.setCompteurElectriciteNumero(numeroElectricite);
        logement.setCompteurElectriciteIndexDepart(indexElectricite);
        logement.setCompteurEauNumero(numeroEau);
        logement.setCompteurEauIndexDepart(indexEau);
        logement.setJiramaTypeGestion(type);
        logement.setJiramaMethodeRepartition(type.necessiteRepartition()
                ? "Répartition au prorata de la consommation relevée sur chaque sous-compteur."
                : null);
        logement.setProprietaire(proprietaire);
        return logement;
    }

    private static ContratDeBail nouveauContrat(Logement logement, Utilisateur locataire,
                                                LocalDate debut, LocalDate fin, String loyer, String caution,
                                                int jourPaiement, StatutContrat statut) {
        ContratDeBail contrat = new ContratDeBail();
        contrat.setLogement(logement);
        contrat.setLocataire(locataire);
        contrat.setDateDebut(debut);
        contrat.setDateFin(fin);
        contrat.setMontantLoyerMga(new BigDecimal(loyer));
        contrat.setMontantCautionMga(new BigDecimal(caution));
        contrat.setJourPaiementMensuel(jourPaiement);
        contrat.setDureePreavisMois(3);
        contrat.setStatutActuel(statut);
        return contrat;
    }

    private static PaiementLoyer nouveauPaiement(ContratDeBail contrat, LocalDate periode, String attendu,
                                                 String paye, LocalDate dateEffective, String mode,
                                                 StatutPaiement statut, boolean quittance) {
        PaiementLoyer paiement = new PaiementLoyer(periode.getMonthValue(), periode.getYear(),
                new BigDecimal(attendu));
        paiement.setContrat(contrat);
        paiement.setMontantPaye(new BigDecimal(paye));
        paiement.setDatePaiementEffectif(dateEffective);
        paiement.setModePaiement(mode);
        paiement.setStatut(statut);
        paiement.setRecuQuittanceGenere(quittance);
        return paiement;
    }
}
