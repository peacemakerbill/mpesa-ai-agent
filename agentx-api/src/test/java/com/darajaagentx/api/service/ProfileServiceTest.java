package com.darajaagentx.api.service;

import com.darajaagentx.api.dto.CodeRequestResponse;
import com.darajaagentx.api.dto.ProfileUpdate;
import com.darajaagentx.api.dto.ProfileView;
import com.darajaagentx.api.exception.TooManyRequestsException;
import com.darajaagentx.api.mail.MailDeliveryException;
import com.darajaagentx.api.model.ProfileStatus;
import com.darajaagentx.api.model.UserProfile;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Profile rules with in memory stores and a fake mail server. */
class ProfileServiceTest {

    static final String PHONE = "254712345678";

    private static ProfileUpdate details(String first, String middle, String last) {
        return new ProfileUpdate(first, middle, last, null, null, null, null, null, null, null, null, null);
    }

    private static byte[] png(int w, int h, boolean alpha) throws Exception {
        BufferedImage img = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, alpha ? 0x80FF0000 : 0x2040C0);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void anExistingCustomerGetsAnEmptyProfileOnFirstUse() {
        Fakes.Rig rig = new Fakes.Rig();
        ProfileView v = rig.service.view(PHONE);
        assertThat(v.phone()).isEqualTo(PHONE);
        assertThat(v.status()).isEqualTo("ACTIVE");
        assertThat(v.complete()).isFalse();
        assertThat(v.emailVerified()).isFalse();
        assertThat(rig.profiles.rows).hasSize(1);
        rig.service.view(PHONE);
        assertThat(rig.profiles.rows).hasSize(1);
    }

    @Test
    void namesAreCheckedAndTidied() {
        Fakes.Rig rig = new Fakes.Rig();
        ProfileView v = rig.service.update(PHONE, details("  amina ", " wanjiru  njeri ", "Otieno-Ochieng'"));
        assertThat(v.firstName()).isEqualTo("amina");
        assertThat(v.middleName()).isEqualTo("wanjiru njeri");
        assertThat(v.fullName()).isEqualTo("amina wanjiru njeri Otieno-Ochieng'");
        assertThat(v.initials()).isEqualTo("AO");
        assertThat(v.complete()).isTrue();

        assertThatThrownBy(() -> rig.service.update(PHONE, details("Am1na", null, null))).hasMessageContaining("letters");
        assertThatThrownBy(() -> rig.service.update(PHONE, details("", null, null))).hasMessageContaining("Enter your first name");
        assertThatThrownBy(() -> rig.service.update(PHONE, details(null, null, "x".repeat(61)))).hasMessageContaining("too long");
        // a middle name can be cleared, a first name can not
        assertThat(rig.service.update(PHONE, details(null, "", null)).middleName()).isNull();
        assertThat(rig.service.view(PHONE).firstName()).isEqualTo("amina");
    }

    @Test
    void leavingAFieldOutKeepsItAndAnEmptyTextClearsIt() {
        Fakes.Rig rig = new Fakes.Rig();
        rig.service.update(PHONE, new ProfileUpdate("Amina", null, "Otieno", " Woodvale Grove, 4th floor ", "Nairobi", "Nairobi", "Kenya", null, null, null, null, null));
        ProfileView v = rig.service.update(PHONE, new ProfileUpdate(null, null, null, null, "Westlands", null, null, null, null, null, null, null));
        assertThat(v.address()).isEqualTo("Woodvale Grove, 4th floor");
        assertThat(v.city()).isEqualTo("Westlands");
        assertThat(v.county()).isEqualTo("Nairobi");
        v = rig.service.update(PHONE, new ProfileUpdate(null, null, null, "", null, null, null, null, null, null, null, null));
        assertThat(v.address()).isNull();
        assertThatThrownBy(() -> rig.service.update(PHONE, new ProfileUpdate(null, null, null, "x".repeat(201), null, null, null, null, null, null, null, null)))
                .hasMessageContaining("address is too long");
        assertThatThrownBy(() -> rig.service.update(PHONE, new ProfileUpdate(null, null, null, "line\u0000break", null, null, null, null, null, null, null, null)))
                .hasMessageContaining("not allowed");
    }

