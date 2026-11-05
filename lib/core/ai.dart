import 'dart:convert';
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
  AiClient(this.store);

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
    final response = await http.get(endpoint('/health'), headers: headers)
        .timeout(const Duration(seconds: 12));
    if (response.statusCode != 200) throw Exception('节点不可用（HTTP ${response.statusCode}）');
    final data = jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
    return AiHealth(connected: data['ok'] == true,
        providerConfigured: data['provider_configured'] == true,
        model: data['model']?.toString() ?? 'unknown');
  }

  Future<bool> check() async => (await health()).providerConfigured;

  Future<AiResult> ask(String question, {String mode = 'ask',
    List<CompassEntry> evidence = const [], String? privateText}) async {
    final requestId = 'req_${CompassStore.newId()}';
    String? consentToken;
    if (privateText != null && privateText.isNotEmpty) {
      final response = await http.post(endpoint('/v1/consent'), headers: headers,
        body: jsonEncode({'private_text': privateText, 'request_id': requestId, 'question': question, 'mode': mode}))
        .timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) throw Exception('日记本次授权失败（${response.statusCode}）');
      consentToken = (jsonDecode(utf8.decode(response.bodyBytes)) as Map)['token'] as String;
    }
    final payload = <String, dynamic>{
      'question': question, 'mode': mode,
      'evidence': evidence.take(12).where((e) => e.body.trim().isNotEmpty && e.kind != 'journal')
          .map((e) => <String, String>{'source_id': e.id,
             'title': e.title.length > 200 ? e.title.substring(0, 200) : e.title,
             'text': e.body.length > 6000 ? e.body.substring(0, 6000) : e.body}).toList(),
      if (privateText != null && privateText.isNotEmpty) ...{
        'private_text': privateText, 'request_id': requestId, 'question': question, 'mode': mode, 'consent_token': consentToken,
      }
    };
    final response = await http.post(endpoint('/v1/chat'), headers: headers,
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