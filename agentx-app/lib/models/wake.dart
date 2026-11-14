/// What the server says while its services start. A host that puts idle services to sleep wakes them on the first request.
class WakeStatus {
  const WakeStatus({required this.api, required this.agent, required this.ready});

  /// The sign-in and requests service. It answered, so this is true for any answer at all.
  final bool api;

  /// The assistant service, which keeps the chat. False while it is still waking.
  final bool agent;

  /// True when the app can start working.
  final bool ready;

  factory WakeStatus.fromJson(Map<String, dynamic> json) => WakeStatus(
        api: json['api'] == 'up',
        agent: json['agent'] == 'up',
        ready: json['ready'] == true,
      );
}
