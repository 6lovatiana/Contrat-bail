package mg.bailtech.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversion d'un montant en Ariary en toutes lettres.
 * <p>
 * Exigence du MVP : le contrat doit porter le montant en chiffres <em>et</em>
 * en
 * toutes lettres (article 3). Implémentation sans dépendance externe.
 *
 * <h2>Règles de l'orthographe française appliquées</h2>
 * <ul>
 * <li>« quatre-vingts » et non « quatre-vingt » (pluriel de 80) ;</li>
 * <li>« quatre-vingt-dix », « soixante-dix », « dix-sept »… : les dizaines
 * françaises se construisent sur une base 20 pour 70 et 90 ;</li>
 * <li>« deux cents » avec un <em>s</em> seulement si rien ne suit
 * (200 → deux cents, 201 → deux cent un) ;</li>
 * <li>« mille » est invariable et ne se prépose jamais de « un »
 * (1 000 → mille, 4 000 000 → quatre cent mille et non « quatre cents
 * milles ») ;</li>
 * <li>devant « million » et « milliard » en revanche le <em>s</em> de « cent »
 * se maintient (200 000 000 → deux cents millions) ;</li>
 * <li>« et » devant quatre et vingt : « quatre-vingt-quatre » s'écrit avec un
 * trait d'union, « quatre et vingt » ne s'emploie que dans les dates.</li>
 * </ul>
 */
public final class ConversionMontant {

    private static final String[] UNITES = {
            "", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
            "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize",
            "dix-sept", "dix-huit", "dix-neuf"
    };

    private static final String[] DIZAINES = {
            "", "", "vingt", "trente", "quarante", "cinquante", "soixante",
            "soixante", "quatre-vingt", "quatre-vingt"
    };

    private ConversionMontant() {
        // classe utilitaire
    }

    /**
     * Convertit un montant en toutes lettres, en majuscule initiale.
     *
     * @param montant montant à convertir ; {@code null} ou négatif est traité comme
     *                0
     * @return « Quatre cent mille Ariary », « Mille deux cent Ariary et cinquante
     *         centimes », …
     */
    public static String enLettres(BigDecimal montant) {
        if (montant == null || montant.signum() < 0) {
            return "Zéro Ariary";
        }
        BigDecimal arrondi = montant.setScale(2, RoundingMode.HALF_UP);
        long entier = arrondi.longValue();
        int centimes = arrondi.subtract(new BigDecimal(entier))
                .movePointRight(2)
                .intValue();

        StringBuilder texte = new StringBuilder();
        if (entier == 0 && centimes == 0) {
            texte.append("Zéro Ariary");
        } else {
            if (entier > 0) {
                texte.append(majuscule(entierEnLettres(entier))).append(" Ariary");
            }
            if (centimes > 0) {
                if (entier > 0) {
                    texte.append(" et ");
                }
                // en minuscules : la majuscule n'a porté que sur le début du montant
                texte.append(centimesEnLettres(centimes));
            }
        }
        return texte.toString();
    }

    /** Partie entière, sans devise : 400000 → « quatre cent mille ». */
    public static String entierEnLettres(long nombre) {
        if (nombre == 0) {
            return "zéro";
        }
        if (nombre < 0) {
            return "moins " + entierEnLettres(-nombre);
        }

        StringBuilder texte = new StringBuilder();

        // milliards, puis millions : « deux milliards trois millions »
        long[] paliers = { 1_000_000_000L, 1_000_000L };
        String[] noms = { "milliard", "million" };
        for (int i = 0; i < paliers.length; i++) {
            long valeur = nombre / paliers[i];
            if (valeur == 0) {
                continue;
            }
            nombre %= paliers[i];
            texte.append(valeurEnLettres(valeur)).append(' ').append(noms[i]);
            if (valeur > 1) {
                texte.append('s');
            }
            if (nombre > 0) {
                texte.append(' ');
            }
        }

        // Le millier est particulier : « mille » est invariable et ne se prépose
        // jamais de « un » (1 000 → mille, 2 000 → deux mille, 4 000 000 → quatre
        // cent mille, et non « un mille »).
        if (nombre >= 1_000) {
            long milliers = nombre / 1_000;
            nombre %= 1_000;
            if (milliers > 1) {
                // « quatre cent mille » : le s de « cent » disparaît devant
                // « mille », contrairement à « deux cents millions ».
                texte.append(sansSDeCent(valeurEnLettres(milliers))).append(' ');
            }
            texte.append("mille");
            if (nombre > 0) {
                texte.append(' ');
            }
        }

        if (nombre > 0) {
            texte.append(valeurEnLettres(nombre));
        }
        return texte.toString();
    }