    @Test
    void locationNeedsBothValuesOnTheMapAndCanBeCleared() {
        Fakes.Rig rig = new Fakes.Rig();
        ProfileView v = rig.service.update(PHONE, new ProfileUpdate(null, null, null, null, null, null, null, -1.2921004, 36.8219462, null, null, null));
        assertThat(v.latitude()).isEqualTo(-1.2921);
        assertThat(v.longitude()).isEqualTo(36.821946);
        assertThatThrownBy(() -> rig.service.update(PHONE, new ProfileUpdate(null, null, null, null, null, null, null, 95.0, 36.0, null, null, null)))
                .hasMessageContaining("not on the map");
        assertThatThrownBy(() -> rig.service.update(PHONE, new ProfileUpdate(null, null, null, null, null, null, null, 1.0, null, null, null, null)))
                .hasMessageContaining("both");
        assertThat(rig.service.view(PHONE).latitude()).isEqualTo(-1.2921);
        v = rig.service.update(PHONE, new ProfileUpdate(null, null, null, null, null, null, null, null, null, true, null, null));
        assertThat(v.latitude()).isNull();
        assertThat(v.longitude()).isNull();
    }

    @Test
    void anEmailBecomesTheCustomersOnlyAfterTheCodeIsTypedBack() {
        Fakes.Rig rig = new Fakes.Rig();
        rig.service.update(PHONE, details("Amina", null, "Otieno"));
        CodeRequestResponse sent = rig.service.requestEmailCode(PHONE, " Amina@Example.COM ");
        assertThat(sent.devCode()).isNull();
        assertThat(rig.mailbox.sent).hasSize(1);
        assertThat(rig.mailbox.sent.get(0).to()).containsExactly("amina@example.com");
        String code = rig.mailbox.lastCode();
        assertThat(code).isNotNull();

        ProfileView waiting = rig.service.view(PHONE);
        assertThat(waiting.email()).isNull();
        assertThat(waiting.emailVerified()).isFalse();
        assertThat(waiting.pendingEmail()).isEqualTo("amina@example.com");

        ProfileView done = rig.service.verifyEmail(PHONE, code);
        assertThat(done.email()).isEqualTo("amina@example.com");
        assertThat(done.emailVerified()).isTrue();
        assertThat(done.pendingEmail()).isNull();
        // the first verified email brings a welcome
        assertThat(rig.mailbox.sent).hasSize(2);
        assertThat(rig.mailbox.sent.get(1).subject()).contains("Welcome");
        // a code works once
        assertThatThrownBy(() -> rig.service.verifyEmail(PHONE, code)).hasMessageContaining("not valid");
    }

