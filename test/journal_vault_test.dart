import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:zhixing_compass/core/journal_vault.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() => FlutterSecureStorage.setMockInitialValues({}));
  late Directory root;
  setUp(() async => root = await Directory.systemTemp.createTemp('compass-vault-'));
  tearDown(() async {
    if (await root.exists()) await root.delete(recursive: true);
  });

  test('200k Chinese characters are encrypted, restored and tamper protected', () async {
    final vault = JournalVault(const FlutterSecureStorage(), root);
    final content = '这是我记录的一段真实思考。\n' * 18000;
    expect(content.length, greaterThan(200000));
    final r1 = await vault.writeText('j_01', content);
    final r2 = await vault.writeText('j_01', content + '继续补充');
    final rebooted = JournalVault(const FlutterSecureStorage(), root);
    expect(await rebooted.readText('j_01', r1), content);
    expect(await rebooted.readText('j_01', r2), content + '继续补充');
    final first = File(root.path + '/j_01/revision_' + r2 + '/00000000.chunk');
    final bytes = await first.readAsBytes();
    expect(String.fromCharCodes(bytes).contains('真实思考'), isFalse);
    bytes[bytes.length - 1] ^= 1;
    await first.writeAsBytes(bytes);
    await expectLater(rebooted.readText('j_01', r2), throwsA(anything));
    expect(await rebooted.readText('j_01', r1), content);
  });

  test('PDF attachment bytes are encrypted and restored without plaintext disk copies', () async {
    final src = File(root.path + '/user.pdf');
    final raw = <int>[37,80,68,70,45, ...List<int>.generate(140000, (i) => i % 250)];
    await src.writeAsBytes(raw);
    final vault = JournalVault(const FlutterSecureStorage(), root);
    await vault.encryptAttachment('j_02', 'file_01', src);
    expect(await vault.readAttachment('j_02', 'file_01'), raw);
    final rawCipher = await File(root.path + '/j_02/asset_file_01/00000000.chunk').readAsBytes();
    expect(rawCipher.take(5).toList(), isNot([37,80,68,70,45]));
    await vault.removeAttachment('j_02', 'file_01');
    expect(await Directory(root.path + '/j_02/asset_file_01').exists(), isFalse);
  });
}
