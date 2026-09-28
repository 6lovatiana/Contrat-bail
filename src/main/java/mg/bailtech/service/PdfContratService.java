package mg.bailtech.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import mg.bailtech.model.ContratDeBail;
import mg.bailtech.repository.ContratDeBailRepository;

@Service
public class PdfContratService {
    private final ContratDeBailRepository contratRepository;

    public PdfContratService(ContratDeBailRepository contratRepository) {
        this.contratRepository = contratRepository;
    }

    @Transactional(readOnly = true)
    public byte[] genererPourContrat(Integer contratId) {
        ContratDeBail contrat = contratRepository.findById(contratId)
                .orElseThrow(() -> new IllegalArgumentException("Contrat introuvable : " + contratId));
        return generer(contrat);
    }

    public byte[] generer(ContratDeBail contrat) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 54, 54, 54, 54);
            PdfWriter.getInstance(document, output);
            document.open();

            Font titre = new Font(Font.HELVETICA, 15, Font.BOLD);
            Font texte = new Font(Font.HELVETICA, 10, Font.NORMAL);
            document.add(new Paragraph("CONTRAT DE BAIL A USAGE D'HABITATION", titre));
            document.add(new Paragraph("\nEntre les soussignes :", texte));
            document.add(new Paragraph("Bailleur : " + nom(contrat.getLogement().getProprietaire()), texte));
            document.add(new Paragraph("Locataire : " + nom(contrat.getLocataire()), texte));
            document.add(new Paragraph("\nLe present contrat concerne le logement situe a : "
                    + contrat.getLogement().getAdresseLot() + ", "
                    + contrat.getLogement().getQuartierFokontany() + ", "
                    + contrat.getLogement().getVille() + ".", texte));
            document.add(new Paragraph("\nDuree : du " + contrat.getDateDebut() + " au "
                    + contrat.getDateFin() + ".", texte));

            document.newPage();
            document.add(new Paragraph("CONDITIONS FINANCIERES ET SIGNATURES", titre));
            document.add(new Paragraph("\nLoyer mensuel : " + contrat.getMontantLoyerMga() + " MGA.", texte));
            document.add(new Paragraph("Depot de garantie : " + contrat.getMontantCautionMga() + " MGA.", texte));
            document.add(new Paragraph("Paiement le " + contrat.getJourPaiementMensuel()
                    + " de chaque mois. Preavis : " + contrat.getDureePreavisMois() + " mois.", texte));
            document.add(new Paragraph("\nStatut : " + contrat.getStatutActuel(), texte));
            document.add(new Paragraph("\nSignature du bailleur : ____________________\n\n"
                    + "Signature du locataire : ____________________", texte));
            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException exception) {
            throw new IllegalStateException("Impossible de generer le PDF du contrat.", exception);
        }
    }

    private String nom(mg.bailtech.model.Utilisateur utilisateur) {
        return utilisateur.getNom() + (utilisateur.getPrenom() == null ? "" : " " + utilisateur.getPrenom());
    }
}