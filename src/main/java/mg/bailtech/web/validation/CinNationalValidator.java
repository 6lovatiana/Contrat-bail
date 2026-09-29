package mg.bailtech.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import mg.bailtech.model.Utilisateur;

/**
 * Validateur de la contrainte {@link CinNational}.
 *
 * <p>La vérification se fait caractère par caractère plutôt que par
 * {@link String#matches} : la classe regex {@code \d} de Java inclut les chiffres
 * Unicode (chiffres arabo-indiens, pleine largeur…) qu'un CIN malgache
 * n'utilise jamais, et certains points de code de la catégorie «Nombre» sont
 * invisibles à l'écran. Seuls les chiffres ASCII {@code 0-9} sont acceptés.
 */
public class CinNationalValidator implements ConstraintValidator<CinNational, CharSequence> {

    /** Longueur réglementaire du numéro de CIN malgache. */
    public static final int LONGUEUR_ATTENDUE = Utilisateur.CIN_LONGUEUR;

    @Override
    public boolean isValid(CharSequence valeur, ConstraintValidatorContext contexte) {
        // Champ facultatif : l'obligation de saisie est portée par @NotBlank.
        return valeur == null || valeur.isEmpty() || estFormatValide(valeur.toString());
    }

    /**
     * Indique si une valeur respecte exactement le format attendu : 12 caractères,
     * tous des chiffres ASCII, sans espace ni séparateur.
     * <p>
     * Utilisé par la couche service pour bloquer un enregistrement qui
     * contournerait les annotations (import de données, script de reprise).
     */
    public static boolean estFormatValide(String valeur) {
        if (valeur == null || valeur.length() != LONGUEUR_ATTENDUE) {
            return false;
        }
        for (int i = 0; i < valeur.length(); i++) {
            char c = valeur.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
