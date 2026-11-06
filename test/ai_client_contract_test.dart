import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/core/ai.dart';
import 'package:zhixing_compass/core/store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() {
    ffi.sqfliteFfiInit();
    databaseFactory = ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  late CompassStore store;
  late String dbPath;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    dbPath = join(await getDatabasesPath(), 'zhixing_compass_v3.db');
    await deleteDatabase(dbPath);
    store = await CompassStore.open();
    await store.setConnection('https://gateway.example/', 'client-test-token');
  });
  tearDown(() async {
    await store.closeForTests();
    await Future<void>.delayed(const Duration(milliseconds: 40));
    await deleteDatabase(dbPath);
  });

  test('phone health request includes device token and decodes model state', () async {
    final requests = <http.Request>[];
    final ai = AiClient(store, transport: MockClient((req) async {
      requests.add(req);
      return http.Response(jsonEncode({
        'ok': true, 'provider_configured': true, 'model': 'deepseek-flash'
      }), 200, headers: {'content-type': 'application/json'});
    }));
    final health = await ai.health();
    expect(health.connected, isTrue);
    expect(health.providerConfigured, isTrue);
    expect(health.model, 'deepseek-flash');
    expect(requests.single.url.toString(), 'https://gateway.example/health');
    expect(requests.single.headers['X-Device-Token'], 'client-test-token');
  });

  test('phone sends prior turns and retrieved source evidence then renders response', () async {
    final source = await store.add(kind: 'source', title: '职业资料',
      body: '职业规划的真实来源片段', status: '已整理');
    Map<String, dynamic>? captured;
    final ai = AiClient(store, transport: MockClient((req) async {
      expect(req.url.path, '/v1/chat');
      expect(req.headers['X-Device-Token'], 'client-test-token');
      captured = jsonDecode(req.body) as Map<String, dynamic>;
      return http.Response(jsonEncode({
        'answer':'## 结论\n建议先进行小规模验证。',
        'model':'deepseek-flash',
        'source_ids':[source.id],
      }), 200, headers: {'content-type': 'application/json'});
    }));
    final result = await ai.ask('那么现在应该怎么做？', mode: 'decision',
      history: [
        {'role': 'user','content':'我打算尝试自由职业。'},
        {'role': 'assistant','content':'先评估现金流。'},
      ], evidence: [source]);
    expect(result.answer, contains('建议先进行'));
    expect(result.model, 'deepseek-flash');
    expect(result.sourceIds, [source.id]);
    expect(captured!['question'], '那么现在应该怎么做？');
    expect((captured!['history'] as List).length, 2);
    expect(captured!['history'][1]['role'], 'assistant');
    expect(captured!['evidence'][0]['text'], contains('职业规划'));
    expect(captured!.containsKey('private_text'), isFalse);
    expect(captured!.containsKey('consent_token'), isFalse);
  });

  test('phone cannot silently treat HTTP 401 or invalid token as success', () async {
    final ai = AiClient(store, transport: MockClient((req) async {
      return http.Response(jsonEncode({'detail':'设备令牌错误'}), 401,
        headers: {'content-type':'application/json'});
    }));
    await expectLater(ai.health(), throwsA(
      predicate((e) => e.toString().contains('HTTP 401'))));
    await expectLater(ai.ask('你好'), throwsA(
      predicate((e) => e.toString().contains('设备令牌错误'))));
  });

  test('phone shows missing provider but health endpoint remains reachable', () async {
    final ai = AiClient(store, transport: MockClient((req) async {
      return http.Response(jsonEncode({
        'ok': true, 'provider_configured': false, 'model': 'none'
      }), 200, headers: {'content-type': 'application/json'});
    }));
    expect((await ai.health()).connected, true);
    expect(await ai.check(), false);
  });

  test('phone does not include private journal in ordinary evidence list', () async {
    final secret = await store.add(kind:'journal',title:'私人记录',
      body:'严格不得上传的日记正文');
    Map<String, dynamic>? payload;
    final ai = AiClient(store, transport: MockClient((req) async {
      payload = jsonDecode(req.body) as Map<String, dynamic>;
      return http.Response(jsonEncode({
        'answer':'收到','model':'deepseek-flash','source_ids':[]
      }), 200, headers: {'content-type':'application/json'});
    }));
    await ai.ask('请解释普通概念',evidence:[secret]);
    expect(payload!['evidence'], isEmpty);
    expect(jsonEncode(payload),isNot(contains('严格不得上传的日记正文')));
  });
}
