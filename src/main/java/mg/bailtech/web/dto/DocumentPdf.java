package mg.bailtech.web.dto;

/**
 * Document généré, prêt à être renvoyé au navigateur.
 *
 * @param nomFichier  nom proposé au téléchargement
 * @param contenu     octets du PDF
 * @param nbPages     nombre de pages produites
 */
public record DocumentPdf(String nomFichier, byte[] contenu, int nbPages) {

    public static final String TYPE_MIME = "application/pdf";

    public long taille() {
        return contenu == null ? 0 : contenu.length;
    }
}
