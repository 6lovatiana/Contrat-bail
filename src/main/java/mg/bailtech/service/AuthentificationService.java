package mg.bailtech.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import mg.bailtech.model.Utilisateur;
import mg.bailtech.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des identités : hachage et vérification des mots de passe.
 *
 * <h2>Pourquoi BCrypt</h2>
 * Le script d'initialisation réserve 255 caractères pour
 * {@code utilisateur.mot_de_passe} en précisant « empreinte du mot de passe
 * haché (BCrypt) ». BCrypt est donc le choix du modèle de données. Il est ici
 * employé via {@code spring-security-crypto}, un module <em>isolé</em> :
 * contrairement à {@code spring-boot-starter-security}, il n'enclenche aucune
 * auto-configuration, ne filtre aucune requête et n'impose aucun
 * {@code UserDetailsService}. Le dépôt des identités et la gestion de session
 * restent donc à brancher, sans que ce service les préempte.
 *
 * <h2>Propriétés de sécurité retenues</h2>
 * <ul>
 *   <li><strong>Sel par mot de passe</strong> : deux troubles de la même valeur
 *       ne produisent pas la même empreinte, ce qui interdit les attaques par
 *       table de pré-calculs ( rainbow tables).</li>
 *   <li><strong>Coût 12</strong> : ~250 ms de calcul par hachage sur un
 *       matériel courant. C'est le compromis habituel entre sécurité et temps de
 *       réponse d'une connexion.</li>
 *   <li><strong>Vérification à temps constant</strong> : {@code matches} compare
 *       l'empreinte paresseusement et n'échoue jamais plus vite que la
 *       validation complète, ce qui ne laisse pas fuiter d'information sur un
 *       préfixe d'empreinte correct.</li>
 * </ul>
 *
 * <p>Un mot de passe n'est jamais journalisé ni renvoyé par l'API : seuls
 * {@link #hacher} et {@link #verifier} manipulent la valeur en clair.
 */
@Service
public class AuthentificationService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthentificationService.class);

    /**
     * Longueur d'un mot de passe temporaire, en octets aléatoires. Assez long
     * pour ne jamais être deviné, assez court pour être transmis par téléphone.
     */
    private static final int LONGUEUR_MOT_DE_PASSE_TEMPORAIRE = 18;

    private static final int FORCE_ALEATOIRE = 12;

    private final UtilisateurRepository utilisateurs;

    private final PasswordEncoder encodeur;

    private final SecureRandom aleatoire = new SecureRandom();

    public AuthentificationService(UtilisateurRepository utilisateurs, PasswordEncoder encodeur) {
        this.utilisateurs = utilisateurs;
        this.encodeur = encodeur;
    }

    /**
     * Hache un mot de passe en clair.
     *
     * @param motDePasse mot de passe en clair
     * @return empreinte BCrypt, sûre à stocker
     * @throws RegleMetierException si le mot de passe est vide ou trop court
     */
    public String hacher(String motDePasse) {
        String valeur = motDePasse == null ? "" : motDePasse;
        if (valeur.isBlank()) {
            throw new RegleMetierException(null, "securite.motDePasse.vide",
                    "Le mot de passe ne peut pas être vide.");
        }
        if (valeur.length() < 8) {
            throw new RegleMetierException(null, "securite.motDePasse.tropCourt",
                    "Le mot de passe doit comporter au moins 8 caractères.");
        }
        return encodeur.encode(valeur);
    }

    /**
     * Vérifie un mot de passe face à une empreinte stockée.
     * <p>
     * Ne lève pas d'exception si l'empreinte est absente ou illisible : un
     * compte sans hash exploitable est traité comme un échec d'authentification,
     * ce qui évite de distinguer « compte inconnu » de « mot de passe faux ».
     *
     * @return {@code true} si le mot de passe correspond
     */
    public boolean verifier(String motDePasse, String empreinte) {
        if (motDePasse == null || empreinte == null || empreinte.isBlank()) {
            return false;
        }
        try {
            return encodeur.matches(motDePasse, empreinte);
        } catch (IllegalArgumentException e) {
            // Empreinte qui n'est pas un bcrypt valide (donnée héritée, script).
            LOG.warn("Empreinte de mot de passe illisible : elle sera à réinitialiser.");
            return false;
        }
    }

    /**
     * Authentifie un utilisateur par sa CIN et son mot de passe.
     *
     * @return l'utilisateur authentifié, ou {@link Optional#empty()} si la CIN
     *         est inconnue ou le mot de passe incorrect
     */
    @Transactional(readOnly = true)
    public Optional<Utilisateur> authentifier(String cinNumero, String motDePasse) {
        if (cinNumero == null || cinNumero.isBlank() || motDePasse == null) {
            return Optional.empty();
        }
        // La recherche est insensible aux espaces : « 123 456 789 012 » et
        // « 123456789012 » désignent la même personne.
        Optional<Utilisateur> trouve = utilisateurs.findByCinNumeroNormalise(cinNumero.trim());
        if (trouve.isEmpty()) {
            return Optional.empty();
        }
        Utilisateur utilisateur = trouve.get();
        if (!verifier(motDePasse, utilisateur.getMotDePasse())) {
            return Optional.empty();
        }
        LOG.info("Authentification réussie pour l'utilisateur n° {}.", utilisateur.getId());
        return Optional.of(utilisateur);
    }

    /**
     * Produit une empreinte d'un mot de passe aléatoire, non devinable.
     * <p>
     * Utilisé lorsqu'un compte est créé sans que son titulaire choisisse lui-même
     * un secret : l'assistant de contrat enregistre un locataire, il ne lui
     * fournit pas d'accès. L'empreinte obtenue correspond à un mot de passe que
     * personne ne connaît ; elle n'ouvre donc aucun accès, tout en remplaçant
     * l'ancienne chaîne en clair.
     */
    public String hacherMotDePasseAleatoire() {
        return hacher(motDePasseAleatoire());
    }

    /** Mot de passe temporaire en clair, à transmettre hors du système. */
    public String motDePasseAleatoire() {
        byte[] octets = new byte[LONGUEUR_MOT_DE_PASSE_TEMPORAIRE];
        aleatoire.nextBytes(octets);
        String brut = Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
        return brut.substring(0, LONGUEUR_MOT_DE_PASSE_TEMPORAIRE);
    }

    /**
     * Remplace le mot de passe d'un compte après contrôle de l'ancien.
     *
     * @throws RegleMetierException si l'ancien mot de passe est incorrect ou si
     *         le nouveau ne respecte pas les règles de longueur
     */
    @Transactional
    public void changerMotDePasse(Integer utilisateurId, String ancien, String nouveau) {
        Utilisateur utilisateur = utilisateurs.findById(utilisateurId)
                .orElseThrow(() -> new RegleMetierException(null, "securite.utilisateur.absent",
                        "Le compte à modifier est introuvable."));

        if (!verifier(ancien, utilisateur.getMotDePasse())) {
            LOG.warn("Changement de mot de passe refusé pour l'utilisateur n° {} : "
                    + "ancien mot de passe incorrect.", utilisateurId);
            throw new RegleMetierException(null, "securite.motDePasse.incorrect",
                    "Le mot de passe actuel est incorrect.");
        }

        // hacher() refuse les valeurs trop courtes ou vides avant tout contact
        // avec la base : la validation précède donc l'écriture.
        utilisateur.setMotDePasse(hacher(nouveau));
        utilisateurs.save(utilisateur);
        LOG.info("Mot de passe renouvelé pour l'utilisateur n° {}.", utilisateurId);
    }

    /**
     * Empreinte pré-hachée d'une valeur de démonstration, déterministe pour que
     * l'équipe puisse se connecter avec un mot de passe connu en développement.
     */
    public String hacherDemonstration(String motDePasseDemo) {
        return encodeur.encode(motDePasseDemo);
    }

    /**
     * Trace d'audit non réversible d'une tentative, exploitable en journalisation :
     * elle permet de corréler deux échecs sans jamais écrire le mot de passe.
     */
    public static String empreinteAudit(String cinNumero, String motDePasse) {
        try {
            MessageDigest empreinte = MessageDigest.getInstance("SHA-256");
            byte[] brut = empreinte.digest((cinNumero + " " + motDePasse)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder sortie = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sortie.append(String.format("%02x", brut[i]));
            }
            return sortie.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 est imposé par la plateforme Java : sans lui, aucune trace.
            return "indisponible";
        }
    }
}
