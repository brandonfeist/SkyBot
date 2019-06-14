package com.skybot.irc.services.impl;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.common.exception.NotFoundException;
import com.github.twitch4j.helix.domain.CreateClipList;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.skybot.irc.config.SkyBotProperties;
import com.skybot.irc.services.ITwitchHelixService;
import com.skybot.irc.services.IVoiceCommandService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class VoiceCommandService implements IVoiceCommandService {

    private static final double COMMAND_SIMILARITY_THRESHOLD = 0.64;

    private final ITwitchHelixService twitchHelixService;
    private final TwitchClient twitchClient;
    private final SkyBotProperties skyBotProperties;

    @Autowired
    public VoiceCommandService(ITwitchHelixService twitchHelixService,
                               TwitchClient twitchClient,
                               SkyBotProperties skyBotProperties) {
        this.twitchHelixService = twitchHelixService;
        this.twitchClient = twitchClient;
        this.skyBotProperties = skyBotProperties;
    }

    @Override
    public void findCommand(String command) {
        Map<String, Double> viableCommands = new HashMap<>();

        if(command.isEmpty()) {
            log.warn("Voice command empty");
            // Do I didn't get that. response
        } else {
            skyBotProperties.getCommands().forEach((commandValue, commandList) -> {
                commandList.forEach(commandText -> {
                    String longer = command, shorter = commandText;
                    if (command.length() < commandText.length()) { // longer should always have greater length
                        longer = commandText;
                        shorter = command;
                    }

                    int longerLength = longer.length();
                    LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
                    double similarity = (longerLength -
                            levenshteinDistance.apply(longer.trim().toLowerCase(), shorter.trim().toLowerCase())) /
                            (double) longerLength;
                    if(similarity >= COMMAND_SIMILARITY_THRESHOLD) {
                        viableCommands.put(commandValue, similarity);
                        log.info("Threshold met for [{}] and [{}]: Threshold: {}", command, commandText, similarity);
                    }
                });
            });
        }

        if(viableCommands.isEmpty()) {
            log.warn("Bot command not found for voice command [{}]", command);
            // Do I didn't get that. response
        } else {
            String bestCommand = viableCommands.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .get()
                    .getKey();

            log.info("Using command [{}] winning with threshold [{}]", bestCommand, viableCommands.get(bestCommand));
        }
    }

    @Override
    public void createClipAndShare(String channel) {
        try {
            CreateClipList createClipList = twitchHelixService.createClipSelf(false);

            if (!createClipList.getData().isEmpty()) {
                createClipList.getData().forEach(clip -> {
                    int indexOfEdit = clip.getEditUrl().lastIndexOf("/edit");
                    String noEditClipUrl = clip.getEditUrl().substring(0, indexOfEdit);
                    twitchClient.getChat().sendMessage(channel, noEditClipUrl);

                    log.debug("Sent clip url [{}] to channel [{}] chat", noEditClipUrl, channel);
                });
                // Send confirmation and voice audio through websocket to live client
            } else {
                log.error("There was a problem creating the clip, no clips were made.");
                // Websocket "Problem Creating clip"
            }
        } catch(HystrixRuntimeException ex) {
            log.error("ex {}", ex);
            log.error("Error creating clip: {}", ex.getFailureType());
            // Websocket "Problem Creating clip"
        } catch(NotFoundException ex) {
            log.error("ex {}", ex);
        }
    }

    public void check() {
        log.info("TEST");
    }
}
