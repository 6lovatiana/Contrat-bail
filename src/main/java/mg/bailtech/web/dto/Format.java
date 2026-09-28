package mg.bailtech.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Petits utilitaires de présentation partagés par les pages Thymeleaf.
 * <p>
 * Les montants sont affichés en Ariary avec un séparateur de milliers (obligation
 * d'affichage mentionnée dans {@code md/MVP.md}) et les dates au format français.
 */
public final class Format {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private static final DateTimeFormatter DATE_COURTE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRANCE);

    private static final DecimalFormat MONTANT;

    static {
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        MONTANT = new DecimalFormat("#,##0.00", symboles);
        MONTANT.setGroupingSize(3);
    }

    private Format() {
        // classe utilitaire
    }

    /** « 1 200 000,00 » — montant complet avec décimales. */
    public static String montant(BigDecimal valeur) {
        return valeur == null ? "0,00" : MONTANT.format(valeur);
    }

    /** « 1 200 000 » — montant arrondi à l'unité, pour les cartes de synthèse. */
    public static String montantEntier(BigDecimal valeur) {
        if (valeur == null) {
            return "0";
        }
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.FRANCE))
                .format(valeur.setScale(0, RoundingMode.HALF_UP));
    }

    /** « 01/10/2026 ». */
    public static String date(LocalDate valeur) {
        return valeur == null ? "" : DATE.format(valeur);
    }

    /** « 12 sept. 2026 ». */
    public static String dateCourte(LocalDate valeur) {
        return valeur == null ? "" : DATE_COURTE.format(valeur);
    }

    /** « 05 » — jour de paiement contracts. */
    public static String jour(Integer valeur) {
        return valeur == null ? "05" : String.format("%02d", valeur);
    }

    /**
     * « 101 ••• 890 » : ne jamais afficher une CIN en clair dans les listes, seules
     * les trois premières et les trois derniers chiffres restent lisibles.
     */
    public static String cinMasque(String cin) {
        if (cin == null || cin.isBlank()) {
            return "";
        }
        String chiffres = cin.replaceAll("\\D", "");
        if (chiffres.length() <= 4) {
            return "••• " + chiffres;
        }
        String debut = chiffres.substring(0, Math.min(3, chiffres.length() - 4));
        return debut + " ••• " + chiffres.substring(chiffres.length() - 3);
    }
}
