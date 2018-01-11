package com.greenfox.blackjackbot.webhook;

import at.mukprojects.giphy4j.Giphy;
import at.mukprojects.giphy4j.entity.search.SearchFeed;
import at.mukprojects.giphy4j.entity.search.SearchRandom;
import at.mukprojects.giphy4j.exception.GiphyException;
import com.github.messenger4j.MessengerPlatform;
import com.github.messenger4j.exceptions.MessengerApiException;
import com.github.messenger4j.exceptions.MessengerIOException;
import com.github.messenger4j.exceptions.MessengerVerificationException;
import com.github.messenger4j.receive.MessengerReceiveClient;
import com.github.messenger4j.receive.events.AccountLinkingEvent;
import com.github.messenger4j.receive.handlers.*;
import com.github.messenger4j.send.*;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Date;

@RestController
@RequestMapping("/callback")
public class CallBackHandler {

  private static final Logger logger = LoggerFactory.getLogger(CallBackHandler.class);

  public static final String XKCD = "DEVELOPER_DEFINED_PAYLOAD_FOR_GOOD_ACTION";
  public static final String NAPIRAJZ = "DEVELOPER_DEFINED_PAYLOAD_FOR_NOT_GOOD_ACTION";
  public static final String JUSTAGIF = "DEVELOPER_DEFINED_PAYLOAD_FOR_GIF_ACTION";
  public static final String PLAY = "DEVELOPER_DEFINED_PAYLOAD_FOR_PLAY";
  public static final String NOPLAY = "DEVELOPER_DEFINED_PAYLOAD_FOR_NOPLAY";
  public static final String USELESS = "USELESS";
  public static final String WEIRD = "WEIRD";
  public static final String PLAYDICE = "DICE";

  private final MessengerReceiveClient receiveClient;
  private final MessengerSendClient sendClient;

  @Autowired
  public CallBackHandler(@Value("${messenger4j.appSecret}") final String appSecret,
      @Value("${messenger4j.verifyToken}") final String verifyToken,
      final MessengerSendClient sendClient) {

    logger.debug("Initializing MessengerReceiveClient - appSecret: {} | verifyToken: {}", appSecret,
        verifyToken);
    this.receiveClient = MessengerPlatform.newReceiveClientBuilder(appSecret, verifyToken)
        .onTextMessageEvent(newTextMessageEventHandler())
        .onQuickReplyMessageEvent(newQuickReplyMessageEventHandler())
        .onPostbackEvent(newPostbackEventHandler())
        .onAccountLinkingEvent(newAccountLinkingEventHandler())
        .onOptInEvent(newOptInEventHandler())
        .onEchoMessageEvent(newEchoMessageEventHandler())
        .onMessageDeliveredEvent(newMessageDeliveredEventHandler())
        .onMessageReadEvent(newMessageReadEventHandler())
        .fallbackEventHandler(newFallbackEventHandler())
        .build();
    this.sendClient = sendClient;
  }

