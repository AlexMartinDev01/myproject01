import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:pdfx/pdfx.dart';
import 'main.dart';
import 'core/docx_extract.dart';

/// In-memory private document preview: never writes decrypted attachments
/// to disk or sends the contents to other apps or an AI service.
class JournalAttachmentViewer extends StatefulWidget {
  final String journalId, assetId, filename;
  const JournalAttachmentViewer({super.key, required this.journalId,
    required this.assetId, required this.filename});
  @override
  State<JournalAttachmentViewer> createState() => _JournalAttachmentViewerState();
}
class _JournalAttachmentViewerState extends State<JournalAttachmentViewer> {
  PdfControllerPinch? pdf;
  Future<String>? word;
  bool loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }
  Future<void> load() async {
    try {
      final bytes = await CompassScope.of(context).readJournalAttachmentBytes(
        widget.journalId, widget.assetId);
      if (!mounted) return;
      if (widget.filename.toLowerCase().endsWith('.pdf')) {
        pdf = PdfControllerPinch(document: PdfDocument.openData(bytes));
      } else if (widget.filename.toLowerCase().endsWith('.docx')) {
        word = compute(parseDocxBytes, bytes);
      } else {
        throw const FormatException('不支持预览此附件格式');
      }
    } catch (e) {
      error = e.toString();
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }
  @override
  void dispose() {
    pdf?.dispose();
    super.dispose();
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.filename,
      maxLines: 1, overflow: TextOverflow.ellipsis)),
    body: loading ? const Center(child: CircularProgressIndicator())
      : error != null ? Center(child: Text('无法打开附件：$error'))
      : pdf != null ? PdfViewPinch(controller: pdf!)
      : FutureBuilder<String>(future: word, builder: (ctx, snap) {
          if (snap.hasError) return Center(child: Padding(
            padding: const EdgeInsets.all(20),
            child: Text('Word 解析失败：${snap.error}')));
          if (!snap.hasData) return const Center(child: CircularProgressIndicator());
          return ListView(padding: const EdgeInsets.all(18), children: [
            const Text('加密 Word 附件 · 当前仅预览提取的文字',
              style: TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 14),
            SelectableText(snap.data!.isEmpty ? '此文档没有可提取的文字' : snap.data!,
              style: const TextStyle(fontSize: 15, height: 1.7)),
          ]);
        }),
  );
}

class JournalBodyViewer extends StatelessWidget {
  final String journalId;
  const JournalBodyViewer({super.key, required this.journalId});
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('查看已保存的日记')),
    body: FutureBuilder<String>(
      future: CompassScope.of(context).privateJournalBody(journalId),
      builder: (ctx, snap) {
        if (snap.hasError) return Center(child: Text('日记解密失败：${snap.error}'));
        if (!snap.hasData) return const Center(child: CircularProgressIndicator());
        return ListView(padding: const EdgeInsets.all(18), children: [
          const Text('正文只在本机解密供你查看，不会自动发送给 AI。',
            style: TextStyle(fontSize: 12, color: Colors.grey)),
          const SizedBox(height: 12),
          SelectableText(snap.data!, style: const TextStyle(height: 1.7, fontSize: 15)),
        ]);
      },
    ),
  );
}
