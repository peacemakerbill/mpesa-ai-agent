import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:flutter/material.dart';

import 'app_state.dart';
import 'config.dart';
import 'models/deep_link.dart';
import 'screens/action_screen.dart';
import 'screens/home_shell.dart';
import 'screens/link_screen.dart';
import 'theme/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const CompanionApp());
}

class CompanionApp extends StatefulWidget {
  const CompanionApp({super.key});

  @override
  State<CompanionApp> createState() => _CompanionAppState();
}

class _CompanionAppState extends State<CompanionApp> {
  final AppState state = AppState();
  final GlobalKey<NavigatorState> navigator = GlobalKey<NavigatorState>();
  StreamSubscription<Uri>? _links;

  @override
  void initState() {
    super.initState();
    state.addListener(_openPending);
    _init();
  }

  Future<void> _init() async {
    await state.start();
    final appLinks = AppLinks();
    final initial = await appLinks.getInitialLink();
    if (initial != null) _handle(initial);
    _links = appLinks.uriLinkStream.listen(_handle);
  }

  void _handle(Uri uri) {
    final id = parseActionLink(uri, scheme: AppConfig.linkScheme);
    if (id != null) state.openLink(id);
  }

  void _openPending() {
    if (!state.linked || state.pendingActionId == null) return;
    final id = state.takePending();
    final nav = navigator.currentState;
    if (id == null || nav == null) return;
    nav.push(MaterialPageRoute<void>(builder: (_) => ActionScreen(state: state, actionId: id)));
  }

  @override
  void dispose() {
    _links?.cancel();
    state.removeListener(_openPending);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // The whole app listens, so a change of look repaints every screen at once.
    return ListenableBuilder(
      listenable: state,
      builder: (context, _) => MaterialApp(
        title: 'M-Pesa Assistant',
        navigatorKey: navigator,
        debugShowCheckedModeBanner: false,
        theme: AppTheme.build(state.theme),
        home: _home(),
      ),
    );
  }

  Widget _home() {
    if (!state.ready) return const _Splash();
    if (!state.linked) return LinkScreen(state: state);
    // A link that arrived before linking is opened right after, by _openPending via notifyListeners.
    WidgetsBinding.instance.addPostFrameCallback((_) => _openPending());
    return HomeShell(state: state);
  }
}

class _Splash extends StatelessWidget {
  const _Splash();

  @override
  Widget build(BuildContext context) => const Scaffold(body: Center(child: CircularProgressIndicator()));
}
