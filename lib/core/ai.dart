import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'store.dart';

class AiResult {
  final String answer;
  final String model;
  final List<String> sourceIds;
  AiResult(this.answer, this.model, this.sourceIds);
}

class AiHealth {
  final bool connected;
  final bool providerConfigured;
  final String model;
  const AiHealth({required this.connected, required this.providerConfigured, required this.model});
}

class AiClient {
  final CompassStore store;
  final http.Client? transport;
  AiClient(this.store, {this.transport});

  Future<http.Response> _get(Uri uri, {required Map<String, String> headers}) =>
      transport == null ? http.get(uri, headers: headers)
          : transport!.get(uri, headers: headers);
  Future<http.Response> _post(Uri uri, {required Map<String, String> headers,
      required String body}) => transport == null
          ? http.post(uri, headers: headers, body: body)
          : transport!.post(uri, headers: headers, body: body);

  Uri endpoint(String path) {
    if (store.serverUrl.isEmpty || store.deviceToken.isEmpty) {
      throw StateError('请先在「设备与 AI 设置」中配置 AI 工作站和设备令牌');
    }
    return Uri.parse('${store.serverUrl}$path');
  }

  Map<String, String> get headers => {
    'Content-Type': 'application/json', 'X-Device-Token': store.deviceToken,
  };

  Future<AiHealth> health() async {
    // Render Free may require a cold start; a 12-second probe is misleading.
    final response = await _get(endpoint('/health'), headers: headers)
        .timeout(const Duration(seconds: 70));
    if (response.statusCode != 200) throw Exception('节点不可用（HTTP ${response.statusCode}）');
    final data = jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
    return AiHealth(connected: data['ok'] == true,
        providerConfigured: data['provider_configured'] == true,
        model: data['model']?.toString() ?? 'unknown');
  }

  Future<bool> check() async => (await health()).providerConfigured;

  Future<bool> privateReviewEnabled() async {
    final res = await _get(endpoint('/health'), headers: headers)
        .timeout(const Duration(seconds: 70));
    if (res.statusCode != 200) {
      throw StateError('AI 服务状态检查失败（HTTP ${res.statusCode}）');
    }
    final data = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    return data['private_review_enabled'] == true && data['provider_configured'] == true;
  }

  /// Explicit user confirmation must happen in the UI BEFORE this method is called.
  /// First POST sends only a SHA-256 digest; second POST sends exactly the previewed text.
  Future<AiResult> reviewPrivate(String text, String question) async {
    final uri = endpoint('/v1/review');
    if (uri.scheme != 'https') {
      throw StateError('私密回顾只能通过 HTTPS 发送，当前 AI 地址不安全');
    }
    if (text.trim().isEmpty || text.length > 18000) {
      throw const FormatException('请选择 18000 字以内的非空日记摘录');
    }
    final requestId = 'req_${CompassStore.newId()}';
    final digest = sha256.convert(utf8.encode(text)).toString();
    final permission = await _post(endpoint('/v1/review/consent'),
      headers: headers,
      body: jsonEncode({'digest': digest, 'request_id': requestId,
        'question': question})).timeout(const Duration(seconds: 20));
    final permissionData = jsonDecode(utf8.decode(permission.bodyBytes)) as Map<String, dynamic>;
    if (permission.statusCode != 200) {
      throw StateError(permissionData['detail']?.toString() ?? '私密回顾授权失败');
    }
    final token = permissionData['token']?.toString() ?? '';
    if (token.isEmpty) throw StateError('服务端未返回本次授权凭证');
    final response = await _post(uri, headers: headers,
      body: jsonEncode({'private_text': text, 'request_id': requestId,
        'question': question, 'consent_token': token}))
        .timeout(const Duration(seconds: 95));
    final data = jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
    if (response.statusCode != 200) {
      throw StateError(data['detail']?.toString() ?? '私密回顾生成失败');
    }
    return AiResult(data['answer']?.toString() ?? '',
      data['model']?.toString() ?? 'unknown', const []);
  }

  Future<AiResult> ask(String question, {String mode = 'ask',
    List<CompassEntry> evidence = const [], String? privateText,
    List<Map<String, String>> history = const []}) async {
    final requestId = 'req_${CompassStore.newId()}';
    String? consentToken;
    if (privateText != null && privateText.isNotEmpty) {
      final response = await _post(endpoint('/v1/consent'), headers: headers,
        body: jsonEncode({'private_text': privateText, 'request_id': requestId, 'question': question, 'mode': mode}))
        .timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) throw Exception('日记本次授权失败（${response.statusCode}）');
      consentToken = (jsonDecode(utf8.decode(response.bodyBytes)) as Map)['token'] as String;
    }
    final payload = <String, dynamic>{
      'question': question, 'mode': mode,
      'history': history.take(40).map((m) => {'role': m['role'], 'content': m['content']}).toList(),
      'evidence': evidence.take(12).where((e) => e.body.trim().isNotEmpty && e.kind != 'journal')
          .map((e) => <String, String>{'source_id': e.id,
             'title': e.title.length > 200 ? e.title.substring(0, 200) : e.title,
             'text': e.body.length > 6000 ? e.body.substring(0, 6000) : e.body}).toList(),
      if (privateText != null && privateText.isNotEmpty) ...{
        'private_text': privateText, 'request_id': requestId, 'question': question, 'mode': mode, 'consent_token': consentToken,
      }
    };
    final response = await _post(endpoint('/v1/chat'), headers: headers,
      body: jsonEncode(payload)).timeout(const Duration(seconds: 85));
    final data = jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
    if (response.statusCode != 200) {
      throw Exception(data['detail']?.toString() ?? 'AI 请求失败（HTTP ${response.statusCode}）');
    }
    return AiResult(data['answer'] as String,
      data['model']?.toString() ?? 'unknown',
      (data['source_ids'] as List? ?? []).map((e) => e.toString()).toList());
  }
}

extension PdfParsing on AiClient {
  Future<Map<String, dynamic>> extractPdf(String localPath) async {
    final request = http.MultipartRequest('POST', endpoint('/v1/pdf'));
    request.headers.addAll({'X-Device-Token': store.deviceToken});
    request.files.add(await http.MultipartFile.fromPath('file', localPath));
    final streamed = await request.send().timeout(const Duration(seconds: 70));
    final response = await http.Response.fromStream(streamed);
    final data = jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
    if (response.statusCode != 200) {
      throw Exception(data['detail']?.toString() ?? 'PDF 解析失败（${response.statusCode}）');
    }
    return data;
  }
}