  @RequestMapping(method = RequestMethod.GET)
  public ResponseEntity<String> verifyWebhook(@RequestParam("hub.mode") final String mode,
      @RequestParam("hub.verify_token") final String verifyToken,
      @RequestParam("hub.challenge") final String challenge) {

    logger
        .debug("Received Webhook verification request - mode: {} | verifyToken: {} | challenge: {}",
            mode,
            verifyToken, challenge);
    try {
      return ResponseEntity.ok(this.receiveClient.verifyWebhook(mode, verifyToken, challenge));
    } catch (MessengerVerificationException e) {
      logger.warn("Webhook verification failed: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
  }

  @RequestMapping(method = RequestMethod.POST)
  public ResponseEntity<Void> handleCallback(@RequestBody final String payload,
      @RequestHeader("X-Hub-Signature") final String signature) {

    logger.debug("Received Messenger Platform callback - payload: {} | signature: {}", payload,
        signature);
    try {
      this.receiveClient.processCallbackPayload(payload, signature);
      logger.debug("Processed callback payload successfully");
      return ResponseEntity.status(HttpStatus.OK).build();
    } catch (MessengerVerificationException e) {
      logger.warn("Processing of callback payload failed: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
  }

  private TextMessageEventHandler newTextMessageEventHandler() {
    return event -> {
      logger.debug("Received TextMessageEvent: {}", event);

      final String messageId = event.getMid();
      final String messageText = event.getText();
      final String senderId = event.getSender().getId();
      final Date timestamp = event.getTimestamp();

      logger.info("Received message '{}' with text '{}' from user '{}' at '{}'",
          messageId, messageText, senderId, timestamp);

      try {
        String lower = messageText.toLowerCase();
        sendReadReceipt(senderId);
        sendTypingOn(senderId);
        sendQuickYesNoReply(senderId);
        sendTypingOff(senderId);
      } catch (MessengerApiException | MessengerIOException e) {
        handleSendException(e);
      }
    };
  }


  private void sendGifMessage(String recipientId, String gif)
      throws MessengerApiException, MessengerIOException {
    this.sendClient.sendImageAttachment(recipientId, gif);
  }

  private void sendQuickReply(String recipientId)
      throws MessengerApiException, MessengerIOException {
    final List<QuickReply> quickReplies = QuickReply.newListBuilder()
        .addTextQuickReply("XKCD comic", XKCD).toList()
        .addTextQuickReply("Napirajz", NAPIRAJZ).toList()
        .addTextQuickReply("GIPHY", JUSTAGIF).toList()
        .addTextQuickReply("I feel lucky", WEIRD).toList()
        .addTextQuickReply("Dice", PLAYDICE).toList()
        .build();

    this.sendClient.sendTextMessage(recipientId, "Pick something to play with", quickReplies);
  }

  private void sendQuickYesNoReply(String recipientId)
      throws MessengerApiException, MessengerIOException {
    final List<QuickReply> quickReplies = QuickReply.newListBuilder()
        .addTextQuickReply("Yes", PLAY).toList()
        .addTextQuickReply("No", NOPLAY).toList()
        .addTextQuickReply("I feel useless", USELESS).toList()
        .build();

    this.sendClient.sendTextMessage(recipientId, "Do you want to play?", quickReplies);
  }

  private void sendReadReceipt(String recipientId)
      throws MessengerApiException, MessengerIOException {
    this.sendClient.sendSenderAction(recipientId, SenderAction.MARK_SEEN);
  }

  private void sendTypingOn(String recipientId) throws MessengerApiException, MessengerIOException {
    this.sendClient.sendSenderAction(recipientId, SenderAction.TYPING_ON);
  }

  private void sendTypingOff(String recipientId)
      throws MessengerApiException, MessengerIOException {
    this.sendClient.sendSenderAction(recipientId, SenderAction.TYPING_OFF);
  }

  private QuickReplyMessageEventHandler newQuickReplyMessageEventHandler() {
    Giphy giphy = new Giphy(System.getenv("GIPHY_API_KEY"));
    return event -> {
      logger.debug("Received QuickReplyMessageEvent: {}", event);

      final String senderId = event.getSender().getId();
      final String messageId = event.getMid();
      final String quickReplyPayload = event.getQuickReply().getPayload();

      logger.info("Received quick reply for message '{}' with payload '{}'", messageId,
          quickReplyPayload);

      try {
        if (quickReplyPayload.equals(PLAY)) {
          SearchRandom giphyData = giphy.searchRandom("cool");
          sendGifMessage(senderId,
              "https://media.giphy.com/media/rrFcUcN3MFmta/giphy.gif");
          sendQuickReply(senderId);
        } else if (quickReplyPayload.equals(XKCD)) {
          sendTextMessage(senderId, "https://xkcd.com/" + generateRandom());
        } else if (quickReplyPayload.equals(NAPIRAJZ)) {
          sendTextMessage(senderId, "http://napirajz.hu/?p=" + generateRandom());
        } else if (quickReplyPayload.equals(JUSTAGIF)) {
          SearchFeed feed = giphy.trend();
          sendGifMessage(senderId, feed.getDataList().get(0).getImages().getOriginal().getUrl());
        } else if (quickReplyPayload.equals(WEIRD)) {
          sendTextMessage(senderId, "http://weirdorconfusing.com/");
        } else if (quickReplyPayload.equals(USELESS)) {
          sendTextMessage(senderId, "http://www.theuselessweb.com/");
        } else if (quickReplyPayload.equals(PLAYDICE)) {

          int user = generateRandomForDice();
          int bot = generateRandomForDice();

          if (user > bot) {
            sendTextMessage(senderId,
                "You won. Your score: " + String.valueOf(user) + " Bot's score: " + String
                    .valueOf(bot));
          } else if (bot > user) {
            sendTextMessage(senderId,
                "You lost. Your score: " + String.valueOf(user) + " Bot's score: " + String
                    .valueOf(bot));
          } else {
            sendTextMessage(senderId,
                "Draw. Your score: " + String.valueOf(user) + " Bot's score: " + String
                    .valueOf(bot));
          }
        } else {
          sendGifMessage(senderId, "https://media.giphy.com/media/3o7TKr3nzbh5WgCFxe/giphy.gif");
          sendTextMessage(senderId, "Go outside then, you moron.");
        }
      } catch (MessengerApiException e) {
        handleSendException(e);
      } catch (MessengerIOException e) {
        handleIOException(e);
      } catch (GiphyException e) {
        e.printStackTrace();
      }
    };
  }


  public Integer generateRandom() {
    Random r = new Random();
    int lowerBound = 1;
    int upperBound = 1940;
    int result = r.nextInt(upperBound - lowerBound) + lowerBound;
    return result;
  }

  public Integer generateRandomForDice() {
    Random r = new Random();
    int lowerBound = 1;
    int upperBound = 6;
    int result = r.nextInt(upperBound - lowerBound) + lowerBound;
    return result;
  }

  private PostbackEventHandler newPostbackEventHandler() {
    return event -> {
      logger.debug("Received PostbackEvent: {}", event);

      final String senderId = event.getSender().getId();
      final String recipientId = event.getRecipient().getId();
      final String payload = event.getPayload();
      final Date timestamp = event.getTimestamp();

      logger.info("Received postback for user '{}' and page '{}' with payload '{}' at '{}'",
          senderId, recipientId, payload, timestamp);

      sendTextMessage(senderId, "Postback called");
    };
  }

  private AccountLinkingEventHandler newAccountLinkingEventHandler() {
    return event -> {
      logger.debug("Received AccountLinkingEvent: {}", event);

      final String senderId = event.getSender().getId();
      final AccountLinkingEvent.AccountLinkingStatus accountLinkingStatus = event.getStatus();
      final String authorizationCode = event.getAuthorizationCode();

      logger
          .info("Received account linking event for user '{}' with status '{}' and auth code '{}'",
              senderId, accountLinkingStatus, authorizationCode);
    };
  }

  private OptInEventHandler newOptInEventHandler() {
    return event -> {
      logger.debug("Received OptInEvent: {}", event);

      final String senderId = event.getSender().getId();
      final String recipientId = event.getRecipient().getId();
      final String passThroughParam = event.getRef();
      final Date timestamp = event.getTimestamp();

      logger.info(
          "Received authentication for user '{}' and page '{}' with pass through param '{}' at '{}'",
          senderId, recipientId, passThroughParam, timestamp);

      sendTextMessage(senderId, "Authentication successful");
    };
  }

  private EchoMessageEventHandler newEchoMessageEventHandler() {
    return event -> {
      logger.debug("Received EchoMessageEvent: {}", event);

      final String messageId = event.getMid();
      final String recipientId = event.getRecipient().getId();
      final String senderId = event.getSender().getId();
      final Date timestamp = event.getTimestamp();

      logger.info(
          "Received echo for message '{}' that has been sent to recipient '{}' by sender '{}' at '{}'",
          messageId, recipientId, senderId, timestamp);
    };
  }

  private MessageDeliveredEventHandler newMessageDeliveredEventHandler() {
    return event -> {
      logger.debug("Received MessageDeliveredEvent: {}", event);

      final List<String> messageIds = event.getMids();
      final Date watermark = event.getWatermark();
      final String senderId = event.getSender().getId();

      if (messageIds != null) {
        messageIds.forEach(messageId -> {
          logger.info("Received delivery confirmation for message '{}'", messageId);
        });
      }

      logger.info("All messages before '{}' were delivered to user '{}'", watermark, senderId);
    };
  }

  private MessageReadEventHandler newMessageReadEventHandler() {
    return event -> {
      logger.debug("Received MessageReadEvent: {}", event);

      final Date watermark = event.getWatermark();
      final String senderId = event.getSender().getId();

      logger.info("All messages before '{}' were read by user '{}'", watermark, senderId);
    };
  }

  /**
   * This handler is called when either the message is unsupported or when the event handler for the actual event type
   * is not registered. In this showcase all event handlers are registered. Hence only in case of an
   * unsupported message the fallback event handler is called.
   */
  private FallbackEventHandler newFallbackEventHandler() {
    return event -> {
      logger.debug("Received FallbackEvent: {}", event);

      final String senderId = event.getSender().getId();
      logger.info("Received unsupported message from user '{}'", senderId);
    };
  }

  private void sendTextMessage(String recipientId, String text) {
    try {
      final Recipient recipient = Recipient.newBuilder().recipientId(recipientId).build();
      final NotificationType notificationType = NotificationType.REGULAR;
      final String metadata = "DEVELOPER_DEFINED_METADATA";

      this.sendClient.sendTextMessage(recipient, notificationType, text, metadata);
    } catch (MessengerApiException | MessengerIOException e) {
      handleSendException(e);
    }
  }

  private void handleSendException(Exception e) {
    logger.error("Message could not be sent. An unexpected error occurred.", e);
  }

  private void handleIOException(Exception e) {
    logger.error("Could not open Spring.io page. An unexpected error occurred.", e);
  }
}
