package mg.bailtech.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import mg.bailtech.model.TypeCompteurJirama;

/**
 * Moteur de répartition des charges JIRAMA entre les locataires d'un immeuble.
 *
 * <h2>Principe</h2>
 * Chaque locataire occupant dispose d'un sous-compteur : sa consommation est la
 * différence entre l'index relevé et l'index de départ enregistré à l'état des
 * lieux ({@code logement.compteur_*_index_depart}). La consommation du compteur
 * principal, elle, est la facture de l'immeuble. L'écart entre les deux — commun
 * d'éclairage, pertes de ligne, taxes — n'est pas imputable à un locataire en
 * particulier et doit donc être affecté selon une règle choisie.
 *
 * <h2>Formule</h2>
 * <pre>
 * base_i        = conso_i x prix_unitaire                     (par énergie)
 * ecart         = conso_principale x prix_unitaire - somme(base_i)
 * partEcart_i   = ecart x poids_i / somme(poids)   (PRORATA)
 *               = ecart / nombre_occupants        (EGALE)
 *               = 0                                (BAILLEUR)
 * part_i        = base_i + partEcart_i
 * part_i        = part_i x facteur   si un montant de facture est declare
 * </pre>
 * où {@code poids_i} est la consommation de l'occupant dans l'énergie concernée,
 * et le facteur ramène le total sur la facture effectivement déclarée par JIRAMA.
 *
 * <h2>Arrondi</h2>
 * L'ariary est l'unité comptable. Les parts sont arrondies à l'entier, puis le
 * reliquat est redistribué à l'unité sur les parts les plus lourdes (méthode du
 * plus grand reste), de sorte que la somme des parts soit exactement égale à la
 * facture. Aucun centime ne disparaît, aucun n'est créé.
 *
 * <p>Classe pure, sans dépendance à Spring ni à la base : elle est couverte par
 * des tests exhaustifs (arrondis, écarts négatifs, cas sans consommation).
 */
public final class MoteurRepartitionJirama {

    /** Traitement de l'écart entre compteur principal et somme des sous-compteurs. */
    public enum ModeEcart {
        /** L'écart reste à la charge du bailleur (charges fixes, pertes). */
        BAILLEUR("À la charge du bailleur"),
        /** L'écart est réparti au prorata de la consommation de chaque occupant. */
        PRORATA("Au prorata de la consommation"),
        /** L'écart est réparti à parts égales entre les occupants. */
        EGALE("À parts égales");

        private final String libelle;

        ModeEcart(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }
    }

    /**
     * Consommation mesurée d'un occupant.
     *
     * @param cle          identifiant du occupant (numéro de contrat)
     * @param electricite  kWh consommés depuis l'état des lieux
     * @param eau          m³ consommés depuis l'état des lieux
     */
    public record ConsommationOccupant(String cle, BigDecimal electricite, BigDecimal eau) {
    }

    /** Part revenant à un occupant. */
    public record LigneRepartition(
            String cle,
            BigDecimal consoElectricite,
            BigDecimal consoEau,
            BigDecimal partElectricite,
            BigDecimal partEau,
            BigDecimal total) {
    }

    /**
     * Résultat complet du calcul.
     *
     * @param montantFacture      montant à répartir (facture déclarée ou calculée)
     * @param montantMesureTotal  somme des consommations mesurées des occupants
     * @param ecart               écart entre le compteur principal et les sous-compteurs
     * @param montantReparti      somme des parts après arrondi
     * @param coherent            {@code false} si l'écart est négatif, signe d'une
     *                            incohérence de relevés
     */
    public record Resultat(
            BigDecimal consoElectricitePrincipale,
            BigDecimal consoEauPrincipale,
            BigDecimal montantFacture,
            BigDecimal montantMesureTotal,
            BigDecimal ecart,
            BigDecimal montantReparti,
            BigDecimal ecartElectricite,
            BigDecimal ecartEau,
            boolean coherent,
            List<LigneRepartition> lignes) {
    }

    private MoteurRepartitionJirama() {
        // classe utilitaire
    }

