package com.skybot.irc.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.skybot.irc.config.SkyBotProperties;
import com.skybot.irc.features.AbstractBasicMessageFeature;
import com.skybot.irc.features.NintendoFriendCode;
import com.skybot.irc.models.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SkyBot {

    private SkyBotVoice voice;

    private SkyBotProperties skyBotProperties;

    private TwitchClient twitchClient;

    private NintendoFriendCode nintendoFriendCode;

    private List<AbstractBasicMessageFeature> messageFeatures;

    @Autowired
    public SkyBot(SkyBotProperties skyBotProperties,
                  SkyBotVoice voice,
                  NintendoFriendCode nintendoFriendCode,
                  TwitchClient twitchClient) {
        log.info("Initializing SkyBot.");

        this.twitchClient = twitchClient;
        this.skyBotProperties = skyBotProperties;
        this.voice = voice;
        this.nintendoFriendCode = nintendoFriendCode;

        registerFeatures();

        twitchClient.getEventManager().onEvent(ChannelMessageEvent.class).subscribe(event -> onChannelMessage(event));

//        if(skyBotProperties.isVoice()) {
//            this.voice.start(skyBotProperties.getChannels().get(0));
//        }
        // Look into spring boot threads sent in email to self
    }

    @EventListener
    public void authSuccessEventListener(AuthenticationSuccessEvent authorizedEvent){
        ObjectMapper objectMapper = new ObjectMapper();
        UserPrincipal userPrincipal = objectMapper.convertValue(
                authorizedEvent.getAuthentication().getPrincipal(),UserPrincipal.class);
        // Instead of having TwitchConfig configured at the load, maybe build twitchClient here? Or force login at twitch
        // config to get credentials then, and then apply to twitchClient...
        // Maybe anything that uses User Token, instead of using TwitchClient use OAuthRestTemplate to make calls to twitch
        // and then use the twitch4j models for the twitch return payload...
        log.info("Successful login, logging in {}", userPrincipal.getUserName());
        joinChannel(userPrincipal.getLogin());
    }

    private void joinChannel(String login) {
        twitchClient.getChat().joinChannel(login);
        log.info("Joined channel {}", login);
    }

    private void registerFeatures() {
        messageFeatures = new ArrayList<>();
        messageFeatures.add(nintendoFriendCode);
    }

    private void onChannelMessage(ChannelMessageEvent event) {
        for(AbstractBasicMessageFeature messageFeature : messageFeatures) {
            if(messageFeature.getTrigger().equals(event.getMessage().trim())) {
                messageFeature.execute(event);
            }
        }
    }
}
