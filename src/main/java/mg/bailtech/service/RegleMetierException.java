package mg.bailtech.service;

/**
 * Règle métier non respectée lors d'un enregistrement.
 * <p>
 * Porte à la fois le champ de formulaire concerné ({@code champ}) et le code
 * d'erreur technique, afin que le contrôleur puisse alimenter directement
 * {@code BindingResult.rejectValue(champ, code, message)}.
 */
public class RegleMetierException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String champ;

    private final String code;

    public RegleMetierException(String champ, String code, String message) {
        super(message);
        this.champ = champ;
        this.code = code;
    }

    /** Nom du champ du formulaire concerné, ou {@code null} pour une erreur globale. */
    public String getChamp() {
        return champ;
    }

    public String getCode() {
        return code;
    }
}