    /**
     * Répartit la facture JIRAMA entre les occupants.
     *
     * @param occupants             consommations mesurées, une par locataire
     * @param consoElecPrincipale   kWh du compteur principal depuis l'état des lieux
     * @param consoEauPrincipale    m³ du compteur principal depuis l'état des lieux
     * @param prixElec              MGA par kWh
     * @param prixEau               MGA par m³
     * @param montantFactureDeclare montant de la facture JIRAMA tel que relevé ; si nul
     *                              ou non positif, le montant calculé est retenu
     * @param mode                  traitement de l'écart
     * @param typeGestion           mode de gestion du compteur du bien
     */
    public static Resultat repartir(List<ConsommationOccupant> occupants,
                                    BigDecimal consoElecPrincipale,
                                    BigDecimal consoEauPrincipale,
                                    BigDecimal prixElec,
                                    BigDecimal prixEau,
                                    BigDecimal montantFactureDeclare,
                                    ModeEcart mode,
                                    TypeCompteurJirama typeGestion) {
        BigDecimal consoElecPrincipal = nonNegatif(consoElecPrincipale);
        BigDecimal consoEauPrincipal = nonNegatif(consoEauPrincipale);
        BigDecimal tarifElec = nonNegatif(prixElec);
        BigDecimal tarifEau = nonNegatif(prixEau);
        List<ConsommationOccupant> liste = occupants == null ? List.of() : occupants;

        // 1. Facture du compteur principal
        BigDecimal montantPrincipalElec = consoElecPrincipal.multiply(tarifElec);
        BigDecimal montantPrincipalEau = consoEauPrincipal.multiply(tarifEau);
        BigDecimal montantCalcule = montantPrincipalElec.add(montantPrincipalEau);

        // 2. Consommations mesurées des sous-compteurs
        BigDecimal totalConsoElec = ZERO;
        BigDecimal totalConsoEau = ZERO;
        for (ConsommationOccupant occupant : liste) {
            totalConsoElec = totalConsoElec.add(nonNegatif(occupant.electricite()));
            totalConsoEau = totalConsoEau.add(nonNegatif(occupant.eau()));
        }
        BigDecimal mesureElec = totalConsoElec.multiply(tarifElec);
        BigDecimal mesureEau = totalConsoEau.multiply(tarifEau);
        BigDecimal montantMesureTotal = mesureElec.add(mesureEau);

        // 3. Écart : commun d'éclairage, pertes, taxes
        BigDecimal ecartElec = montantPrincipalElec.subtract(mesureElec);
        BigDecimal ecartEau = montantPrincipalEau.subtract(mesureEau);
        BigDecimal ecart = ecartElec.add(ecartEau);

        // Un compteur unique ne dessert qu'un occupant : l'écart n'a alors pas
        // d'objet, il est intégralement imputé à l'occupant en place.
        ModeEcart effectif = TypeCompteurJirama.UNIQUE == typeGestion ? ModeEcart.PRORATA : mode;
        if (ModeEcart.BAILLEUR == effectif) {
            // Seul le mesuré est réparti.
            ecartElec = ZERO;
            ecartEau = ZERO;
        }

        // 4. Parts
        List<LigneRepartition> lignes = new ArrayList<>(liste.size());
        BigDecimal poidsElecTotal = ZERO;
        BigDecimal poidsEauTotal = ZERO;
        for (ConsommationOccupant occupant : liste) {
            poidsElecTotal = poidsElecTotal.add(nonNegatif(occupant.electricite()));
            poidsEauTotal = poidsEauTotal.add(nonNegatif(occupant.eau()));
        }
        for (ConsommationOccupant occupant : liste) {
            BigDecimal consoElec = nonNegatif(occupant.electricite());
            BigDecimal consoEau = nonNegatif(occupant.eau());
            BigDecimal partElec = consoElec.multiply(tarifElec);
            BigDecimal partEau = consoEau.multiply(tarifEau);

            if (ModeEcart.PRORATA == effectif) {
                partElec = partElec.add(repartir(ecartElec, consoElec, poidsElecTotal, liste.size()));
                partEau = partEau.add(repartir(ecartEau, consoEau, poidsEauTotal, liste.size()));
            } else if (ModeEcart.EGALE == effectif) {
                partElec = partElec.add(diviser(ecartElec, liste.size()));
                partEau = partEau.add(diviser(ecartEau, liste.size()));
            }
            lignes.add(new LigneRepartition(occupant.cle(), consoElec, consoEau,
                    nonNegatif(partElec), nonNegatif(partEau), ZERO));
        }

        // 5. Facture réellement à répartir
        BigDecimal montantFacture = montantFactureDeclare != null
                && montantFactureDeclare.signum() > 0 ? montantFactureDeclare : montantCalcule;
        if (ModeEcart.BAILLEUR == effectif) {
            montantFacture = montantMesureTotal;
        }

        // 6. Ramenement sur la facture déclarée, puis arrondi sans perte
        BigDecimal totalAvantFacture = lignes.stream()
                .map(l -> l.partElectricite().add(l.partEau()))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal facteur = totalAvantFacture.signum() > 0
                ? montantFacture.divide(totalAvantFacture, 12, RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        List<LigneRepartition> arrondies = arrondir(lignes, facteur, montantFacture);

        BigDecimal montantReparti = arrondies.stream()
                .map(LigneRepartition::total)
                .reduce(ZERO, BigDecimal::add);

        return new Resultat(consoElecPrincipal, consoEauPrincipal, montantFacture, montantMesureTotal,
                ecart, montantReparti, ecartElec, ecartEau, ecart.signum() >= 0, arrondies);
    }

    /** Part d'un écart revenant à un occupant, au prorata de son poids. */
    private static BigDecimal repartir(BigDecimal ecart, BigDecimal poids,
                                       BigDecimal poidsTotal, int nombreOccupants) {
        if (ecart.signum() == 0 || nombreOccupants == 0) {
            return ZERO;
        }
        if (poidsTotal.signum() == 0) {
            // Personne n'a consommé : répartition à parts égales.
            return diviser(ecart, nombreOccupants);
        }
        return ecart.multiply(poids).divide(poidsTotal, 12, RoundingMode.HALF_UP);
    }

    private static BigDecimal diviser(BigDecimal montant, int nombre) {
        if (nombre <= 0) {
            return ZERO;
        }
        return montant.divide(BigDecimal.valueOf(nombre), 12, RoundingMode.HALF_UP);
    }

    /**
     * Applique le facteur de ramènement, arrondit à l'ariary entier et corrige le
     * reliquat par la méthode du plus grand reste : la somme finale est exactement
     * égale à la cible.
     */
    private static List<LigneRepartition> arrondir(List<LigneRepartition> lignes, BigDecimal facteur,
                                                   BigDecimal cible) {
        List<LigneRepartition> resultat = new ArrayList<>(lignes.size());
        List<int[]> rangs = new ArrayList<>();
        BigDecimal sommeArrondie = ZERO;

        for (int i = 0; i < lignes.size(); i++) {
            LigneRepartition ligne = lignes.get(i);
            BigDecimal partElec = ligne.partElectricite().multiply(facteur);
            BigDecimal partEau = ligne.partEau().multiply(facteur);
            BigDecimal totalExact = partElec.add(partEau);
            BigDecimal totalArrondi = totalExact.setScale(0, RoundingMode.HALF_UP);
            sommeArrondie = sommeArrondie.add(totalArrondi);
            // Reste décimal, conservé pour le choix des compensations.
            rangs.add(new int[] {i, totalExact.subtract(totalArrondi)
                    .multiply(BigDecimal.valueOf(1000)).intValue()});
            resultat.add(new LigneRepartition(ligne.cle(), ligne.consoElectricite(), ligne.consoEau(),
                    partElec.setScale(0, RoundingMode.HALF_UP),
                    partEau.setScale(0, RoundingMode.HALF_UP),
                    totalArrondi));
        }

        // Compensations : on ajoute ou retire un ariary aux parts dont le reste
        // décimal est le plus important, jusqu'à atteindre la cible.
        BigDecimal cibleArrondie = cible.setScale(0, RoundingMode.HALF_UP);
        long ecart = cibleArrondie.subtract(sommeArrondie).longValue();
        if (ecart == 0 || resultat.isEmpty()) {
            return resultat;
        }

        List<int[]> tries = new ArrayList<>(rangs);
        Comparator<int[]> parReste = Comparator.comparingInt((int[] t) -> Math.abs(t[1])).reversed()
                .thenComparingInt(t -> t[0]);
        tries.sort(parReste);

        int index = 0;
        boolean versLeHaut = ecart > 0;
        while (ecart != 0) {
            int[] t = tries.get(index % tries.size());
            LigneRepartition ligne = resultat.get(t[0]);
            BigDecimal ajuste = ligne.total().add(versLeHaut ? BigDecimal.ONE : BigDecimal.ONE.negate());
            if (ajuste.signum() < 0) {
                break; // on ne descend pas sous zéro
            }
            resultat.set(t[0], new LigneRepartition(ligne.cle(), ligne.consoElectricite(),
                    ligne.consoEau(), ligne.partElectricite(), ligne.partEau(), ajuste));
            ecart += versLeHaut ? -1 : 1;
            index++;
            if (index > tries.size() * 4) {
                break; // garde-fou : la cible est hors de portée des corrections
            }
        }
        return resultat;
    }

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static BigDecimal nonNegatif(BigDecimal valeur) {
        if (valeur == null || valeur.signum() < 0) {
            return ZERO;
        }
        return valeur;
    }
}
