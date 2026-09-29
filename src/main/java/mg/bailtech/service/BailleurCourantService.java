package mg.bailtech.service;

import java.util.Optional;

import mg.bailtech.model.Utilisateur;
import mg.bailtech.repository.UtilisateurRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Résolution du bailleur à partir de la session, et non d'un paramètre d'URL.
 *
 * <h2>Pourquoi ce service existe</h2>
 * Tant que l'authentification n'existait pas, les contrôleurs répondaient à
 * « quel bailleur est connecté&nbsp;? » par « celui dont l'identifiant figure
 * dans l'URL ». Ce modèle est permissif par construction&nbsp;:
 * <code>?bailleurId=2</code> suffit à devenir un autre propriétaire. Le
 * paramètre n'a pas été remplacé par un autre paramètre mais par la session,
 * qui ne se forge pas depuis le navigateur.
 *
 * <h2>Lecture de la session</h2>
 * Le nom d'utilisateur stocké dans la session est la CIN, telle qu'elle a été
 * saisie pour se connecter. L'utilisateur est rechargé en base à chaque appel
 * plutôt que mis en cache dans la session&nbsp;: une session ne doit pas
 * devenir une copie périmée d'une fiche, et le modèle physique n'a aucune
 * donnée de parc figée. Le coût est une requête primaire par écran, ce qui est
 * négligeable devant les listes que ces écrans chargent déjà.
 *
 * <p>Ce service ne dit <em>rien</em> de ce que l'appelant a le droit de voir.
 * Il répond à « qui est-ce&nbsp;? ». Le « sur quoi&nbsp;? » — le contrat, le
 * logement, l'échéance — reste vérifié en base par les services métier.
 */
@Service
public class BailleurCourantService {

    private final UtilisateurRepository utilisateurs;

    public BailleurCourantService(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    /**
     * Bailleur de la session, ou vide si personne n'est authentifié.
     *
     * <p>Ne lève rien : les points d'entrée publics (page de connexion) ont
     * besoin de pouvoir répondre sans exception.
     */
    @Transactional(readOnly = true)
    public Optional<Utilisateur> bailleurConnecte() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .flatMap(utilisateurs::findByCinNumeroNormalise);
    }

    /**
     * Bailleur de la session, ou refus explicite.
     *
     * @throws UtilisateurIntrouvableException si la session est absente ou ne
     *         correspond plus à aucun utilisateur — cas de la session expirée
     *         alors que la ligne a été supprimée entre-temps
     */
    @Transactional(readOnly = true)
    public Utilisateur exigerBailleurConnecte() {
        return bailleurConnecte().orElseThrow(() -> new UtilisateurIntrouvableException(
                "Votre session a expiré : reconnectez-vous pour continuer."));
    }

    /**
     * Session absente ou inconnue.
     *
     * <p>Se distingue de {@link RegleMetierException}, qui signale une saisie
     * refusée à un utilisateur bien identifié : ici, l'appelant n'a pas de
     * identité à qui rattacher un message de formulaire.
     */
    public static class UtilisateurIntrouvableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public UtilisateurIntrouvableException(String message) {
            super(message);
        }
    }

    /**
     * Vrai si l'identifiant fourni correspond à un propriétaire.
     *
     * <p>Sert à distinguer un bailleur d'un locataire lorsqu'une page doit
     * vérifier qu'il a les moyens d'accéder à l'écran demandé.
     */
    @Transactional(readOnly = true)
    public boolean estBailleur(Utilisateur utilisateur) {
        return utilisateur != null && utilisateurs.estProprietaire(utilisateur.getId());
    }
}
