package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;

/**
 * Resultaat van een batch upload-operatie.
 *
 * @param geupload Lijst van succesvol ge-uploade bestandsnamen
 * @param fouten Lijst van fouten per bestand
 */
record UploadResultaat(List<String> geupload, List<UploadFout> fouten) {}
