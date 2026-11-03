package com.darajaagentx.agent.chat.media;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SigV4Test {

    /** The worked example "GET Object" with a Range header from the AWS Signature Version 4 documentation for S3. */
    @Test
    void matchesTheAwsDocumentationExample() {
        SigV4 signer = new SigV4("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "examplebucket.s3.amazonaws.com");
        headers.put("Range", "bytes=0-9");
        headers.put("x-amz-content-sha256", SigV4.EMPTY_SHA256);
        headers.put("x-amz-date", "20130524T000000Z");
        String auth = signer.authorization("GET", "/test.txt", "", headers, SigV4.EMPTY_SHA256, Instant.parse("2013-05-24T00:00:00Z"));
        assertThat(auth).isEqualTo("AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,"
                + " SignedHeaders=host;range;x-amz-content-sha256;x-amz-date,"
                + " Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41");
    }

    @Test
    void emptyPayloadHashIsTheKnownConstant() {
        assertThat(SigV4.sha256Hex(new byte[0])).isEqualTo(SigV4.EMPTY_SHA256);
    }
}
