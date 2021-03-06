package com.skybot.irc.config;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.speech.v1p1beta1.SpeechSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

@Slf4j
@Configuration
public class SpeechToTextConfig {

    private final String GOOGLE_ACCOUNT = "google-service-account-location";

    @Autowired
    private SkyBotProperties skyBotProperties;

    @Bean
    public SpeechSettings speechSettings() {
        log.info("Generating speech credentials and settings");

        try {
            File resource = new ClassPathResource(skyBotProperties.getCredentials().get(GOOGLE_ACCOUNT)).getFile();
            CredentialsProvider credentialsProvider = FixedCredentialsProvider
                    .create(ServiceAccountCredentials
                            .fromStream(new FileInputStream(resource)));

            return SpeechSettings.newBuilder().setCredentialsProvider(credentialsProvider).build();
        } catch(FileNotFoundException ex) {
            log.error("Could not find the file");
            ex.printStackTrace();
        } catch (IOException ignore) { }

        return null;
    }
}
