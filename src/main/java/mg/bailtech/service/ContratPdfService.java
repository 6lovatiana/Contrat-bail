package mg.bailtech.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.lowagie.text.pdf.PdfReader;
import mg.bailtech.model.ContratDeBail;
import mg.bailtech.model.Logement;
import mg.bailtech.model.PaiementLoyer;
import mg.bailtech.model.Utilisateur;
import mg.bailtech.repository.ContratDeBailRepository;
import mg.bailtech.repository.PaiementLoyerRepository;
import mg.bailtech.web.dto.ContratPdfModel;
import mg.bailtech.web.dto.ContratPdfModel.LigneEcheance;
import mg.bailtech.web.dto.DocumentPdf;
import mg.bailtech.web.dto.Format;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;

/**
 * Moteur documentaire : produit le PDF du contrat de bail à partir du gabarit
 * Thymeleaf {@code contrat/contrat-pdf.html}.
 * <p>
 * La chaîne de rendu est volontairement simple et explicite :
 * <ol>
 *   <li>{@link ContratPdfModel} est assemblé en transaction (les associations
 *       paresseuses sont lues ici, jamais pendant le rendu) ;</li>
 *   <li>le gabarit est rendu en XHTML par le moteur dédié
 *       {@link mg.bailtech.config.ConfigurationImpression} ;</li>
 *   <li>Flying Saucer (backend OpenPDF) convertit ce XHTML en PDF.</li>
 * </ol>
 *
 * <h2>Polices</h2>
 * Le document utilise les polices PDF standard (Times, Helvetica), disponibles
 * partout sans fichier embarqué — le conteneur Docker n'installe aucune police
 * système. Leur jeu de caractères est Latin-1 : le gabarit et les données doivent
 * donc rester dans ce jeu (pas de tiret cadratin, denseigne ou de ligature « œ »).
 * Une police TrueType Unicode est à foreseen si des identités malgaches en
 * alphabets non latins doivent être supportées.
 */
@Service
public class ContratPdfService {

    private static final Logger LOG = LoggerFactory.getLogger(ContratPdfService.class);

    private static final String GABARIT = "contrat/contrat-pdf";
    private static final int NB_PAGES_ATTENDU = 2;

    private final SpringTemplateEngine moteurImpression;
    private final ContratDeBailRepository contrats;
    private final PaiementLoyerRepository paiements;

    public ContratPdfService(SpringTemplateEngine moteurImpression,
                             ContratDeBailRepository contrats,
                             PaiementLoyerRepository paiements) {
        this.moteurImpression = moteurImpression;
        this.contrats = contrats;
        this.paiements = paiements;
    }

    /**
     * Génère le PDF du contrat demandé.
     *
     * @throws RegleMetierException si le contrat n'existe pas
     */
    @Transactional(readOnly = true)
    public DocumentPdf generer(Integer contratId) {
        ContratDeBail contrat = contrats.findById(contratId)
                .orElseThrow(() -> new RegleMetierException(null, "contrat.introuvable",
                        "Le contrat n° " + contratId + " est introuvable."));

        ContratPdfModel modele = assemblerModele(contrat);
        String xhtml = moteurImpression.process(GABARIT, Set.of(), contexte(modele));
        byte[] contenu = convertirEnPdf(xhtml);
        int nbPages = compterPages(contenu);

        if (nbPages != NB_PAGES_ATTENDU) {
            // Le document reste valide, mais la maquette prévoit deux pages : on
            // laisse une trace exploitable plutôt que de laisser croire à un bug.
            LOG.warn("Contrat n° {} : PDF de {} page(s) au lieu de {}.", contratId, nbPages, NB_PAGES_ATTENDU);
        }
        LOG.info("Contrat n° {} : PDF généré ({} page(s), {} octets).", contratId, nbPages, contenu.length);

        return new DocumentPdf(nomFichier(contrat), contenu, nbPages);
    }

    private Context contexte(ContratPdfModel modele) {
        Context contexte = new Context(Locale.FRANCE);
        contexte.setVariable("contrat", modele);
        contexte.setVariable("appareil", "Bailtech");
        return contexte;
    }

    /** Conversion XHTML -> PDF par Flying Saucer. */
    private byte[] convertirEnPdf(String xhtml) {
        try {
            ITextRenderer renderer = new ITextRenderer();
            // "file:///" : URL de base utilisée pour résoudre les ressources
            // externes (images) éventuelles du gabarit.
            renderer.setDocumentFromString(xhtml, "file:///");
            renderer.layout();

            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            renderer.createPDF(sortie);
            return sortie.toByteArray();
        } catch (Exception e) {
            throw new RegleMetierException(null, "pdf.generation.failed",
                    "Le PDF du contrat n'a pas pu être généré : " + e.getMessage());
        }
    }

    /**
     * Compte les pages en relisant le PDF produit : OpenPDF est déjà sur le
     * classpath, inutile d'analyser la structure du fichier à la main.
     */
    private int compterPages(byte[] pdf) {
        try (PdfReader lecteur = new PdfReader(pdf)) {
            return lecteur.getNumberOfPages();
        } catch (Exception e) {
            LOG.warn("Nombre de pages illisible : {}", e.getMessage());
            return 0;
        }
    }

    private String nomFichier(ContratDeBail contrat) {
        String lot = contrat.getLogement() == null ? "contrat" : contrat.getLogement().getAdresseLot();
        return "contrat-de-bail-" + normaliser(lot) + "-" + contrat.getId() + ".pdf";
    }

