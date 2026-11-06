import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import 'main.dart';
import 'screens.dart' show DeviceSettingsPage,
    KnowledgeDetailPage, SourceDetailPage;
import 'core/ai.dart';
import 'core/chat_store.dart';
import 'core/knowledge_candidates.dart';
import 'knowledge_review_v052.dart';
import 'knowledge_ref_v053.dart';
import 'core/knowledge_index.dart';
import 'core/store.dart';

const _modes = <String, String>{
  'ask': '知识问答',
  'learn': '费曼学习',
  'challenge': '反方思辨',
  'decision': '决策分析',
  'review': '行动复盘',
  'brief': '素材提炼',
};

class MessageMarkdown extends StatelessWidget {
  final String data;
  const MessageMarkdown({super.key, required this.data});
  @override
  Widget build(BuildContext context) {
    final base = MarkdownStyleSheet.fromTheme(Theme.of(context));
    return MarkdownBody(
      data: data.replaceAllMapped(RegExp(r'\[(k_[0-9a-f]{16}_[0-9]+)\]'),
        (m) => '[' + m.group(1)! + '](zhixing://ref/' + m.group(1)! + ')'),
      selectable: true,
      styleSheet: base.copyWith(
        p: const TextStyle(fontSize: 15, height: 1.6, color: ink),
        h1: const TextStyle(fontSize: 21, fontWeight: FontWeight.bold, color: ink),
        h2: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: ink),
        h3: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: ink),
        code: const TextStyle(fontFamily: 'monospace',
          backgroundColor: Color(0xFFEDE9F2), color: ink),
      ),
      onTapLink: (text, href, title) async {
        final uri = href == null ? null : Uri.tryParse(href);
        if (uri == null) return;
        if (uri.scheme == 'https') {
          await launchUrl(uri);
        } else if (uri.scheme == 'zhixing' && uri.host == 'ref' &&
            uri.pathSegments.length == 1) {
          await openPage(context, KnowledgeReferencePage(
            chunkId: uri.pathSegments.single));
        }
      },
    );
  }
}

class AiChatPage extends StatefulWidget {
  final String mode;
  final String? conversationId;
  final List<CompassEntry> selectedEvidence;
  final String? approvedPrivateText;
  final String? initialPrompt;
  final bool restrictEvidenceToSelection;
  const AiChatPage({super.key, this.mode = 'ask', this.conversationId,
    this.selectedEvidence = const [], this.approvedPrivateText,
    this.initialPrompt, this.restrictEvidenceToSelection = false});

  @override
  State<AiChatPage> createState() => _AiChatPageState();
}

class _AiChatPageState extends State<AiChatPage> {
  final input = TextEditingController();
  final scroll = ScrollController();
  String? threadId;
  String mode = 'ask';
  List<ChatLine> lines = [];
  bool loading = true, sending = false, extracting = false;
  String? error;
  @override
  void initState() {
    super.initState();
    threadId = widget.conversationId;
    mode = widget.mode;
    input.text = widget.initialPrompt ?? '';
    WidgetsBinding.instance.addPostFrameCallback((_) => loadConversation());
  }

  @override
  void dispose() {
    input.dispose();
    scroll.dispose();
    super.dispose();
  }

  bool get privateMode => widget.approvedPrivateText?.isNotEmpty == true;

  Future<void> loadConversation() async {
    if (!mounted) return;
    final store = CompassScope.of(context);
    try {
      if (threadId != null) {
        final info = await store.chats.getThread(threadId!);
        if (info == null) throw StateError('历史会话不存在或已被删除');
        final loaded = await store.chats.loadMessages(threadId!);
        if (mounted) setState(() {mode = info.mode; lines = loaded; loading = false;});
        moveToBottom();
      } else if (mounted) {
        setState(() => loading = false);
      }
    } catch (e) {
      if (mounted) setState(() {error = '读取聊天历史失败：$e'; loading = false;});
    }
  }

