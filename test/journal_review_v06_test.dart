import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/main.dart';
import 'package:zhixing_compass/core/store.dart';
import 'package:zhixing_compass/journal_review_v06.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() {
    ffi.sqfliteFfiInit();
    databaseFactory = ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await deleteDatabase(join(await getDatabasesPath(), 'zhixing_compass_v3.db'));
  });
  tearDown(() async {
    await deleteDatabase(join(await getDatabasesPath(), 'zhixing_compass_v3.db'));
  });

  testWidgets('journal review starts with no private excerpts or external AI action',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async { store = await CompassStore.open(); });
    await tester.pumpWidget(CompassScope(store: store,
      child: const MaterialApp(home: JournalReviewPage())));
    expect(find.text('每周小结'), findsOneWidget);
    expect(find.text('每月回顾'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('在本机查看正文摘录'), 250);
    expect(find.text('在本机查看正文摘录'), findsOneWidget);
    expect(find.textContaining('本期记录 0 篇'), findsOneWidget);
    expect(find.byType(SelectableText), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });

  testWidgets('switching weekly/monthly review remains local',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async { store = await CompassStore.open(); });
    await tester.pumpWidget(CompassScope(store: store,
      child: const MaterialApp(home: JournalReviewPage())));
    await tester.tap(find.text('每月回顾'));
    await tester.pumpAndSettle();
    expect(find.textContaining('这个月最重要的变化'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });
}
