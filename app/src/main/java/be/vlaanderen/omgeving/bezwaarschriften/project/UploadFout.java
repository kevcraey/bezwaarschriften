package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Beschrijft een fout bij het uploaden van een specifiek bestand.
 *
 * @param bestandsnaam Naam van het bestand dat niet kon worden ge-upload
 * @param reden Reden waarom de upload is mislukt
 */
record UploadFout(String bestandsnaam, String reden) {}
