import 'package:flutter/services.dart';
import 'package:local_auth/local_auth.dart';

enum ApprovalResult { approvedBiometric, approvedDeviceLock, cancelled, notAvailable }

/// Asks the phone to confirm it is really the owner: fingerprint, face, or the phone's PIN, pattern or password.
/// The operating system does the check. We only learn yes or no. This is not the M-Pesa PIN.
class Approval {
  Approval([LocalAuthentication? auth]) : _auth = auth ?? LocalAuthentication();

  final LocalAuthentication _auth;

  Future<ApprovalResult> ask(String reason) async {
    try {
      if (!await _auth.isDeviceSupported()) return ApprovalResult.notAvailable;
      final ok = await _auth.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(biometricOnly: false, stickyAuth: true),
      );
      if (!ok) return ApprovalResult.cancelled;
      // The OS does not say which one was used. If biometrics are enrolled we record that, otherwise the device lock.
      final enrolled = await _auth.getAvailableBiometrics();
      return enrolled.isEmpty ? ApprovalResult.approvedDeviceLock : ApprovalResult.approvedBiometric;
    } on PlatformException {
      return ApprovalResult.notAvailable;
    }
  }
}
