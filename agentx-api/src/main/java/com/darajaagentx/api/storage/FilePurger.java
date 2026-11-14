package com.darajaagentx.api.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Once an hour, drops database files whose keep-until time has passed (chat files). Does nothing for other stores. */
@Component
public class FilePurger {

    private static final Logger log = LoggerFactory.getLogger(FilePurger.class);

    private final FileStore store;

    public FilePurger(FileStore store) {
        this.store = store;
    }

    @Scheduled(fixedDelayString = "${agentx.storage.purge-interval:PT1H}", initialDelayString = "${agentx.storage.purge-interval:PT1H}")
    public void run() {
        if (store instanceof DatabaseFileStore db) {
            try {
                long removed = db.purgeExpired();
                if (removed > 0) {
                    log.info("Removed {} expired files from the database", removed);
                }
            } catch (RuntimeException e) {
                log.warn("Could not remove expired files: {}", e.getClass().getSimpleName());
            }
        }
    }
}
