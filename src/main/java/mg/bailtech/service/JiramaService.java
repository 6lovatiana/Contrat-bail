package mg.bailtech.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import mg.bailtech.web.dto.Format;
import mg.bailtech.web.dto.JiramaForm;
import mg.bailtech.web.dto.ReleveSousCompteur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Câblage du calculateur JIRAMA : lecture des index, répartition, mise à jour
 * des échéances.
 *
 * <p>Ce service fait le lien entre les trois couches du module :
 * <ol>
 *   <li>la <strong>saisie</strong> — index du compteur principal et index des
 *       sous-compteurs, saisis dans le gabarit ;</li>
 *   <li>l'<strong>algorithme</strong> — {@link MoteurRepartitionJirama}, pur et
 *       sans dépendance, testé séparément ;</li>
 *   <li>la <strong>persistance</strong> — la part de chaque locataire est
 *       imputée sur son échéance de la période choisie.</li>
 * </ol>
 *
 * <h2>Où est enregistrée la part JIRAMA&nbsp;?</h2>
 * Le modèle v1 réserve {@code paiement_loyer} à une échéance mensuelle unique
 * par contrat et par période ({@code uq_paiement_periode})&nbsp;: il n'existe
 * aucune ligne distincte pour les charges. La part JIRAMA est donc <strong>
 * affectée</strong> — et non ajoutée — à {@code montant_attendu}&nbsp;:
 * <pre>
 *     montant_attendu = contrat.montant_Loyer_Mga + part JIRAMA du locataire
 * </pre>
 * <p>Le loyer du contrat étant la source unique de vérité, cette affectation est
 * <strong>idempotente</strong>&nbsp;: relancer le calcul remplace la valeur
 * précédente au lieu de la cumuler. Le loyer seul reste déductible, il suffit de
 * retrancher la part affichée par le dernier calcul.
 * <p>Une table {@code charge_jirama} dédiée serait le correctif structurel&nbsp;;
 * elle sort du périmètre du modèle livré (cf. README, §7).
 *
 * <h2>Garde-fous</h2>
 * <ul>
 *   <li>une échéance déjà {@code PAYE} n'est jamais modifiée&nbsp;: on ne
 *       réécrit pas un montant encaissé&nbsp;;</li>
 *   <li>la période doit être postérieure ou égale au début du bail et au plancher
 *       de la base ({@link PaiementLoyer#PERIODE_MIN})&nbsp;;</li>
 *   <li>le relevé fourni est confronté aux baux réellement en cours du
 *       bailleur&nbsp;: un identifiant de contrat foreign est ignoré, et les
 *       index de départ sont réinjectés depuis la base, jamais acceptés du
 *       client.</li>
 * </ul>
 */
@Service
public class JiramaService {

    private static final Logger LOG = LoggerFactory.getLogger(JiramaService.class);

    private final LogementRepository logements;

    private final ContratDeBailRepository contrats;

    private final PaiementLoyerRepository paiements;

    private final EcheanceService echeanceService;

    public JiramaService(LogementRepository logements,
                         ContratDeBailRepository contrats,
                         PaiementLoyerRepository paiements,
                         EcheanceService echeanceService) {
        this.logements = logements;
        this.contrats = contrats;
        this.paiements = paiements;
        this.echeanceService = echeanceService;
    }

    // ==================================================================
    // Préparation de l'écran
    // ==================================================================

    /**
     * Constitue le formulaire de calcul pour un bailleur donné.
     *
     * @param bailleur  bailleur dont les biens sont proposés
     * @param logementId compteur principal retenu, ou {@code null} pour le premier
     * @param saisi     formulaire à réinitialiser, ou {@code null} pour en créer un
     */
    @Transactional(readOnly = true)
    public JiramaForm preparerFormulaire(Utilisateur bailleur, Integer logementId, JiramaForm saisi) {
        JiramaForm formulaire = saisi == null ? new JiramaForm() : saisi;
        formulaire.setSousCompteurs(new ArrayList<>());

        if (bailleur == null) {
            formulaire.setLogementId(null);
            return formulaire;
        }

        List<Logement> parc =
                logements.findByProprietaireIdOrderByVilleAscQuartierFokontanyAsc(bailleur.getId());

        Logement retenu = null;
        if (logementId != null) {
            Integer recherche = logementId;
            retenu = parc.stream().filter(l -> recherche.equals(l.getId())).findFirst().orElse(null);
        }
        if (retenu == null && !parc.isEmpty()) {
            retenu = parc.get(0);
        }
        formulaire.initialiserDepuis(retenu);

        // Un sous-compteur n'a de sens que pour un bail en cours : c'est le
        // locataire occupant qui fournit le relevé.
        for (ContratDeBail contrat : contrats.findContratsActifsDuBailleur(bailleur.getId())) {
            Logement bien = contrat.getLogement();
            if (bien == null) {
                continue;
            }
            Utilisateur locataire = contrat.getLocataire();
            ReleveSousCompteur releve = new ReleveSousCompteur(
                    contrat.getId(), bien.getId(),
                    locataire == null ? "" : locataire.getNomComplet(),
                    bien.getLibelle());
            releve.setIndexDepartElectricite(decale(bien.getCompteurElectriciteIndexDepart()));
            releve.setIndexDepartEau(decale(bien.getCompteurEauIndexDepart()));
            releve.setTypeGestion(bien.getJiramaTypeGestion() == null
                    ? "" : bien.getJiramaTypeGestion().getLibelle());
            // Le champ se pré-remplit avec l'index de départ : sans nouvelle
            // lecture, la consommation affichée est nulle, jamais négative.
            releve.setIndexElectricite(releve.getIndexDepartElectricite());
            releve.setIndexEau(releve.getIndexDepartEau());
            formulaire.getSousCompteurs().add(releve);
        }
        return formulaire;
    }

    // ==================================================================
    // Calcul
    // ==================================================================

    /**
     * Répartit la facture JIRAMA entre les locataires, sans rien écrire.
     *
     * @return le résultat, indexé par contrat pour l'affichage et l'imputation
     */
    @Transactional(readOnly = true)
    public ResultatJirama calculer(JiramaForm formulaire) {
        if (formulaire == null) {
            throw new RegleMetierException(null, "jirama.formulaire.absent",
                    "Aucun relevé n'a été soumis.");
        }
        // Les index de départ font foi : un client ne peut pas les modifier pour
        // gonfler sa consommation.
        List<ReleveSousCompteur> releves = reverifierIndexDepart(formulaire);
        formulaire.setSousCompteurs(releves);

        // Le mode de gestion du compteur fait autorité sur la fiche du bien, pas
        // sur le formulaire : celui-ci n'est pas re-soumis par le navigateur, et
        // un compteur unique doit être reconnu comme tel même si le formulaire
        // est construit ailleurs. Sans cette relecture, un compteur UNIQUE
        // parviendrait avec sa valeur par défaut et le choix « à la charge du
        // bailleur » serait silencieusement ignoré.
        TypeCompteurJirama typeGestion = typeGestionReel(formulaire);

        List<MoteurRepartitionJirama.ConsommationOccupant> consommations = new ArrayList<>();
        for (ReleveSousCompteur releve : releves) {
            consommations.add(new MoteurRepartitionJirama.ConsommationOccupant(
                    String.valueOf(releve.getContratId()),
                    releve.getConsommationElectricite(),
                    releve.getConsommationEau()));
        }

        MoteurRepartitionJirama.Resultat repartition = MoteurRepartitionJirama.repartir(
                consommations,
                formulaire.getConsommationElectricitePrincipale(),
                formulaire.getConsommationEauPrincipale(),
                formulaire.getPrixUnitaireElectricite(),
                formulaire.getPrixUnitaireEau(),
                formulaire.getMontantFacture(),
                formulaire.getModeEcart() == null
                        ? MoteurRepartitionJirama.ModeEcart.PRORATA : formulaire.getModeEcart(),
                typeGestion);

        // Rapprochement part par part, dans l'ordre des relevés saisis.
        Map<Integer, LigneCharge> lignes = new LinkedHashMap<>();
        for (int i = 0; i < releves.size(); i++) {
            ReleveSousCompteur releve = releves.get(i);
            MoteurRepartitionJirama.LigneRepartition part =
                    repartition.lignes().get(i);
            LigneCharge ligne = new LigneCharge();
            ligne.contratId = releve.getContratId();
            ligne.logementId = releve.getLogementId();
            ligne.locataire = releve.getLocataire();
            ligne.bien = releve.getBien();
            ligne.consommationElectricite = part.consoElectricite();
            ligne.consommationEau = part.consoEau();
            ligne.partElectricite = part.partElectricite();
            ligne.partEau = part.partEau();
            ligne.partTotale = part.total();
            ligne.periode = formulaire.getPeriode();
            lignes.put(releve.getContratId(), ligne);
        }

        LOG.info("Repartition JIRAMA : {} locataire(s), facture {} Ar, reparti {} Ar, ecart {} Ar, mode {}.",
                lignes.size(), repartition.montantFacture(), repartition.montantReparti(),
                repartition.ecart(), formulaire.getModeEcart());

        return new ResultatJirama(repartition, lignes, formulaire.getPeriode());
    }

    // ==================================================================
    // Imputation sur les échéances
    // ==================================================================

    /**
     * Calcule la répartition puis l'impute sur l'échéance de chaque locataire.
     *
     * <p><strong>Privée à dessein</strong> : cette méthode écrit en base et ne
     * connaît pas le bailleur. Elle n'est atteignable que par
     * {@link #appliquer(JiramaForm, Utilisateur)}, qui a vérifié le périmètre.
     * La laisser publique rendrait possible un appel ultérieur — un rapport,
     * une tâche de nuit — contournant le contrôle, sans que l'appelant s'en
     * aperçoive.
     *
     * @return le détail des écritures effectuées
     */
    @Transactional
    private ResultatJirama appliquerVerifie(JiramaForm formulaire) {
        ResultatJirama resultat = calculer(formulaire);
        YearMonth periode = resultat.getPeriode();

        if (periode.atDay(1).isBefore(PaiementLoyer.PERIODE_MIN)) {
            throw new RegleMetierException("mois", "jirama.periode.refusee",
                    "La période " + periode + " est antérieure au plancher de la base ("
                            + PaiementLoyer.PERIODE_MIN.getMonthValue() + "/"
                            + PaiementLoyer.PERIODE_MIN.getYear() + ").");
        }

        List<Ecriture> ecritures = new ArrayList<>();
        for (LigneCharge ligne : resultat.getLignes()) {
            ecritures.add(imputer(ligne, periode));
        }
        resultat.setEcritures(ecritures);

        long reportees = ecritures.stream().filter(Ecriture::isReportee).count();
        LOG.info("Charges JIRAMA de {} imputees sur {} echeance(s), {} reportee(s).",
                periode, ecritures.size() - reportees, reportees);
        return resultat;
    }

    /**
     * Variante qui refuse d'écrire si le formulaire désigne un bien ou un
     * contrat hors du parc du bailleur.
     *
     * <p>Le contrôle est fait <em>avant</em> toute écriture, et il est bloquant
     * : la répartition étant recalculée à partir des seuls relevés vérifiés, un
     * contrat étranger ne figure simplement pas dans le résultat. L'ignorer
     * silencieusement, comme le fait {@link #reverifierIndexDepart}, laisserait
     * croire à une imputation complète alors qu'une partie a été omise.
     *
     * @throws RegleMetierException si un identifiant du formulaire n'appartient
     *         pas au bailleur
     */
    @Transactional
    public ResultatJirama appliquer(JiramaForm formulaire, Utilisateur bailleur) {
        verifierPerimetre(formulaire, bailleur);
        return appliquerVerifie(formulaire);
    }

    /**
     * Vérifie que tous les identifiants du formulaire désignent des ressources
     * du bailleur.
     *
     * @throws RegleMetierException au premier identifiant étranger
     */
    private void verifierPerimetre(JiramaForm formulaire, Utilisateur bailleur) {
        if (bailleur == null) {
            throw new RegleMetierException(null, "jirama.bailleur.absent",
                    "Aucun bailleur connecté : reconnectez-vous pour imputer des charges.");
        }
        Integer identifiantBailleur = bailleur.getId();

        if (formulaire.getLogementId() != null) {
            Logement bien = logements.findById(formulaire.getLogementId()).orElse(null);
            if (bien == null || bien.getProprietaire() == null
                    || !identifiantBailleur.equals(bien.getProprietaire().getId())) {
                LOG.warn("Imputation JIRAMA refusée : le bien n° {} n'appartient pas au bailleur n° {}.",
                        formulaire.getLogementId(), identifiantBailleur);
                throw new RegleMetierException("logementId", "jirama.logement.autorisation",
                        "Le bien indiqué n'appartient pas à votre parc immobilier.");
            }
        }

        for (ReleveSousCompteur releve : formulaire.getSousCompteurs()) {
            if (releve == null || releve.getContratId() == null) {
                continue;
            }
            ContratDeBail contrat = contrats.findById(releve.getContratId()).orElse(null);
            if (contrat == null || contrat.getLogement() == null
                    || contrat.getLogement().getProprietaire() == null
                    || !identifiantBailleur.equals(contrat.getLogement().getProprietaire().getId())) {
                LOG.warn("Imputation JIRAMA refusée : le contrat n° {} n'appartient pas au bailleur n° {}.",
                        releve.getContratId(), identifiantBailleur);
                throw new RegleMetierException("sousCompteurs", "jirama.contrat.autorisation",
                        "Un des relevés désigne un contrat qui ne fait pas partie de votre parc.");
            }
        }
    }

    /**
     * Écrit la part d'un locataire sur son échéance.
     * <p>
     * Affectation et non addition&nbsp;: {@code montant_attendu} est recalculé
     * depuis le loyer du contrat, ce qui rend l'opération rejouable sans jamais
     * cumuler deux fois les mêmes charges.
     */
    private Ecriture imputer(LigneCharge ligne, YearMonth periode) {
        Ecriture ecriture = new Ecriture();
        ecriture.locataire = ligne.locataire;
        ecriture.bien = ligne.bien;
        ecriture.periode = periode;
        ecriture.partJirama = ligne.partTotale;
        ecriture.reportee = false;

        if (ligne.contratId == null) {
            ecriture.reportee = true;
            ecriture.motif = "relevé sans contrat rattaché";
            return ecriture;
        }

        ContratDeBail contrat = contrats.findById(ligne.contratId).orElse(null);
        if (contrat == null) {
            ecriture.reportee = true;
            ecriture.motif = "contrat introuvable";
            return ecriture;
        }

        YearMonth debut = YearMonth.from(contrat.getDateDebut());
        if (periode.isBefore(debut)) {
            ecriture.reportee = true;
            ecriture.motif = "période antérieure au début du bail (" + debut + ")";
            return ecriture;
        }

        // L'échéance doit exister avant d'être modifiée ; la génération est
        // idempotente et ne produit que les périodes manquantes.
        echeanceService.genererEcheances(contrat);
        Optional<PaiementLoyer> echeance = paiements.findByContratIdAndPeriodeMoisAndPeriodeAnnee(
                contrat.getId(), periode.getMonthValue(), periode.getYear());
        if (echeance.isEmpty()) {
            ecriture.reportee = true;
            ecriture.motif = "aucune échéance n'a pu être ouverte pour cette période";
            return ecriture;
        }

        PaiementLoyer paiement = echeance.get();
        ecriture.montantAvant = paiement.getMontantAttendu();

        if (paiement.getStatut() == StatutPaiement.PAYE) {
            // Réécrire le montant d'une échéance encaissée fausserait un reçu
            // déjà délivré : on laisse la ligne en place et on le signale.
            ecriture.reportee = true;
            ecriture.motif = "échéance déjà réglée";
            return ecriture;
        }

        BigDecimal loyer = contrat.getMontantLoyerMga() == null
                ? BigDecimal.ZERO : contrat.getMontantLoyerMga();
        paiement.setMontantAttendu(loyer.add(ligne.partTotale));
        paiements.save(paiement);

        ecriture.montantApres = paiement.getMontantAttendu();
        ecriture.chargeAncienne = ecriture.montantAvant == null
                ? BigDecimal.ZERO : ecriture.montantAvant.subtract(loyer);
        return ecriture;
    }

    // ==================================================================
    // Types de sortie
    // ==================================================================

    /**
     * Part JIRAMA revenant à un locataire, rapprochée de sa fiche.
     *
     * <p>Classe interne au service : elle transporte les montants calculés par
     * l'algorithme jusqu'à la vue, sans qu'aucun gabarit n'accède à une entité
     * JPA paresseuse.
     */
    public static class LigneCharge {

        private Integer contratId;

        private Integer logementId;

        private String locataire = "";

        private String bien = "";

        private BigDecimal consommationElectricite = BigDecimal.ZERO;

        private BigDecimal consommationEau = BigDecimal.ZERO;

        private BigDecimal partElectricite = BigDecimal.ZERO;

        private BigDecimal partEau = BigDecimal.ZERO;

        private BigDecimal partTotale = BigDecimal.ZERO;

        private YearMonth periode;

        public Integer getContratId() {
            return contratId;
        }

        /**
         * Montants déjà formatés à la française (espace de milliers, virgule
         * décimale). Les champs sont préparés ici plutôt que dans le gabarit :
         * {@code Format} est une classe utilitaire statique, sans bean, donc
         * inaccessible par {@code @format}, et #numbers ne sait pas mettre en
         * forme un BigDecimal avec séparateur de milliers.
         */
        public String getPartElectriciteLisible() {
            return Format.montantEntier(this.partElectricite);
        }

        public String getPartEauLisible() {
            return Format.montantEntier(this.partEau);
        }

        public String getPartTotaleLisible() {
            return Format.montantEntier(this.partTotale);
        }

        public String getConsommationElectriciteLisible() {
            return Format.nombre(this.consommationElectricite);
        }

        public String getConsommationEauLisible() {
            return Format.nombre(this.consommationEau);
        }

        public Integer getLogementId() {
            return logementId;
        }

        public String getLocataire() {
            return locataire;
        }

        public String getBien() {
            return bien;
        }

        public BigDecimal getConsommationElectricite() {
            return consommationElectricite;
        }

        public BigDecimal getConsommationEau() {
            return consommationEau;
        }

        public BigDecimal getPartElectricite() {
            return partElectricite;
        }

        public BigDecimal getPartEau() {
            return partEau;
        }

        public BigDecimal getPartTotale() {
            return partTotale;
        }

        public YearMonth getPeriode() {
            return periode;
        }

        public String getPeriodeLisible() {
            return periode == null ? "" : String.format("%02d/%d",
                    periode.getMonthValue(), periode.getYear());
        }
    }

    /** Trace d'une imputation sur l'échéance d'un locataire. */
    public static class Ecriture {

        private String locataire = "";

        private String bien = "";

        private YearMonth periode;

        private BigDecimal partJirama = BigDecimal.ZERO;

        private BigDecimal montantAvant = BigDecimal.ZERO;

        private BigDecimal montantApres = BigDecimal.ZERO;

        private BigDecimal chargeAncienne = BigDecimal.ZERO;

        private boolean reportee;

        private String motif = "";

        public String getLocataire() {
            return locataire;
        }

        public String getBien() {
            return bien;
        }

        public YearMonth getPeriode() {
            return periode;
        }

        public String getPeriodeLisible() {
            return periode == null ? "" : String.format("%02d/%d",
                    periode.getMonthValue(), periode.getYear());
        }

        public BigDecimal getPartJirama() {
            return partJirama;
        }

        public BigDecimal getMontantAvant() {
            return montantAvant;
        }

        public BigDecimal getMontantApres() {
            return montantApres;
        }

        /** Montants formatés à la française, cf. {@link LigneCharge}. */        public String getPartJiramaLisible() {
            return Format.montantEntier(partJirama);
        }

        public String getMontantAvantLisible() {
            return Format.montantEntier(montantAvant);
        }

        public String getMontantApresLisible() {
            return Format.montantEntier(montantApres);
        }

        public BigDecimal getChargeAncienne() {
            return chargeAncienne;
        }

        public boolean isReportee() {
            return reportee;
        }

        public String getMotif() {
            return motif;
        }

        /** Variation du montant attendu, négative si la charge avait diminué. */
        public BigDecimal getVariation() {
            if (montantAvant == null || montantApres == null) {
                return BigDecimal.ZERO;
            }
            return montantApres.subtract(montantAvant);
        }
    }

    /** Résultat complet d'un calcul, avec les écritures lorsqu'il a été appliqué. */
    public static class ResultatJirama {

        private final MoteurRepartitionJirama.Resultat repartition;

        private final Map<Integer, LigneCharge> lignes;

        private final YearMonth periode;

        private List<Ecriture> ecritures = new ArrayList<>();

        ResultatJirama(MoteurRepartitionJirama.Resultat repartition,
                       Map<Integer, LigneCharge> lignes,
                       YearMonth periode) {
            this.repartition = repartition;
            this.lignes = lignes;
            this.periode = periode;
        }

        void setEcritures(List<Ecriture> ecritures) {
            this.ecritures = ecritures == null ? new ArrayList<>() : ecritures;
        }

        public List<LigneCharge> getLignes() {
            return new ArrayList<>(lignes.values());
        }

        public BigDecimal getMontantFacture() {
            return repartition.montantFacture();
        }

        public BigDecimal getMontantMesureTotal() {
            return repartition.montantMesureTotal();
        }

        public BigDecimal getEcart() {
            return repartition.ecart();
        }

        public BigDecimal getEcartElectricite() {
            return repartition.ecartElectricite();
        }

        public BigDecimal getEcartEau() {
            return repartition.ecartEau();
        }

        public BigDecimal getMontantReparti() {
            return repartition.montantReparti();
        }

        public BigDecimal getConsommationElectricitePrincipale() {
            return repartition.consoElectricitePrincipale();
        }

        public BigDecimal getConsommationEauPrincipale() {
            return repartition.consoEauPrincipale();
        }

        /** Faux si la somme des sous-compteurs dépasse le compteur principal. */
        public boolean isCoherent() {
            return repartition.coherent();
        }

        public YearMonth getPeriode() {
            return periode;
        }

        public String getPeriodeLisible() {
            return periode == null ? "" : String.format("%02d/%d",
                    periode.getMonthValue(), periode.getYear());
        }

        public List<Ecriture> getEcritures() {
            return ecritures;
        }

        public boolean isApplique() {
            return !ecritures.isEmpty();
        }

        /** Agrégats formatés à la française, cf. {@link LigneCharge}. */
        public String getMontantFactureLisible() {
            return Format.montantEntier(repartition.montantFacture());
        }

        public String getMontantMesureTotalLisible() {
            return Format.montantEntier(repartition.montantMesureTotal());
        }

        public String getEcartLisible() {
            return Format.montantEntier(repartition.ecart());
        }

        public String getMontantRepartiLisible() {
            return Format.montantEntier(repartition.montantReparti());
        }

        public long getNbReportees() {
            return ecritures.stream().filter(Ecriture::isReportee).count();
        }
    }

    // ==================================================================
    // Internes
    // ==================================================================

    /**
     * Mode de gestion du compteur du bien retenu, relu en base.
     * <p>
     * À défaut de bien identifiable, la valeur portée par le formulaire est
     * retenue : le calcul reste possible, et c'est le seul cas où l'on accepte
     * une information non vérifiée en base.
     */
    private TypeCompteurJirama typeGestionReel(JiramaForm formulaire) {
        if (formulaire.getLogementId() == null) {
            return formulaire.getTypeGestion();
        }
        return logements.findById(formulaire.getLogementId())
                .map(Logement::getJiramaTypeGestion)
                .filter(java.util.Objects::nonNull)
                .orElseGet(formulaire::getTypeGestion);
    }

    /**
     * Réinjecte les index de départ registrés en base et ne conserve que les
     * relevés rattachés à un bail réellement en cours.
     */
    private List<ReleveSousCompteur> reverifierIndexDepart(JiramaForm formulaire) {
        List<ReleveSousCompteur> retenus = new ArrayList<>();

        for (ReleveSousCompteur releve : formulaire.getSousCompteurs()) {
            if (releve == null || releve.getContratId() == null) {
                continue;
            }
            Optional<ContratDeBail> trouve = contrats.findById(releve.getContratId());
            if (trouve.isEmpty() || trouve.get().getStatutActuel() != StatutContrat.EN_COURS) {
                continue;
            }
            // L'index de départ est celui du bien réel du bail, pas celui
            // qu'annonce le formulaire.
            Logement bien = trouve.get().getLogement();
            if (bien != null) {
                releve.setLogementId(bien.getId());
                releve.setIndexDepartElectricite(decale(bien.getCompteurElectriciteIndexDepart()));
                releve.setIndexDepartEau(decale(bien.getCompteurEauIndexDepart()));
            }
            if (releve.getIndexElectricite() == null) {
                releve.setIndexElectricite(releve.getIndexDepartElectricite());
            }
            if (releve.getIndexEau() == null) {
                releve.setIndexEau(releve.getIndexDepartEau());
            }
            retenus.add(releve);
        }
        return retenus;
    }

    private static BigDecimal decale(Integer valeur) {
        return valeur == null ? BigDecimal.ZERO : BigDecimal.valueOf(valeur);
    }
}
