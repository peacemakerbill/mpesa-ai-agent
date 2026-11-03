package com.darajaagentx.api.service;

/** Puts a message into a customer's chat. */
public interface ChatNotifier {

    void text(String phone, String text);

    void image(String phone, String pngBase64, String caption);
}
