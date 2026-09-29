package mg.bailtech.web;

import mg.bailtech.config.ConfigurationSecurite;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Point d'entrée de l'authentification.
 *
 * <p>Seul le rendu de la page est confié au contrôleur : la vérification du
 * couple CIN / mot de passe et la création de la session sont faites par le
 * filtre de Spring Security, avant qu'une requête n'atteigne ce point. La page
 * elle-même est publique — sans quoi il n'existerait aucun moyen de s'y rendre.
 */
@Controller
public class ConnexionController {

    /**
     * Formulaire de connexion.
     *
     * <p>Un utilisateur déjà authentifié qui rouvre cette page est renvoyé à
     * son espace : le bailleur au tableau de bord, le locataire vers la page
     * qui lui dit ce qui manque. Afficher un formulaire devant quelqu'un qui a
     * déjà une session n'apporte rien et lui fait croire qu'il doit se
     * reconnecter.
     */
    @GetMapping("/connexion")
    public String formulaire(Authentication authentication) {
        if (estAuthentifie(authentication)) {
            return estBailleur(authentication)
                    ? "redirect:/dashboard"
                    : "redirect:/espace-locataire";
        }
        return "connexion";
    }

    /**
     * Espace d'un compte locataire.
     *
     * <p>La page explique ce que le module locataire apportera et propose de
     * se déconnecter. Elle ne porte aucune donnée de bail : le locataire n'a
     * rien à administrer en v1, et afficher les Metrics d'un parc qu'il ne
     * possède pas n'aurait aucun sens.
     */
    @GetMapping("/espace-locataire")
    public String espaceLocataire(Authentication authentication) {
        // Un bailleur qui ouvre cette URL par erreur est renvoyé vers son écran :
        // les Metrics qu'il y trouverait seraient les siennes, ce qui brouillerait
        // la lecture de la page.
        if (estAuthentifie(authentication) && estBailleur(authentication)) {
            return "redirect:/dashboard";
        }
        return "espace-locataire";
    }

    private static boolean estAuthentifie(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
    }

    /** Rôle porté par l'utilisateur connecté, sans toucher à la base. */
    private static boolean estBailleur(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> ConfigurationSecurite.AUTHORITE_BAILLEUR.equals(a.getAuthority()));
    }
}
