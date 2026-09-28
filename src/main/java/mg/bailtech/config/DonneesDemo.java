package mg.bailtech.config;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    private static final String MOT_DE_PASSE_DEMO = "{demo-bailtech}";

    private final UtilisateurRepository utilisateurs;
    private final LogementRepository logements;
    private final ContratDeBailRepository contrats;
    private final PaiementLoyerRepository paiements;

    @Value("${bailtech.demo:false}")
    private boolean active;

    public DonneesDemo(UtilisateurRepository utilisateurs,
                       LogementRepository logements,
                       ContratDeBailRepository contrats,
                       PaiementLoyerRepository paiements) {
        this.utilisateurs = utilisateurs;
        this.logements = logements;
        this.contrats = contrats;
        this.paiements = paiements;
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
        LocalDate moisPrecedent = debutDeMois(aujourdhui).minusMonths(1);
        LocalDate moisCourant = debutDeMois(aujourdhui);

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

        paiements.saveAll(List.of(
                nouveauPaiement(actif1, moisPrecedent.minusMonths(1), "1200000.00", "1200000.00",
                        aujourdhui.minusMonths(1).withDayOfMonth(3), "MOBILE_MONEY", StatutPaiement.PAYE, true),
                nouveauPaiement(actif1, moisPrecedent, "1200000.00", "1200000.00",
                        aujourdhui.withDayOfMonth(4), "ESPECES", StatutPaiement.PAYE, true),
                nouveauPaiement(actif1, moisCourant, "1200000.00", "400000.00",
                        null, "MOBILE_MONEY", StatutPaiement.PARTIEL, false),
                nouveauPaiement(actif2, moisPrecedent, "650000.00", "0.00",
                        null, null, StatutPaiement.EN_RETARD, false),
                nouveauPaiement(actif2, moisCourant, "650000.00", "0.00",
                        null, null, StatutPaiement.A_PAYER, false),
                nouveauPaiement(actif3, moisPrecedent, "800000.00", "800000.00",
                        aujourdhui.withDayOfMonth(2), "VIREMENT", StatutPaiement.PAYE, true)));

        LOG.info("Jeu de démonstration chargé : 4 utilisateurs, 4 logements, 4 contrats, 6 échéances.");
    }

    /**
     * Premier jour du mois, ramené à l'année plancher de la contrainte
     * {@code CHECK (periode_annee >= 2026)} : le jeu de démonstration ne doit pas
     * échouer si l'on l'exécute sur une base antérieure.
     */
    private static LocalDate debutDeMois(LocalDate date) {
        int annee = Math.max(date.getYear(), PaiementLoyer.ANNEE_MIN);
        return LocalDate.of(annee, 1, 1);
    }

    private static Utilisateur nouvelUtilisateur(String nom, String prenom, String cin,
                                                 LocalDate cinDate, String cinLieu, String profession,
                                                 String adresse, String telephone, String email) {
        Utilisateur utilisateur = new Utilisateur(nom, prenom, cin);
        utilisateur.setCinDateDelivrance(cinDate);
        utilisateur.setCinLieuDelivrance(cinLieu);
        utilisateur.setProfession(profession);
        utilisateur.setAdresseActuelle(adresse);
        utilisateur.setTelephone(telephone);
        utilisateur.setEmail(email);
        utilisateur.setMotDePasse(MOT_DE_PASSE_DEMO);
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
