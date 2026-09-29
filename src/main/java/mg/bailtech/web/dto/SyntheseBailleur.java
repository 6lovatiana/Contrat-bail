package mg.bailtech.web.dto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Synthèse chiffrée du tableau de bord (cartes « Total des loyers », « Contrats
 * actifs », « Paiements en attente »).
 * <p>
 * Les agrégats sont calculés par les repositories ; la mise en forme est faite ici
 * pour que le gabarit n'ait qu'à afficher des chaînes.
 */
public class SyntheseBailleur {

    private final String bailleurNom;

    private final String bailleurPrenom;

    private final String totalLoyers;

    private final long nombreLogements;

    private final long contratsActifs;

    private final long paiementsEnRetard;

    private final BigDecimal montantEnRetard;

    private final Map<String, Long> statuts = new LinkedHashMap<>();

    public SyntheseBailleur(String bailleurNom,
                            String bailleurPrenom,
                            BigDecimal totalLoyers,
                            long nombreLogements,
                            long contratsActifs,
                            long paiementsEnRetard,
                            BigDecimal montantEnRetard,
                            Map<String, Long> statuts) {
        this.bailleurNom = bailleurNom;
        this.bailleurPrenom = bailleurPrenom;
        this.totalLoyers = Format.montantEntier(totalLoyers);
        this.nombreLogements = nombreLogements;
        this.contratsActifs = contratsActifs;
        this.paiementsEnRetard = paiementsEnRetard;
        this.montantEnRetard = montantEnRetard == null ? BigDecimal.ZERO : montantEnRetard;
        this.statuts.putAll(statuts);
    }

    public String getBailleurNom() {
        return bailleurNom;
    }

    public String getBailleurPrenom() {
        return bailleurPrenom;
    }

    public String getSalutation() {
        return bailleurPrenom == null || bailleurPrenom.isBlank() ? bailleurNom : bailleurPrenom;
    }

    public String getTotalLoyers() {
        return totalLoyers;
    }

    public long getNombreLogements() {
        return nombreLogements;
    }

    public long getContratsActifs() {
        return contratsActifs;
    }

    public long getPaiementsEnRetard() {
        return paiementsEnRetard;
    }

    public String getMontantEnRetard() {
        return Format.montantEntier(montantEnRetard);
    }

    public Map<String, Long> getStatuts() {
        return statuts;
    }
}
