package com.darajaagentx.api.service;

import com.darajaagentx.api.config.ProfileProperties;
import com.darajaagentx.api.dto.RecipientImportResult;
import com.darajaagentx.api.dto.RecipientView;
import com.darajaagentx.api.exception.ActionNotFoundException;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.model.EmailRecipient;
import com.darajaagentx.api.repository.EmailRecipientRepository;
import com.darajaagentx.api.util.CsvReader;
import com.darajaagentx.api.util.Emails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The addresses a customer keeps for sending reports to. They can be added one by one, pasted as a list, or uploaded as a
 * CSV or text file. A name such as "Accountant" lets the chat say "send it to my accountant".
 */
@Service
public class RecipientService {

    public static final int MAX_FILE_BYTES = 262_144;
    public static final int MAX_LINES = 2000;

    private final EmailRecipientRepository repo;
    private final int maxRecipients;

    @Autowired
    public RecipientService(EmailRecipientRepository repo, ProfileProperties props) {
        this(repo, props.maxRecipients());
    }

    RecipientService(EmailRecipientRepository repo, int maxRecipients) {
        this.repo = repo;
        this.maxRecipients = maxRecipients;
    }

    public List<RecipientView> list(String owner) {
        return repo.findByOwnerPhoneOrderByCreatedAtAsc(owner).stream().map(RecipientView::of).toList();
    }

    public List<EmailRecipient> all(String owner) {
        return repo.findByOwnerPhoneOrderByCreatedAtAsc(owner);
    }

    /** Adds an address, or renames it when it is already there. */
    @Transactional
    public RecipientView add(String owner, String rawEmail, String rawName) {
        String email = Emails.require(rawEmail);
        String name = name(rawName);
        Optional<EmailRecipient> existing = repo.findByOwnerPhoneAndEmail(owner, email);
        if (existing.isPresent()) {
            EmailRecipient r = existing.get();
            if (name != null) {
                r.setName(name);
                repo.save(r);
            }
            return RecipientView.of(r);
        }
        if (repo.countByOwnerPhone(owner) >= maxRecipients) {
            throw new InvalidActionRequestException("The list is full (" + maxRecipients + " addresses). Remove some first.");
        }
        return RecipientView.of(repo.save(new EmailRecipient(owner, email, name)));
    }

    @Transactional
    public void remove(String owner, UUID id) {
        EmailRecipient r = repo.findByIdAndOwnerPhone(id, owner).orElseThrow(() -> new ActionNotFoundException("That address is not on your list."));
        repo.deleteAll(List.of(r));
    }

    /** Removes by address or by name, for the chat. Returns what was removed. */
    @Transactional
    public Optional<EmailRecipient> removeBy(String owner, String emailOrName) {
        Optional<EmailRecipient> found = find(owner, emailOrName);
        found.ifPresent(r -> repo.deleteAll(List.of(r)));
        return found;
    }

    /** An address on the list by its address or its name (case does not matter). */
    public Optional<EmailRecipient> find(String owner, String emailOrName) {
        if (emailOrName == null || emailOrName.isBlank()) {
            return Optional.empty();
        }
        String wanted = emailOrName.trim();
        String email = Emails.clean(wanted);
        List<EmailRecipient> all = repo.findByOwnerPhoneOrderByCreatedAtAsc(owner);
        if (email != null) {
            return all.stream().filter(r -> r.getEmail().equals(email)).findFirst();
        }
        String lower = wanted.toLowerCase(Locale.ROOT).replaceAll("^(my|the)\\s+", "");
        return all.stream().filter(r -> r.getName() != null && r.getName().toLowerCase(Locale.ROOT).equals(lower)).findFirst();
    }

    // ------------------------------------------------------------------ lists and files

    /** Pasted text: addresses separated by commas, spaces or new lines. */
    @Transactional
    public RecipientImportResult addMany(String owner, String text) {
        Emails.Found found = Emails.findAll(text);
        Map<String, String> entries = new LinkedHashMap<>();
        found.emails().forEach(e -> entries.put(e, null));
        return save(owner, entries, found.invalid());
    }

