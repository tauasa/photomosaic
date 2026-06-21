package org.tauasa.apps.photomosaic.provider.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.util.List;

/**
 * Google sign-in via the OAuth 2.0 installed-application (loopback) flow.
 *
 * <p>The user supplies their own Desktop OAuth client ({@code client_secret.json}) and consents
 * in their browser. The app only ever holds a short-lived token for the read-only Picker scope;
 * no password is entered into the app.
 */
public final class GoogleAuth {

    public static final String PICKER_SCOPE =
            "https://www.googleapis.com/auth/photospicker.mediaitems.readonly";

    private final GsonFactory json = GsonFactory.getDefaultInstance();
    private final NetHttpTransport transport = new NetHttpTransport();

    public Credential authorize(File clientSecretJson, File tokensDir) throws IOException {
        if (!clientSecretJson.isFile()) {
            throw new IOException("client_secret.json not found at " + clientSecretJson);
        }
        Files.createDirectories(tokensDir.toPath());

        try (Reader reader = new InputStreamReader(Files.newInputStream(clientSecretJson.toPath()))) {
            GoogleClientSecrets secrets = GoogleClientSecrets.load(json, reader);

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    transport, json, secrets, List.of(PICKER_SCOPE))
                    .setDataStoreFactory(new FileDataStoreFactory(tokensDir))
                    .setAccessType("offline")
                    .build();

            LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                    .setHost("localhost")
                    .setPort(-1) // pick a free loopback port
                    .build();

            return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        }
    }
}
