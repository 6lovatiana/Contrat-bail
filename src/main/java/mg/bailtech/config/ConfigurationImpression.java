package mg.bailtech.config;

import java.nio.charset.Charset;

import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Moteur Thymeleaf dédié à l'impression PDF.
 * <p>
 * <strong>Pourquoi un second moteur ?</strong> Le moteur du web fonctionne en mode
 * HTML5 et produit du HTML, alors que le moteur de rendu PDF (Flying Saucer)
 * analyse le document avec un parseur XML : le gabarit doit être du XHTML bien
 * formé (balises auto-fermantes, entités numériques). Les deux moteurs utilisent
 * le même dossier de gabarits ({@code spring.thymeleaf.prefix}) mais ne doivent
 * jamais se confondre.
 * <p>
 * <strong>Pourquoi le resolveur n'est-il pas un bean ?</strong> Spring Boot
 * ajoute au moteur du web tous les beans {@code ITemplateResolver} qu'il trouve.
 * Déclarer le resolveur XHTML comme bean risquerait donc de le faire concourir
 * avec le resolveur HTML sur les fichiers {@code .html} du site. Il est ici
 * construit localement au sein du bean moteur, hors du contexte de beans.
 */
@Configuration
public class ConfigurationImpression {

    @Bean
    public SpringTemplateEngine moteurImpression(ApplicationContext applicationContext,
                                                 ThymeleafProperties properties) {
        SpringResourceTemplateResolver resolveur = new SpringResourceTemplateResolver();
        resolveur.setApplicationContext(applicationContext);
        resolveur.setPrefix(properties.getPrefix());
        resolveur.setSuffix(properties.getSuffix());
        // Thymeleaf 3.1 a fusionné le mode « XHTML » dans le mode « XML » : c'est
        // celui-ci qui impose une sortie XML bien formée, analyseable par le
        // parseur SAX de Flying Saucer.
        resolveur.setTemplateMode(TemplateMode.XML);
        // ThymeleafProperties.getEncoding() renvoie une Charset, le resolveur
        // attend un nom d'encodage.
        Charset encodage = properties.getEncoding();
        resolveur.setCharacterEncoding(encodage == null ? "UTF-8" : encodage.name());
        resolveur.setCacheable(properties.isCache());

        SpringTemplateEngine moteur = new SpringTemplateEngine();
        moteur.setTemplateResolver(resolveur);
        return moteur;
    }
}
