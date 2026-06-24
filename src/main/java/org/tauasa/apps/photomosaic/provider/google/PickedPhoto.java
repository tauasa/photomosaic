package org.tauasa.apps.photomosaic.provider.google;

/**
 * A photo the user selected in the Google Photos picker.
 *
 * @param id       media item id
 * @param baseUrl  temporary download URL (append size params + bearer token to fetch bytes)
 * @param mimeType e.g. image/jpeg
 * @param filename original filename, if provided
 */
public record PickedPhoto(String id, String baseUrl, String mimeType, String filename) {

    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
