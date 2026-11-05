import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';
import 'package:cryptography/cryptography.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path/path.dart' as p;

/// Private AES-256-GCM chunk store. Plaintext is never written to app files.
class JournalVault {
  JournalVault(this.secure, this.root);
  final FlutterSecureStorage secure;
  final Directory root;
  final algorithm = AesGcm.with256bits();
  SecretKey? _cachedKey;
  static const chunkSize = 32768;

  String safe(String id) {
    if (!RegExp(r'^[a-zA-Z0-9_-]+$').hasMatch(id)) {
      throw const FormatException('非法的私密记录标识');
    }
    return id;
  }

  String newId() => DateTime.now().microsecondsSinceEpoch.toString() +
      '_' + Random.secure().nextInt(0x7fffffff).toString();

  Future<SecretKey> key() async {
    if (_cachedKey != null) return _cachedKey!;
    final old = await secure.read(key: 'journal-vault:aes256:v1');
    if (old != null && old.isNotEmpty) {
      final data = base64Decode(old);
      if (data.length != 32) throw const FormatException('私密日记密钥错误');
      return _cachedKey = SecretKey(data);
    }
    final fresh = await algorithm.newSecretKey();
    await secure.write(key: 'journal-vault:aes256:v1',
        value: base64Encode(await fresh.extractBytes()));
    return _cachedKey = fresh;
  }

  Future<List<int>> encrypt(List<int> data) async {
    final nonce = algorithm.newNonce();
    final box = await algorithm.encrypt(data, secretKey: await key(), nonce: nonce);
    return [...box.nonce, ...box.mac.bytes, ...box.cipherText];
  }

  Future<List<int>> decrypt(List<int> data) async {
    if (data.length < 29) throw const FormatException('私密文件已损坏');
    final box = SecretBox(data.sublist(28), nonce: data.sublist(0, 12),
        mac: Mac(data.sublist(12, 28)));
    return algorithm.decrypt(box, secretKey: await key());
  }

  Directory journalDir(String id) => Directory(p.join(root.path, safe(id)));

  Future<String> writeText(String journalId, String text) async {
    final rev = newId(), id = safe(journalId);
    final temp = Directory(p.join(root.path, id, 'writing_' + rev));
    final target = Directory(p.join(root.path, id, 'revision_' + rev));
    await temp.create(recursive: true);
    try {
      final bytes = utf8.encode(text);
      var n = 0;
      for (var i = 0; i < bytes.length; i += chunkSize) {
        final part = bytes.sublist(i, min(i + chunkSize, bytes.length));
        await File(p.join(temp.path, n.toString().padLeft(8, '0') + '.chunk'))
            .writeAsBytes(await encrypt(part), flush: true);
        n++;
      }
      await temp.rename(target.path);
      return rev;
    } catch (_) {
      if (await temp.exists()) await temp.delete(recursive: true);
      rethrow;
    }
  }

  Future<List<int>> _readParts(Directory dir) async {
    if (!await dir.exists()) throw StateError('私密版本或附件不存在');
    final files = await dir.list().where((e) => e is File).cast<File>().toList();
    files.sort((a, b) => a.path.compareTo(b.path));
    final buf = BytesBuilder(copy: false);
    for (final file in files) {
      buf.add(await decrypt(await file.readAsBytes()));
    }
    return buf.takeBytes();
  }

  Future<String> readText(String journalId, String rev) async =>
      utf8.decode(await _readParts(Directory(p.join(
          journalDir(journalId).path, 'revision_' + safe(rev)))));

  Future<void> removeRevision(String journalId, String rev) async {
    final dir = Directory(p.join(journalDir(journalId).path, 'revision_' + safe(rev)));
    if (await dir.exists()) await dir.delete(recursive: true);
  }

  Future<void> encryptAttachment(String journalId, String assetId, File source) async {
    final dir = journalDir(journalId);
    final temp = Directory(p.join(dir.path, 'writing_asset_' + safe(assetId)));
    final target = Directory(p.join(dir.path, 'asset_' + safe(assetId)));
    await temp.create(recursive: true);
    try {
      var n = 0;
      await for (final block in source.openRead()) {
        for (var i = 0; i < block.length; i += chunkSize) {
          await File(p.join(temp.path, n.toString().padLeft(8, '0') + '.chunk'))
              .writeAsBytes(await encrypt(block.sublist(i, min(i + chunkSize, block.length))),
                  flush: true);
          n++;
        }
      }
      await temp.rename(target.path);
    } catch (_) {
      if (await temp.exists()) await temp.delete(recursive: true);
      rethrow;
    }
  }

  Future<Uint8List> readAttachment(String journalId, String assetId) async =>
      Uint8List.fromList(await _readParts(Directory(p.join(
          journalDir(journalId).path, 'asset_' + safe(assetId)))));

  Future<void> removeAttachment(String journalId, String assetId) async {
    final dir = Directory(p.join(journalDir(journalId).path, 'asset_' + safe(assetId)));
    if (await dir.exists()) await dir.delete(recursive: true);
  }

  Future<void> deleteJournal(String journalId) async {
    final dir = journalDir(journalId);
    if (await dir.exists()) await dir.delete(recursive: true);
  }
}
