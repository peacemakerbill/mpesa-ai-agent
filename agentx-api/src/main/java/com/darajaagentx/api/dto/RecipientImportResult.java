package com.darajaagentx.api.dto;

import java.util.List;

/**
 * What uploading or pasting a list of addresses did.
 *
 * @param added        new addresses saved
 * @param alreadySaved addresses that were already on the list
 * @param invalid      lines that were not an address (the first ones are in [invalidSamples])
 * @param limitReached true when some addresses were left out because the list is full
 */
public record RecipientImportResult(int added, int alreadySaved, int invalid, List<String> invalidSamples, boolean limitReached,
                                    List<RecipientView> recipients) {
}