  void moveToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (scroll.hasClients) {
        scroll.animateTo(scroll.position.maxScrollExtent,
          duration: const Duration(milliseconds: 240), curve: Curves.easeOut);
      }
    });
  }

  List<Map<String, String>> conversationContext() {
    // Include only previous ordinary messages and cap the upstream budget.
    var total = 0;
    final picked = <Map<String, String>>[];
    for (final msg in lines.reversed) {
      final contextText = msg.content.length > 5900
          ? msg.content.substring(0, 5900) + '\n[此条较长，仅传递部分上下文]'
          : msg.content;
      if (picked.length >= 30 || total + contextText.length > 40000) break;
      picked.add({'role': msg.role, 'content': contextText});
      total += contextText.length;
    }
    return picked.reversed.toList();
  }

  Future<void> send() async {
    final prompt = input.text.trim();
    if (prompt.isEmpty || sending || loading) return;
    if (privateMode) {
      notice(context, '云端日记 AI 尚未开放。请勿发送私密日记；此页面不会上传私密正文。');
      return;
    }
    setState(() {sending = true; error = null;});
    final store = CompassScope.of(context);
    try {
      final hits = await store.findKnowledge(prompt, limit: 9);
      final evidence = widget.restrictEvidenceToSelection
        ? widget.selectedEvidence
        : (widget.selectedEvidence.isNotEmpty
            ? widget.selectedEvidence
            : hits.map((hit) => hit.toEvidence()).toList());
      final response = await AiClient(store).ask(prompt,
        mode: mode, evidence: evidence, history: conversationContext());
      var id = threadId;
      if (id == null) {
        final created = await store.chats.createThread(title: prompt, mode: mode);
        id = created.id;
        threadId = id;
      }
      await store.chats.addMessage(id, 'user', prompt);
      await store.chats.addMessage(id, 'assistant', response.answer,
        model: response.model, sourceIds: response.sourceIds);
      final updated = await store.chats.loadMessages(id);
      if (!mounted) return;
      setState(() {lines = updated; input.clear();});
      moveToBottom();
    } catch (e) {
      if (mounted) setState(() => error = 'AI 回复或本地保存失败：$e');
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  Future<void> extractDraft(ChatLine message) async {
    if (extracting || privateMode) return;
    final store = CompassScope.of(context);
    setState(() => extracting = true);
    try {
      // A single-turn request without new endpoints: compatible with the old
      // Render deployment. The source answer is not silently copied into a card.
      final result = await AiClient(store).ask(
        knowledgeExtractionPrompt(message.content), mode: 'ask');
      final points = parseKnowledgeCandidates(result.answer);
      if (!mounted) return;
      await openPage(context, KnowledgeReviewPage(
        candidates: points,
        conversationId: message.conversationId,
        messageId: message.id,
        offeredSourceIds: message.sourceIds,
        inputWasTruncated: message.content.length > 6500,
      ));
    } catch (e) {
      if (mounted) notice(context, 'AI 提炼失败，未保存知识卡：$e。可以重试。');
    } finally {
      if (mounted) setState(() => extracting = false);
    }
  }

  Widget messageBubble(ChatLine line, CompassStore store) {
    final user = line.role == 'user';
    final available = line.sourceIds.map((id) => store.byId(id)).whereType<CompassEntry>().toList();
    return Align(
      alignment: user ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: const BoxConstraints(maxWidth: 720),
        margin: const EdgeInsets.symmetric(vertical: 7, horizontal: 12),
        padding: const EdgeInsets.all(15),
        decoration: BoxDecoration(
          color: user ? const Color(0xFFEDE2F4) : Colors.white,
          border: Border.all(color: const Color(0xFFECE3F0)),
          borderRadius: BorderRadius.circular(18),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(user ? '我' : '知行罗盘 · AI',
            style: const TextStyle(fontWeight: FontWeight.w700,
              color: grape, fontSize: 12)),
          const SizedBox(height: 10),
          if (user) SelectableText(line.content,
            style: const TextStyle(fontSize: 15, height: 1.55, color: ink))
          else MessageMarkdown(data: line.content),
          const SizedBox(height: 8),
          if (!user && available.isNotEmpty) ...[
            const Divider(),
            const Text('本次提供的资料（不等于每条结论均已核实）',
              style: TextStyle(fontSize: 11, color: Colors.grey)),
            Wrap(spacing: 6, runSpacing: 4, children: available.map((e) =>
              ActionChip(label: Text(e.title.length > 18 ? '${e.title.substring(0, 18)}…' : e.title),
                onPressed: () => openPage(context, e.kind == 'card' ?
                  KnowledgeDetailPage(id: e.id) : SourceDetailPage(id: e.id)))).toList()),
          ],
          if (!user && line.sourceIds.any((id) => id.startsWith('k_')))
            FutureBuilder<List<KnowledgeHit?>>(future: Future.wait(
              line.sourceIds.where((id) => id.startsWith('k_'))
                .map((id) => store.knowledge.lookup(id))),
              builder: (ctx, snap) {
                if (!snap.hasData) return const SizedBox.shrink();
                final refs = snap.data!.whereType<KnowledgeHit>().toList();
                if (refs.isEmpty) return const SizedBox.shrink();
                return Column(crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('检索提供的原文片段 · 点击查看具体段落',
                      style: TextStyle(fontSize: 11, color: Colors.grey)),
                    Wrap(spacing: 6, runSpacing: 4, children: refs.map((hit) =>
                      ActionChip(label: Text(hit.title.length > 20
                        ? hit.title.substring(0, 20) + '…' : hit.title),
                        onPressed: () => openPage(context,
                          KnowledgeReferencePage(chunkId: hit.chunkId)))).toList()),
                  ]);
              }),
          Wrap(spacing: 4, children: [
            IconButton(tooltip: '复制这条消息', icon: const Icon(Icons.copy, size: 18),
              onPressed: () async {
                await Clipboard.setData(ClipboardData(text: line.content));
                if (mounted) notice(context, '已复制');
              }),
            if (!user) TextButton.icon(
              onPressed: extracting ? null : () => extractDraft(line),
              icon: const Icon(Icons.bookmark_add_outlined, size: 18),
              label: Text(extracting ? '提炼中…' : '提炼多个知识点')),
          ]),
          if (!user && line.model.isNotEmpty)
            Text('模型：${line.model}', style: const TextStyle(fontSize: 10, color: Colors.grey)),
        ]),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('AI 思考', style: TextStyle(fontWeight: FontWeight.bold)),
          Text(_modes[mode] ?? mode,
            style: const TextStyle(color: Colors.grey, fontSize: 12)),
        ]),
        actions: [
          IconButton(tooltip: '历史会话', icon: const Icon(Icons.history),
            onPressed: () => openPage(context, const ChatHistoryPage())),
          IconButton(tooltip: '开始新会话', icon: const Icon(Icons.add_comment_outlined),
            onPressed: () => openPage(context, AiChatPage(mode: mode))),
        ],
      ),
      body: Column(children: [
        if (privateMode) Container(
          padding: const EdgeInsets.all(12), color: const Color(0xFFFFF0EE),
          child: const Text('为了避免旧版云端授权提示不准确，V0.5.1 暂不支持将日记发往云端。'
            '本页不会发送或保存日记内容。')),
        if (store.serverUrl.isEmpty) ListTile(
          leading: const Icon(Icons.wifi_off),
          title: const Text('AI 服务尚未连接'),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => openPage(context, const DeviceSettingsPage()),
        ),
        Expanded(child: loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(controller: scroll,
              padding: const EdgeInsets.fromLTRB(6, 12, 6, 12),
              children: [
                if (lines.isEmpty && !privateMode) const Padding(
                  padding: EdgeInsets.all(20),
                  child: Column(children: [
                    Icon(Icons.auto_awesome, size: 42, color: grape),
                    SizedBox(height: 12),
                    Text('先问一个问题，再继续追问',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                    SizedBox(height: 8),
                    Text('回答支持标题、列表、引用、代码和表格；历史会加密保存在本机。',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.grey, height: 1.6)),
                    SizedBox(height: 8),
                    Text('当前仅选取少量来源材料，完整知识库智能检索将在 V0.5.3 实现。',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 12, color: Colors.grey)),
                  ]),
                ),
                ...lines.map((m) => messageBubble(m, store)),
                if (sending) const Padding(padding: EdgeInsets.all(18),
                  child: Row(children: [
                    SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
                    SizedBox(width: 10), Text('正在分析，回答会在成功后加密保存…'),
                  ])),
                if (error != null) Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text(error!, style: const TextStyle(color: Colors.red))),
              ])),
        SafeArea(top: false, child: Container(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
          decoration: const BoxDecoration(color: Color(0xFFF9F5FA),
            border: Border(top: BorderSide(color: Color(0xFFECE3F0)))),
          child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Expanded(child: TextField(
              controller: input,
              enabled: !privateMode,
              minLines: 1, maxLines: 4,
              keyboardType: TextInputType.multiline,
              decoration: InputDecoration(
                hintText: privateMode ? '私密日记暂不可通过云端发送' : '继续追问，AI 会参考此会话的历史…',
                filled: true, fillColor: Colors.white),
            )),
            const SizedBox(width: 8),
            IconButton.filled(
              tooltip: '发送问题', onPressed: sending || loading || privateMode ? null : send,
              icon: const Icon(Icons.arrow_upward),
            ),
          ]),
        )),
      ]),
    );
  }
}

