package com.miniredis.pubsub;

import com.miniredis.net.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages Pub/Sub channels and subscriptions.
 */
public class PubSubManager {
    private static final Logger log = LoggerFactory.getLogger(PubSubManager.class);

    private final ConcurrentHashMap<String, Set<ClientSession>> channels = new ConcurrentHashMap<>();

    public void subscribe(ClientSession session, String channel) {
        channels.computeIfAbsent(channel, k -> new CopyOnWriteArraySet<>()).add(session);
        session.getSubscribedChannels().add(channel);
        session.setInPubSubMode(true);
        log.debug("Client subscribed to channel: {}", channel);
    }

    public void unsubscribe(ClientSession session, String channel) {
        Set<ClientSession> subs = channels.get(channel);
        if (subs != null) {
            subs.remove(session);
            if (subs.isEmpty()) {
                channels.remove(channel);
            }
        }
        session.getSubscribedChannels().remove(channel);
        if (session.getSubscribedChannels().isEmpty()) {
            session.setInPubSubMode(false);
        }
    }

    public void unsubscribeAll(ClientSession session) {
        for (String channel : session.getSubscribedChannels()) {
            Set<ClientSession> subs = channels.get(channel);
            if (subs != null) {
                subs.remove(session);
                if (subs.isEmpty()) {
                    channels.remove(channel);
                }
            }
        }
        session.getSubscribedChannels().clear();
        session.setInPubSubMode(false);
    }

    public int publish(String channel, String message) {
        Set<ClientSession> subs = channels.get(channel);
        if (subs == null || subs.isEmpty()) {
            return 0;
        }

        String respMessage = "*3\r\n$7\r\nmessage\r\n$" + channel.length() + "\r\n" + channel + "\r\n$" + message.length() + "\r\n" + message + "\r\n";
        
        int count = 0;
        for (ClientSession session : subs) {
            try {
                session.sendMessage(respMessage);
                count++;
            } catch (Exception e) {
                log.debug("Failed to send message to subscriber, cleaning up subscription", e);
                unsubscribe(session, channel);
            }
        }
        return count;
    }
}
