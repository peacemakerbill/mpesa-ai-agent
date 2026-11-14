package com.darajaagentx.api.controller;

import com.darajaagentx.api.storage.FileStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lets agent-service keep the files of a chat (photos, voice notes, documents) in whatever this service stores files in:
 * a folder, the database or Cloudinary. That way the agent needs no disk and no file service of its own. Only for agent-service:
 * the X-Internal-Key filter protects /internal/**. The agent decides who may see a file, this only keeps the bytes. Names are the ids
 * the agent made (a UUID, or a UUID followed by .meta) and nothing else is accepted.
 */
@RestController
@RequestMapping("/internal/files/chat-media")
public class InternalFilesController {

    private static final Pattern NAME = Pattern.compile("^[0-9a-fA-F-]{36}(\\.meta)?$");
    private static final String FOLDER = "chat-media/";

    private final FileStore files;

    public InternalFilesController(FileStore files) {
        this.files = files;
    }

    /** Keeps the bytes. {@code keepSeconds} is how long they are needed, and a store that can drop them after that does. */
    @PutMapping("/{name}")
    public ResponseEntity<Void> put(@PathVariable String name, @RequestBody byte[] bytes,
                                    @RequestParam(value = "keepSeconds", required = false) Long keepSeconds) {
        if (!NAME.matcher(name).matches()) {
            return ResponseEntity.badRequest().build();
        }
        Duration keep = keepSeconds == null || keepSeconds <= 0 ? null : Duration.ofSeconds(keepSeconds);
        files.put(FOLDER + name, bytes, keep);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{name}")
    public ResponseEntity<byte[]> get(@PathVariable String name) {
        if (!NAME.matcher(name).matches()) {
            return ResponseEntity.notFound().build();
        }
        Optional<byte[]> found = files.get(FOLDER + name);
        return found.map(b -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(b))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        if (!NAME.matcher(name).matches()) {
            return ResponseEntity.badRequest().build();
        }
        files.delete(FOLDER + name);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
