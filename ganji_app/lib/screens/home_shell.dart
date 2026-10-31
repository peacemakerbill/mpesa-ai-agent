import 'package:flutter/material.dart';

import '../app_state.dart';
import '../chat/chat_controller.dart';
import 'actions_screen.dart';
import 'chat_screen.dart';

/// The signed in app: the chat with the assistant, and the list of requests to approve.
class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.state});

  final AppState state;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> with WidgetsBindingObserver {
  late final ChatController _chat = ChatController(api: widget.state.api, onSessionExpired: widget.state.sessionExpired);
  int _tab = 0;

  /// Changes each time the Requests tab opens, so the list is read again.
  int _requestsVersion = 0;
  bool _foreground = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _chat.start();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _chat.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState lifecycle) {
    _foreground = lifecycle == AppLifecycleState.resumed;
    _sync();
  }

  /// The chat is read only while it is on screen and the app is open.
  void _sync() {
    if (_foreground && _tab == 0) {
      _chat.start();
    } else {
      _chat.pause();
    }
  }

  void _select(int i) {
    if (i == _tab) return;
    setState(() {
      _tab = i;
      if (i == 1) _requestsVersion++;
    });
    _sync();
  }

  @override
  Widget build(BuildContext context) {
    final keyboardOpen = MediaQuery.viewInsetsOf(context).bottom > 0;
    return Scaffold(
      body: IndexedStack(index: _tab, children: [
        ChatScreen(state: widget.state, chat: _chat, device: widget.state.chatDevice, contacts: widget.state.contacts),
        ActionsScreen(key: ValueKey(_requestsVersion), state: widget.state),
      ]),
      // The bar steps aside while the keyboard is open, so the message box sits right above the keyboard.
      bottomNavigationBar: keyboardOpen
          ? null
          : NavigationBar(
              selectedIndex: _tab,
              onDestinationSelected: _select,
              destinations: const [
                NavigationDestination(icon: Icon(Icons.chat_bubble_outline), selectedIcon: Icon(Icons.chat_bubble), label: 'Chat'),
                NavigationDestination(icon: Icon(Icons.fact_check_outlined), selectedIcon: Icon(Icons.fact_check), label: 'Requests'),
              ],
            ),
    );
  }
}
