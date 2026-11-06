import 'dart:convert';
import 'dart:typed_data';
import 'package:cryptography/cryptography.dart';

class PrivateBackupCodec {
  static const format = 'zhixing-private-v1';
  static const rounds = 180000;
  static final cipher = AesGcm.with256bits();

  static Future<SecretKey> key(String passphrase, List<int> salt) =>
    Pbkdf2(macAlgorithm: Hmac.sha256(), iterations: rounds, bits: 256)
      .deriveKeyFromPassword(password: passphrase, nonce: salt);

  static Future<Uint8List> seal(Map<String,dynamic> payload, String passphrase) async {
    if (passphrase.runes.length < 12) throw const FormatException('密码至少 12 字符');
    final data = utf8.encode(jsonEncode(payload));
    if (data.length > 50 * 1024 * 1024) throw const FormatException('备份超过 50 MB');
    final salt = Cryptography.instance.newRandomBytes(16);
    final nonce = cipher.newNonce();
    final box = await cipher.encrypt(data, secretKey: await key(passphrase,salt), nonce: nonce);
    return Uint8List.fromList(utf8.encode(jsonEncode({
      'format':format,'rounds':rounds,'salt':base64Encode(salt),
      'nonce':base64Encode(box.nonce),'tag':base64Encode(box.mac.bytes),
      'data':base64Encode(box.cipherText)
    })));
  }

  static Future<Map<String,dynamic>> unseal(Uint8List data, String passphrase) async {
    if (data.length > 72 * 1024 * 1024) throw const FormatException('文件过大');
    final envelope = jsonDecode(utf8.decode(data));
    if (envelope is! Map<String,dynamic> || envelope['format'] != format ||
        envelope['rounds'] != rounds) throw const FormatException('备份版本不支持');
    final salt = base64Decode(envelope['salt'] as String);
    final nonce = base64Decode(envelope['nonce'] as String);
    final tag = base64Decode(envelope['tag'] as String);
    final cipherText = base64Decode(envelope['data'] as String);
    if (salt.length != 16 || nonce.length != 12 || tag.length != 16 ||
        cipherText.length > 50 * 1024 * 1024) throw const FormatException('备份已损坏');
    final plaintext = await cipher.decrypt(SecretBox(cipherText,
      nonce:nonce,mac:Mac(tag)),secretKey:await key(passphrase,salt));
    final payload = jsonDecode(utf8.decode(plaintext));
    if (payload is! Map<String,dynamic> || payload['version'] != 1 ||
        payload['journals'] is! List) throw const FormatException('备份内容无效');
    return payload;
  }
}