    private static String normaliser(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return "sans-lot";
        }
        return valeur.trim().toLowerCase(Locale.FRANCE).replaceAll("[^a-z0-9]+", "-");
    }

    // ==================================================================
    // Assemblage du modèle
    // ==================================================================

    /**
     * Assemble le modèle du document. Appelé depuis {@link #generer(Integer)},
     * qui est déjà transactionnel : les associations paresseuses sont donc
     * lisibles ici sans annoter une seconde fois une méthode auto-invoquée.
     */
    private ContratPdfModel assemblerModele(ContratDeBail contrat) {
        ContratPdfModel modele = new ContratPdfModel();
        modele.setReference("N° " + contrat.getId() + "/" + contrat.getDateDebut().getYear());
        modele.setLieu(contrat.getLogement() == null ? "ANTANANARIVO" : contrat.getLogement().getVille().toUpperCase(Locale.FRANCE));
        modele.setDateEdition(Format.date(LocalDate.now()));

        remplirBailleur(modele, contrat.getLogement() == null ? null : contrat.getLogement().getProprietaire());
        remplirLocataire(modele, contrat.getLocataire());
        remplirBien(modele, contrat.getLogement());
        remplirConditions(modele, contrat);
        modele.setEcheances(remplirEcheances(contrat));
        return modele;
    }

    private void remplirBailleur(ContratPdfModel modele, Utilisateur bailleur) {
        if (bailleur == null) {
            return;
        }
        modele.setBailleurNom(bailleur.getNomComplet());
        modele.setBailleurCin(bailleur.getCinNumero());
        modele.setBailleurProfession(bailleur.getProfession() == null ? "Non renseignée" : bailleur.getProfession());
        modele.setBailleurAdresse(bailleur.getAdresseActuelle());
        modele.setBailleurTelephone(bailleur.getTelephone());
    }

    private void remplirLocataire(ContratPdfModel modele, Utilisateur locataire) {
        if (locataire == null) {
            return;
        }
        modele.setLocataireNom(locataire.getNomComplet());
        modele.setLocataireCin(locataire.getCinNumero());
        modele.setLocataireAdresse(locataire.getAdresseActuelle());
        modele.setLocataireTelephone(locataire.getTelephone());
    }

    private void remplirBien(ContratPdfModel modele, Logement logement) {
        if (logement == null) {
            return;
        }
        modele.setBienType(logement.getTypeDeBien());
        modele.setBienAdresse("Lot " + logement.getAdresseLot());
        modele.setBienQuartier(logement.getQuartierFokontany());
        modele.setBienVille(logement.getVille());
        modele.setBienPieces(logement.getNombrePiecesPrincipales() + " pièce(s) principale(s)");
        modele.setBienDescription(logement.getDescriptionConsistance() == null
                ? "Non renseignée" : logement.getDescriptionConsistance());
        modele.setBienModeComptage(logement.getJiramaTypeGestion() == null
                ? "" : logement.getJiramaTypeGestion().getLibelle());
        modele.setBienMethodeRepartition(logement.getJiramaMethodeRepartition() == null
                ? "" : logement.getJiramaMethodeRepartition());
        modele.setBienCompteurElectricite(logement.getCompteurElectriciteNumero() == null
                ? "" : logement.getCompteurElectriciteNumero());
        modele.setBienCompteurEau(logement.getCompteurEauNumero() == null
                ? "" : logement.getCompteurEauNumero());
    }

    private void remplirConditions(ContratPdfModel modele, ContratDeBail contrat) {
        modele.setDateDebut(Format.date(contrat.getDateDebut()));
        modele.setDateFin(Format.date(contrat.getDateFin()));
        modele.setLoyer(Format.montant(contrat.getMontantLoyerMga()));
        // Article 3 : le montant figure en chiffres ET en toutes lettres, cette
        // seconde forme allein faisant foi en cas de contestation. La conversion
        // est faite ici plutôt que dans le gabarit, afin que le modèle PDF reste
        // une simple projection des données du contrat.
        modele.setLoyerEnLettres(ConversionMontant.enLettres(contrat.getMontantLoyerMga()));
        modele.setCaution(Format.montant(contrat.getMontantCautionMga()));
        modele.setCautionMois(contrat.getCautionEnMoisDeLoyer().toPlainString());
        modele.setJourPaiement(Format.jour(contrat.getJourPaiementMensuel()));
        modele.setDureePreavis(contrat.getDureePreavisMois() + " mois");
        modele.setStatut(contrat.getStatutActuel() == null ? "" : contrat.getStatutActuel().getLibelle());
        modele.setStatutCss(switch (contrat.getStatutActuel() == null ? "" : contrat.getStatutActuel().name()) {
            case "EN_COURS" -> "#065f46";
            case "EN_ATTENTE_SIGNATURE" -> "#92400e";
            case "RESILIE" -> "#991b1b";
            default -> "#334155";
        });
    }

    private List<LigneEcheance> remplirEcheances(ContratDeBail contrat) {
        return paiements.findByContratIdOrderByPeriodeAnneeDescPeriodeMoisDesc(contrat.getId())
                .stream()
                .map(this::ligne)
                .toList();
    }

    private LigneEcheance ligne(PaiementLoyer paiement) {
        return new LigneEcheance(
                paiement.getPeriodeLisible(),
                Format.montant(paiement.getMontantAttendu()) + " MGA",
                paiement.getStatut() == null ? "" : paiement.getStatut().getLibelle(),
                switch (paiement.getStatut() == null ? "" : paiement.getStatut().name()) {
                    case "PAYE" -> "#065f46";
                    case "EN_RETARD" -> "#991b1b";
                    case "PARTIEL" -> "#92400e";
                    default -> "#334155";
                });
    }
}
