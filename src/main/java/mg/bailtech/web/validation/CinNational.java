package mg.bailtech.web.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Contrôle du format d'un numéro de carte d'identité nationale malgache.
 *
 * <p>Exigence du cahier des charges : « bloquer la validation si le champ CIN ne
 * fait pas exactement 12 chiffres ». La contrainte est plus stricte qu'une simple
 * expression régulière car elle refuse indistinctement&nbsp;:
 * <ul>
 *   <li>un numéro de longueur différente de 12 (trop court, trop long) ;</li>
 *   <li>tout caractère non numérique (lettres, espaces, tirets, points, séparateurs
 *       de milliers, caractères de contrôle Unicode) ;</li>
 *   <li>une valeur entièrement composée d'espaces.</li>
 * </ul>
 *
 * <p>La valeur {@code null} ou vide est ici <strong>considérée comme valide</strong> :
 * l'obligation de saisir une CIN est portée par {@code @NotBlank}, et le
 * générateur de contrat autorise à choisir un locataire déjà enregistré sans
 * ressaisir son identité. Empiler {@code @NotBlank} sur ce champ rendrait la
 * sélection d'un locataire existant impossible.
 *
 * @see CinNationalValidator
 */
@Documented
@Constraint(validatedBy = CinNationalValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
        ElementType.ANNOTATION_TYPE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CinNational {

    String message() default "{utilisateur.cinNumero.format}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
