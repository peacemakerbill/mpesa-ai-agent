package com.darajaagentx.api.dto;

import java.util.Map;

/**
 * The assistant's request to prepare a business operation. The phone is the signed-in customer's number from the chat,
 * never something the model chose.
 *
 * @param account        the business account by name, till, shortcode or id
 * @param type           an operation type, for example STK_PUSH or B2C
 * @param params         what the operation needs, see BusinessOperationService
 * @param source         how the details arrived: TEXT, VOICE, IMAGE, DOCUMENT or CONTACT
 * @param idempotencyKey the same key returns the same operation, so a retried model call does not send twice
 */
public record PrepareOperationRequest(String phone, String account, String type, Map<String, String> params, String source, String idempotencyKey) {
}