    /** « zéro », …, « quatre-vingt-dix-neuf ». */
    private static String valeurEnLettres(long nombre) {
        if (nombre == 0) {
            return "zéro";
        }
        if (nombre < 0) {
            return "moins " + valeurEnLettres(-nombre);
        }
        if (nombre < 20) {
            return UNITES[(int) nombre];
        }
        if (nombre < 100) {
            int dizaines = (int) (nombre / 10);
            int unites = (int) (nombre % 10);
            if (dizaines == 7) {
                // 70 = soixante-dix ; 71 = soixante et onze ; 72 = soixante-douze
                if (unites == 1) {
                    return "soixante et onze";
                }
                return "soixante-" + valeurEnLettres(10 + unites);
            }
            if (dizaines == 9) {
                // 90 = quatre-vingt-dix ; 91 = quatre-vingt-onze (sans « et »)
                return "quatre-vingt-" + valeurEnLettres(10 + unites);
            }
            if (unites == 0) {
                // 80 est le seul multiple de vingt qui prend un s
                return dizaines == 8 ? "quatre-vingts" : DIZAINES[dizaines];
            }
            if (unites == 1) {
                // « et » unit le vingt aux dizaines 20 à 60 (vingt et un,
                // soixante et un) ; il ne s'emploie ni après 80 ni après 90
                // (quatre-vingt-un, quatre-vingt-onze).
                return dizaines >= 2 && dizaines <= 6
                        ? DIZAINES[dizaines] + " et un"
                        : "quatre-vingt-un";
            }
            return DIZAINES[dizaines] + "-" + valeurEnLettres(unites);
        }
        if (nombre < 1000) {
            int centaines = (int) (nombre / 100);
            long reste = nombre % 100;
            String prefixe = centaines == 1 ? "cent" : UNITES[centaines] + " cent";
            if (reste == 0) {
                // deux cents, trois cents : le s s'ajoute sans élément suivant
                return centaines > 1 ? prefixe + "s" : prefixe;
            }
            return prefixe + " " + valeurEnLettres(reste);
        }
        // Les paliers supérieurs sont traités par entierEnLettres.
        return Long.toString(nombre);
    }

    private static String centimesEnLettres(int centimes) {
        String mot = centimes == 1 ? "centime" : "centimes";
        return valeurEnLettres(centimes) + " " + mot;
    }

    /**
     * Retire le <em>s</em> de « cent » lorsqu'un nom suit : « deux cent mille ».
     * Seule terminaison concernée, « quatre-vingts » restant intact.
     */
    private static String sansSDeCent(String texte) {
        if (texte.endsWith(" cents")) {
            return texte.substring(0, texte.length() - 1);
        }
        return texte;
    }

    private static String majuscule(String texte) {
        if (texte == null || texte.isEmpty()) {
            return texte;
        }
        return Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
    }

    /**
     * Met une majuscule initiale à un texte déjà converti.
     *
     * <p>Exposé parce que le contrat écrit certaines durées en toutes lettres
     * sans passer par {@link #enLettres} : le préavis et la durée du bail ne
     * sont pas des montants, et leur conversion en lettres est obtenue par
     * {@link #entierEnLettres}, qui rend en minuscules. La même capitalisation
     * que celle appliquée aux montants est alors réappliquée par l'appelant.
     */
    public static String capitale(String texte) {
        return majuscule(texte);
    }

    /**
     * Arrondi à l'ariary entier, l'unité monétaire étant indivisible en pratique
     * dans les écritures comptables malgaches.
     */
    public static BigDecimal arrondirAriary(BigDecimal montant) {
        return montant == null ? BigDecimal.ZERO : montant.setScale(0, RoundingMode.HALF_UP);
    }
}
