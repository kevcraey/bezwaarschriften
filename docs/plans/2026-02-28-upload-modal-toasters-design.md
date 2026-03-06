# Design: Upload modal en toasters

## Aanleiding

Upload-zone staat inline in de Documenten tab. Gebruiker wil feedback via toasters na upload en de upload-zone in een modal.

## Wijzigingen

### 1. Upload modal

De inline `#upload-zone` div wordt verplaatst naar een `vl-modal`:
- Titel: "Bestanden toevoegen"
- Content: bestaande `vl-upload` component (max 100 bestanden, .txt)
- Button slot: "Uploaden" knop
- "Bestanden toevoegen" knop opent de modal
- Na succesvolle upload sluit de modal en herlaadt de bestandenlijst

### 2. Toasters

Een `vl-toaster` (placement: `top-right`) wordt toegevoegd aan de shadow DOM.

Na upload-response:
- **Succes-toaster** (type: `success`): "X bestand(en) succesvol opgeladen" — auto-verdwijnt (fadeOut)
- **Error-toaster** (type: `error`): "X bestand(en) niet opgeladen: bestand met dezelfde naam bestaat al" — closable, blijft staan

Beide kunnen tegelijk verschijnen bij een gemengde bulk-upload (deels succes, deels duplicaten).

### 3. Backend

Geen wijzigingen nodig. `UploadResponse` bevat al `geupload[]` en `fouten[]` met reden per bestand.

### 4. Component registratie

`VlToasterComponent` toevoegen aan `registerWebComponents()`. `VlModalComponent` is al geregistreerd.
