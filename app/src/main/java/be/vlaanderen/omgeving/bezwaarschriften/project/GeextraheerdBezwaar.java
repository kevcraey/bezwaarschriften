package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Een individueel bezwaar geextraheerd uit een passage van een bezwaarschrift.
 *
 * @param passageId Verwijzing naar de bron-passage
 * @param samenvatting Kernachtige omschrijving van het bezwaar
 * @param categorie Classificatie: milieu, mobiliteit, ruimtelijke_ordening,
 *                  procedure, gezondheid, economisch, sociaal, overig
 */
public record GeextraheerdBezwaar(int passageId, String samenvatting, String categorie) {}
