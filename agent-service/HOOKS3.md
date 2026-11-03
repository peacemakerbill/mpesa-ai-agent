<div align="center">

<img src="../docs/assets/banner-hooks.svg" alt="" width="100%"/>

<br/>

<img src="../docs/assets/badges-hooks.svg" alt="Spring Boot agent-service, edits to existing files, new files are complete and copied in, package com.darajaagentx.agent." width="880"/>

<br/><br/>

Three small edits connect chat file storage to the agent-service.

<sub><a href="README.md">Back to agent-service</a> &nbsp;|&nbsp; <a href="../README.md">Project overview</a></sub>

<img src="../docs/assets/divider.svg" alt="" width="100%"/>

</div>

# Part 3 edits to existing agent-service files

New files (copy in): everything under `chat/media/`, and the replacement `chat/ChatMedia.java`. These use no JSON library, so they work with
Jackson 2 or 3. The package is `com.darajaagentx.agent`.

## 1. ChatMedia replaced

`ChatMedia` no longer takes `(StateStore, ObjectMapper)`. It takes a `MediaBlobStore` (and a retention setting). Its public methods
`put`, `get`, `exists` and `Found` are unchanged, and it gains `remove(phone, id)` and `storeKind()`.

Where tests build it by hand, for example `ChatChannelTest`:

```java
// before
private final ChatMedia media = new ChatMedia(state, TestSupport.mapper());
// after
private final ChatMedia media = new ChatMedia(new StateBlobStore(state));
```

(import `com.darajaagentx.agent.chat.media.StateBlobStore`.)

Old files that are in Redis under `chat:media:<id>` in the old JSON form are not read by the new code. They expire within 24 hours anyway.

## 2. ChatController: delete a file

Add next to the existing `download` method (imports `DeleteMapping`):

```java
/** The owner removes one of their files. */
@DeleteMapping("/media/{id}")
public ResponseEntity<Void> deleteMedia(@PathVariable String phone, @PathVariable String id) {
    return media.remove(Phones.normalize(phone), id)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
}
```

agentx-api already passes `DELETE /api/chat/media/{id}` through (new in this part).

## 3. application.yml

Add the `chat.media` block from `STORAGE.md`. With nothing set, behaviour is exactly what it was before (state store, 24 hours).
