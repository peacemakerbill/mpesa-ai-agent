import 'package:flutter/material.dart';

import '../app_state.dart';
import '../chat/chat_controller.dart';
import '../layout/responsive.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';
import 'action_screen.dart';
import 'actions_screen.dart';
import 'chat_screen.dart';

/// The signed in app: the chat with the assistant, and the list of requests to approve.
///
/// On a phone these are two tabs under a bottom bar. On a wider window the bar becomes a side rail, and on a big tablet or a
/// desktop window the chat and the requests sit side by side, so a request the assistant prepares opens next to the conversation.
class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.state});

  final AppState state;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> with WidgetsBindingObserver {
  late final ChatController _chat = ChatController(api: widget.state.api, onSessionExpired: widget.state.sessionExpired);

  /// Keeps the typed message and the attachment tray when the window changes shape and the chat moves.
  final GlobalKey _chatKey = GlobalKey();

  /// The navigator inside the right pane of the two pane layout.
  final GlobalKey<NavigatorState> _paneNav = GlobalKey<NavigatorState>();

  /// Counts up whenever the list of requests should be read again.
  final ValueNotifier<int> _requests = ValueNotifier<int>(0);

  int _tab = 0;
  bool _foreground = true;
  bool _chatVisible = true;

  /// The newest chat message that mentions a request, so a new one refreshes the list.
  int _requestMarker = -1;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _chat.addListener(_onChat);
    _chat.start();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _chat.removeListener(_onChat);
    _chat.dispose();
    _requests.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState lifecycle) {
    _foreground = lifecycle == AppLifecycleState.resumed;
    _sync();
  }

  void _onChat() {
    if (!_chat.loaded) return;
    var marker = 0;
    for (final m in _chat.messages) {
      if (m.requestId != null && m.id > marker) marker = m.id;
    }
    if (_requestMarker == -1) {
      _requestMarker = marker;
      return;
    }
    if (marker > _requestMarker) {
      _requestMarker = marker;
      _requests.value++;
    }
  }

  /// The chat is read only while it is on screen and the app is open.
  void _sync() {
    if (_foreground && _chatVisible) {
      _chat.start();
    } else {
      _chat.pause();
    }
  }

  void _select(int i) {
    if (i == _tab) return;
    setState(() => _tab = i);
    if (i == 1) _requests.value++;
  }

  void _openInPane(String id) {
    final nav = _paneNav.currentState;
    if (nav == null) return;
    nav.popUntil((r) => r.isFirst);
    nav.push(MaterialPageRoute<void>(builder: (_) => ActionScreen(state: widget.state, actionId: id)));
  }

  @override
  Widget build(BuildContext context) {
    final size = context.screenSize;
    final chatVisible = size == ScreenSize.expanded || _tab == 0;
    if (chatVisible != _chatVisible) {
      _chatVisible = chatVisible;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _sync();
      });
    }

    final chat = ChatScreen(key: _chatKey, state: widget.state, chat: _chat, device: widget.state.chatDevice, contacts: widget.state.contacts);

    switch (size) {
      case ScreenSize.compact:
        return RequestPane(open: null, child: _compact(context, chat));
      case ScreenSize.medium:
        return RequestPane(open: null, child: _medium(context, chat));
      case ScreenSize.expanded:
        return RequestPane(open: _openInPane, child: _expanded(context, chat));
    }
  }

  Widget _requestsScreen() => ActionsScreen(state: widget.state, refresh: _requests);

  /// A phone: two tabs and a bottom bar.
  Widget _compact(BuildContext context, Widget chat) {
    final keyboardOpen = MediaQuery.viewInsetsOf(context).bottom > 0;
    return Scaffold(
      body: IndexedStack(index: _tab, children: [chat, _requestsScreen()]),
      // The bar steps aside while the keyboard is open, so the message box sits right above the keyboard.
      bottomNavigationBar: keyboardOpen
          ? null
          : NavigationBar(
              selectedIndex: _tab,
              onDestinationSelected: _select,
              destinations: const [
                NavigationDestination(icon: Icon(Icons.chat_bubble_outline), selectedIcon: Icon(Icons.chat_bubble), label: 'Chat'),
                NavigationDestination(icon: Icon(Icons.confirmation_number_outlined), selectedIcon: Icon(Icons.confirmation_number), label: 'Requests'),
              ],
            ),
    );
  }

  /// A small tablet or a phone on its side: a rail on the left and one pane.
  Widget _medium(BuildContext context, Widget chat) {
    final t = context.tk;
    return Scaffold(
      body: Row(children: [
        NavigationRail(
          selectedIndex: _tab,
          onDestinationSelected: _select,
          labelType: NavigationRailLabelType.all,
          leading: const Padding(padding: EdgeInsets.only(top: 14, bottom: 18), child: BrandMark(size: 34)),
          destinations: const [
            NavigationRailDestination(icon: Icon(Icons.chat_bubble_outline), selectedIcon: Icon(Icons.chat_bubble), label: Text('Chat')),
            NavigationRailDestination(icon: Icon(Icons.confirmation_number_outlined), selectedIcon: Icon(Icons.confirmation_number), label: Text('Requests')),
          ],
        ),
        VerticalDivider(width: 1, thickness: 1, color: t.p.line),
        Expanded(child: IndexedStack(index: _tab, children: [chat, _requestsScreen()])),
      ]),
    );
  }

  /// A big tablet or a desktop window: the chat on the left, the requests on the right.
  Widget _expanded(BuildContext context, Widget chat) {
    final t = context.tk;
    final width = MediaQuery.sizeOf(context).width;
    final pane = (width * 0.36).clamp(380.0, 480.0).toDouble();
    return Scaffold(
      body: Row(children: [
        Expanded(child: chat),
        VerticalDivider(width: 1, thickness: 1, color: t.p.line),
        SizedBox(
          width: pane,
          child: Navigator(
            key: _paneNav,
            onGenerateRoute: (settings) => MaterialPageRoute<void>(settings: settings, builder: (_) => _requestsScreen()),
          ),
        ),
      ]),
    );
  }
}
