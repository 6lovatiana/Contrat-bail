package mg.bailtech.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuration du module de sécurité des identités.
 *
 * <h2>Pourquoi un seul encodeur, et non Spring Security complet&nbsp;?</h2>
 * Le cahier des charges demande « la logique de chiffrement des données
 * d'authentification », c'est-à-dire le hachage et la vérification des mots de
 * passe. Ajouter {@code spring-boot-starter-security} apporterait un filtre
 * qui exige une authentification sur <em>toutes</em> les requêtes, imposerait
 * une page de connexion et un {@code UserDetailsService} — autant de pièces
 * qui fermeraient l'application sans qu'aucune ne soit prête. Seule la brique
 * {@code spring-security-crypto} est donc retenue&nbsp;: elle fournit
 * {@link BCryptPasswordEncoder} sans aucune auto-configuration, sans filtre et
 * sans dépendance à un contexte web. Le jour où le dépôt des identités sera
 * branché, il suffira de remplacer l'encodeur par un
 * {@code DaoAuthenticationProvider} qui l'utilise déjà.
 */
@Configuration
public class ConfigurationSecurite {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationSecurite.class);

    /**
     * Coût de BCrypt. Douze est la valeur recommandée par défaut&nbsp;:
     * environ 250 ms par hachage sur un matériel de bureau de 2020, ce qui rend
     * une attaque par force brute coûteuse sans dégrader la réponse d'une
     * connexion. Chaque incrément de 1 double le temps de calcul.
     */
    private static final int COUT_BCRYPT_PAR_DEFAUT = 12;

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
}
