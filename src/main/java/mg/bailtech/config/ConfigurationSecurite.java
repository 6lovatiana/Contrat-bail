package mg.bailtech.config;

import java.util.ArrayList;
import java.util.List;

import mg.bailtech.model.Utilisateur;
import mg.bailtech.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Sécurité de l'application : hachage des mots de passe, authentification par
 * formulaire et règles d'accès aux URL.
 *
 * <h2>Pourquoi CIN + mot de passe, et non un email</h2>
 * Le modèle v1 n'a pas de colonne de rôle ni d'identifiant de connexion : la
 * seule donnée d'identification stable et unique dont dispose un usager
 * malgache est son numéro de CIN ({@code utilisateur.cin_numero}, contrainte
 * d'unicité en base). C'est donc ce couple CIN / mot de passe qui ouvre une
 * session, et non un email qui peut être saisi librement à l'inscription.
 *
 * <h2>Le rôle est déduit, pas stocké</h2>
 * Aucun utilisateur ne se voit attribuer un rôle en base. Un compte qui possède
 * au moins un logement reçoit {@code ROLE_BAILLEUR}, sinon {@code ROLE_LOCATAIRE}
 * — la même distinction que celle utilisée par les repositories
 * ({@code findBailleurs()} / {@code findLocataires()}). Un locataire qui tente
 * d'ouvrir le tableau de bord est donc refusé, sans qu'une colonne de rôle
 * ait eu besoin d'être ajoutée au schéma.
 *
 * <h2>Ce que la sécurité ne peut pas faire à la place du métier</h2>
 * Une session établie prouve <em>qui</em> appelle, jamais <em>sur quoi</em>.
 * Le contrat n° 12 ne devient pas pour autant le sien : ce contrôle de
 * propriété est fait en base par les services ({@code ContratService.activer},
 * {@code JiramaService.appliquer}) et par {@link mg.bailtech.web.VisionneuseController}.
 * Les deux couches sont nécessaires : l'une empêche d'être quelqu'un d'autre,
 * l'autre empêche d'accéder aux biens d'autrui.
 */
@Configuration
@EnableWebSecurity
public class ConfigurationSecurite {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationSecurite.class);

    /**
     * Coût de BCrypt. Douze est la valeur recommandée par défaut&nbsp;:
     * environ 250 ms par hachage sur un matériel de bureau de 2020, ce qui rend
     * une attaque par force brute coûteuse sans dégrader la réponse d'une
     * connexion. Chaque incrément de 1 double le temps de calcul.
     */
    private static final int COUT_BCRYPT_PAR_DEFAUT = 12;

    private final UtilisateurRepository utilisateurs;

    public ConfigurationSecurite(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    // ==================================================================
    // Hachage
    // ==================================================================

    @Bean
    public PasswordEncoder encodeurMotDePasse(
            @Value("${bailtech.securite.cout-bcrypt:" + COUT_BCRYPT_PAR_DEFAUT + "}") int cout) {
        int coutRetenu = Math.max(4, Math.min(cout, 31));
        if (coutRetenu != cout) {
            LOG.warn("Coût BCrypt {} hors bornes [4 ; 31] : {} est retenu.", cout, coutRetenu);
        }
        LOG.info("Hachage des mots de passe : BCrypt de coût {}.", coutRetenu);
        return new BCryptPasswordEncoder(coutRetenu);
    }

    // ==================================================================
    // Identités
    // ==================================================================

    /**
     * Résout l'utilisateur par sa CIN, insensible aux espaces accidentels.
     *
     * <p>L'empreinte BCrypt est renvoyée telle quelle : c'est
     * {@code DaoAuthenticationProvider} qui la confronte au mot de passe saisi,
     * via le {@link PasswordEncoder} ci-dessus. Le service
     * {@code AuthentificationService} conserve la même vérification pour ses
     * usages programmatiques — un seul algorithme, un seul coût.
     *
     * <p>Un compte inconnu et un mot de passe faux produisent la même erreur
     * Spring : la page de connexion ne distingue pas « CIN inconnu » de
     * « mot de passe incorrect », ce qui évite d'énumérer les CIN enregistrées.
     */
    @Bean
    public UserDetailsService serviceUtilisateurs() {
        return identifiant -> {
            Utilisateur trouve = utilisateurs.findByCinNumeroNormalise(identifiant.trim())
                    .orElseThrow(() -> new UsernameNotFoundException("Identifiant inconnu"));
            return new User(trouve.getCinNumero(), trouve.getMotDePasse(), authorities(trouve));
        };
    }

    /** Rôle déduit du parc : propriétaire d'au moins un bien, ou simple preneur. */
    private List<SimpleGrantedAuthority> authorities(Utilisateur utilisateur) {
        List<SimpleGrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority(utilisateurs.estProprietaire(utilisateur.getId())
                ? "ROLE_BAILLEUR" : "ROLE_LOCATAIRE"));
        return roles;
    }

    // ==================================================================
    // Règles d'accès
    // ==================================================================

    /**
     * Verrouille les URL.
     *
     * <p>Tout exige une session, à l'exception des ressources statiques et de la
     * page de connexion elle-même. C'est une liste blanche courte et explicite :
     * ajouter une page au produit, c'est ajouter une ligne, et non se demander
     * si loubli d'une règle va l'exposer.
     *
     * <p>La protection CSRF reste active (aucune désactivation). Les formulaires
     * du produit sont des POST : ils portent le jeton rendu par Thymeleaf. C'est
     * précisément ce qui empêche un site tiers de forger une imputation JIRAMA ou
     * une signature de bail au nom du bailleur connecté.
     */
    @Bean
    public SecurityFilterChain filtre(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(autorisations -> autorisations
                        .requestMatchers("/css/**", "/icons/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/connexion", "/erreur").permitAll()
                        .anyRequest().authenticated())
                .formLogin(connexion -> connexion
                        .loginPage("/connexion")
                        .loginProcessingUrl("/connexion")
                        .usernameParameter("cin")
                        .passwordParameter("motDePasse")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/connexion?erreur")
                        .permitAll())
                .logout(deconnexion -> deconnexion
                        .logoutRequestMatcher(new AntPathRequestMatcher("/deconnexion", "POST"))
                        .logoutSuccessUrl("/connexion?deconnexion")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .headers(entetes -> entetes
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults()));
        return http.build();
    }

    // ==================================================================
    // Coffre de documents
    // ==================================================================

    /**
     * Vérifie au démarrage que le coffre de documents a bien sa clé.
     * <p>
     * L'absence de clé n'interrompt pas l'application&nbsp;: le calculateur
     * JIRAMA et l'assistant de contrat n'ont rien à y faire. Un avertissement
     * est en revanche journalisé, parce qu'un coffre ouvert sans clé rendrait
     * toute pièce déposée illisible — et une lecture de pièce échouerait
     * bruyamment, au pire moment, sur le dossier d'un locataire.
     */
    @Bean
    public AlerteCleCoffre alerteCleCoffre(
            @Value("${bailtech.securite.cle-chiffrement:}") String cle) {
        return new AlerteCleCoffre(cle);
    }

    /** Simple trace de l'état du coffre au démarrage, sans effet de bord. */
    public static class AlerteCleCoffre {

        AlerteCleCoffre(String cle) {
            if (cle == null || cle.isBlank()) {
                LOG.warn("bailtech.securite.cle-chiffrement n'est pas definie : le coffre de documents "
                        + "refusera toute ecriture tant qu'elle ne le sera pas. En developpement, generer "
                        + "une cle par StockageSecuriseService.genererCle().");
            } else {
                LOG.info("Cle de chiffrement du coffre de documents configuree.");
            }
        }
    }

    /**
     * Rôle porté par un utilisateur possédant au moins un logement.
     *
     * <p>Les écrans de gestion (tableau de bord, contrats, JIRAMA, coffre-fort)
     * sont réservés à ce rôle : un locataire authentifié ne voit que la page de
     * connexion, faute d'écran qui lui soit propre en v1.
     */
    public static final String AUTHORITE_BAILLEUR = "ROLE_BAILLEUR";
}