class ChatHistoryPage extends StatefulWidget {
  const ChatHistoryPage({super.key});
  @override
  State<ChatHistoryPage> createState() => _ChatHistoryPageState();
}

class _ChatHistoryPageState extends State<ChatHistoryPage> {
  final search = TextEditingController();
  late Future<List<ChatThread>> threads;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    threads = CompassScope.of(context).chats.listThreads();
  }

  @override
  void dispose() {search.dispose();super.dispose();}

  void reload() {
    if (mounted) setState(() {
      threads = CompassScope.of(context).chats.listThreads(search: search.text);
    });
  }

  Future<void> rename(ChatThread t) async {
    final controller = TextEditingController(text: t.title);
    final newTitle = await showDialog<String>(context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('重命名会话'),
        content: TextField(controller: controller, autofocus: true, maxLength: 90),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, controller.text),
            child: const Text('保存')),
        ]));
    controller.dispose();
    if (newTitle == null || newTitle.trim().isEmpty || !mounted) return;
    await CompassScope.of(context).chats.renameThread(t.id, newTitle);
    reload();
  }

  Future<void> delete(ChatThread t) async {
    final confirmed = await showDialog<bool>(context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除整个会话？'),
        content: const Text('所有聊天消息和本机加密内容将被删除，操作不可撤销。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('删除')),
        ]));
    if (confirmed != true || !mounted) return;
    await CompassScope.of(context).chats.deleteThread(t.id);
    reload();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('AI 历史会话')),
    body: Column(children: [
      Padding(padding: const EdgeInsets.all(14),
        child: TextField(controller: search, onChanged: (_) => reload(),
          decoration: const InputDecoration(prefixIcon: Icon(Icons.search),
            hintText: '按会话标题搜索'))),
      Expanded(child: FutureBuilder<List<ChatThread>>(
        future: threads,
        builder: (ctx, snap) {
          if (snap.hasError) return Center(child: Text('读取历史失败：${snap.error}'));
          if (!snap.hasData) return const Center(child: CircularProgressIndicator());
          final list = snap.data!;
          if (list.isEmpty) return const Center(child: Text('暂时没有会话记录'));
          return ListView.builder(
            itemCount: list.length,
            itemBuilder: (ctx, index) {
              final item = list[index];
              return ListTile(
                leading: const Icon(Icons.chat_bubble_outline, color: grape),
                title: Text(item.title, maxLines: 2, overflow: TextOverflow.ellipsis),
                subtitle: Text('${_modes[item.mode] ?? item.mode} · ${item.updatedAt.year}-${item.updatedAt.month}-${item.updatedAt.day}'),
                onTap: () async {
                  await openPage(context, AiChatPage(conversationId: item.id, mode: item.mode));
                  reload();
                },
                trailing: PopupMenuButton<String>(
                  onSelected: (value) => value == 'rename' ? rename(item) : delete(item),
                  itemBuilder: (ctx) => const [
                    PopupMenuItem(value: 'rename', child: Text('重命名')),
                    PopupMenuItem(value: 'delete', child: Text('删除会话')),
                  ]),
              );
            },
          );
        },
      )),
    ]),
  );
}
