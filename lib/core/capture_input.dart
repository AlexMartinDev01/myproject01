/// Parses incoming Android shared text without contacting third-party services.
class CaptureInput {
  final String title;
  final String body;
  final String origin;
  final String status;
  final String sourceType;
  const CaptureInput({required this.title, required this.body, required this.origin,
    required this.status, required this.sourceType});

  static final _url = RegExp(r'https?://[^\s<>"\u201c\u201d\u300a\u300b]+', caseSensitive: false);

  static CaptureInput fromText(String raw) {
    final text = raw.trim();
    if (text.isEmpty) throw const FormatException('请输入需要记录的内容');
    final match = _url.firstMatch(text);
    final url = match == null ? '' :
      match.group(0)!.replaceFirst(RegExp(r'[.,;:!?)\u3002\uff0c\uff01\uff1f]+$'), '');
    final uri = Uri.tryParse(url);
    final hasUrl = uri != null && (uri.scheme == 'http' || uri.scheme == 'https')
      && uri.host.isNotEmpty;
    final origin = hasUrl ? url : '';
    final note = hasUrl ? (text.replaceFirst(url, '').trim()) : text;
    final first = note.isEmpty ? origin : note.split(RegExp(r'[\r\n]')).first.trim();
    final title = first.length <= 42 ? first : '${first.substring(0, 42)}…';
    return CaptureInput(title: title.isEmpty ? '随手记录' : title,
      body: note, origin: origin,
      status: hasUrl ? '需补内容' : '待处理',
      sourceType: hasUrl ? '网页/视频链接' : '个人想法');
  }
}
