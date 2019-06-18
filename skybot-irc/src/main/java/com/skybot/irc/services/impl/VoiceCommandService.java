package com.skybot.irc.services.impl;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.common.exception.NotFoundException;
import com.github.twitch4j.helix.domain.CreateClipList;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.skybot.irc.config.SkyBotProperties;
import com.skybot.irc.models.UserPrincipal;
import com.skybot.irc.services.ITwitchHelixService;
import com.skybot.irc.services.IVoiceCommandService;
import com.skybot.irc.utility.VoiceCommandKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class VoiceCommandService implements IVoiceCommandService {

    private static final double COMMAND_SIMILARITY_THRESHOLD = 0.64;

    private final UserPrincipal userPrincipal;
    private final ITwitchHelixService twitchHelixService;
    private final TwitchClient twitchClient;
    private final SkyBotProperties skyBotProperties;

    @Autowired
    public VoiceCommandService(ITwitchHelixService twitchHelixService,
                               TwitchClient twitchClient,
                               UserPrincipal userPrincipal,
                               SkyBotProperties skyBotProperties) {
        this.twitchHelixService = twitchHelixService;
        this.userPrincipal = userPrincipal;
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
            skyBotProperties.getCommands().forEach((commandValue, commandList) -> commandList.forEach(commandText -> {
                // https://stackoverflow.com/questions/955110/similarity-string-comparison-in-javas
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
            }));
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

            mapCommand(VoiceCommandKeys.resolve(bestCommand));
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

    private void mapCommand(VoiceCommandKeys command) {
        switch (command) {
            case CLIP:
                CreateClipList createClipList = twitchHelixService.createClipSelf(false);
                createClipList.getData().forEach(clip ->
                        twitchClient.getChat().sendMessage(userPrincipal.getLogin(), clip.getEditUrl()));
                // Assistant voice response saying the clips were made and are in the chat.
                break;
            case START_POLL:
                log.info("Starting a poll");
                break;
            case END_POLL:
                log.info("Ending the poll");
                break;
            case SONG_INFO:
                log.info("Getting song info");
                break;
            case NEXT_SONG:
                log.info("Next song");
                break;
            case PREV_SONG:
                log.info("Previous song");
                break;
            case PLAY_SONG:
                log.info("Playing song");
                break;
            case PAUSE_SONG:
                log.info("Pausing song");
                break;
            default:
                log.info("I didn't get that.");
                break;
        }
    }
}