    /**
     * An uploaded file. A CSV with a header may have the columns email and name (any order, "e-mail" and "email address"
     * work too). Without a header each line may hold an address and, in another cell, a name. A plain text file with an
     * address on every line works as well.
     */
    @Transactional
    public RecipientImportResult importFile(String owner, byte[] data) {
        if (data == null || data.length == 0) {
            throw new InvalidActionRequestException("The file is empty.");
        }
        if (data.length > MAX_FILE_BYTES) {
            throw new InvalidActionRequestException("The file is bigger than 256 KB. Split it into smaller files.");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            throw new InvalidActionRequestException("The file is not saved as UTF-8. In Excel choose Save as, CSV UTF-8.");
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> table = CsvReader.parse(text);
        if (table.size() > MAX_LINES) {
            throw new InvalidActionRequestException("The file has more than " + MAX_LINES + " lines. Split it into smaller files.");
        }
        int emailCol = -1;
        int nameCol = -1;
        if (!table.isEmpty()) {
            List<String> header = table.get(0);
            for (int i = 0; i < header.size(); i++) {
                String h = header.get(i).trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
                if (h.equals("email") || h.equals("emailaddress") || h.equals("mail")) {
                    emailCol = i;
                } else if (h.equals("name") || h.equals("fullname") || h.equals("label") || h.equals("contact")) {
                    nameCol = i;
                }
            }
        }
        boolean hasHeader = emailCol >= 0;
        Map<String, String> entries = new LinkedHashMap<>();
        List<String> invalid = new ArrayList<>();
        for (int r = hasHeader ? 1 : 0; r < table.size(); r++) {
            List<String> row = table.get(r);
            // the cells that hold addresses: the email column, or every cell with an @ when there is no header
            List<String> addressCells = new ArrayList<>();
            if (hasHeader) {
                String cell = emailCol < row.size() ? row.get(emailCol).trim() : "";
                if (!cell.isEmpty()) {
                    addressCells.add(cell);
                }
            } else {
                for (String cell : row) {
                    if (cell.indexOf('@') >= 0) {
                        addressCells.add(cell.trim());
                    }
                }
                if (addressCells.isEmpty() && row.size() == 1 && !row.get(0).isBlank()) {
                    addressCells.add(row.get(0).trim());    // a line with no @ at all is reported as not an address
                }
            }
            if (addressCells.isEmpty()) {
                continue;
            }
            List<String> found = new ArrayList<>();
            for (String cell : addressCells) {
                Emails.Found f = Emails.findAll(cell);
                found.addAll(f.emails());
                invalid.addAll(f.invalid());
                if (f.emails().isEmpty() && f.invalid().isEmpty()) {
                    invalid.add(cell.length() > 60 ? cell.substring(0, 57) + "..." : cell);
                }
            }
            if (found.isEmpty()) {
                continue;
            }
            String name = null;
            if (nameCol >= 0 && nameCol < row.size()) {
                name = name(row.get(nameCol));
            } else if (!hasHeader) {
                for (String cell : row) {
                    if (cell.indexOf('@') < 0 && !cell.isBlank()) {
                        name = name(cell);
                        break;
                    }
                }
            }
            for (String e : found) {
                entries.putIfAbsent(e, found.size() == 1 ? name : null);
            }
        }
        return save(owner, entries, invalid);
    }

    private RecipientImportResult save(String owner, Map<String, String> entries, List<String> invalid) {
        int added = 0;
        int already = 0;
        boolean limit = false;
        long count = repo.countByOwnerPhone(owner);
        for (Map.Entry<String, String> e : entries.entrySet()) {
            Optional<EmailRecipient> existing = repo.findByOwnerPhoneAndEmail(owner, e.getKey());
            if (existing.isPresent()) {
                already++;
                if (e.getValue() != null && existing.get().getName() == null) {
                    existing.get().setName(e.getValue());
                    repo.save(existing.get());
                }
                continue;
            }
            if (count >= maxRecipients) {
                limit = true;
                continue;
            }
            repo.save(new EmailRecipient(owner, e.getKey(), e.getValue()));
            count++;
            added++;
        }
        List<String> samples = invalid.size() > 20 ? invalid.subList(0, 20) : invalid;
        return new RecipientImportResult(added, already, invalid.size(), List.copyOf(samples), limit, list(owner));
    }

    private static String name(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() > 80 || s.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidActionRequestException("The name is too long or has characters that are not allowed.");
        }
        return s;
    }
}
