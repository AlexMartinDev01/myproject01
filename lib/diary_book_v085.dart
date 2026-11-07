import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'core/store.dart';

/// A book is a user-approved, readable PDF export, NOT a private backup.
/// All private content is decrypted only for the selected entries in memory.
class DiaryBookService {
  static const fontAsset = 'assets/fonts/NotoSansSC.ttf';
  static const maxEntries = 500;
  static const maxCharacters = 1500000;

  static List<CompassEntry> sortEntries(Iterable<CompassEntry> entries) {
    final selected=entries.where((e)=>e.kind=='journal').toList()
      ..sort((a,b) {
        final order=a.createdAt.compareTo(b.createdAt);
        return order!=0?order:a.id.compareTo(b.id);
      });
    if(selected.isEmpty) throw const FormatException('请至少选择一篇日记');
    if(selected.length>maxEntries) {
      throw const FormatException('单本书最多支持 500 篇日记');
    }
    return selected;
  }

  static String formatDate(DateTime date) =>
    '${date.year} 年 ${date.month.toString().padLeft(2,'0')} 月 '
    '${date.day.toString().padLeft(2,'0')} 日';

  static String monthKey(DateTime date) =>
    '${date.year} 年 ${date.month.toString().padLeft(2,'0')} 月';

  /// Small text widgets allow long diary contents to flow between pages.
  /// Keep Unicode surrogate pairs intact and never rewrite the original text.
  static List<String> splitBody(String body,{int chunkSize=360}) {
    if(body.isEmpty)return ['（本篇日记暂无正文）'];
    final result=<String>[];
    for(final para in body.split('\n')) {
      if(para.isEmpty){result.add('');continue;}
      final points=para.runes.toList();
      for(var i=0;i<points.length;i+=chunkSize) {
        result.add(String.fromCharCodes(
          points.skip(i).take(chunkSize)));
      }
    }
    return result;
  }

