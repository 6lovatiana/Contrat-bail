package mg.bailtech.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stockage chiffré des pièces administratives du profil (CIN numérisée, pièce
 * d'identité, justificatif de domicile, photo de bailleur).
 *
 * <h2>Ce que garantit ce service</h2>
 * <ul>
 *   <li><strong>Confidentialité au repos</strong> : les pièces sont chiffrées en
 *       AES-256-GCM avant d'atteindre le disque. Rien n'est écrit en clair, pas
 *       même temporairement&nbsp;: le chiffrement est effectué en mémoire sur le
 *       contenu reçu.</li>
 *   <li><strong>Intégrité</strong> : GCM est un mode authentifié. Toute
 *       modification du fichier — un bit retourné lors d'une sauvegarde, une
 *       retouche manuelle, une altération volontaire — fait échouer la lecture
 *       au lieu de restituer une pièce fausse.</li>
 *   <li><strong>IV unique par écriture</strong> : un vecteur d'initialisation de
 *       96 bits est tiré au hasard à chaque enregistrement et préfixé au
 *       fichier. Réutiliser un IV sous une même clé est la faute classique qui
 *      <em>casse</em> GCM&nbsp;; ici deux pièces identiques n'ont donc pas la
 *       même empreinte chiffrée.</li>
 *   <li><strong>Clé hors du code et du dépôt</strong> : la clé provient de la
 *       configuration (variable d'environnement). Aucune clé n'est écrite en
 *       dur&nbsp;; à défaut de configuration, le service refuse dvarthetailler
 *       plutôt que de produire un fichier que l'on ne saura pas rouvrir.</li>
 *   <li><strong>Anti-traversée de chemin</strong> : le nom d'origine est
 *       assaini puis validé, et la clé de stockage est comparée au répertoire
 *       racine après résolution canonique. Un nom {@code ../../webapps/ROOT} ne
 *       peut donc pas écrire hors du coffre.</li>
 *   <li><strong>Dépôt hors de la racine servie</strong> : le coffre n'est jamais
 *       sous {@code classpath:/static} ni sous le répertoire des gabarits, donc
 *       jamais exposé par une requête HTTP.</li>
 * </ul>
 *
 * <h2>Ce qui reste à faire</h2>
 * Le modèle v1 ne comporte aucune table de pièces jointes : les métadonnées
 * (pièce, propriétaire, type de document, date de dépôt) n'ont pas d'endroit où
 * être enregistrées, et aucun contrôleur n'expose encore de téléversement. Ce
 * service fournit donc la brique de stockage sécurisée et vérifiée, prête à
 * être branchée ; le point de terminaison HTTP et la table d'index relèvent du
 * module « documents ».
 */
@Service
public class StockageSecuriseService {

    private static final Logger LOG = LoggerFactory.getLogger(StockageSecuriseService.class);

    /** Longueur d'un IV GCM, en octets : 96 bits, la taille recommandée par la norme. */
    private static final int TAILLE_IV = 12;

    /** Taille de l'étiquette d'authentification GCM, en octets (128 bits). */
    private static final int TAILLE_TAG = 16;

    private static final int TAILLE_CLE_AES = 32;

    /** Plafond de dépôt : 10 Mo, largement supérieur à une CIN numérisée. */
    private static final long TAILLE_MAXIMALE = 10L * 1024 * 1024;

    /**
     * Extensions admises pour une pièce administrative. La liste est volontairement
     * restreinte&nbsp;: un exécutable ou une page HTML stockés dans le coffre
     * pourraient être servis tels quels si le coffre devenait accessible par
     * inadvertance.
     */
    private static final Set<String> EXTENSIONS_AUTORISEES =
            Set.of("jpg", "jpeg", "png", "pdf", "tif", "tiff");

    private final Path racine;

    private final String cleBase64;

    private final SecureRandom aleatoire = new SecureRandom();

    public StockageSecuriseService(
            @Value("${bailtech.securite.coffre:./coffre-securise}") String racine,
            @Value("${bailtech.securite.cle-chiffrement:}") String cleBase64) {
        this.racine = Paths.get(racine).toAbsolutePath().normalize();
        this.cleBase64 = cleBase64 == null ? "" : cleBase64.trim();
    }

    // ==================================================================
    // Écriture
    // ==================================================================

    /**
     * Chiffre et enregistre une pièce.
     *
     * @param nomTiers    nom d'origine fourni par l'utilisateur, utilisé pour en
     *                    tirer l'extension&nbsp;; il n'est jamais réutilisé tel quel
     *                    comme nom de fichier
     * @param contenu     contenu en clair
     * @return la clé de stockage, seule donnée à conserver côté base
     * @throws RegleMetierException si l'extension est refusée, le fichier trop
     *         volumineux, ou la clé de chiffrement absente
     */
    public String enregistrer(String nomTiers, byte[] contenu) {
        verifierConfiguration();
        if (contenu == null || contenu.length == 0) {
            throw new RegleMetierException(null, "coffre.fichier.vide",
                    "Le fichier à enregistrer est vide.");
        }
        if (contenu.length > TAILLE_MAXIMALE) {
            throw new RegleMetierException(null, "coffre.fichier.tropGros",
                    "Le fichier dépasse la taille maximale autorisée (10 Mo).");
        }
        String extension = extensionAutorisee(nomTiers);

        String cle = UUID.randomUUID().toString().replace("-", "")
                + (extension.isEmpty() ? "" : "." + extension);
        Path cible = resoudre(cle);

        byte[] iv = new byte[TAILLE_IV];
        aleatoire.nextBytes(iv);
        byte[] chiffre = chiffrer(contenu, iv);

        // Disposition sur disque : [ IV 12 o | texte chiffré + tag GCM 16 o ]
        // L'IV doit accompagner la pièce : sans lui, la lecture n'aurait aucun
        // moyen de retrouver le même état initial et le déchiffrement échouerait.
        byte[] aEcrire = new byte[TAILLE_IV + chiffre.length];
        System.arraycopy(iv, 0, aEcrire, 0, TAILLE_IV);
        System.arraycopy(chiffre, 0, aEcrire, TAILLE_IV, chiffre.length);

        try {
            Files.createDirectories(racine);
            // CREATE_NEW : refuse d'écraser, et ferme la porte à une collision
            // de clé comme à un lien symbolique préparé par un attaquant.
            Files.write(cible, aEcrire, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RegleMetierException(null, "coffre.ecriture.impossible",
                    "Le fichier n'a pas pu être enregistré : " + e.getMessage());
        }

        LOG.info("Piece {} enregistree ({} octets chiffres, extension {}).",
                cle, chiffre.length, extension.isEmpty() ? "aucune" : extension);
        return cle;
    }

    // ==================================================================
    // Lecture
    // ==================================================================

    /**
     * Déchiffre une pièce.
     *
     * @throws RegleMetierException si la pièce est absente, altérée, ou si la clé
     *         de stockage est invalide
     */
    public byte[] lire(String cle) {
        verifierConfiguration();
        Path source = resoudre(cle);
        byte[] donnees;
        try {
            donnees = Files.readAllBytes(source);
        } catch (java.nio.file.NoSuchFileException e) {
            throw new RegleMetierException(null, "coffre.fichier.absent",
                    "La pièce demandée est introuvable.");
        } catch (IOException e) {
            throw new RegleMetierException(null, "coffre.lecture.impossible",
                    "La pièce n'a pas pu être lue : " + e.getMessage());
        }

        if (donnees.length <= TAILLE_IV + TAILLE_TAG) {
            throw new RegleMetierException(null, "coffre.fichier.incomplet",
                    "La pièce enregistrée est tronquée.");
        }

        byte[] iv = new byte[TAILLE_IV];
        System.arraycopy(donnees, 0, iv, 0, TAILLE_IV);

        try {
            return dechiffrer(donnees, TAILLE_IV, donnees.length - TAILLE_IV, iv);
        } catch (GeneralSecurityException e) {
            // AEADBadTagException : le fichier a été modifié, ou la clé a changé.
            LOG.warn("Echec d'authentification GCM sur la piece {} : contenu alteré ou cle erronee.",
                    reducte(cle));
            throw new RegleMetierException(null, "coffre.piece.alteree",
                    "Cette pièce a été altérée ou sa clé de chiffrement est erronée. "
                            + "Elle ne peut pas être présentée comme une pièce justificative.");
        }
    }

    // ==================================================================
    // Suppression
    // ==================================================================

    /** Supprime définitivement une pièce. */
    public void supprimer(String cle) {
        Path cible = resoudre(cle);
        try {
            Files.deleteIfExists(cible);
            LOG.info("Piece {} supprimee du coffre.", reducte(cle));
        } catch (IOException e) {
            throw new RegleMetierException(null, "coffre.suppression.impossible",
                    "La pièce n'a pas pu être supprimée : " + e.getMessage());
        }
    }

    // ==================================================================
    // Internes
    // ==================================================================

    private byte[] chiffrer(byte[] contenu, byte[] iv) {
        try {
            Cipher chiffre = Cipher.getInstance("AES/GCM/NoPadding");
            chiffre.init(Cipher.ENCRYPT_MODE, secret(), new GCMParameterSpec(TAILLE_TAG * 8, iv));
            return chiffre.doFinal(contenu);
        } catch (GeneralSecurityException e) {
            throw new RegleMetierException(null, "coffre.chiffrement.impossible",
                    "Le chiffrement a échoué : " + e.getMessage());
        }
    }

    private byte[] dechiffrer(byte[] donnees, int debut, int longueur, byte[] iv)
            throws GeneralSecurityException {
        Cipher chiffre = Cipher.getInstance("AES/GCM/NoPadding");
        chiffre.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(TAILLE_TAG * 8, iv));
        return chiffre.doFinal(donnees, debut, longueur);
    }

    /** Clé AES-256 reconstruite depuis la configuration. */
    private SecretKey secret() {
        byte[] octets;
        try {
            octets = Base64.getDecoder().decode(cleBase64);
        } catch (IllegalArgumentException e) {
            throw new RegleMetierException(null, "coffre.cle.invalide",
                    "La clé de chiffrement n'est pas un Base64 valide.");
        }
        if (octets.length != TAILLE_CLE_AES) {
            throw new RegleMetierException(null, "coffre.cle.invalide",
                    "La clé de chiffrement doit faire 256 bits (32 octets encodés en Base64).");
        }
        return new SecretKeySpec(octets, "AES");
    }

    /**
     * Vérifie que le service est utilisable.
     * <p>
     * Le contrôle est répété à chaque opération plutôt que fait une fois au
     * démarrage : il ne coûte qu'une comparaison de chaînes, et il garantit
     * qu'aucune écriture ne se produit avec une configuration par défaut.
     */
    private void verifierConfiguration() {
        if (cleBase64.isEmpty()) {
            throw new RegleMetierException(null, "coffre.cle.absente",
                    "Aucune clé de chiffrement n'est configurée : définir "
                            + "bailtech.securite.cle-chiffrement avant d'utiliser le coffre.");
        }
    }

    /**
     * Traduit une clé de stockage en chemin, en refusant toute sortie du coffre.
     * <p>
     * Le nom est d'abord normalisé, puis la racine et le nom sont résolus, puis
     * le résultat est comparé à la racine. C'est cette comparaison finale qui
     * tranche&nbsp;: un {@code ../} sufficiently long pour remonter hors de la
     * racine est détecté ici, même si les filtres de caractères l'ont laissé
     * passer.
     */
    private Path resoudre(String cle) {
        if (cle == null || cle.isBlank()) {
            throw new RegleMetierException(null, "coffre.cle.invalide",
                    "Aucune pièce n'a été désignée.");
        }
        // Défense en profondeur : on n'accepte qu'un nom de fichier simple.
        if (cle.contains("/") || cle.contains("\\") || cle.contains("..")
                || cle.contains("\0") || cle.contains(":")) {
            throw new RegleMetierException(null, "coffre.cle.invalide",
                    "L'identifiant de pièce est invalide.");
        }
        Path cible = racine.resolve(cle).normalize();
        if (!cible.startsWith(racine) || cible.equals(racine)) {
            throw new RegleMetierException(null, "coffre.chemin.interdit",
                    "L'identifiant de pièce désigne un emplacement interdit.");
        }
        return cible;
    }

    /**
     * Extrait et contrôle l'extension d'un nom de fichier fourni par un client.
     * <p>
     * Le nom d'origine n'est jamais utilisé comme nom de stockage&nbsp;: seule
     * l'extension, confrontée à une liste blanche, en est tirée.
     */
    private static String extensionAutorisee(String nomTiers) {
        if (nomTiers == null || nomTiers.isBlank()) {
            return "";
        }
        int point = nomTiers.lastIndexOf('.');
        if (point < 0 || point == nomTiers.length() - 1) {
            return "";
        }
        String extension = nomTiers.substring(point + 1).toLowerCase(Locale.ROOT);
        // Une extension ne peut contenir que des lettres, sinon on la rejette
        // entièrement plutôt que de la normaliser.
        if (!extension.matches("[a-z]{2,5}")) {
            throw new RegleMetierException(null, "coffre.extension.invalide",
                    "Le type de fichier est refusé.");
        }
        if (!EXTENSIONS_AUTORISEES.contains(extension)) {
            throw new RegleMetierException(null, "coffre.extension.refusee",
                    "Seuls les fichiers image ou PDF sont acceptés ("
                            + String.join(", ", EXTENSIONS_AUTORISEES.stream().sorted().toList()) + ").");
        }
        return extension;
    }

    /** Trace d'audit : les 8 premiers caractères de la clé suffisent à l'identifier. */
    private static String reducte(String cle) {
        return cle == null ? "?" : cle.substring(0, Math.min(8, cle.length()));
    }

    /**
     * Génère une clé AES-256 adaptée au format attendu par la configuration.
     * <p>
     * Utilitaire de mise en service : à appeler une seule fois, le résultat
     * devant être placé dans une variable d'environnement et jamais dans un
     * fichier suivi par Git.
     */
    public static String genererCle() {
        byte[] octets = new byte[TAILLE_CLE_AES];
        new SecureRandom().nextBytes(octets);
        return Base64.getEncoder().encodeToString(octets);
    }

    /** Racine du coffre, exposée pour le contrôle d'accès du module documents. */
    public Path getRacine() {
        return racine;
    }

    /**
     * Charge une clé depuis un flux (fichier de secret hors du dépôt).
     * <p>
     * Utilitaire de mise en service : permet de lire la clé d'un fichier
     * {@code .env} ou d'un secret monté en volume, plutôt que de la saisir
     * dans une variable d'environnement.
     */
    public static String lireCleDepuisFlux(InputStream flux) throws IOException {
        byte[] brut = flux.readAllBytes();
        return new String(brut, StandardCharsets.UTF_8).trim();
    }
}
