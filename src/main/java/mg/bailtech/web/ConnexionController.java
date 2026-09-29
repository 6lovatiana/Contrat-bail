package mg.bailtech.web;

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
     * <p>Un utilisateur déjà authentifié qui rouvre cette page est renvoyé au
     * tableau de bord : afficher un formulaire devant quelqu'un qui a déjà une
     * session n'apporte rien et lui fait croire qu'il doit se reconnecter.
     */
    @GetMapping("/connexion")
    public String formulaire(org.springframework.security.core.Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            return "redirect:/dashboard";
        }
        return "connexion";
    }
}
