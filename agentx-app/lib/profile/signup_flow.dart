import '../app_state.dart';

/// Finishes creating an account: checks the SMS code, keeps the session on this phone and moves the app to the signed in state.
///
/// The server answers exactly like a sign in, so the same session storage is used. [AppState.start] then reads that session
/// back, which switches the app from the sign in page to the home page.
Future<void> finishSignup(AppState state, {required String phone, required String code}) async {
  final result = await state.api.signupVerify(
    phone: phone,
    code: code,
    deviceId: await state.store.deviceId(),
    deviceName: 'Phone',
  );
  await state.store.saveSession(token: result.token, phone: result.phone, expiresAt: result.expiresAt);
  await state.start();
}

/// True for something that looks like an email address. The server makes the real check, this only catches typing slips.
bool looksLikeEmail(String value) => RegExp(r'^[^\s@,;<>]+@[^\s@,;<>]+\.[^\s@,;<>]{2,}$').hasMatch(value.trim());

/// The digits of a phone number as typed.
int phoneDigits(String value) => value.replaceAll(RegExp(r'\D'), '').length;
