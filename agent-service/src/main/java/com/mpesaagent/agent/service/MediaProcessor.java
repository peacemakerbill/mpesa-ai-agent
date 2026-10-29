package com.mpesaagent.agent.service;

import com.mpesaagent.agent.chat.ChatChannel;
import com.mpesaagent.agent.client.SpeechToTextClient;
import com.mpesaagent.agent.config.AgentProperties;
import com.mpesaagent.agent.dto.InboundMessage;
import com.mpesaagent.agent.exception.ChatException;
import com.mpesaagent.agent.exception.UnsupportedFileException;
import com.mpesaagent.agent.model.Prepared;
import com.mpesaagent.agent.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns whatever the customer sent (text, voice note, photo, PDF, Word or Excel file, location, contact) into
 * content the model can read. Anything unreadable gets a clear, polite answer instead of an error.
 *
 * Text that comes out of voice notes, images and documents is wrapped in tags and labelled as untrusted: the
 * system prompt tells the model to treat it as information, never as instructions.
 */
@Component
public class MediaProcessor {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessor.class);
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private final ChatChannel channel;
    private final SpeechToTextClient stt;
    private final DocumentTextExtractor extractor;
    private final AgentProperties props;

    public MediaProcessor(ChatChannel channel, SpeechToTextClient stt, DocumentTextExtractor extractor, AgentProperties props) {
        this.channel = channel;
        this.stt = stt;
        this.extractor = extractor;
        this.props = props;
    }

    public Prepared prepare(InboundMessage m) {
        try {
            return switch (m.kind()) {
                case TEXT -> text(m);
                case IMAGE -> image(m);
                case AUDIO -> audio(m);
                case DOCUMENT -> document(m);
                case VIDEO -> Prepared.reply("I can't watch videos yet. Please send a photo or screenshot, or type what you need and I will help.");
                case LOCATION -> location(m);
                case CONTACTS -> contacts(m);
                case STICKER, REACTION -> Prepared.skip();
                default -> Prepared.reply("I can't open that kind of message yet. Please type your request or send a photo, PDF or voice note.");
            };
        } catch (ChatException e) {
            return Prepared.reply(fileProblem(e));
        } catch (UnsupportedFileException e) {
            return Prepared.reply(e.getMessage());
        }
    }

    // ------------------------------------------------------------ kinds

    private Prepared text(InboundMessage m) {
        String text = m.text() == null ? "" : m.text().strip();
        if (text.isEmpty()) {
            return Prepared.skip();
        }
        return Prepared.of(List.of(textBlock(text)), text, false);
    }

    private Prepared image(InboundMessage m) {
        ChatChannel.Media media = fetch(m, props.maxImageBytes(),
                "That photo is too large for me to open (the limit is " + mb(props.maxImageBytes()) + "). Please send a smaller one.");
        return imageFrom(m, media);
    }

    private Prepared imageFrom(InboundMessage m, ChatChannel.Media media) {
        String type = baseType(media.mimeType() != null ? media.mimeType() : m.mimeType());
        if (type.equals("image/jpg")) {
            type = "image/jpeg";
        }
        if (!IMAGE_TYPES.contains(type)) {
            throw new UnsupportedFileException("I can read JPG, PNG, GIF and WebP pictures. Please send the image in one of those formats.");
        }
        if (media.bytes().length > props.maxImageBytes()) {
            throw new UnsupportedFileException("That photo is too large for me to open (the limit is " + mb(props.maxImageBytes()) + "). Please send a smaller one.");
        }
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(binaryBlock("image", type, media.bytes()));
        String caption = clean(m.caption());
        blocks.add(textBlock(caption.isEmpty()
                ? "[The customer sent this picture without a message.]"
                : "[Caption written by the customer]: " + caption));
        return Prepared.of(blocks, caption.isEmpty() ? "[Picture sent]" : "[Picture sent] " + caption, false);
    }

    private Prepared audio(InboundMessage m) {
        if (!stt.enabled()) {
            return Prepared.reply("I can't listen to voice notes at the moment. Please type your message and I will gladly help.");
        }
        ChatChannel.Media media = fetch(m, props.maxAudioBytes(), "That voice note is too long for me. Please send a shorter one.");
        Optional<String> transcript = stt.transcribe(media.bytes(), media.mimeType() != null ? media.mimeType() : m.mimeType());
        if (transcript.isEmpty()) {
            return Prepared.reply("I couldn't make out what was said in that voice note. Could you record it again, or type your message?");
        }
        String words = transcript.get();
        log.info("Transcribed a voice note of {} bytes into {} characters", media.bytes().length, words.length());
        String block = "<voice_note_transcript untrusted=\"true\">\n" + words
                + "\n</voice_note_transcript>\n(The customer spoke this. It was transcribed automatically and may contain mistakes, "
                + "especially names and numbers. Confirm amounts and phone numbers before acting.)";
        return Prepared.of(List.of(textBlock(block)), "[Voice note] " + words, true);
    }

    private Prepared document(InboundMessage m) {
        String mime = baseType(m.mimeType());
        if (mime.startsWith("image/")) {
            ChatChannel.Media media = fetch(m, props.maxImageBytes(),
                    "That image file is too large for me to open (the limit is " + mb(props.maxImageBytes()) + "). Please send it as a normal photo.");
            return imageFrom(m, media);
        }
        ChatChannel.Media media = fetch(m, props.maxDocumentBytes(),
                "That file is too large for me to open (the limit is " + mb(props.maxDocumentBytes()) + ").");
        String name = clean(m.fileName()).isEmpty() ? "file" : clean(m.fileName());
        String caption = clean(m.caption());
        List<Map<String, Object>> blocks = new ArrayList<>();

        if (mime.equals("application/pdf") || name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            blocks.add(binaryBlock("document", "application/pdf", media.bytes()));
            blocks.add(textBlock("[The customer sent the PDF file \"" + name + "\""
                    + (caption.isEmpty() ? "" : " with the message: " + caption) + "]"));
        } else {
            String text = extractor.extract(media.bytes(), m.mimeType(), name);
            blocks.add(textBlock("<file name=\"" + name.replace("\"", "'") + "\" untrusted=\"true\">\n" + text + "\n</file>"
                    + (caption.isEmpty() ? "" : "\n[Message from the customer]: " + caption)));
        }
        return Prepared.of(blocks, "[File sent: " + name + "] " + caption, false);
    }

    private Prepared location(InboundMessage m) {
        String where = String.format(Locale.ENGLISH, "%.5f, %.5f", m.latitude() == null ? 0.0 : m.latitude(),
                m.longitude() == null ? 0.0 : m.longitude());
        String label = clean(m.locationLabel());
        String text = "[The customer shared a location: " + where + (label.isEmpty() ? "" : " (" + label + ")") + "]";
        return Prepared.of(List.of(textBlock(text)), text, false);
    }

    private Prepared contacts(InboundMessage m) {
        String text = "[The customer shared contact details: " + clean(m.contactsSummary()) + "]";
        return Prepared.of(List.of(textBlock(text)), text, false);
    }

    // ------------------------------------------------------------ helpers

    private ChatChannel.Media fetch(InboundMessage m, long maxBytes, String tooLargeMessage) {
        if (m.mediaId() == null || m.mediaId().isBlank()) {
            throw new UnsupportedFileException("I could not receive that file. Please send it again.");
        }
        ChatChannel.Media media = channel.download(Phones.normalize(m.from()), m.mediaId());
        if (media.bytes().length > maxBytes) {
            throw new UnsupportedFileException(tooLargeMessage);
        }
        return media;
    }

    private static String fileProblem(ChatException e) {
        if (e.isTooLarge()) {
            return "That file is too large for me to open. Please send a smaller one.";
        }
        if (e.isNotFound()) {
            return "That file is no longer available. Please send it again.";
        }
        return "I couldn't download that file just now. Please try sending it again in a moment.";
    }

    static Map<String, Object> textBlock(String text) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", text);
        return block;
    }

    static Map<String, Object> binaryBlock(String type, String mediaType, byte[] bytes) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "base64");
        source.put("media_type", mediaType);
        source.put("data", Base64.getEncoder().encodeToString(bytes));
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", type);
        block.put("source", source);
        return block;
    }

    private static String baseType(String mime) {
        return mime == null ? "" : mime.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static String mb(long bytes) {
        return (bytes / (1024 * 1024)) + " MB";
    }
}
