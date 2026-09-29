package mg.bailtech.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Refus d'accès à une ressource qui n'appartient pas à l'appelant.
 *
 * <p>Levée lorsqu'un contrôleur a établi que la ressource demandée existe mais
 * appartient au parc d'un autre bailleur. Elle se distingue de
 * {@link mg.bailtech.service.RegleMetierException}, qui traduit une saisie
 * refusée à un formulaire : ici, rien à rattacher à un champ.
 */
public class AccesRefuseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccesRefuseException(String message) {
        super(message);
    }

    /**
     * Traduit le refus en page d'explication.
     *
     * <p>Le statut 403 est renvoyé avec la page : un client qui automatise la
     * requête voit le refus, et l'utilisateur lit pourquoi. Le message ne
     * nomme jamais le propriétaire réel du contrat.
     */
    @ControllerAdvice
    public static class Gestionnaire {

        private static final Logger LOG = LoggerFactory.getLogger(Gestionnaire.class);

        @ExceptionHandler(AccesRefuseException.class)
        @ResponseStatus(HttpStatus.FORBIDDEN)
        public String intercepter(AccesRefuseException e, Model model) {
            LOG.warn("Accès refusé : {}", e.getMessage());
            model.addAttribute("messageErreur", e.getMessage());
            return "erreur/acces-refuse";
        }
    }
}
