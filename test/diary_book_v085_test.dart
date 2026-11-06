import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/core/store.dart';
import 'package:zhixing_compass/diary_book_v085.dart';
import 'package:zhixing_compass/diary_book_page_v085.dart';
import 'package:zhixing_compass/main.dart';

void main(){
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll((){
    ffi.sqfliteFfiInit();
    databaseFactory=ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  setUp(()async{
    SharedPreferences.setMockInitialValues({});
    await deleteDatabase(join(await getDatabasesPath(),'zhixing_compass_v3.db'));
  });
  tearDown(()async{
    await deleteDatabase(join(await getDatabasesPath(),'zhixing_compass_v3.db'));
  });

  test('book selection sorts chronology, excludes other kinds and preserves Unicode', ()async{
    final store=await CompassStore.open();
    final entry=await store.add(kind:'journal',title:'成长记录',body:'第一天的经历🙂\n第二天的反思');
    await store.add(kind:'source',title:'公开笔记',body:'不该进入日记书');
    final journals=DiaryBookService.sortEntries(store.items);
    expect(journals.length,1);
    expect(journals.single.id,entry.id);
    final pieces=DiaryBookService.splitBody('读书🙂观察'*600);
    expect(pieces.join(), '读书🙂观察'*600);
    expect(pieces.length,greaterThan(2));
    expect(DiaryBookService.formatDate(entry.createdAt),contains('年'));
    await store.closeForTests();
  });

  test('offline PDF export produces valid PDF and never changes private original', ()async{
    final store=await CompassStore.open();
    final a=await store.add(kind:'journal',title:'我的第一篇',body:'今天我学会了记录。',
      status:'私密');
    final b=await store.add(kind:'journal',title:'特别的一天',body:'回顾与感恩。\n下一段。',
      status:'私密');
    final pdf=await DiaryBookService.build(store:store,
      entries:[b,a],title:'写给未来的自己',preface:'这一年，我想留下真实的自己。');
    expect(pdf.length,greaterThan(3000));
    expect(latin1.decode(pdf.take(8).toList()),startsWith('%PDF-'));
    expect(await store.privateJournalBody(a.id),'今天我学会了记录。');
    expect(await store.privateJournalBody(b.id),'回顾与感恩。\n下一段。');
    expect(store.ofKind('journal').length,2);
    await store.closeForTests();
  });

  testWidgets('book editor exposes private selection and offline export button',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(()async{
      store=await CompassStore.open();
      await store.add(kind:'journal',title:'日记测试',body:'这是正文。',status:'私密');
    });
    await tester.pumpWidget(CompassScope(store:store,
      child:const MaterialApp(home:DiaryBookPage())));
    expect(find.text('制作我的日记书'),findsOneWidget);
    expect(find.text('全部'),findsOneWidget);
    expect(find.text('日记测试'),findsOneWidget);
    expect(find.text('生成并保存 PDF 日记书'),findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(()=>store.closeForTests());
  });
}
