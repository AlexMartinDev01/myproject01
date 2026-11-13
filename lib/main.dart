import 'dart:async';
import 'package:flutter/material.dart';
import 'package:receive_sharing_intent_plus/receive_sharing_intent_plus.dart';
import 'core/store.dart';
import 'screens.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final store = await CompassStore.open();
  runApp(CompassApp(store: store));
}

const grape = Color(0xFF7E5B98);
const ink = Color(0xFF352943);
const plum = Color(0xFF9E718F);
const pastel = Color(0xFFF7F2F9);

class CompassScope extends InheritedNotifier<CompassStore> {
  const CompassScope({super.key, required CompassStore store, required super.child})
      : super(notifier: store);
  static CompassStore of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<CompassScope>()!.notifier!;
}

class CompassApp extends StatefulWidget {
  final CompassStore store;
  const CompassApp({super.key, required this.store});
  @override
  State<CompassApp> createState() => _CompassAppState();
}

class _CompassAppState extends State<CompassApp> {
  Future<void> startFirstCapture() async {
    // Finish onboarding into the home dashboard. Recording is available there
    // immediately, but users are not forced into a multi-field capture form.
    await widget.store.setOnboarded();
  }
  @override
  Widget build(BuildContext context) => CompassScope(store: widget.store,
    child: MaterialApp(
      title: '知行罗盘', debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: grape, brightness: Brightness.light,
          surface: const Color(0xFFFFFBFE)),
        scaffoldBackgroundColor: const Color(0xFFFBF9FC),
        visualDensity: VisualDensity.standard,
        navigationBarTheme: NavigationBarThemeData(
          height: 72,
          backgroundColor: Colors.white,
          indicatorColor: const Color(0xFFEDE4F4),
          labelTextStyle: WidgetStateProperty.resolveWith((states) => TextStyle(
            fontSize: 11, fontWeight: states.contains(WidgetState.selected)
              ? FontWeight.w700 : FontWeight.w500, color: ink))),
        appBarTheme: const AppBarTheme(backgroundColor: Color(0xFFF9F5FA),
          foregroundColor: ink, centerTitle: false),
        filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(
          backgroundColor: grape, foregroundColor: Colors.white,
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 14))),
        cardTheme: CardThemeData(color: Colors.white, elevation: 0,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20))),
        outlinedButtonTheme: OutlinedButtonThemeData(style: OutlinedButton.styleFrom(
          foregroundColor: grape, minimumSize: const Size(48, 44),
          side: const BorderSide(color: Color(0xFFDBCCEA)),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)))),
        snackBarTheme: SnackBarThemeData(
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
        inputDecorationTheme: InputDecorationTheme(
          filled: true, fillColor: Colors.white,
          border: OutlineInputBorder(borderSide: const BorderSide(color: Color(0xFFEADDEE)),
            borderRadius: BorderRadius.circular(16)),
          enabledBorder: OutlineInputBorder(borderSide: const BorderSide(color: Color(0xFFEADDEE)),
            borderRadius: BorderRadius.circular(16)),
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 15)),
      ),
      home: AnimatedBuilder(animation: widget.store, builder: (_, __) =>
        widget.store.onboarded
          ? const HomeShell()
          : WelcomePage(onStart: startFirstCapture)),
    ));
}

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});
  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int selected = 0;
  StreamSubscription<String>? _shareSubscription;
  String? _lastSharedText;
  DateTime? _lastSharedAt;
  bool _openingShare = false;

  @override
  void initState() {
    super.initState();
    _shareSubscription = ReceiveSharingIntentPlus.getTextStream().listen(
      _acceptShare, onError: (Object error) {
        if (mounted) notice(context, '无法接收分享，请复制链接后手动记录');
      });
    ReceiveSharingIntentPlus.getInitialText().then((value) {
      if (value != null) _acceptShare(value);
    }, onError: (Object _) {
      // Sharing is optional: users can still write notes while offline.
    });
  }

  void _acceptShare(String content) {
    final text = content.trim();
    if (!mounted || text.isEmpty || _openingShare) return;
    final now = DateTime.now();
    if (_lastSharedText == text && _lastSharedAt != null &&
        now.difference(_lastSharedAt!).inSeconds < 5) return;
    _lastSharedText = text;
    _lastSharedAt = now;
    _openingShare = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) { _openingShare = false; return; }
      try {
        await openPage(context, QuickCapturePage(initialText: text));
      } finally {
        _openingShare = false;
      }
    });
  }

  @override
  void dispose() {
    _shareSubscription?.cancel();
    super.dispose();
  }
  static const screens = <Widget>[
    TodayPage(), InboxPage(), KnowledgePage(), JournalPage(), WorkspacePage(),
  ];
  @override
  Widget build(BuildContext context) => Scaffold(
    body: IndexedStack(index: selected, children: screens),
    bottomNavigationBar: NavigationBar(
      backgroundColor: Colors.white, indicatorColor: const Color(0xFFECE1F4),
      selectedIndex: selected,
      onDestinationSelected: (i) => setState(() => selected = i),
      destinations: const [
        NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: '首页'),
        NavigationDestination(icon: Icon(Icons.inbox_outlined), selectedIcon: Icon(Icons.inbox), label: '收藏'),
        NavigationDestination(icon: Icon(Icons.menu_book_outlined), selectedIcon: Icon(Icons.menu_book), label: '知识'),
        NavigationDestination(icon: Icon(Icons.edit_note_outlined), selectedIcon: Icon(Icons.edit_note), label: '日记'),
        NavigationDestination(icon: Icon(Icons.apps_outlined), selectedIcon: Icon(Icons.apps), label: '更多'),
      ],
    ),
  );
}