  static Future<Uint8List> build({
    required CompassStore store,
    required List<CompassEntry> entries,
    required String title,
    String preface = '',
    String subtitle = '',
    String coverStyle = 'classic',
  }) async {
    final ordered=sortEntries(entries);
    final bookTitle=title.trim();
    if(bookTitle.isEmpty || bookTitle.runes.length>80) {
      throw const FormatException('书名应为 1–80 个字符');
    }
    if(subtitle.runes.length>120) {
      throw const FormatException('副标题请控制在 120 字以内');
    }
    if(!['classic','rose','minimal'].contains(coverStyle)) {
      throw const FormatException('未知封面样式');
    }
    if(preface.runes.length>3000) {
      throw const FormatException('序言请控制在 3000 字以内');
    }
    // Read all selected entries before PDF construction. Failure aborts
    // the export, rather than silently omitting encrypted content.
    final bodies=<String,String>{};
    var chars=0;
    for(final entry in ordered) {
      final text=await store.privateJournalBody(entry.id);
      chars+=text.runes.length;
      if(chars>maxCharacters) {
        throw const FormatException('内容过长，建议缩小时间范围或分册导出');
      }
      bodies[entry.id]=text;
    }
    final fontData=await rootBundle.load(fontAsset);
    final font=pw.Font.ttf(fontData);
    final document=pw.Document(
      title:bookTitle,author:'知行罗盘 · 私人日记',compress:true);
    const dark=PdfColor.fromInt(0xFF40334D);
    const muted=PdfColor.fromInt(0xFF82758B);
    final plum=coverStyle=='rose'
      ? const PdfColor.fromInt(0xFFB06C83)
      : coverStyle=='minimal'
        ? const PdfColor.fromInt(0xFF626B79)
        : const PdfColor.fromInt(0xFF80649B);
    final coverTextStyle=pw.TextStyle(font:font,fontSize:28,color:dark);
    final bodyStyle=pw.TextStyle(font:font,fontSize:11.5,
      color:dark,lineSpacing:6);
    final smallStyle=pw.TextStyle(font:font,fontSize:9,color:muted);
    document.addPage(pw.Page(pageFormat:PdfPageFormat.a5,
      margin:const pw.EdgeInsets.all(33),
      build:(ctx)=>pw.Column(
        mainAxisAlignment:pw.MainAxisAlignment.center,
        crossAxisAlignment:pw.CrossAxisAlignment.center,
        children:[
          pw.Container(height:coverStyle=='minimal'?1:3,width:72,color:plum),
          pw.SizedBox(height:32),
          pw.Text(bookTitle,style:coverTextStyle,textAlign:pw.TextAlign.center),
          pw.SizedBox(height:20),
          if(subtitle.trim().isNotEmpty) ...[
            pw.Text(subtitle.trim(),style:pw.TextStyle(font:font,
              fontSize:13,color:plum),textAlign:pw.TextAlign.center),
            pw.SizedBox(height:12),
          ],
          pw.Text('我的私人日记集',style:smallStyle),
          pw.SizedBox(height:45),
          pw.Text('${formatDate(ordered.first.createdAt)} — '
            '${formatDate(ordered.last.createdAt)}',
            style:smallStyle,textAlign:pw.TextAlign.center),
          pw.SizedBox(height:10),
          pw.Text('共 ${ordered.length} 篇日记',style:smallStyle),
        ])));

    final byMonth=<String,List<CompassEntry>>{};
    for(final entry in ordered) {
      byMonth.putIfAbsent(monthKey(entry.createdAt),()=>[]).add(entry);
    }
    document.addPage(pw.MultiPage(pageFormat:PdfPageFormat.a5,
      margin:const pw.EdgeInsets.fromLTRB(34,36,34,42),
      build:(ctx)=>[
        pw.Header(level:0,text:'目录',
          textStyle:pw.TextStyle(font:font,fontSize:22,color:dark)),
        pw.SizedBox(height:18),
        ...byMonth.entries.expand((month)=>[
          pw.Padding(padding:const pw.EdgeInsets.only(top:10,bottom:4),
            child:pw.Text(month.key,style:pw.TextStyle(
              font:font,fontSize:14,color:plum))),
          ...month.value.map((entry)=>pw.Padding(
            padding:const pw.EdgeInsets.only(left:12,top:3,bottom:3),
            child:pw.Text(
              '${entry.createdAt.day.toString().padLeft(2,'0')} 日 · ${entry.title}',
              style:smallStyle))),
        ]),
      ],
    ));
    if(preface.trim().isNotEmpty) {
      document.addPage(pw.MultiPage(pageFormat:PdfPageFormat.a5,
        margin:const pw.EdgeInsets.fromLTRB(34,36,34,42),
        build:(ctx)=>[
          pw.Header(level:0,text:'序言',textStyle:pw.TextStyle(
            font:font,fontSize:21,color:dark)),
          ...splitBody(preface).map((text)=>pw.Padding(
            padding:const pw.EdgeInsets.only(bottom:6),
            child:pw.Text(text,style:bodyStyle))),
        ]));
    }

    final content=<pw.Widget>[];
    for(final month in byMonth.entries) {
      content.add(pw.Header(level:0,text:month.key,
        textStyle:pw.TextStyle(font:font,fontSize:19,color:plum)));
      for(final entry in month.value) {
        content.add(pw.SizedBox(height:13));
        content.add(pw.Header(level:1,text:entry.title,
          textStyle:pw.TextStyle(font:font,fontSize:15,color:dark)));
        content.add(pw.Text(formatDate(entry.createdAt),style:smallStyle));
        content.add(pw.SizedBox(height:8));
        for(final line in splitBody(bodies[entry.id]!)) {
          content.add(pw.Text(line.isEmpty?' ':line,style:bodyStyle));
        }
        content.add(pw.SizedBox(height:20));
      }
    }
    document.addPage(pw.MultiPage(pageFormat:PdfPageFormat.a5,
      maxPages:1200,
      margin:const pw.EdgeInsets.fromLTRB(34,36,34,44),
      footer:(ctx)=>pw.Align(alignment:pw.Alignment.center,
        child:pw.Text('${ctx.pageNumber}',style:smallStyle)),
      build:(ctx)=>content,
    ));
    return Uint8List.fromList(await document.save());
  }
}
