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
      final save=find.text('保存本次内容 · 成功后清空输入框');
      if(save.evaluate().isNotEmpty) {
        final button=tester.widget<FilledButton>(find.ancestor(
          of:save,matching:find.byType(FilledButton)));
        if(button.onPressed!=null) break;
      }
    }
    final readyButton=tester.widget<FilledButton>(find.ancestor(
      of:find.text('保存本次内容 · 成功后清空输入框'),
      matching:find.byType(FilledButton)));
    expect(readyButton.onPressed,isNotNull,
      reason:'Private journal must finish decrypting before append is enabled');
    final fields=find.byType(TextField);
    expect(fields,findsNWidgets(2));
    final appendController=tester.widget<TextField>(fields.at(1)).controller!;
    expect(appendController.text,isEmpty);
    await tester.enterText(fields.at(1),'追加的新想法和今天的经历');
    await tester.pump();
    final save=find.text('保存本次内容 · 成功后清空输入框');
    await tester.ensureVisible(save);
    await tester.tap(save);
    // SQLite FFI is serviced by a real isolate and requires runAsync.
    await tester.runAsync(() async {
      for(var i=0;i<80;i++) {
        final saved=await store.privateJournalBody(entry.id);
        if(saved.contains('追加的新想法')) break;
        await Future<void>.delayed(const Duration(milliseconds:50));
      }
    });
    await tester.pumpAndSettle();
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
}