    @Test
    void theCodeIsNeverStoredAsItIsAndAWrongCodeCountsAgainstTheLimit() {
        Fakes.Rig rig = new Fakes.Rig();
        rig.service.requestEmailCode(PHONE, "a@example.com");
        String code = rig.mailbox.lastCode();
        UserProfile p = rig.profiles.repo().findByPhone(PHONE).orElseThrow();
        assertThat(p.getEmailCodeHash()).doesNotContain(code);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> rig.service.verifyEmail(PHONE, wrong)).hasMessageContaining("not valid");
        }
        // after five wrong tries even the right code is refused and a new one is needed
        assertThatThrownBy(() -> rig.service.verifyEmail(PHONE, code)).hasMessageContaining("not valid");
        assertThat(rig.service.view(PHONE).pendingEmail()).isNull();
    }

    @Test
    void aCodeExpires() {
        Fakes.Rig rig = new Fakes.Rig();
        rig.service.requestEmailCode(PHONE, "a@example.com");
        String code = rig.mailbox.lastCode();
        rig.clock.advance(Duration.ofMinutes(11));
        assertThatThrownBy(() -> rig.service.verifyEmail(PHONE, code)).hasMessageContaining("not valid or has expired");
    }

    @Test
    void aNewAddressNeverReplacesAGoodOneUntilVerified() {
        Fakes.Rig rig = new Fakes.Rig();
        rig.verified(PHONE, "old@example.com");
        rig.clock.advance(Duration.ofMinutes(1));
        rig.service.requestEmailCode(PHONE, "new@example.com");
        ProfileView v = rig.service.view(PHONE);
        assertThat(v.email()).isEqualTo("old@example.com");
        assertThat(v.emailVerified()).isTrue();
        assertThat(v.pendingEmail()).isEqualTo("new@example.com");
        rig.service.verifyEmail(PHONE, rig.mailbox.lastCode());
        assertThat(rig.service.view(PHONE).email()).isEqualTo("new@example.com");
    }

    @Test
    void asksAreRefusedForBadAddressesTheSameAddressAndTooManyCodes() {
        Fakes.Rig rig = new Fakes.Rig();
        assertThatThrownBy(() -> rig.service.requestEmailCode(PHONE, "not-an-email")).hasMessageContaining("not a valid email");
        assertThatThrownBy(() -> rig.service.requestEmailCode(PHONE, "a@b.co\r\nBcc: x@y.com")).hasMessageContaining("not a valid email");
        rig.verified(PHONE, "me@example.com");
        rig.clock.advance(Duration.ofMinutes(1));
        assertThatThrownBy(() -> rig.service.requestEmailCode(PHONE, "ME@example.com")).hasMessageContaining("already verified");

        Fakes.Rig busy = new Fakes.Rig();
        busy.service.requestEmailCode(PHONE, "a@example.com");
        assertThatThrownBy(() -> busy.service.requestEmailCode(PHONE, "a@example.com")).isInstanceOf(TooManyRequestsException.class);
        for (int i = 0; i < 4; i++) {
            busy.clock.advance(Duration.ofMinutes(1));
            busy.service.requestEmailCode(PHONE, "a" + i + "@example.com");
        }
        busy.clock.advance(Duration.ofMinutes(1));
        assertThatThrownBy(() -> busy.service.requestEmailCode(PHONE, "z@example.com")).hasMessageContaining("Too many codes");
    }

    @Test
    void whenTheMailServerRefusesNothingIsLeftWaiting() {
        Fakes.Rig rig = new Fakes.Rig();
        rig.verified(PHONE, "old@example.com");
        rig.clock.advance(Duration.ofMinutes(1));
        rig.mailbox.fail = true;
        assertThatThrownBy(() -> rig.service.requestEmailCode(PHONE, "new@example.com")).isInstanceOf(MailDeliveryException.class);
        ProfileView v = rig.service.view(PHONE);
        assertThat(v.pendingEmail()).isNull();
        assertThat(v.email()).isEqualTo("old@example.com");
        assertThat(rig.logs.rows.get(rig.logs.rows.size() - 1).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void aTestConsoleCanReadTheCodeOnlyWhenNothingReallyLeavesTheServer() {
        // dev code delivery and the log mail transport: the console gets the code in the answer
        Fakes.Rig dev = new Fakes.Rig(Fakes.mailProps(), true, false);
        CodeRequestResponse r = dev.service.requestEmailCode(PHONE, "a@example.com");
        assertThat(r.devCode()).isEqualTo(dev.mailbox.lastCode());
        // a real mail server: never handed back, even in dev code delivery
        Fakes.Rig real = new Fakes.Rig(Fakes.mailProps(), true, true);
        assertThat(real.service.requestEmailCode(PHONE, "a@example.com").devCode()).isNull();
        // SMS or log code delivery: never handed back
        Fakes.Rig sms = new Fakes.Rig(Fakes.mailProps(), false, false);
        assertThat(sms.service.requestEmailCode(PHONE, "a@example.com").devCode()).isNull();
    }

    @Test
    void removingTheEmailStopsNoticesAndReports() {
        Fakes.Rig rig = new Fakes.Rig();
        rig.verified(PHONE, "me@example.com");
        ProfileView v = rig.service.removeEmail(PHONE);
        assertThat(v.email()).isNull();
        assertThat(v.emailVerified()).isFalse();
        assertThat(rig.service.verifiedEmail(PHONE).isPresent()).isFalse();
    }

    @Test
    void aPictureIsCheckedShrunkAndKeptInTheFileStore() throws Exception {
        Fakes.Rig rig = new Fakes.Rig();
        ProfileView v = rig.service.setPicture(PHONE, png(1600, 900, false));
        assertThat(v.hasPicture()).isTrue();
        assertThat(v.pictureVersion() > 0).isTrue();
        assertThat(rig.files.files).hasSize(1);
        String key = rig.files.files.keySet().iterator().next();
        assertThat(key).startsWith("profile-pictures/");
        assertThat(key).endsWith(".jpg");
        BufferedImage stored = ImageIO.read(new java.io.ByteArrayInputStream(rig.files.files.get(key)));
        assertThat(stored.getWidth()).isEqualTo(512);
        assertThat(stored.getHeight()).isEqualTo(288);
        assertThat(rig.service.picture(PHONE).orElseThrow().type()).isEqualTo("image/jpeg");

        // a new picture replaces the old file
        rig.clock.advance(Duration.ofSeconds(5));
        rig.service.setPicture(PHONE, png(100, 100, true));
        assertThat(rig.files.files).hasSize(1);
        assertThat(rig.files.files.keySet().iterator().next()).endsWith(".png");
        assertThat(rig.service.picture(PHONE).orElseThrow().type()).isEqualTo("image/png");

        ProfileView gone = rig.service.removePicture(PHONE);
        assertThat(gone.hasPicture()).isFalse();
        assertThat(rig.files.files).isEmpty();
        assertThat(rig.service.picture(PHONE).isPresent()).isFalse();
    }

    @Test
    void aFileThatIsNotAPictureOrIsTooBigIsRefused() throws Exception {
        Fakes.Rig rig = new Fakes.Rig();
        assertThatThrownBy(() -> rig.service.setPicture(PHONE, "<html><script>alert(1)</script></html>".getBytes())).hasMessageContaining("PNG or JPEG");
        assertThatThrownBy(() -> rig.service.setPicture(PHONE, new byte[0])).hasMessageContaining("empty");
        assertThatThrownBy(() -> rig.service.setPicture(PHONE, new byte[3_145_729])).hasMessageContaining("too large");
        byte[] fake = png(10, 10, false);
        fake[fake.length / 2] ^= 0x55;
        fake = java.util.Arrays.copyOf(fake, 40);   // a PNG header followed by nothing
        byte[] truncated = fake;
        assertThatThrownBy(() -> rig.service.setPicture(PHONE, truncated)).hasMessageContaining("picture");
        assertThat(rig.files.files).isEmpty();
    }

    @Test
    void beingSignedInActivatesAHalfFinishedSignUp() {
        Fakes.Rig rig = new Fakes.Rig();
        UserProfile pending = new UserProfile(PHONE);
        pending.setStatus(ProfileStatus.PENDING);
        pending.setFirstName("Amina");
        rig.profiles.repo().save(pending);
        assertThat(rig.service.view(PHONE).status()).isEqualTo("ACTIVE");
    }

    @Test
    void signInAndPaymentNoticesGoOnlyToAVerifiedAddressAndRespectTheSwitches() {
        Fakes.Rig rig = new Fakes.Rig();
        rig.mailer.signedIn(PHONE, "Pixel 8");
        assertThat(rig.mailbox.sent).isEmpty();               // no profile at all
        rig.service.getOrCreate(PHONE);
        rig.mailer.signedIn(PHONE, "Pixel 8");
        assertThat(rig.mailbox.sent).isEmpty();               // no verified email

        rig.verified(PHONE, "me@example.com");
        rig.mailer.signedIn(PHONE, "Pixel 8 <script>");
        assertThat(rig.mailbox.sent).hasSize(1);
        assertThat(rig.mailbox.sent.get(0).to()).containsExactly("me@example.com");
        assertThat(rig.mailbox.sent.get(0).html()).doesNotContain("<script>");
        assertThat(rig.mailbox.sent.get(0).html()).contains("&lt;script&gt;");
        // one notice per half hour
        rig.mailer.signedIn(PHONE, "Pixel 8");
        assertThat(rig.mailbox.sent).hasSize(1);
        rig.clock.advance(Duration.ofMinutes(31));
        rig.mailer.signedIn(PHONE, "Pixel 8");
        assertThat(rig.mailbox.sent).hasSize(2);

        rig.mailer.operationFinished(PHONE, "SUCCEEDED", "Pay out to a phone (B2C)", "KES 500 to 0712 345 678", "Sent.", "QK12345678");
        assertThat(rig.mailbox.sent).hasSize(3);
        assertThat(rig.mailbox.sent.get(2).subject()).contains("done");
        assertThat(rig.mailbox.sent.get(2).text()).contains("QK12345678");
        rig.mailer.operationFinished(PHONE, "DECLINED", "Pay out to a phone (B2C)", "x", null, null);
        rig.mailer.operationFinished(PHONE, "EXPIRED", "Pay out to a phone (B2C)", "x", null, null);
        assertThat(rig.mailbox.sent).hasSize(3);
        rig.mailer.operationFinished(PHONE, "FAILED", "STK Push", "KES 10", "Insufficient funds", null);
        assertThat(rig.mailbox.sent).hasSize(4);
        assertThat(rig.mailbox.sent.get(3).subject()).contains("failed");

        // the customer's own switches
        rig.service.update(PHONE, new ProfileUpdate(null, null, null, null, null, null, null, null, null, null, false, false));
        rig.clock.advance(Duration.ofHours(2));
        rig.mailer.signedIn(PHONE, "Pixel 8");
        rig.mailer.paymentDone(PHONE, "Payment", "Send KES 100", "QK1");
        assertThat(rig.mailbox.sent).hasSize(4);
    }

    @Test
    void everyEmailIsWrittenToTheLogWithoutItsBody() {
        Fakes.Rig rig = new Fakes.Rig();
        rig.service.requestEmailCode(PHONE, "a@example.com");
        assertThat(rig.logs.rows).hasSize(1);
        assertThat(rig.logs.rows.get(0).getKind()).isEqualTo("EMAIL_CODE");
        assertThat(rig.logs.rows.get(0).getStatus()).isEqualTo("SENT");
        assertThat(rig.logs.rows.get(0).getRecipients()).isEqualTo("a@example.com");
        assertThat(rig.logs.rows.get(0).getSubject()).doesNotContain(rig.mailbox.lastCode());
        Fakes.Rig quiet = new Fakes.Rig();
        quiet.mailbox.delivers = false;
        quiet.service.requestEmailCode(PHONE, "a@example.com");
        assertThat(quiet.logs.rows.get(0).getStatus()).isEqualTo("LOGGED");
    }
}