Future<T?> openPage<T>(BuildContext context, Widget page) =>
    Navigator.of(context).push<T>(MaterialPageRoute(builder: (_) => page));

void notice(BuildContext context, String message) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
}

class PageFrame extends StatelessWidget {
  final String title;
  final String subtitle;
  final List<Widget> children;
  final List<Widget> actions;
  final Widget? bottomBar;
  const PageFrame({super.key, required this.title, this.subtitle = '',
    required this.children, this.actions = const [], this.bottomBar});
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
      actions: actions),
    bottomNavigationBar: bottomBar,
    body: SafeArea(child: ListView(
      padding: const EdgeInsets.fromLTRB(18, 8, 18, 30),
      children: [if (subtitle.isNotEmpty) Padding(
        padding: const EdgeInsets.only(bottom: 18),
        child: Text(subtitle, style: const TextStyle(color: Color(0xFF8C8090)))), ...children],
    )),
  );
}

class SoftCard extends StatelessWidget {
  final Widget child;
  final EdgeInsets padding;
  final Color? color;
  const SoftCard({super.key, required this.child, this.padding = const EdgeInsets.all(18), this.color});
  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    margin: const EdgeInsets.only(bottom: 12),
    padding: padding,
    decoration: BoxDecoration(color: color ?? Colors.white,
      borderRadius: BorderRadius.circular(18),
      border: Border.all(color: const Color(0xFFECE7F1))),
    child: Material(type: MaterialType.transparency, child: child),
  );
}

class SectionHeading extends StatelessWidget {
  final String title;
  final Widget? action;
  const SectionHeading(this.title, {super.key, this.action});
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(2, 15, 2, 10),
    child: Row(children: [Expanded(child: Text(title,
      style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: ink))),
      if (action != null) action!]),
  );
}

class SmallChip extends StatelessWidget {
  final String label;
  const SmallChip(this.label, {super.key});
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
    decoration: BoxDecoration(color: const Color(0xFFF0E6F7), borderRadius: BorderRadius.circular(15)),
    child: Text(label, style: const TextStyle(fontSize: 11, color: grape)));
}

class NavCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  const NavCard({super.key, required this.icon, required this.title,
    this.subtitle = '', required this.onTap});
  @override
  Widget build(BuildContext context) => SoftCard(
    padding: EdgeInsets.zero,
    child: ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      leading: Container(padding: const EdgeInsets.all(10), decoration: BoxDecoration(
        color: const Color(0xFFF2EBF8), borderRadius: BorderRadius.circular(12)),
        child: Icon(icon, color: grape)),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: subtitle.isNotEmpty ? Text(subtitle) : null,
      trailing: const Icon(Icons.chevron_right, color: Color(0xFFA296AC)), onTap: onTap));
}
