import 'dart:typed_data';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'main.dart';
import 'core/store.dart';
import 'diary_book_v085.dart';

/// User-driven export; nothing is uploaded or saved until an explicit action.
class DiaryBookPage extends StatefulWidget {
  const DiaryBookPage({super.key});
  @override
  State<DiaryBookPage> createState()=>_DiaryBookPageState();
}

class _DiaryBookPageState extends State<DiaryBookPage> {
  final title=TextEditingController(text:'写给未来的自己');
  final preface=TextEditingController();
  final selectedIds=<String>{};
  String range='all';
  bool initialized=false;
  bool busy=false;
  String message='';

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if(!initialized) {
      selectedIds.addAll(CompassScope.of(context).ofKind('journal')
        .map((e)=>e.id));
      initialized=true;
    }
  }

  @override
  void dispose() {
    title.dispose();preface.dispose();super.dispose();
  }

  List<CompassEntry> eligible(List<CompassEntry> diaries) {
    final now=DateTime.now();
    return diaries.where((e)=>
      range=='all' ||
      (range=='year' && e.createdAt.year==now.year) ||
      (range=='month' && e.createdAt.year==now.year &&
       e.createdAt.month==now.month)).toList();
  }

  Future<void> exportBook() async {
    if(busy)return;
    final store=CompassScope.of(context);
    final chosen=store.ofKind('journal').where((e)=>
      selectedIds.contains(e.id)).toList();
    if(chosen.isEmpty) {
      setState(()=>message='请至少勾选一篇日记');return;
    }
    final approved=await showDialog<bool>(context:context,
      builder:(ctx)=>AlertDialog(
        title:const Text('确认生成可阅读的日记书？'),
        content:Text('本次选择 ${chosen.length} 篇日记。导出的 PDF 不加密，'
          '可以被任何获得该文件的人阅读。\n\n'
          '内容仅在手机本机处理，不发送到 AI 或云端；原日记不会修改。'
          '请将文件保存在私人位置，切勿把 PDF 当作加密备份。'),
        actions:[
          TextButton(onPressed:()=>Navigator.pop(ctx,false),
            child:const Text('取消')),
          FilledButton(onPressed:()=>Navigator.pop(ctx,true),
            child:const Text('我已了解，生成 PDF')),
        ]));
    if(approved!=true || !mounted)return;
    setState((){busy=true;message='正在本机排版，请勿关闭页面';});
    try{
      final Uint8List bytes=await DiaryBookService.build(
        store:store,entries:chosen,title:title.text,preface:preface.text);
      if(!mounted)return;
      final now=DateTime.now();
      final filename='zhixing-diary-book-${now.year}-'
        '${now.month.toString().padLeft(2,'0')}-'
        '${now.day.toString().padLeft(2,'0')}.pdf';
      final saved=await FilePicker.platform.saveFile(
        dialogTitle:'保存我的日记书',
        fileName:filename,type:FileType.custom,allowedExtensions:['pdf'],
        bytes:bytes);
      if(mounted)setState(()=>message=saved==null
        ? '已取消保存；原日记保持不变'
        : '已请求系统保存 PDF：$saved。请打开文件检查文字和页码。');
    }catch(e){
      if(mounted)setState(()=>message='生成失败：$e；原日记保持不变');
    }finally{
      if(mounted)setState(()=>busy=false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final diaries=CompassScope.of(context).ofKind('journal')
      ..sort((a,b)=>a.createdAt.compareTo(b.createdAt));
    final options=eligible(diaries);
    final selectedCount=options.where((e)=>selectedIds.contains(e.id)).length;
    return PageFrame(title:'制作我的日记书',
      subtitle:'离线整理 · 自动排版 · 私人 PDF',
      bottomBar:SafeArea(top:false,child:Padding(
        padding:const EdgeInsets.fromLTRB(16,8,16,12),
        child:FilledButton.icon(onPressed:busy?null:exportBook,
          icon:const Icon(Icons.picture_as_pdf_outlined),
          label:Text(busy?'正在生成…':'生成并保存 PDF 日记书')))),
      children:[
        SoftCard(color:const Color(0xFFF1EAF8),child:Column(
          crossAxisAlignment:CrossAxisAlignment.start,children:[
            const Text('把日子装订成一本书',
              style:TextStyle(fontSize:20,fontWeight:FontWeight.w700)),
            const SizedBox(height:9),
            const Text('自动生成封面、月份目录、每篇日期与正文。'
              'A5 尺寸适合手机阅读，也便于后续打印。',
              style:TextStyle(height:1.5)),
          ])),
        const SizedBox(height:14),
        const Text('1 · 书名',style:TextStyle(fontWeight:FontWeight.w700)),
        const SizedBox(height:8),
        TextField(controller:title,maxLength:80,
          decoration:const InputDecoration(hintText:'例如：写给未来的自己')),
        const SizedBox(height:9),
        const Text('2 · 选择日记',style:TextStyle(fontWeight:FontWeight.w700)),
        const SizedBox(height:8),
        SingleChildScrollView(scrollDirection:Axis.horizontal,child:Row(
          children:[
            for(final option in [
              ('all','全部'),('year','今年'),('month','本月')])
              Padding(padding:const EdgeInsets.only(right:8),
                child:ChoiceChip(label:Text(option.$2),
                  selected:range==option.$1,
                  onSelected:busy?null:(_)=>setState((){
                    range=option.$1;
                    selectedIds..clear()..addAll(eligible(diaries).map((e)=>e.id));
                  }))),
          ])),
        const SizedBox(height:10),
        Row(children:[
          Expanded(child:Text('已选 $selectedCount / ${options.length} 篇',
            style:const TextStyle(fontWeight:FontWeight.w600))),
          TextButton(onPressed:busy?null:()=>setState(()=>
            selectedIds.addAll(options.map((e)=>e.id))),
            child:const Text('全选')),
          TextButton(onPressed:busy?null:()=>setState(()=>
            selectedIds.removeAll(options.map((e)=>e.id))),
            child:const Text('清空')),
        ]),
        if(options.isEmpty)const SoftCard(
          child:Text('当前范围没有日记，可以切换“全部”或先写一篇。')),
        ...options.map((entry)=>CheckboxListTile(
          dense:true,contentPadding:EdgeInsets.zero,
          value:selectedIds.contains(entry.id),
          title:Text(entry.title,maxLines:2,overflow:TextOverflow.ellipsis),
          subtitle:Text(DiaryBookService.formatDate(entry.createdAt)),
          onChanged:busy?null:(checked)=>setState((){
            if(checked==true){selectedIds.add(entry.id);}
            else{selectedIds.remove(entry.id);}
          }),
        )),
        const SizedBox(height:10),
        const Text('3 · 可选序言',style:TextStyle(fontWeight:FontWeight.w700)),
        const SizedBox(height:8),
        TextField(controller:preface,minLines:3,maxLines:5,maxLength:3000,
          decoration:const InputDecoration(
            hintText:'写几句话送给未来的自己（可留空）')),
        const SizedBox(height:8),
        const SoftCard(child:Text('隐私提醒：PDF 为可阅读文件，不是加密备份。'
          '本功能不接入 AI、不上传日记，也不更改已保存的日记。')),
        if(busy)const LinearProgressIndicator(),
        if(message.isNotEmpty)Text(message,
          style:const TextStyle(color:grape)),
        const SizedBox(height:8),
      ]);
  }
}
