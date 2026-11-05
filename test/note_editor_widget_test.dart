import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/main.dart';
import 'package:zhixing_compass/screens.dart';
import 'package:zhixing_compass/core/store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() {
    ffi.sqfliteFfiInit();
    databaseFactory = ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });

  testWidgets('source note editor saves a persistent note using real SQLite', (tester) async {
    SharedPreferences.setMockInitialValues({});
    late CompassStore store;
    late CompassEntry source;
    await tester.runAsync(() async {
      final dbPath = join(await getDatabasesPath(), 'zhixing_compass_v3.db');
      await deleteDatabase(dbPath);
      store = await CompassStore.open();
      source = await store.add(kind: 'source', title: '阅读测试书籍', body: '原文不变');
    });
    await tester.pumpWidget(CompassScope(store: store,
      child: MaterialApp(home: Builder(builder: (ctx) => Scaffold(
        body: TextButton(onPressed: () => openPage(ctx, SourceNoteEditorPage(sourceId: source.id)),
          child: const Text('打开笔记编辑')))))));
    await tester.tap(find.text('打开笔记编辑'));
    await tester.pumpAndSettle();
    final fields = find.byType(TextField);
    expect(fields, findsNWidgets(3));
    await tester.enterText(fields.at(0), '第 1 天读书笔记');
    await tester.enterText(fields.at(1), '第一章');
    await tester.enterText(fields.at(2), '今天想到了一个反例。');
    await tester.ensureVisible(find.text('保存笔记'));
    await tester.tap(find.text('保存笔记'));
    // SQFlite FFI works in a real isolate. Pumping in FakeAsync alone does not
    // advance that isolate, so allow it to complete via runAsync before settle.
    await tester.runAsync(() async {
      for (var i = 0; i < 100 && store.notesForSource(source.id).isEmpty; i++) {
        await Future<void>.delayed(const Duration(milliseconds: 50));
      }
    });
    await tester.pumpAndSettle();
    expect(store.notesForSource(source.id), hasLength(1));
    expect(store.notesForSource(source.id).single.title, '第 1 天读书笔记');
    expect(store.notesForSource(source.id).single.body, '今天想到了一个反例。');
    expect(store.byId(source.id)?.body, '原文不变');
    await tester.pumpWidget(const MaterialApp(home: SizedBox()));
    await tester.runAsync(() => store.closeForTests());
  });
}