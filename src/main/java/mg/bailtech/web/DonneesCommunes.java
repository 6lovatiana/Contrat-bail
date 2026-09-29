package mg.bailtech.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import mg.bailtech.repository.LogementRepository;
import mg.bailtech.service.BailleurCourantService;

/**
 * Données présentes sur tous les écrans, quelle que soit la page.
 *
 * <p>La barre latérale affiche le nombre de biens gérés dans le bloc
 * « Abonnement ». Ce compteur était lu depuis {@code synthese}, attribut que
 * seul le tableau de bord renseigne&nbsp;: sur les autres écrans, le repli
 * affichait « 0/15 biens gérés » à un propriétaire qui en possède quatre. Un
 * chiffre faux dans une zone toujours visible est plus trompeur qu'un chiffre
 * absent.
 *
 * <p>Ce conseil d'analyse fournit donc le compte à toutes les requêtes
 * authentifiées. Le prix est une requête de comptage, déjà nécessaire au
 * tableau de bord et négligeable ailleurs au regard des listes que chaque écran
 * charge par ailleurs.
 */
@ControllerAdvice
public class DonneesCommunes {

    private final BailleurCourantService bailleurCourant;
    private final LogementRepository logements;

    public DonneesCommunes(BailleurCourantService bailleurCourant, LogementRepository logements) {
        this.bailleurCourant = bailleurCourant;
        this.logements = logements;
    }

    /**
     * Nombre de biens du bailleur connecté, ou {@code null} si personne n'est
     * connecté — la page de connexion n'a pas de parc à annoncer.
     */
    @ModelAttribute("nombreLogementsParc")
    public Long nombreLogementsParc() {
        return bailleurCourant.bailleurConnecte()
                .map(bailleur -> logements.countByProprietaireId(bailleur.getId()))
                .orElse(null);
    }
}
