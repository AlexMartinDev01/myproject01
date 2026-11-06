import 'dart:io';
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
    databaseFactory=ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  late String path;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    path=join(await getDatabasesPath(),'zhixing_compass_v3.db');
    await deleteDatabase(path);
  });
  tearDown(() async {
    await Future<void>.delayed(const Duration(milliseconds:35));
    await deleteDatabase(path);
  });

  testWidgets('save appends without overwriting, then clears only the new input', (tester) async {
    late CompassStore store;
    late CompassEntry entry;
    await tester.runAsync(() async {
      store=await CompassStore.open();
      entry=await store.add(kind:'journal',title:'我的日记',
        body:'已经保存的第一段',status:'私密');
    });
    await tester.pumpWidget(CompassScope(store:store,
      child:MaterialApp(home:JournalEditPage(id:entry.id))));
    // Allow the background SQLite/secure-storage isolate to complete the
    // initial async journal decrypt before FakeAsync settles the widget.
    // Loading an encrypted journal may use the OS secure storage and SQLite
    // isolate. Do not click the disabled save button before loading finishes.
    for(var i=0;i<20;i++) {
      await tester.runAsync(() => Future<void>.delayed(
        const Duration(milliseconds:150)));
      await tester.pumpAndSettle();
      final save=find.text('保存本次内容');
      if(save.evaluate().isNotEmpty) {
        final button=tester.widget<FilledButton>(find.ancestor(
          of:save,matching:find.byType(FilledButton)));
        if(button.onPressed!=null) break;
      }
    }
    final readyButton=tester.widget<FilledButton>(find.ancestor(
      of:find.text('保存本次内容'),
      matching:find.byType(FilledButton)));
    expect(readyButton.onPressed,isNotNull,
      reason:'Private journal must finish decrypting before append is enabled');
    final fields=find.byType(TextField);
    expect(fields,findsNWidgets(2));
    final appendController=tester.widget<TextField>(fields.at(1)).controller!;
    expect(appendController.text,isEmpty);
    await tester.enterText(fields.at(1),'追加的新想法和今天的经历');
    await tester.pump();
    final save=find.text('保存本次内容');
    await tester.ensureVisible(save);
    final button=tester.widget<FilledButton>(find.ancestor(
      of:save,matching:find.byType(FilledButton)));
    expect(button.onPressed,isNotNull);
    // Call the *actual button callback* in a real async zone. Unlike Android,
    // WidgetTester.tap operates in FakeAsync and freezes some cryptography
    // continuations awaiting the SQLite worker isolate.
    await tester.runAsync(() async {
      button.onPressed!();
      for(var i=0;i<100;i++) {
        final saved=await store.privateJournalBody(entry.id);
        if(saved.contains('追加的新想法')) break;
        await Future<void>.delayed(const Duration(milliseconds:50));
      }
    });
    // The attachment list can show an indeterminate loading indicator
    // while a real SQLite isolate completes. Do not wait for every animated
    // frame to settle; one pump is sufficient to inspect the saved controller.
    await tester.pump();
    final persisted=await tester.runAsync(() => store.privateJournalBody(entry.id));
    expect(persisted, '已经保存的第一段\n\n追加的新想法和今天的经历',
      reason:'Tapped Save must persist both existing and appended text');
    // The ListView may have scrolled the field offstage after tapping Save.
    expect(appendController.text,isEmpty,
      reason:'Append draft clears only after encrypted persistence');
    await tester.runAsync(() async {
      expect((await store.journalHistory(entry.id)).length,2);
    });
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });

  test('encrypted PDF attachment can be reopened with exact bytes', () async {
    final store=await CompassStore.open();
    final entry=await store.add(kind:'journal',title:'日记附件',
      body:'原有日记',status:'私密');
    final file=File(join(await getDatabasesPath(),'journal_attachment_sample.pdf'));
    final bytes=<int>[37,80,68,70,45,49,46,55,10,...List<int>.generate(800,(i)=>i%255)];
    await file.writeAsBytes(bytes);
    await store.addJournalAttachment(entry.id,file,'sample.pdf');
    final row=(await store.journalAttachments(entry.id)).single;
    final restored=await store.readJournalAttachmentBytes(entry.id,row['id'] as String);
    expect(restored.toList(),bytes);
    await store.removeJournalAttachment(entry.id,row['id'] as String);
    await expectLater(store.readJournalAttachmentBytes(entry.id,row['id'] as String),throwsStateError);
    await file.delete();
    await store.closeForTests();
  });
  testWidgets('saved journal opens read-first page with persistent append action',
      (tester) async {
    late CompassStore store;
    late CompassEntry entry;
    await tester.runAsync(() async {
      store = await CompassStore.open();
      entry = await store.add(kind: 'journal', title: '十一月读书日记',
        body: '这是此前加密保存的内容。', status: '私密');
    });
    await tester.pumpWidget(CompassScope(store: store,
      child: MaterialApp(home: JournalReadPage(id: entry.id))));
    expect(find.text('阅读私密日记'), findsOneWidget);
    expect(find.text('继续写 · 追加到这篇日记'), findsOneWidget);
    for (var i = 0; i < 15; i++) {
      await tester.runAsync(() => Future<void>.delayed(
        const Duration(milliseconds: 100)));
      await tester.pump();
      if (find.text('这是此前加密保存的内容。').evaluate().isNotEmpty) break;
    }
    expect(find.text('这是此前加密保存的内容。'), findsOneWidget);
    expect(find.text('保存本次内容'), findsNothing,
      reason: 'Reading a saved diary must not open the editor by default');
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });

  testWidgets('save action is in fixed bottom bar before long input scroll',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async { store = await CompassStore.open(); });
    await tester.pumpWidget(CompassScope(store: store,
      child: const MaterialApp(home: JournalEditPage())));
    await tester.pump();
    final save = find.text('保存本次内容');
    expect(save, findsOneWidget);
    expect(find.ancestor(of: save, matching: find.byType(FilledButton)),
      findsOneWidget);
    expect(find.textContaining('本次追加内容'), findsWidgets);
    // PageFrame's scrollable body and fixed save bar are siblings in Scaffold.
    final scaffold = tester.widget<Scaffold>(find.byType(Scaffold).first);
    expect(scaffold.bottomNavigationBar, isNotNull);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });

}
