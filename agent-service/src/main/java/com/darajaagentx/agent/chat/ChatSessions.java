package com.darajaagentx.agent.chat;

import com.darajaagentx.agent.store.StateStore;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Many chats per customer, the way Claude or ChatGPT keep them: start a new one, go back to an old one, rename,
 * pin and delete.
 *
 * <p>The assistant always works in the customer's one live chat (the {@link ChatLog} and the model's short term
 * memory). Every other chat is kept aside as a saved copy. Opening a chat sets the live one aside and brings the
 * saved one in, so nothing else in the agent needs to know that several chats exist.
 *
 * <p>The list of chats lives under {@code chat:sessions:<phone>} and each saved chat under
 * {@code chat:saved:<phone>:<id>}, both in the shared state store and kept for {@link #KEEP}.
 */
@Component
public class ChatSessions {

    private static final Logger log = LoggerFactory.getLogger(ChatSessions.class);

    public static final int MAX_CHATS = 100;
    public static final int MAX_TITLE = 80;
    static final String NEW_CHAT = "New chat";
    static final Duration KEEP = Duration.ofDays(180);

    /** Where ConversationStore keeps what the model remembers. Must stay the same as the key used there. */
    static final String HISTORY_KEY = "chat:history:";
    static final Duration HISTORY_TTL = Duration.ofHours(24);

    /** One chat as the apps see it. */
    public record Summary(String id, String title, boolean pinned, boolean active, long createdAt, long updatedAt,
                          String preview, int messages) {
    }

    /** All of a customer's chats, pinned first and then the most recently used. */
    public record Listing(String activeId, List<Summary> chats) {
    }

    /** A refusal the controller turns into an HTTP answer. */
    public static class Failure extends RuntimeException {
        private final int status;

        public Failure(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String id, String autoTitle, String customTitle, boolean pinned, long createdAt, long updatedAt,
                 String preview, int messages) {

        @JsonIgnore
        String displayTitle() {
            return customTitle != null && !customTitle.isBlank() ? customTitle : autoTitle;
        }

        Entry withCustomTitle(String value) {
            return new Entry(id, autoTitle, value, pinned, createdAt, updatedAt, preview, messages);
        }

        Entry withPinned(boolean value) {
            return new Entry(id, autoTitle, customTitle, value, createdAt, updatedAt, preview, messages);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Index(String activeId, List<Entry> chats) {

        @JsonIgnore
        Optional<Entry> find(String id) {
            return chats.stream().filter(e -> e.id().equals(id)).findFirst();
        }

        @JsonIgnore
        Entry active() {
            return find(activeId).orElse(chats.get(0));
        }

        Index replacing(Entry changed) {
            List<Entry> list = new ArrayList<>();
            for (Entry e : chats) {
                list.add(e.id().equals(changed.id()) ? changed : e);
            }
            return new Index(activeId, list);
        }

        Index without(String id) {
            List<Entry> list = new ArrayList<>(chats);
            list.removeIf(e -> e.id().equals(id));
            return new Index(activeId, list);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Saved(List<ChatMessage> messages, List<String> history) {

        static Saved empty() {
            return new Saved(List.of(), List.of());
        }
    }

    private final StateStore store;
    private final ObjectMapper mapper;
    private final ChatLog chatLog;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public ChatSessions(StateStore store, ObjectMapper mapper, ChatLog chatLog) {
        this.store = store;
        this.mapper = mapper;
        this.chatLog = chatLog;
    }

    public Listing list(String phone) {
        synchronized (lock(phone)) {
            return view(refreshed(phone, load(phone)));
        }
    }

    /** Starts a fresh chat. When the current one is still empty it simply stays the current one. */
    public Listing create(String phone) {
        synchronized (lock(phone)) {
            Index index = refreshed(phone, load(phone));
            Entry current = index.active();
            if (current.messages() == 0) {
                return view(index);
            }
            requireIdle(phone);
            if (index.chats().size() >= MAX_CHATS) {
                throw new Failure(409, "You have " + MAX_CHATS + " chats. Delete one to start another.");
            }
            setAside(phone, current);
            wipeLive(phone);
            Entry fresh = fresh();
            List<Entry> chats = new ArrayList<>(index.chats());
            chats.add(fresh);
            Index next = new Index(fresh.id(), chats);
            save(phone, next);
            return view(next);
        }
    }

    /** Makes the chosen chat the live one. The previous live chat is kept aside, unless it was empty and plain. */
    public Listing open(String phone, String id) {
        synchronized (lock(phone)) {
            Index index = refreshed(phone, load(phone));
            Entry target = index.find(id).orElseThrow(ChatSessions::missing);
            if (target.id().equals(index.activeId())) {
                return view(index);
            }
            requireIdle(phone);
            Entry current = index.active();
            Saved saved = readSaved(phone, target.id());

            boolean dropCurrent = disposable(current);
            if (!dropCurrent) {
                setAside(phone, current);
            }
            chatLog.replace(phone, saved.messages());
            replaceHistory(phone, saved.history());

            Index next = new Index(target.id(), index.chats());
            if (dropCurrent) {
                next = next.without(current.id());
            }
            // The saved copy goes last, so a failure above never loses a chat
            save(phone, next);
            store.delete(savedKey(phone, target.id()));
            return view(next);
        }
    }

    /** Gives the chat a name. An empty name goes back to the automatic one. */
    public Listing rename(String phone, String id, String title) {
        String clean = title == null ? "" : title.strip().replaceAll("\\s+", " ");
        if (clean.length() > MAX_TITLE) {
            throw new Failure(400, "A chat name can have up to " + MAX_TITLE + " characters.");
        }
        synchronized (lock(phone)) {
            Index index = refreshed(phone, load(phone));
            Entry entry = index.find(id).orElseThrow(ChatSessions::missing);
            Index next = index.replacing(entry.withCustomTitle(clean.isEmpty() ? null : clean));
            save(phone, next);
            return view(next);
        }
    }

    public Listing pin(String phone, String id, boolean pinned) {
        synchronized (lock(phone)) {
            Index index = refreshed(phone, load(phone));
            Entry entry = index.find(id).orElseThrow(ChatSessions::missing);
            Index next = index.replacing(entry.withPinned(pinned));
            save(phone, next);
            return view(next);
        }
    }

    /** Deletes a chat for good. Deleting the current one leaves a new empty chat in its place. */
    public Listing delete(String phone, String id) {
        synchronized (lock(phone)) {
            Index index = refreshed(phone, load(phone));
            Entry entry = index.find(id).orElseThrow(ChatSessions::missing);
            if (entry.id().equals(index.activeId())) {
                requireIdle(phone);
                wipeLive(phone);
                Entry fresh = fresh();
                List<Entry> chats = new ArrayList<>(index.without(entry.id()).chats());
                chats.add(fresh);
                Index next = new Index(fresh.id(), chats);
                save(phone, next);
                return view(next);
            }
            Index next = index.without(entry.id());
            save(phone, next);
            store.delete(savedKey(phone, entry.id()));
            return view(next);
        }
    }

    /** Deletes every chat, pinned ones included, and starts with one empty chat. */
    public Listing deleteAll(String phone) {
        synchronized (lock(phone)) {
            Index index = refreshed(phone, load(phone));
            requireIdle(phone);
            for (Entry entry : index.chats()) {
                if (!entry.id().equals(index.activeId())) {
                    store.delete(savedKey(phone, entry.id()));
                }
            }
            wipeLive(phone);
            Entry fresh = fresh();
            Index next = new Index(fresh.id(), List.of(fresh));
            save(phone, next);
            return view(next);
        }
    }

    // The index

    private Index load(String phone) {
        Optional<String> json = store.get(indexKey(phone));
        if (json.isPresent()) {
            try {
                Index index = mapper.readValue(json.get(), Index.class);
                if (index != null && index.chats() != null && !index.chats().isEmpty()) {
                    return index;
                }
            } catch (JacksonException e) {
                log.warn("The chat list could not be read, starting a new one");
            }
        }
        // First use (or a lost list): whatever chat is live now becomes the first entry
        Entry first = fresh();
        Index index = new Index(first.id(), List.of(first));
        save(phone, index);
        return index;
    }

    private void save(String phone, Index index) {
        try {
            store.set(indexKey(phone), mapper.writeValueAsString(index), KEEP);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not store the chat list", e);
        }
    }

    /** Brings the live chat's entry up to date (title, last message, count) from the chat itself. */
    private Index refreshed(String phone, Index index) {
        Entry current = index.active();
        Entry updated = derive(current, chatLog.all(phone));
        if (updated.equals(current)) {
            return index;
        }
        Index next = index.replacing(updated);
        save(phone, next);
        return next;
    }

    static Entry derive(Entry entry, List<ChatMessage> live) {
        if (live.isEmpty()) {
            return new Entry(entry.id(), NEW_CHAT, entry.customTitle(), entry.pinned(), entry.createdAt(), entry.updatedAt(), "", 0);
        }
        String title = entry.autoTitle();
        if (title == null || title.isBlank() || NEW_CHAT.equals(title)) {
            for (ChatMessage m : live) {
                String said = "in".equals(m.dir) ? describe(m) : "";
                if (!said.isBlank()) {
                    title = shorten(said, 40);
                    break;
                }
            }
        }
        ChatMessage last = live.get(live.size() - 1);
        return new Entry(entry.id(), title == null || title.isBlank() ? NEW_CHAT : title, entry.customTitle(), entry.pinned(),
                entry.createdAt(), last.at > 0 ? last.at : entry.updatedAt(), shorten(describe(last), 90), live.size());
    }

    /** What to show for a message in a list: its words, or a word for what it is. */
    static String describe(ChatMessage m) {
        String words = firstNonBlank(m.text, m.caption);
        if (!words.isBlank()) {
            return words.replaceAll("\\s+", " ").strip();
        }
        String kind = m.kind == null ? "" : m.kind;
        return switch (kind) {
            case "IMAGE" -> "Photo";
            case "AUDIO" -> Boolean.TRUE.equals(m.voiceNote) ? "Voice message" : "Audio";
            case "VIDEO" -> "Video";
            case "DOCUMENT" -> m.fileName != null && !m.fileName.isBlank() ? m.fileName : "Document";
            case "LOCATION" -> "Location";
            case "CONTACTS" -> "Contact";
            case "BUTTON_REPLY", "LIST_REPLY" -> firstNonBlank(m.actionTitle);
            default -> "";
        };
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    static String shorten(String text, int max) {
        String clean = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        if (clean.length() <= max) {
            return clean;
        }
        int cut = clean.lastIndexOf(' ', max);
        if (cut < max / 2) {
            cut = max;
        }
        return clean.substring(0, cut).stripTrailing() + "...";
    }

    private Listing view(Index index) {
        List<Summary> chats = index.chats().stream()
                .map(e -> new Summary(e.id(), e.displayTitle(), e.pinned(), e.id().equals(index.activeId()), e.createdAt(),
                        e.updatedAt(), e.preview() == null ? "" : e.preview(), e.messages()))
                .sorted(Comparator.comparing(Summary::pinned, Comparator.reverseOrder())
                        .thenComparing(Summary::updatedAt, Comparator.reverseOrder())
                        .thenComparing(Summary::createdAt, Comparator.reverseOrder()))
                .toList();
        return new Listing(index.activeId(), chats);
    }

    // Moving chats in and out of the live slot

    private void setAside(String phone, Entry entry) {
        Saved saved = new Saved(chatLog.all(phone), store.readList(HISTORY_KEY + phone));
        try {
            store.set(savedKey(phone, entry.id()), mapper.writeValueAsString(saved), KEEP);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not keep the chat aside", e);
        }
    }

    private Saved readSaved(String phone, String id) {
        Optional<String> json = store.get(savedKey(phone, id));
        if (json.isEmpty()) {
            return Saved.empty();
        }
        try {
            Saved saved = mapper.readValue(json.get(), Saved.class);
            return new Saved(saved.messages() == null ? List.of() : saved.messages(),
                    saved.history() == null ? List.of() : saved.history());
        } catch (JacksonException e) {
            log.warn("A saved chat could not be read, opening it empty");
            return Saved.empty();
        }
    }

    private void wipeLive(String phone) {
        chatLog.clear(phone);
        store.delete(HISTORY_KEY + phone);
    }

    private void replaceHistory(String phone, List<String> history) {
        store.delete(HISTORY_KEY + phone);
        for (String turn : history) {
            store.appendToList(HISTORY_KEY + phone, turn, 200, HISTORY_TTL);
        }
    }

    private static boolean disposable(Entry entry) {
        return entry.messages() == 0 && !entry.pinned() && entry.customTitle() == null;
    }

    private void requireIdle(String phone) {
        if (chatLog.isTyping(phone)) {
            throw new Failure(409, "The assistant is still replying. Try again in a moment.");
        }
    }

    private static Entry fresh() {
        long now = Instant.now().toEpochMilli();
        return new Entry(UUID.randomUUID().toString().replace("-", "").substring(0, 12), NEW_CHAT, null, false, now, now, "", 0);
    }

    private static Failure missing() {
        return new Failure(404, "That chat no longer exists.");
    }

    private Object lock(String phone) {
        return locks.computeIfAbsent(phone, p -> new Object());
    }

    private static String indexKey(String phone) {
        return "chat:sessions:" + phone;
    }

    private static String savedKey(String phone, String id) {
        return "chat:saved:" + phone + ":" + id;
    }
}